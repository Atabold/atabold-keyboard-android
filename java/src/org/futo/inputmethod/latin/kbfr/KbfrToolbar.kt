package org.futo.inputmethod.latin.kbfr

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.Action
import org.futo.inputmethod.latin.uix.KeyboardManagerForAction
import org.futo.inputmethod.latin.uix.LocalKeyboardScheme
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.setSetting
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.theme.Typography

// kbfr fork: the fork's toolbar actions (spec 5.7, 5.8), their icons, and the status strip above
// the toolbar.

// Warm up: one signed /v1/ping per tap, never automatic (spec 5.8, R5).
val WarmUpAction = Action(
    icon = R.drawable.kbfr_warm_up,
    name = R.string.kbfr_action_warm_up,
    windowImpl = null,
    simplePressImpl = { manager, _ -> warmUp(manager) },
)

// Online: the general switch of the online features (spec 5.7, 6).
val OnlineToggleAction = Action(
    icon = R.drawable.kbfr_online,
    name = R.string.kbfr_action_online,
    windowImpl = null,
    simplePressImpl = { manager, _ ->
        val context = manager.getContext()
        manager.getLifecycleScope().launch {
            context.setSetting(KbfrOnlineEnabled.key, !context.getSetting(KbfrOnlineEnabled))
        }
    },
)

private fun isOnlineAction(action: Action): Boolean =
    action === WarmUpAction || action === CorrectAction || action === ImproveAction || action === TranslateAction ||
        action === FormatAction

private var warmUpJob: Job? = null

private fun warmUp(manager: KeyboardManagerForAction) {
    if (warmUpJob?.isActive == true) return
    val context = manager.getContext()
    warmUpJob = manager.getLifecycleScope().launch {
        KbfrState.recheckContamination(manager.getLatinIMEForDebug().getBaseInputConnection())
        val block = OnlineGate.check(context)
        if (block != null) {
            KbfrState.showMessage(context.getString(block.label()))
            return@launch
        }
        KbfrState.warmUp = WarmUpState.LOADING
        try {
            val result = withContext(Dispatchers.IO) {
                BackendClient.ping(BuildConfig.KBFR_ENDPOINT, context.getSetting(KbfrTimeoutSeconds) * 1000)
            }
            if (result == PingResult.REGISTERED) {
                // The instance answered, so it is warm now. No countdown after this: the phone
                // cannot know when Scaleway stops it (spec 5.8).
                KbfrState.warmUp = WarmUpState.DONE
                delay(2_000)
            } else {
                KbfrState.showMessage(context.getString(result.label()))
            }
        } finally {
            KbfrState.warmUp = WarmUpState.IDLE
        }
    }
}

@StringRes
fun OnlineBlock.label(): Int = when (this) {
    OnlineBlock.APP_NOT_ALLOWED -> R.string.kbfr_block_app_not_allowed
    OnlineBlock.APP_ALWAYS_BLOCKED -> R.string.kbfr_block_app_always_blocked
    OnlineBlock.SENSITIVE_FIELD -> R.string.kbfr_block_sensitive_field
    OnlineBlock.INCOGNITO -> R.string.kbfr_block_incognito
    OnlineBlock.CONTAMINATED -> R.string.kbfr_block_contaminated
    OnlineBlock.ONLINE_OFF -> R.string.kbfr_block_online_off
    OnlineBlock.INTEGRITY -> R.string.kbfr_block_integrity
    OnlineBlock.NETWORK_NOT_ALLOWED -> R.string.kbfr_block_network
}

private val ClockFaces = listOf("🕐", "🕑", "🕒", "🕓", "🕔", "🕕", "🕖", "🕗", "🕘", "🕙", "🕚", "🕛")

// 🔥, then a turning clock while the ping runs, then ✅ for 2 s once the backend answered.
@Composable
private fun WarmUpEmoji(size: Dp) {
    val text = when (KbfrState.warmUp) {
        WarmUpState.IDLE -> "🔥"
        WarmUpState.DONE -> "✅"
        WarmUpState.LOADING -> {
            var frame by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(100)
                    frame = (frame + 1) % ClockFaces.size
                }
            }
            ClockFaces[frame]
        }
    }
    Text(text, fontSize = with(LocalDensity.current) { size.toSp() })
}

// Toolbar icon of an action. Online actions blocked in this field are dimmed, with a lock (spec 5);
// tapping them still runs the action, which shows the reason.
@Composable
fun KbfrActionIcon(action: Action, tint: Color, size: Dp, default: @Composable () -> Unit) {
    val onlineEnabled = useDataStoreValue(KbfrOnlineEnabled)
    when {
        action === OnlineToggleAction -> Icon(
            painter = painterResource(if (onlineEnabled) R.drawable.kbfr_online else R.drawable.kbfr_online_off),
            contentDescription = stringResource(action.name),
            tint = tint,
            modifier = Modifier.size(size),
        )
        isOnlineAction(action) -> {
            val blocked = OnlineGate.block(KbfrState.policy, KbfrState.contaminated, onlineEnabled, KbfrState.integrity?.ok, networkAllowed = true) != null
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.alpha(if (blocked) 0.4f else 1f)) {
                    if (action === WarmUpAction) WarmUpEmoji(size) else default()
                }
                if (blocked) {
                    Icon(
                        painter = painterResource(R.drawable.kbfr_lock),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = size * 0.25f, y = size * 0.1f)
                            .size(size * 0.6f),
                    )
                }
            }
        }
        else -> default()
    }
}

// Above the toolbar: the fixed red banner while integrity fails (spec 5.7, 8), "Corrected · Undo"
// (or Improved / Translated) after an insertion until the field changes (spec 5.1 point 6), and
// short messages of the online actions for a few seconds. Integrity is checked each time the
// keyboard view is created.
@Composable
fun KbfrStatusBar() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { KbfrState.refreshIntegrity(context) }

    val problems = KbfrState.integrity?.problems.orEmpty()
    if (problems.isNotEmpty()) {
        val reasons = problems.map { stringResource(it.label()) }.joinToString(", ")
        Surface(color = Color(0xFFB3261E), modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.kbfr_integrity_banner, reasons),
                color = Color.White,
                style = Typography.Small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }

    KbfrState.undo?.let { undo ->
        StripLine {
            Text(stringResource(undo.label), color = LocalKeyboardScheme.current.onKeyboardContainer, style = Typography.Small)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.kbfr_undo),
                color = LocalKeyboardScheme.current.primary,
                style = Typography.Small,
                modifier = Modifier.clickable { undoCorrection() }.padding(horizontal = 8.dp),
            )
        }
    }

    val message = KbfrState.message ?: return
    LaunchedEffect(message) {
        delay(4_000)
        if (KbfrState.message === message) KbfrState.message = null
    }
    StripLine {
        Text(
            message.text,
            color = LocalKeyboardScheme.current.onKeyboardContainer,
            style = Typography.Small,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StripLine(content: @Composable RowScope.() -> Unit) {
    Surface(color = LocalKeyboardScheme.current.keyboardContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}
