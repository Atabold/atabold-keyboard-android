package org.futo.inputmethod.latin.kbfr

// kbfr fork: the check of spec 5.6, run on the phone on the text about to be sent, before every
// request. Only the categories found leave this object, never the values (R4).

// defaultRule is the spec's default, stored like the setting: "block", "ask" or "ignore".
enum class SensitiveKind(val defaultRule: String) {
    CARD("block"), IBAN("block"), SECRET("block"), TAX_CODE("ask"), OTP("ask"), EMAIL("ignore"), PHONE("ignore")
}

sealed interface SendCheck {
    data object Send : SendCheck

    // found is empty when only "confirm before sending" asks for it.
    data class Confirm(val found: List<SensitiveKind>) : SendCheck

    data class Blocked(val found: List<SensitiveKind>) : SendCheck
}

object SensitiveData {
    // 13-19 digits, written in one piece or in the usual groups: 4-4-4-x and the 4-6-5 of Amex.
    private val CARD = Regex("""(?<![\d+])(?:\d{13,19}|\d{4}(?:[ -]\d{4}){2,3}[ -]\d{1,4}|\d{4}[ -]\d{6}[ -]\d{4,5})(?!\d)""")

    // Country code in capitals, check digits, then the account in one piece or in groups of four.
    private val IBAN = Regex("""\b[A-Z]{2}\d{2}(?:[A-Za-z0-9]{11,30}|(?: [A-Za-z0-9]{4}){2,7}(?: [A-Za-z0-9]{1,4})?)\b""")

    // Tax and national ID numbers (kind TAX_CODE): one pattern per country, and its check where the number has
    // one. A match that fails its check is not reported.
    private class NationalId(val pattern: Regex, val isValid: (String) -> Boolean)

    private val NATIONAL_IDS = listOf(
        // IT: 16 characters; a variant of the format replaces some digits with the letters L-V, so those places take either.
        NationalId(Regex("""(?i)\b[A-Z]{6}[\dL-NP-V]{2}[A-Z][\dL-NP-V]{2}[A-Z][\dL-NP-V]{3}[A-Z]\b""")) { checkIt(it) },
        // ES: DNI (8 digits and a letter) or NIE (X, Y or Z, 7 digits and a letter).
        NationalId(Regex("""(?i)\b(?:\d{8}-?[A-Z]|[XYZ]-?\d{7}-?[A-Z])\b""")) { checkEs(it) },
        // FR: social security number (NIR), 15 digits in one piece or in groups; 2A and 2B are Corsica.
        NationalId(Regex("""(?i)\b[12][ .]?\d{2}[ .]?(?:0[1-9]|1[0-2]|[2-9]\d)[ .]?(?:\d{2}|2[AB])[ .]?\d{3}[ .]?\d{3}[ .]?\d{2}\b""")) { checkFr(it) },
        // DE: tax identification number, 11 digits in one piece or as 2-3-3-3.
        NationalId(Regex("""\b[1-9]\d ?\d{3} ?\d{3} ?\d{3}\b""")) { checkDe(it) },
        // GB: National Insurance number, two letters, six digits and a letter from A to D.
        NationalId(Regex("""(?i)\b[A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z] ?\d{2} ?\d{2} ?\d{2} ?[A-D]\b""")) { checkGb(it) },
        // US: Social Security number, 3-2-4 digits with hyphens or spaces.
        NationalId(Regex("""\b\d{3}[- ]\d{2}[- ]\d{4}\b""")) { checkUs(it) },
        // CH: AHV / AVS number, 756 and ten more digits, usually written 756.xxxx.xxxx.xx.
        NationalId(Regex("""\b756[. ]?\d{4}[. ]?\d{4}[. ]?\d{2}\b""")) { checkCh(it) },
    )

    // 6-8 digits on their own: not part of a longer number, also one written with separators.
    private val OTP = Regex("""(?<![\p{L}\d])(?<!\d[ .,/:-])\d{6,8}(?![ .,/:-]?\d)(?!\p{L})""")

    private val EMAIL = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}""")

    // International format only (spec 5.6): + or 00, then 8-15 digits.
    private val PHONE = Regex("""(?<![\p{L}\d+])(?:\+|00)\d(?:[ .-]?\d){7,14}(?!\d)""")

    // Well-known key formats: OpenAI/Anthropic/Stripe, GitHub, GitLab, Slack, AWS, Google, JWT, PEM.
    private val SECRET_PREFIXES = Regex(
        """\b(?:[sr]k[-_][A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|glpat-[A-Za-z0-9_-]{20,}""" +
            """|xox[abprs]-[A-Za-z0-9-]{10,}|(?:AKIA|ASIA)[A-Z0-9]{16}|AIza[A-Za-z0-9_-]{35}""" +
            """|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,})|-----BEGIN [A-Z ]*PRIVATE KEY-----"""
    )

    private const val QUOTES_AND_PUNCTUATION = "\"'()[]{}<>.,;:!?«»“”‘’"

    fun find(text: String): Set<SensitiveKind> {
        val found = LinkedHashSet<SensitiveKind>()
        if (CARD.findAll(text).any { luhn(it.value.filter(Char::isDigit)) }) found += SensitiveKind.CARD
        if (IBAN.findAll(text).any { isIban(it.value) }) found += SensitiveKind.IBAN
        if (SECRET_PREFIXES.containsMatchIn(text) || tokens(text).any { looksRandom(it) }) found += SensitiveKind.SECRET
        if (NATIONAL_IDS.any { id -> id.pattern.findAll(text).any { id.isValid(it.value) } }) found += SensitiveKind.TAX_CODE
        if (OTP.containsMatchIn(text)) found += SensitiveKind.OTP
        if (EMAIL.containsMatchIn(text)) found += SensitiveKind.EMAIL
        if (PHONE.containsMatchIn(text)) found += SensitiveKind.PHONE
        return found
    }

    // Any category set to block stops the request; otherwise "ask" categories, or the confirm
    // setting, open the confirmation first. ruleOf returns the stored setting value.
    fun check(text: String, ruleOf: (SensitiveKind) -> String, confirm: Boolean): SendCheck {
        val found = find(text)
        val blocked = found.filter { ruleOf(it) == "block" }
        if (blocked.isNotEmpty()) return SendCheck.Blocked(blocked)
        val ask = found.filter { ruleOf(it) == "ask" }
        return if (ask.isNotEmpty() || confirm) SendCheck.Confirm(ask) else SendCheck.Send
    }

    // "Confirm before sending" (spec 6): never / always / above N characters.
    fun confirmNeeded(mode: String, aboveChars: Int, length: Int): Boolean =
        mode == "always" || (mode == "above" && length > aboveChars)

    fun luhn(digits: String): Boolean {
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return sum % 10 == 0
    }

    // In the grouped form a following word of four letters can look like one more group, so every
    // shorter run of groups is tried too.
    private fun isIban(match: String): Boolean {
        val groups = match.split(' ')
        return (groups.size downTo 1).any { n ->
            val compact = groups.take(n).joinToString("").uppercase()
            compact.length in 15..34 && ibanMod97(compact) == 1
        }
    }

    // ISO 13616: the first four characters move to the end, letters become 10-35.
    private fun ibanMod97(iban: String): Int {
        var r = 0
        for (c in iban.substring(4) + iban.substring(0, 4)) {
            val v = if (c.isDigit()) c - '0' else c - 'A' + 10
            r = (if (v >= 10) r * 100 + v else r * 10 + v) % 97
        }
        return r
    }

    private fun isNationalId(token: String): Boolean = NATIONAL_IDS.any { it.pattern.matches(token) && it.isValid(token) }

    // IT: values of the characters in odd places (1st, 3rd, ...), digits and letters alike.
    private val IT_ODD = intArrayOf(1, 0, 5, 7, 9, 13, 15, 17, 19, 21, 2, 4, 18, 20, 11, 3, 6, 8, 12, 14, 16, 10, 22, 25, 24, 23)

    private fun checkIt(code: String): Boolean {
        val id = code.uppercase()
        val sum = (0 until 15).sumOf { i ->
            val v = if (id[i].isDigit()) id[i] - '0' else id[i] - 'A'
            if (i % 2 == 0) IT_ODD[v] else v
        }
        return id[15] == 'A' + sum % 26
    }

    // ES: the letter is "TRWAGMYFPDXBNJZSQVHLCKE"[number mod 23]; in a NIE, X, Y and Z stand for 0, 1 and 2.
    private fun checkEs(code: String): Boolean {
        val id = code.uppercase().replace("-", "")
        val prefix = when (id[0]) {
            'X' -> "0"
            'Y' -> "1"
            'Z' -> "2"
            else -> ""
        }
        val number = if (prefix.isEmpty()) id.substring(0, 8) else prefix + id.substring(1, 8)
        return "TRWAGMYFPDXBNJZSQVHLCKE"[number.toInt() % 23] == id.last()
    }

    // FR: the last two digits are 97 minus the first thirteen mod 97; Corsica's 2A and 2B count as 19 and 18.
    private fun checkFr(code: String): Boolean {
        val id = code.uppercase().filter { it.isLetterOrDigit() }
        if (id.length != 15) return false
        val number = id.substring(0, 13).replace("2A", "19").replace("2B", "18").toLongOrNull() ?: return false
        return 97 - number % 97 == id.substring(13).toLong()
    }

    private val THREE_IN_A_ROW = Regex("""(\d)\1\1""")

    // DE: among the first ten digits exactly one appears two or three times and none three times in a row; the
    // eleventh is the ISO 7064 (mod 11, 10) check of the first ten.
    private fun checkDe(code: String): Boolean {
        val digits = code.filter(Char::isDigit)
        if (digits.length != 11) return false
        val first = digits.substring(0, 10)
        val counts = IntArray(10).also { c -> first.forEach { c[it - '0']++ } }
        if (counts.count { it >= 2 } != 1 || counts.any { it > 3 } || THREE_IN_A_ROW.containsMatchIn(first)) return false
        var product = 10
        for (c in first) {
            var sum = (c - '0' + product) % 10
            if (sum == 0) sum = 10
            product = sum * 2 % 11
        }
        return (11 - product) % 10 == digits[10] - '0'
    }

    // GB: these prefixes are never issued.
    private val GB_UNUSED_PREFIXES = setOf("BG", "GB", "KN", "NK", "NT", "TN", "ZZ")

    private fun checkGb(code: String): Boolean = code.filterNot(Char::isWhitespace).uppercase().take(2) !in GB_UNUSED_PREFIXES

    // US: no area 000, 666 or 900-999, no group 00, no serial 0000.
    private fun checkUs(code: String): Boolean {
        val digits = code.filter(Char::isDigit)
        val area = digits.substring(0, 3).toInt()
        return area != 0 && area != 666 && area < 900 && digits.substring(3, 5) != "00" && digits.substring(5) != "0000"
    }

    // CH: the last digit is the EAN-13 check of the first twelve.
    private fun checkCh(code: String): Boolean {
        val digits = code.filter(Char::isDigit)
        if (digits.length != 13) return false
        val sum = (0 until 12).sumOf { (digits[it] - '0') * if (it % 2 == 0) 1 else 3 }
        return (10 - sum % 10) % 10 == digits[12] - '0'
    }

    private fun tokens(text: String): Sequence<String> =
        text.split(Regex("""\s+""")).asSequence().map { it.trim { c -> c in QUOTES_AND_PUNCTUATION } }

    // A generated key or password: 12+ characters mixing letters and digits, where the kind of
    // character (lower, upper, digit) changes often. Model names, file names and words with a year
    // change rarely ("claude-opus-4-1", "IMG_20250805_1234"), keys and UUIDs often. Links, emails,
    // @mentions, #hashtags, IBANs and national IDs are not secrets here.
    private fun looksRandom(token: String): Boolean {
        if (token.length < 12 || token.contains("://") || token.startsWith("www.", ignoreCase = true)) return false
        if (token.startsWith('@') || token.startsWith('#') || EMAIL.matches(token)) return false
        if (IBAN.matches(token) && isIban(token) || isNationalId(token)) return false
        val kinds = token.mapNotNull {
            when {
                it.isDigit() -> 0
                it.isLowerCase() -> 1
                it.isUpperCase() -> 2
                else -> null
            }
        }
        if (0 !in kinds || kinds.none { it != 0 }) return false
        val changes = kinds.zipWithNext().count { (a, b) -> a != b }
        return changes >= 0.3 * (kinds.size - 1)
    }
}
