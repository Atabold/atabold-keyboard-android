package org.futo.inputmethod.latin.kbfr

// kbfr fork: the word-by-word diff of the Corriger panel, computed on the phone (spec 5.1 point 4).

// Offsets are in the original text (origStart/origEnd) and in the model's result (resStart/resEnd).
data class DiffChange(
    val index: Int,
    val from: String,
    val to: String,
    val origStart: Int,
    val origEnd: Int,
    val resStart: Int,
    val resEnd: Int,
)

sealed interface DiffPart {
    data class Same(val text: String) : DiffPart
    data class Changed(val change: DiffChange) : DiffPart
}

object WordDiff {
    // Words (letters, marks, digits), runs of whitespace, and every other character on its own, so an
    // apostrophe or a hyphen is its own token: "la université" → "l'université" keeps "université".
    // DOT_MATCHES_ALL: every character lands in a token, or it would be lost from the inserted text.
    private val TOKEN = Regex("""[\p{L}\p{M}\p{N}_]+|\s+|.""", RegexOption.DOT_MATCHES_ALL)

    // Above this many LCS cells the middle part becomes one change instead: bounded memory on the phone.
    private const val MAX_CELLS = 4_000_000

    fun isWordChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || Character.getType(c).let {
        it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() || it == Character.ENCLOSING_MARK.toInt()
    }

    fun tokenize(text: String): List<String> = TOKEN.findAll(text).map { it.value }.toList()

    fun diff(original: String, result: String): List<DiffPart> {
        val a = tokenize(original)
        val b = tokenize(result)

        var prefix = 0
        while (prefix < a.size && prefix < b.size && a[prefix] == b[prefix]) prefix++
        var suffix = 0
        while (suffix < a.size - prefix && suffix < b.size - prefix && a[a.size - 1 - suffix] == b[b.size - 1 - suffix]) suffix++

        val ops = ArrayList<Op>()
        repeat(prefix) { ops += Op.Same(a[it]) }
        ops += middleOps(a.subList(prefix, a.size - suffix), b.subList(prefix, b.size - suffix))
        for (i in a.size - suffix until a.size) ops += Op.Same(a[i])
        return group(ops)
    }

    // Accepted changes take the model's text, the others keep the original.
    fun apply(parts: List<DiffPart>, accepted: Set<Int>): String = buildString {
        for (part in parts) when (part) {
            is DiffPart.Same -> append(part.text)
            is DiffPart.Changed -> append(if (part.change.index in accepted) part.change.to else part.change.from)
        }
    }

    private sealed interface Op {
        val token: String
        data class Same(override val token: String) : Op
        data class Del(override val token: String) : Op
        data class Ins(override val token: String) : Op
    }

    private fun middleOps(a: List<String>, b: List<String>): List<Op> {
        if (a.isEmpty() || b.isEmpty() || a.size.toLong() * b.size > MAX_CELLS) {
            return a.map { Op.Del(it) } + b.map { Op.Ins(it) }
        }
        // lcs[i * width + j] = LCS length of a[i..] and b[j..]
        val width = b.size + 1
        val lcs = IntArray((a.size + 1) * width)
        for (i in a.size - 1 downTo 0) {
            for (j in b.size - 1 downTo 0) {
                lcs[i * width + j] = if (a[i] == b[j]) {
                    lcs[(i + 1) * width + j + 1] + 1
                } else {
                    maxOf(lcs[(i + 1) * width + j], lcs[i * width + j + 1])
                }
            }
        }
        val ops = ArrayList<Op>()
        var i = 0
        var j = 0
        while (i < a.size && j < b.size) {
            if (a[i] == b[j]) {
                ops += Op.Same(a[i]); i++; j++
                continue
            }
            val afterDelete = lcs[(i + 1) * width + j]
            val afterInsert = lcs[i * width + j + 1]
            val delete = when {
                afterDelete != afterInsert -> afterDelete > afterInsert
                // A tie keeps the LCS either way: take the step that matches right away. With
                // "a la école" → "à l'école" the space after "a" stays common, so the two fixes
                // stay two changes instead of one.
                i + 1 < a.size && a[i + 1] == b[j] -> true
                j + 1 < b.size && a[i] == b[j + 1] -> false
                else -> true
            }
            if (delete) ops += Op.Del(a[i++]) else ops += Op.Ins(b[j++])
        }
        while (i < a.size) ops += Op.Del(a[i++])
        while (j < b.size) ops += Op.Ins(b[j++])
        return ops
    }

    // Consecutive deletions and insertions become one change.
    private fun group(ops: List<Op>): List<DiffPart> {
        val parts = ArrayList<DiffPart>()
        val same = StringBuilder()
        val from = StringBuilder()
        val to = StringBuilder()
        var origPos = 0
        var resPos = 0
        var changeOrigStart = 0
        var changeResStart = 0
        var changeCount = 0

        fun flushChange() {
            if (from.isEmpty() && to.isEmpty()) return
            parts += DiffPart.Changed(
                DiffChange(changeCount++, from.toString(), to.toString(), changeOrigStart, origPos, changeResStart, resPos)
            )
            from.clear()
            to.clear()
        }

        for (op in ops) {
            if (op is Op.Same) {
                flushChange()
                same.append(op.token)
                origPos += op.token.length
                resPos += op.token.length
                continue
            }
            if (from.isEmpty() && to.isEmpty()) {
                if (same.isNotEmpty()) parts += DiffPart.Same(same.toString())
                same.clear()
                changeOrigStart = origPos
                changeResStart = resPos
            }
            if (op is Op.Del) {
                from.append(op.token)
                origPos += op.token.length
            } else {
                to.append(op.token)
                resPos += op.token.length
            }
        }
        flushChange()
        if (same.isNotEmpty()) parts += DiffPart.Same(same.toString())
        return parts
    }
}
