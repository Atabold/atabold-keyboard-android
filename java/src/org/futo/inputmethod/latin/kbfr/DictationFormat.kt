package org.futo.inputmethod.latin.kbfr

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.ActionInputTransaction
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.utils.ModelOutputSanitizer

// kbfr fork: automatic formatting of dictation (spec 5.4), the only automatic send (R5). FUTO's voice
// input hands its final text here instead of inserting it; on any problem the text goes in as
// dictated. The text stays in memory only (R4).
object DictationFormat {
    // Spec 5.4: after this the dictated text goes in as is. Not the timeout setting: the user is
    // waiting in front of the voice window.
    private const val TIMEOUT_SECONDS = 8

    // Inserts the dictated text, formatted when possible. onSending: the request is about to leave,
    // so the voice window can show "Formatting…".
    suspend fun commit(manager: KeyboardManagerForAction, transaction: ActionInputTransaction, dictated: String, onSending: () -> Unit) {
        val formatted = format(manager, dictated, onSending)
        // FUTO fits the text to the cursor (spaces, capital letter, final punctuation) after formatting.
        val original = ModelOutputSanitizer.sanitize(dictated, transaction.textContext)
        val inserted = formatted?.let { ModelOutputSanitizer.sanitize(it, transaction.textContext) } ?: original
        transaction.commit(inserted)
        if (inserted != original) offerUndo(manager, inserted, original)
    }

    // The formatted text, or null to insert the dictated one.
    private suspend fun format(manager: KeyboardManagerForAction, dictated: String, onSending: () -> Unit): String? {
        val context = manager.getContext()
        val text = dictated.trim()
        if (!context.getSetting(KbfrAutoFormat) || text.isEmpty()) return null
        // Where online features are off, the text goes in as dictated without a notice (spec 5.4).
        KbfrState.recheckContamination(inputConnection(manager))
        if (OnlineGate.check(context) != null) return null

        val maxChars = context.getSetting(KbfrMaxChars)
        if (text.length > maxChars) return notFormatted(context, context.getString(R.string.kbfr_correct_too_long, maxChars))
        // Nobody can be asked in the middle of a dictation, so "ask" keeps the text as dictated like
        // "block". "Confirm before sending" does not apply: turning this on is the consent
        // (user's choice).
        val found = when (val check = SensitiveData.check(text, { context.getSetting(KbfrSensitiveRules.getValue(it)) }, confirm = false)) {
            is SendCheck.Blocked -> check.found
            is SendCheck.Confirm -> check.found
            SendCheck.Send -> null
        }
        if (found != null) {
            KbfrState.showMessage(context.getString(R.string.kbfr_format_sensitive, kindNames(context, found)))
            return null
        }

        onSending()
        // A blocking connection cannot be cut short, so the request runs apart and is awaited for 8 s
        // only; a late answer is dropped.
        val request = manager.getLifecycleScope().async { sendTextAction(context, TextAction.FORMAT, text, TIMEOUT_SECONDS) }
        return when (val outcome = withTimeoutOrNull(TIMEOUT_SECONDS * 1000L) { request.await() }) {
            is KbOutcome.Ok -> outcome.response.result
            is KbOutcome.Failed -> notFormatted(context, outcome.message)
            null -> notFormatted(context, context.getString(R.string.kbfr_err_timeout, TIMEOUT_SECONDS))
        }
    }

    // The small notice of spec 5.4, in the status strip.
    private fun notFormatted(context: Context, reason: String): String? {
        KbfrState.showMessage(context.getString(R.string.kbfr_format_failed, reason))
        return null
    }

    // Undo puts the dictated text back until the field changes, like after Corriger (user's
    // choice). The cursor is right after the inserted text; if the field says otherwise, no Undo.
    private fun offerUndo(manager: KeyboardManagerForAction, inserted: String, original: String) {
        val field = inputConnection(manager)?.let { readField(it) } ?: return
        val start = field.selEnd - inserted.length
        if (start < 0 || !field.text.startsWith(inserted, start)) return
        KbfrState.undo = UndoInfo(manager, start, inserted, original, R.string.kbfr_undo_formatted)
    }
}
