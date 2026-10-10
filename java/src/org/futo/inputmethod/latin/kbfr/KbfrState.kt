package org.futo.inputmethod.latin.kbfr

import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction

enum class WarmUpState { IDLE, LOADING, DONE }

// A new object per message, so showing the same text twice restarts its timer.
class StripMessage(val text: String)

// What Undo needs after a result was inserted: the original stays in RAM only (spec 5.1 point 6).
// label: "Corrected", "Improved" or "Translated", shown in the strip.
class UndoInfo(val manager: KeyboardManagerForAction, val start: Int, val inserted: String, val original: String, @StringRes val label: Int)

// kbfr fork: state of the fork's online features that the toolbar and the status strip observe.
// In memory only, like everything else about typed text (R4).
object KbfrState {
    // Policy of the field being edited. The same rules InputAttributes evaluates, kept here as
    // Compose state so the toolbar locks update when the field changes.
    var policy by mutableStateOf<InputContextPolicy?>(null)
        private set

    // R3: this field holds a sensitive clip pasted from the keyboard. Online features stay off here
    // while the pasted text is in the field: messaging apps use one field for every chat, so the
    // lock goes with the chat that holds it, and emptying the field lifts it.
    var contaminated by mutableStateOf(false)
        private set

    private val pasted = PastedFingerprints()

    // Last integrity check of this process; null until the first one.
    var integrity by mutableStateOf<IntegrityStatus?>(null)
        private set

    var warmUp by mutableStateOf(WarmUpState.IDLE)

    var message by mutableStateOf<StripMessage?>(null)

    var undo by mutableStateOf<UndoInfo?>(null)

    private var fieldKey: String? = null

    // Sensitive clips during their 60 s (spec 5.5), so a Ctrl+V paste of one is recognised. A list,
    // not a set: the same text copied twice has two timers. Main thread only.
    private val liveSensitiveClips = ArrayList<String>()

    fun onInputStarted(editorInfo: EditorInfo?, ic: InputConnection?) {
        policy = PrivacyPolicy.evaluate(editorInfo?.packageName, editorInfo?.inputType ?: 0, editorInfo?.imeOptions ?: 0)
        // Undo lasts until the user moves to another field; a restart of the same field keeps it.
        val key = editorInfo?.let { "${it.packageName}/${it.fieldId}" }
        if (key != fieldKey) undo = null
        fieldKey = key
        recheckContamination(ic)
    }

    fun onSensitivePaste(text: String) {
        pasted.add(text)
        contaminated = true
    }

    // When a field starts and right before an online action, the field's text decides. Fields where
    // online features are off anyway are not read; an unreadable field keeps the last state.
    fun recheckContamination(ic: InputConnection?) {
        if (pasted.isEmpty() || policy?.onlineAllowed != true) {
            contaminated = false
            return
        }
        val field = ic?.let { readField(it) } ?: return
        contaminated = pasted.foundIn(field.text)
    }

    fun addLiveSensitiveClip(text: String) {
        liveSensitiveClips += text
    }

    fun removeLiveSensitiveClip(text: String) {
        liveSensitiveClips.remove(text)
    }

    // Ctrl+V from the keyboard (Paste action, text-edit panel) pastes the system clipboard.
    fun onClipboardPaste(context: Context) {
        if (liveSensitiveClips.isEmpty()) return
        val text = try {
            context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        } catch (_: Exception) {
            null
        }
        if (text != null && text in liveSensitiveClips) onSensitivePaste(text)
    }

    // Emptying the field lifts the lock at once: only then is the cursor at 0 with no text on either
    // side, so the field is read only in that case.
    fun onSelectionUpdate(selStart: Int, selEnd: Int, ic: InputConnection?) {
        if (!contaminated || selStart != 0 || selEnd != 0 || ic == null) return
        if (ic.getTextBeforeCursor(1, 0)?.isEmpty() == true && ic.getTextAfterCursor(1, 0)?.isEmpty() == true) {
            contaminated = false
        }
    }

    suspend fun refreshIntegrity(context: Context): IntegrityStatus =
        withContext(Dispatchers.IO) { Integrity.check(context) }.also { integrity = it }

    fun showMessage(text: String) {
        message = StripMessage(text)
    }
}
