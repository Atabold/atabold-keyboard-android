package org.futo.inputmethod.latin.kbfr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// kbfr fork: the phone-side logic of Corriger, Améliorer, Traduire and Format (spec 5.1-5.4, 7.1) that
// needs no Android: which text is sent, the request and response of /v1/kb, error codes, and how
// the model's explanations attach to the diff. The text itself only ever lives in memory (R4).

// The whole field, with the selection in the same offsets.
data class FieldText(val text: String, val selStart: Int, val selEnd: Int)

sealed interface CorrectTarget {
    data class Ok(val start: Int, val end: Int, val text: String) : CorrectTarget
    data object Empty : CorrectTarget
    data class TooLong(val maxChars: Int, val selectionMode: Boolean) : CorrectTarget
}

// Corriger, Améliorer, Traduire and Format (spec 5.1-5.4), by the backend's action names.
enum class TextAction(val wire: String) { CORRECT("correct"), IMPROVE("improve"), TRANSLATE("translate"), FORMAT("format") }

@Serializable
data class KbOptions(
    @SerialName("explain_in") val explainIn: String,
    @SerialName("target_lang") val targetLang: String,
    @SerialName("translate_foreign") val translateForeign: Boolean,
    val register: String,
    val detail: String,
    val profile: String,
    @SerialName("translate_to") val translateTo: String,
    @SerialName("format_style") val formatStyle: String,
    @SerialName("format_fix_grammar") val formatFixGrammar: Boolean,
    @SerialName("harmonize_variant") val harmonizeVariant: Boolean,
)

@Serializable
data class KbRequest(val action: String, val text: String, val options: KbOptions)

@Serializable
data class KbEdit(val from: String, val to: String, val category: String, val explanation: String)

// A word of the other English variety (American vs British), left as written: a notice, not an error.
@Serializable
data class KbNote(val from: String, val to: String, val explanation: String)

@Serializable
data class KbResponse(val result: String, val edits: List<KbEdit> = emptyList(), val notes: List<KbNote> = emptyList())

@Serializable
private data class KbErrorBody(val error: String)

enum class KbError {
    NOT_REGISTERED, RATE_LIMITED, DAILY_CAP, PROVIDER_LIMIT, TOO_LARGE, REJECTED, UPSTREAM_ERROR, UPSTREAM_TIMEOUT, UNEXPECTED
}

// The changes one explanation covers, accepted or rejected together with one checkbox.
data class ChangeGroup(val changes: List<DiffChange>, val edits: List<KbEdit>)

data class ExplainedDiff(val parts: List<DiffPart>, val groups: List<ChangeGroup>, val unmatched: List<KbEdit>)

object Correction {
    private val json = Json { ignoreUnknownKeys = true }

    // Chat apps: the whole field, a selection is ignored. Email and documents: the selection if there
    // is one, else the whole field. Too long is refused, never truncated (spec 5.1 point 1).
    fun target(field: FieldText, selectionEnabled: Boolean, maxChars: Int): CorrectTarget {
        val text = field.text
        val selStart = minOf(field.selStart, field.selEnd).coerceIn(0, text.length)
        val selEnd = maxOf(field.selStart, field.selEnd).coerceIn(0, text.length)
        val useSelection = selectionEnabled && selStart < selEnd
        var start = if (useSelection) selStart else 0
        var end = if (useSelection) selEnd else text.length
        // Surrounding whitespace is not sent: the model would drop it and the diff would show that.
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return when {
            start == end -> CorrectTarget.Empty
            end - start > maxChars -> CorrectTarget.TooLong(maxChars, selectionEnabled)
            else -> CorrectTarget.Ok(start, end, text.substring(start, end))
        }
    }

    // The backend ignores the options an action does not read (spec 7.1), so all are sent.
    fun requestBody(action: TextAction, text: String, options: KbOptions): ByteArray =
        json.encodeToString(KbRequest.serializer(), KbRequest(action.wire, text, options)).toByteArray()

    // null when a 200 body does not match the contract.
    fun parseResponse(body: String): KbResponse? =
        try {
            json.decodeFromString(KbResponse.serializer(), body)
        } catch (_: Exception) {
            null
        }

    // The backend's error codes (spec 7.1); the body is never shown, only mapped.
    fun error(status: Int, body: String): KbError {
        val code = try {
            json.decodeFromString(KbErrorBody.serializer(), body).error
        } catch (_: Exception) {
            null
        }
        return when (status) {
            401 -> KbError.NOT_REGISTERED
            429 -> when (code) {
                "daily_cap" -> KbError.DAILY_CAP
                "provider_limit" -> KbError.PROVIDER_LIMIT
                else -> KbError.RATE_LIMITED
            }
            413 -> KbError.TOO_LARGE
            400 -> KbError.REJECTED
            502 -> KbError.UPSTREAM_ERROR
            504 -> KbError.UPSTREAM_TIMEOUT
            else -> KbError.UNEXPECTED
        }
    }

    // An edit claims a change when a whole-word occurrence of edit.from in the original overlaps it,
    // or one of edit.to in the result does ("a → à" must not claim the "a" inside "la"). Each change
    // then goes to the most specific edit claiming it, the one with the shortest text: the model
    // copies some context into from/to, and a context reaching a nearby error must not tie two
    // errors to one checkbox. An edit left with no change of its own joins the group of the first
    // change it claims; an edit that claims nothing is listed apart.
    // keepAsWritten: the notes when harmonize_variant is off. Their words must stay as written
    // (spec 7.1), but the model sometimes changes them in the result anyway: such a change becomes
    // unchanged text again, and an edit for it is dropped.
    fun explain(original: String, result: String, allEdits: List<KbEdit>, keepAsWritten: List<KbNote> = emptyList()): ExplainedDiff {
        fun noted(from: String, to: String) = keepAsWritten.any { it.from == from.trim() && it.to == to.trim() }
        var next = 0
        val parts = WordDiff.diff(original, result).map { part ->
            when {
                part !is DiffPart.Changed -> part
                noted(part.change.from, part.change.to) -> DiffPart.Same(part.change.from)
                else -> DiffPart.Changed(part.change.copy(index = next++))
            }
        }
        val changes = parts.filterIsInstance<DiffPart.Changed>().map { it.change }
        val edits = allEdits.filterNot { noted(it.from, it.to) }

        val claims = edits.map { edit ->
            changes.filter { c ->
                overlaps(original, edit.from, c.origStart, c.origEnd) || overlaps(result, edit.to, c.resStart, c.resEnd)
            }.map { it.index }
        }
        // Group key of each change: the index of the edit that gets it, or -(change + 1) when no
        // edit claims it, so it gets a group of its own.
        val keyOf = changes.map { c ->
            edits.indices.filter { c.index in claims[it] }.minByOrNull { edits[it].from.length + edits[it].to.length }
                ?: -(c.index + 1)
        }
        val groupEdits = LinkedHashMap<Int, MutableList<KbEdit>>()
        keyOf.forEach { groupEdits.getOrPut(it) { ArrayList() } }
        val unmatched = ArrayList<KbEdit>()
        edits.forEachIndexed { e, edit -> groupEdits[e]?.add(edit) }
        edits.forEachIndexed { e, edit ->
            when {
                e in groupEdits -> Unit
                claims[e].isEmpty() -> unmatched += edit
                else -> groupEdits.getValue(keyOf[claims[e].first()]) += edit
            }
        }

        val groups = groupEdits.map { (key, explanations) -> ChangeGroup(changes.filter { keyOf[it.index] == key }, explanations) }
        return ExplainedDiff(parts, groups, unmatched)
    }

    // The text to insert: accepted changes take the model's text, and each checked note changes its
    // word where the text keeps it as written.
    fun apply(parts: List<DiffPart>, acceptedChanges: Set<Int>, acceptedNotes: List<KbNote>): String = buildString {
        for (part in parts) when (part) {
            is DiffPart.Same -> splitByNotes(part.text, acceptedNotes).forEach { (piece, note) -> append(note?.to ?: piece) }
            is DiffPart.Changed -> append(if (part.change.index in acceptedChanges) part.change.to else part.change.from)
        }
    }

    // Pieces of an unchanged text, with the whole-word occurrences of the notes' words marked.
    fun splitByNotes(text: String, notes: List<KbNote>): List<Pair<String, KbNote?>> {
        val pieces = ArrayList<Pair<String, KbNote?>>()
        var pos = 0
        while (true) {
            var nextAt = -1
            var nextNote: KbNote? = null
            for (note in notes) {
                if (note.from.isBlank()) continue
                var at = text.indexOf(note.from, pos)
                while (at >= 0 && !isWholeWord(text, at, at + note.from.length)) at = text.indexOf(note.from, at + 1)
                if (at >= 0 && (nextAt < 0 || at < nextAt)) {
                    nextAt = at
                    nextNote = note
                }
            }
            val note = nextNote ?: break
            if (nextAt > pos) pieces += text.substring(pos, nextAt) to null
            pieces += note.from to note
            pos = nextAt + note.from.length
        }
        if (pos < text.length) pieces += text.substring(pos) to null
        return pieces
    }

    private fun overlaps(text: String, needle: String, start: Int, end: Int): Boolean {
        if (needle.isBlank()) return false
        var s = start
        var e = end
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        var at = text.indexOf(needle)
        while (at >= 0) {
            val stop = at + needle.length
            // A pure insertion (s == e) is a point: it counts when it touches the occurrence.
            val hit = if (s == e) s in at..stop else at < e && s < stop
            if (hit && isWholeWord(text, at, stop)) return true
            at = text.indexOf(needle, at + 1)
        }
        return false
    }

    private fun isWholeWord(text: String, start: Int, stop: Int): Boolean {
        val cutsAtStart = start > 0 && WordDiff.isWordChar(text[start - 1]) && WordDiff.isWordChar(text[start])
        val cutsAtStop = stop < text.length && WordDiff.isWordChar(text[stop - 1]) && WordDiff.isWordChar(text[stop])
        return !cutsAtStart && !cutsAtStop
    }
}
