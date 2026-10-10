package org.futo.inputmethod.latin.kbfr

// kbfr fork: which copied texts look sensitive by themselves (spec 5.5). The other two criteria,
// the EXTRA_IS_SENSITIVE flag and being copied in a blocked context, are checked by the callers.
object ClipboardSensitivity {
    // Fixed, not a setting (spec 5.5).
    const val SENSITIVE_TTL_MS = 60_000L

    const val MASK = "••••••"

    private val OTP = Regex("""\d{6,8}""")
    private val EMAIL = Regex("""[^\s@]+@[^\s@]+\.[^\s@]+""")

    @JvmStatic
    fun looksSensitive(text: String): Boolean {
        val trimmed = text.trim()
        return OTP.matches(trimmed) || looksLikeSecret(trimmed)
    }

    // A random-looking token (passwords, API keys, IBANs): 12+ characters without spaces, mixing
    // letters and digits, that is not a link or an email address.
    private fun looksLikeSecret(text: String): Boolean =
        text.length >= 12 &&
            text.none { it.isWhitespace() } &&
            text.any { it.isLetter() } &&
            text.any { it.isDigit() } &&
            !text.contains("://") &&
            !text.startsWith("www.", ignoreCase = true) &&
            !EMAIL.matches(text)
}
