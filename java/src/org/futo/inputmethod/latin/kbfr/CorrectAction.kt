package org.futo.inputmethod.latin.kbfr

import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.ActionWindow
import org.futo.inputmethod.latin.uix.CloseResult
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.theme.Typography
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale

// kbfr fork: Corriger, Améliorer, Traduire and Format end to end (spec 5.1-5.4): which text, the
// checks before sending (5.6, confirm), the signed request, the results panel, the checked
// replacement and Undo. The text and the result stay in memory only (R4).

// Each action checks and reads the text, then opens the panel. Problems before sending go to the
// status strip, like the other online actions.
val CorrectAction = Action(
    icon = R.drawable.icon_spellcheck,
    name = R.string.kbfr_action_correct,
    windowImpl = null,
    simplePressImpl = { manager, _ -> startTextAction(manager, TextAction.CORRECT) },
)

val ImproveAction = Action(
    icon = R.drawable.kbfr_improve,
    name = R.string.kbfr_action_improve,
    windowImpl = null,
    simplePressImpl = { manager, _ -> startTextAction(manager, TextAction.IMPROVE) },
)

val TranslateAction = Action(
    icon = R.drawable.kbfr_translate,
    name = R.string.kbfr_action_translate,
    windowImpl = null,
    simplePressImpl = { manager, _ -> startTextAction(manager, TextAction.TRANSLATE) },
)

// Manual Format: the text to process, like the actions above (spec 5.4). The automatic one is in
// DictationFormat.
val FormatAction = Action(
    icon = R.drawable.kbfr_format,
    name = R.string.kbfr_action_format,
    windowImpl = null,
    simplePressImpl = { manager, _ -> startTextAction(manager, TextAction.FORMAT) },
)

// Opened only by the actions above, never shown in the action editor.
val CorrectPanelAction = Action(
    icon = R.drawable.icon_spellcheck,
    name = R.string.kbfr_action_correct_panel,
    windowImpl = { manager, _ -> CorrectPanel(manager, pendingRequest.also { pendingRequest = null }) },
    simplePressImpl = null,
    shownInEditor = false,
)

// What the panel works on. confirm: null to send at once, else the panel asks first, naming the
// sensitive categories found (none when only the "confirm before sending" setting asks).
private class PanelRequest(val action: TextAction, val target: CorrectTarget.Ok, val confirm: List<SensitiveKind>?)

private var pendingRequest: PanelRequest? = null
private var startJob: Job? = null

// Field offsets beyond this are not trusted: a field this long is refused anyway (max 4000 sent).
private const val MAX_FIELD_CHARS = 100_000

// The checks of spec 5.1 point 2: policy (online gate), the text, the character limit, the
// sensitive data (5.6) and the confirmation the settings ask for.
private fun startTextAction(manager: KeyboardManagerForAction, action: TextAction) {
    if (startJob?.isActive == true) return
    val context = manager.getContext()
    startJob = manager.getLifecycleScope().launch {
        KbfrState.recheckContamination(inputConnection(manager))
        OnlineGate.check(context)?.let {
            KbfrState.showMessage(context.getString(it.label()))
            return@launch
        }
        val policy = KbfrState.policy ?: return@launch
        val field = inputConnection(manager)?.let { readField(it) }
        if (field == null) {
            KbfrState.showMessage(context.getString(R.string.kbfr_correct_unreadable))
            return@launch
        }
        when (val target = Correction.target(field, policy.selectionEnabled, context.getSetting(KbfrMaxChars))) {
            CorrectTarget.Empty -> KbfrState.showMessage(context.getString(action.emptyMessage()))
            is CorrectTarget.TooLong -> KbfrState.showMessage(
                context.getString(
                    if (target.selectionMode) R.string.kbfr_correct_select_part else R.string.kbfr_correct_too_long,
                    target.maxChars,
                )
            )
            is CorrectTarget.Ok -> {
                val confirm = SensitiveData.confirmNeeded(
                    context.getSetting(KbfrConfirmMode), context.getSetting(KbfrConfirmAboveChars), target.text.length,
                )
                val check = SensitiveData.check(target.text, { context.getSetting(KbfrSensitiveRules.getValue(it)) }, confirm)
                if (check is SendCheck.Blocked) {
                    KbfrState.showMessage(context.getString(R.string.kbfr_sensitive_blocked, kindNames(context, check.found)))
                } else {
                    pendingRequest = PanelRequest(action, target, (check as? SendCheck.Confirm)?.found)
                    manager.activateAction(CorrectPanelAction)
                }
            }
        }
    }
}

fun kindNames(context: Context, kinds: List<SensitiveKind>): String = kinds.joinToString(", ") { context.getString(it.label()) }

@StringRes
private fun TextAction.title(): Int = when (this) {
    TextAction.CORRECT -> R.string.kbfr_action_correct
    TextAction.IMPROVE -> R.string.kbfr_action_improve
    TextAction.TRANSLATE -> R.string.kbfr_action_translate
    TextAction.FORMAT -> R.string.kbfr_action_format
}

@StringRes
private fun TextAction.emptyMessage(): Int = when (this) {
    TextAction.CORRECT -> R.string.kbfr_correct_empty
    TextAction.IMPROVE -> R.string.kbfr_improve_empty
    TextAction.TRANSLATE -> R.string.kbfr_translate_empty
    TextAction.FORMAT -> R.string.kbfr_format_empty
}

@StringRes
private fun TextAction.noChangesMessage(): Int = when (this) {
    TextAction.CORRECT -> R.string.kbfr_correct_no_errors
    TextAction.IMPROVE -> R.string.kbfr_improve_no_changes
    TextAction.TRANSLATE -> R.string.kbfr_translate_no_changes
    TextAction.FORMAT -> R.string.kbfr_format_no_changes
}

@StringRes
private fun TextAction.undoLabel(): Int = when (this) {
    TextAction.CORRECT -> R.string.kbfr_undo_corrected
    TextAction.IMPROVE -> R.string.kbfr_undo_improved
    TextAction.TRANSLATE -> R.string.kbfr_undo_translated
    TextAction.FORMAT -> R.string.kbfr_undo_formatted
}

// The app's own connection, not one FUTO may put in place for a text field inside the keyboard.
fun inputConnection(manager: KeyboardManagerForAction): InputConnection? =
    manager.getLatinIMEForDebug().getBaseInputConnection()

// Also used by R3 (KbfrState.recheckContamination).
fun readField(ic: InputConnection): FieldText? {
    val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = MAX_FIELD_CHARS }, 0)
    val text = extracted?.text
    if (extracted != null && text != null && extracted.startOffset == 0 && extracted.partialStartOffset < 0 && text.length < MAX_FIELD_CHARS) {
        return FieldText(text.toString(), extracted.selectionStart, extracted.selectionEnd)
    }
    // Some editors give no extracted text: rebuild the field around the cursor.
    val before = ic.getTextBeforeCursor(MAX_FIELD_CHARS, 0) ?: return null
    val selected = ic.getSelectedText(0) ?: ""
    val after = ic.getTextAfterCursor(MAX_FIELD_CHARS, 0) ?: return null
    if (before.length >= MAX_FIELD_CHARS || after.length >= MAX_FIELD_CHARS) return null
    return FieldText("$before$selected$after", before.length, before.length + selected.length)
}

// Selects the range by its offsets and reads it back: only if the editor returns exactly the
// expected text are the offsets right and the text unchanged, and only then is it replaced (spec 5.1
// point 5). Not inside a batch edit: some editors apply a batched selection only at its end.
private fun replaceChecked(ic: InputConnection, start: Int, end: Int, expected: String, replacement: String, cursorOnFailure: Int): Boolean {
    ic.finishComposingText()
    if (!ic.setSelection(start, end) || ic.getSelectedText(0)?.toString() != expected) {
        ic.setSelection(cursorOnFailure, cursorOnFailure)
        return false
    }
    ic.beginBatchEdit()
    ic.commitText(replacement, 1)
    ic.endBatchEdit()
    return true
}

// Called from the "Corrected · Undo" strip (or Improved / Translated).
fun undoCorrection() {
    val undo = KbfrState.undo ?: return
    KbfrState.undo = null
    val end = undo.start + undo.inserted.length
    val ic = inputConnection(undo.manager)
    if (ic == null || !replaceChecked(ic, undo.start, end, undo.inserted, undo.original, end)) {
        KbfrState.showMessage(undo.manager.getContext().getString(R.string.kbfr_text_changed))
    }
}

sealed interface KbOutcome {
    class Ok(val response: KbResponse, val elapsedMs: Long) : KbOutcome

    // message: the short error of spec 5.7, ready to show.
    class Failed(val message: String) : KbOutcome
}

// One signed POST /v1/kb with every option of the settings: the backend ignores the ones an action
// does not read (spec 7.1).
suspend fun sendTextAction(context: Context, action: TextAction, text: String, timeoutSeconds: Int): KbOutcome {
    val base = BackendClient.baseUrl(BuildConfig.KBFR_ENDPOINT)
        ?: return KbOutcome.Failed(context.getString(R.string.kbfr_ping_invalid_endpoint))
    val body = Correction.requestBody(
        action,
        text,
        KbOptions(
            explainIn = context.getSetting(KbfrExplainIn),
            targetLang = context.getSetting(KbfrTargetLang),
            translateForeign = context.getSetting(KbfrTranslateForeign),
            register = context.getSetting(KbfrRegister),
            detail = context.getSetting(KbfrDetail),
            profile = context.getSetting(KbfrProfile),
            translateTo = context.getSetting(KbfrTranslateTo),
            formatStyle = context.getSetting(KbfrFormatStyle),
            formatFixGrammar = context.getSetting(KbfrFormatFixGrammar),
            harmonizeVariant = context.getSetting(KbfrHarmonizeVariant),
        ),
    )
    val started = SystemClock.elapsedRealtime()
    val response = try {
        withContext(Dispatchers.IO) { BackendClient.signedPost(base, "/v1/kb", body, timeoutSeconds * 1000) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: SocketTimeoutException) {
        return KbOutcome.Failed(context.getString(R.string.kbfr_err_timeout, timeoutSeconds))
    } catch (_: IOException) {
        return KbOutcome.Failed(context.getString(R.string.kbfr_ping_unreachable))
    } catch (_: Exception) {
        return KbOutcome.Failed(context.getString(R.string.kbfr_ping_unexpected))
    }
    val elapsed = SystemClock.elapsedRealtime() - started

    if (response.status != 200) return KbOutcome.Failed(context.getString(Correction.error(response.status, response.body).label()))
    val parsed = Correction.parseResponse(response.body) ?: return KbOutcome.Failed(context.getString(R.string.kbfr_ping_unexpected))
    return KbOutcome.Ok(parsed, elapsed)
}

@StringRes
private fun KbError.label(): Int = when (this) {
    KbError.NOT_REGISTERED -> R.string.kbfr_err_not_registered
    KbError.RATE_LIMITED -> R.string.kbfr_err_rate_limited
    KbError.DAILY_CAP -> R.string.kbfr_err_daily_cap
    KbError.PROVIDER_LIMIT -> R.string.kbfr_err_provider_limit
    KbError.TOO_LARGE -> R.string.kbfr_err_too_large
    KbError.REJECTED -> R.string.kbfr_err_rejected
    KbError.UPSTREAM_ERROR -> R.string.kbfr_err_upstream
    KbError.UPSTREAM_TIMEOUT -> R.string.kbfr_err_upstream_timeout
    KbError.UNEXPECTED -> R.string.kbfr_ping_unexpected
}

private val CategoryLabels = mapOf(
    "spelling" to R.string.kbfr_cat_spelling,
    "accents" to R.string.kbfr_cat_accents,
    "elision" to R.string.kbfr_cat_elision,
    "agreement" to R.string.kbfr_cat_agreement,
    "past_participle_agreement" to R.string.kbfr_cat_past_participle_agreement,
    "conjugation" to R.string.kbfr_cat_conjugation,
    "tense_mood" to R.string.kbfr_cat_tense_mood,
    "article" to R.string.kbfr_cat_article,
    "preposition" to R.string.kbfr_cat_preposition,
    "pronoun" to R.string.kbfr_cat_pronoun,
    "word_order" to R.string.kbfr_cat_word_order,
    "punctuation" to R.string.kbfr_cat_punctuation,
    "capitalization" to R.string.kbfr_cat_capitalization,
    "register" to R.string.kbfr_cat_register,
    "vocabulary" to R.string.kbfr_cat_vocabulary,
    "false_friend" to R.string.kbfr_cat_false_friend,
    "translation" to R.string.kbfr_cat_translation,
    "style" to R.string.kbfr_cat_style,
    "transcription" to R.string.kbfr_cat_transcription,
    "variant" to R.string.kbfr_cat_variant,
)

private val Removed = SpanStyle(color = Color(0xFFE5484D), textDecoration = TextDecoration.LineThrough)
private val Added = SpanStyle(color = Color(0xFF30A46C), fontWeight = FontWeight.SemiBold)

private class CorrectPanel(
    private val manager: KeyboardManagerForAction,
    request: PanelRequest?,
) : ActionWindow() {
    private sealed interface State {
        // Nothing is sent before the user taps Send.
        data class Confirm(val found: List<SensitiveKind>) : State
        data object Sending : State
        data class Failed(val message: String) : State
        data class NoErrors(val elapsedMs: Long) : State
        data class Done(val explained: ExplainedDiff, val notes: List<KbNote>, val elapsedMs: Long) : State
        // Traduire: the result only, no single changes (spec 5.3).
        data class Translated(val result: String, val elapsedMs: Long) : State
    }

    private val context = manager.getContext()
    private val action = request?.action ?: TextAction.CORRECT
    private val target = request?.target
    private var state by mutableStateOf<State>(State.Sending)

    // Per change group: accepted (default) or rejected.
    private val accepted = mutableStateListOf<Boolean>()

    // Per English-variety note: applied only when checked (default unchecked).
    private val noteAccepted = mutableStateListOf<Boolean>()

    private var job: Job? = null

    init {
        val confirm = request?.confirm
        if (confirm != null) state = State.Confirm(confirm) else startSending()
    }

    private fun startSending() {
        state = State.Sending
        job = manager.getLifecycleScope().launch { send() }
    }

    private suspend fun send() {
        val target = target ?: run {
            state = State.Failed(context.getString(action.emptyMessage()))
            return
        }
        val harmonize = context.getSetting(KbfrHarmonizeVariant)
        val outcome = sendTextAction(context, action, target.text, context.getSetting(KbfrTimeoutSeconds))
        if (outcome is KbOutcome.Failed) {
            state = State.Failed(outcome.message)
            return
        }
        val parsed = (outcome as KbOutcome.Ok).response
        val elapsed = outcome.elapsedMs
        // Always reviewed in the panel: the insertion setting belongs to Corriger and Améliorer (spec 6).
        if (action == TextAction.TRANSLATE) {
            state = if (parsed.result == target.text) State.NoErrors(elapsed) else State.Translated(parsed.result, elapsed)
            return
        }
        // With harmonize on, variety words are changes of their own: no notes. With it off, the
        // noted words stay as written even if the model changed them.
        val notes = if (harmonize) emptyList() else parsed.notes
        val explained = Correction.explain(target.text, parsed.result, parsed.edits, keepAsWritten = notes)
        val result = WordDiff.apply(explained.parts, explained.groups.flatMap { it.changes }.map { it.index }.toSet())
        // Format always shows the panel, like Traduire: the insertion setting is Corriger's and Améliorer's.
        if (action != TextAction.FORMAT && context.getSetting(KbfrInsertMode) == "replace") {
            if (result == target.text) {
                manager.closeActionWindow()
                KbfrState.showMessage(notesLine(notes) ?: context.getString(action.noChangesMessage()))
            } else if (insert(result)) {
                notesLine(notes)?.let { KbfrState.showMessage(it) }
            }
            return
        }
        if (result == target.text && notes.isEmpty()) {
            state = State.NoErrors(elapsed)
            return
        }
        accepted.clear()
        accepted.addAll(explained.groups.map { true })
        noteAccepted.clear()
        noteAccepted.addAll(notes.map { false })
        state = State.Done(explained, notes, elapsed)
    }

    // "Replace at once" has no panel: the English-variety notes go to the status strip.
    private fun notesLine(notes: List<KbNote>): String? =
        if (notes.isEmpty()) null
        else context.getString(R.string.kbfr_correct_notes_strip, notes.joinToString(", ") { "${it.from} → ${it.to}" })

    private fun insert(text: String): Boolean {
        val target = target ?: return false
        val ic = inputConnection(manager)
        val ok = ic != null && replaceChecked(ic, target.start, target.end, target.text, text, target.end)
        manager.closeActionWindow()
        if (ok) {
            KbfrState.undo = UndoInfo(manager, target.start, text, target.text, action.undoLabel())
        } else {
            KbfrState.showMessage(context.getString(R.string.kbfr_text_changed))
        }
        return ok
    }

    override fun close(): CloseResult {
        job?.cancel()
        return CloseResult.Default
    }

    @Composable
    override fun windowName(): String = stringResource(action.title())

    @Composable
    override fun WindowContents(keyboardShown: Boolean) {
        val chars = target?.text?.length ?: 0
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            when (val s = state) {
                is State.Confirm -> {
                    if (s.found.isNotEmpty()) {
                        Text(stringResource(R.string.kbfr_sensitive_contains, kindNames(context, s.found)), style = Typography.Body.Medium)
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(stringResource(R.string.kbfr_confirm_send, chars), style = Typography.Body.Regular)
                    Spacer(Modifier.weight(1f))
                    Buttons {
                        TextButton(onClick = { manager.closeActionWindow() }) { Text(stringResource(R.string.kbfr_cancel)) }
                        Button(onClick = { startSending() }) { Text(stringResource(R.string.kbfr_send)) }
                    }
                }
                State.Sending -> {
                    Text(stringResource(R.string.kbfr_correct_sending, chars), style = Typography.SmallMl)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is State.Failed -> {
                    Text(s.message, style = Typography.Body.Regular)
                    Spacer(Modifier.weight(1f))
                    Buttons { TextButton(onClick = { manager.closeActionWindow() }) { Text(stringResource(R.string.kbfr_correct_close)) } }
                }
                is State.NoErrors -> {
                    Text(sentLine(chars, s.elapsedMs), style = Typography.SmallMl)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(action.noChangesMessage()), style = Typography.Body.Regular)
                    Spacer(Modifier.weight(1f))
                    Buttons { TextButton(onClick = { manager.closeActionWindow() }) { Text(stringResource(R.string.kbfr_correct_close)) } }
                }
                is State.Done -> Result(s, chars)
                is State.Translated -> {
                    Text(sentLine(chars, s.elapsedMs), style = Typography.SmallMl)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Spacer(Modifier.height(8.dp))
                        Text(s.result, style = Typography.Body.Regular)
                    }
                    Buttons {
                        TextButton(onClick = { manager.closeActionWindow() }) { Text(stringResource(R.string.kbfr_cancel)) }
                        Button(onClick = { insert(s.result) }) { Text(stringResource(R.string.kbfr_correct_insert)) }
                    }
                }
            }
        }
    }

    @Composable
    private fun sentLine(chars: Int, elapsedMs: Long): String =
        stringResource(R.string.kbfr_correct_sent, chars, String.format(Locale.getDefault(), "%.1f", elapsedMs / 1000.0))

    @Composable
    private fun ColumnScope.Result(s: State.Done, chars: Int) {
        val groupOf = IntArray(s.explained.parts.count { it is DiffPart.Changed })
        s.explained.groups.forEachIndexed { g, group -> group.changes.forEach { groupOf[it.index] = g } }
        fun isAccepted(change: DiffChange) = accepted.getOrElse(groupOf[change.index]) { true }
        val checkedNotes = s.notes.filterIndexed { i, _ -> noteAccepted.getOrElse(i) { false } }
        fun acceptedChanges() = s.explained.groups.filterIndexed { g, _ -> accepted[g] }.flatMap { it.changes }.map { it.index }.toSet()

        Text(sentLine(chars, s.elapsedMs), style = Typography.SmallMl)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (s.explained.groups.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(action.noChangesMessage()), style = Typography.Body.Regular)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    for (part in s.explained.parts) when (part) {
                        is DiffPart.Same -> Correction.splitByNotes(part.text, checkedNotes).forEach { (piece, note) ->
                            if (note == null) {
                                append(piece)
                            } else {
                                withStyle(Removed) { append(note.from) }
                                withStyle(Added) { append(note.to) }
                            }
                        }
                        is DiffPart.Changed -> if (isAccepted(part.change)) {
                            withStyle(Removed) { append(part.change.from) }
                            withStyle(Added) { append(part.change.to) }
                        } else {
                            append(part.change.from)
                        }
                    }
                },
                style = Typography.Body.Regular,
            )
            Spacer(Modifier.height(8.dp))
            s.explained.groups.forEachIndexed { g, group ->
                Row(
                    Modifier.fillMaxWidth().clickable { accepted[g] = !accepted[g] }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Checkbox(checked = accepted[g], onCheckedChange = { accepted[g] = it })
                    Column(Modifier.weight(1f).padding(top = 12.dp)) {
                        if (group.edits.isEmpty()) {
                            group.changes.forEach { EditTitle(it.from, it.to) }
                        } else {
                            group.edits.forEach { EditLines(it) }
                        }
                    }
                }
            }
            if (s.explained.unmatched.isNotEmpty()) {
                Text(stringResource(R.string.kbfr_correct_other_explanations), style = Typography.Body.Medium, modifier = Modifier.padding(top = 8.dp))
                s.explained.unmatched.forEach { Column(Modifier.padding(vertical = 4.dp)) { EditLines(it) } }
            }
            Notes(s.notes)
        }
        Buttons {
            TextButton(onClick = { manager.closeActionWindow() }) { Text(stringResource(R.string.kbfr_cancel)) }
            TextButton(
                onClick = { insert(Correction.apply(s.explained.parts, acceptedChanges(), checkedNotes)) },
                enabled = accepted.any { it } || checkedNotes.isNotEmpty(),
            ) { Text(stringResource(R.string.kbfr_correct_insert)) }
            // All corrections; notes stay as checked, they are suggestions.
            if (s.explained.groups.isNotEmpty()) {
                val all = s.explained.groups.flatMap { it.changes }.map { it.index }.toSet()
                Button(onClick = { insert(Correction.apply(s.explained.parts, all, checkedNotes)) }) {
                    Text(stringResource(R.string.kbfr_correct_accept_all))
                }
            }
        }
    }

    @Composable
    private fun EditTitle(from: String, to: String) {
        Text(
            buildAnnotatedString {
                withStyle(Removed) { append(shown(from)) }
                append("  →  ")
                withStyle(Added) { append(shown(to)) }
            },
            style = Typography.Body.Regular,
        )
    }

    // A paragraph break has no visible text of its own (Format adds them): it shows as ¶.
    private fun shown(text: String): String = when {
        text.isNotBlank() -> text.trim()
        '\n' in text -> "¶"
        else -> "—"
    }

    @Composable
    private fun EditLines(edit: KbEdit) {
        EditTitle(edit.from, edit.to)
        val category = CategoryLabels[edit.category]?.let { stringResource(it) } ?: edit.category
        Text("$category · ${edit.explanation}", style = Typography.SmallMl)
    }

    // English-variety notices: not errors, so their checkbox starts unchecked.
    @Composable
    private fun Notes(notes: List<KbNote>) {
        if (notes.isEmpty()) return
        Text(stringResource(R.string.kbfr_correct_notes), style = Typography.Body.Medium, modifier = Modifier.padding(top = 8.dp))
        notes.forEachIndexed { i, note ->
            Row(
                Modifier.fillMaxWidth().clickable { noteAccepted[i] = !noteAccepted[i] }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(checked = noteAccepted[i], onCheckedChange = { noteAccepted[i] = it })
                Column(Modifier.weight(1f).padding(top = 12.dp)) {
                    Text("${note.from}  →  ${note.to}", style = Typography.Body.Regular)
                    Text(note.explanation, style = Typography.SmallMl)
                }
            }
        }
    }

    @Composable
    private fun Buttons(content: @Composable () -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
    }
}
