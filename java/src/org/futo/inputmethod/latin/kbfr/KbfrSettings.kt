package org.futo.inputmethod.latin.kbfr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.annotation.StringRes
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.BuildConfig
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.SettingsKey
import org.futo.inputmethod.latin.uix.getSetting
import org.futo.inputmethod.latin.uix.settings.CollapsibleSection
import org.futo.inputmethod.latin.uix.settings.DropDownPickerSettingItem
import org.futo.inputmethod.latin.uix.settings.ScreenTitle
import org.futo.inputmethod.latin.uix.settings.SettingItem
import org.futo.inputmethod.latin.uix.settings.SettingSlider
import org.futo.inputmethod.latin.uix.settings.SettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.Tip
import org.futo.inputmethod.latin.uix.settings.UserSetting
import org.futo.inputmethod.latin.uix.settings.UserSettingsMenu
import org.futo.inputmethod.latin.uix.settings.useDataStore
import org.futo.inputmethod.latin.uix.settings.useDataStoreValue
import org.futo.inputmethod.latin.uix.settings.userSettingDecorationOnly
import org.futo.inputmethod.latin.uix.settings.userSettingToggleDataStore
import org.futo.inputmethod.latin.uix.settings.userSettingToggleSharedPrefs
import kotlin.math.roundToInt

// kbfr fork: "Correction & privacy" settings (spec 6): connection, privacy with the sensitive-data
// rules, Corriger / Améliorer, Traduire, voice, security.

val KbfrTimeoutSeconds = SettingsKey(intPreferencesKey("kbfr_timeout_s"), 20)
val KbfrWifiOnly = SettingsKey(booleanPreferencesKey("kbfr_wifi_only"), false)
val KbfrOnlineEnabled = SettingsKey(booleanPreferencesKey("kbfr_online"), true)
val KbfrMaxChars = SettingsKey(intPreferencesKey("kbfr_max_chars"), 4000)

// "never", "always" or "above" KbfrConfirmAboveChars (spec 6).
val KbfrConfirmMode = SettingsKey(stringPreferencesKey("kbfr_confirm_mode"), "never")
val KbfrConfirmAboveChars = SettingsKey(intPreferencesKey("kbfr_confirm_above_chars"), 1000)

// One rule per category of spec 5.6: "block", "ask" or "ignore".
val KbfrSensitiveRules = SensitiveKind.entries.associateWith {
    SettingsKey(stringPreferencesKey("kbfr_sensitive_${it.name.lowercase()}"), it.defaultRule)
}

// Corriger options, stored as the values the backend accepts (spec 7.1). The language of the
// explanations starts as the build's choice (kbfr.properties: explainIn).
val KbfrExplainIn = SettingsKey(stringPreferencesKey("kbfr_explain_in"), BuildConfig.KBFR_DEFAULT_EXPLAIN_IN)
val KbfrTargetLang = SettingsKey(stringPreferencesKey("kbfr_target_lang"), "auto")
val KbfrTranslateForeign = SettingsKey(booleanPreferencesKey("kbfr_translate_foreign"), true)
val KbfrRegister = SettingsKey(stringPreferencesKey("kbfr_register"), "auto")
val KbfrDetail = SettingsKey(stringPreferencesKey("kbfr_detail"), "short")
val KbfrProfile = SettingsKey(stringPreferencesKey("kbfr_profile"), "fast")
val KbfrHarmonizeVariant = SettingsKey(booleanPreferencesKey("kbfr_harmonize_variant"), false)

// Phone-only: "diff" shows the changes panel, "replace" inserts at once with Undo (spec 6).
val KbfrInsertMode = SettingsKey(stringPreferencesKey("kbfr_insert_mode"), "diff")

// Traduire, the backend's value (spec 6, 7.1). Starts as the build's choice (kbfr.properties: translateTo).
val KbfrTranslateTo = SettingsKey(stringPreferencesKey("kbfr_translate_to"), BuildConfig.KBFR_DEFAULT_TRANSLATE_TO)

// Voice (spec 5.4, 6). Automatic formatting is phone-only; style ("minimal" / "clean") and grammar
// go to the backend, for the manual Format too.
val KbfrAutoFormat = SettingsKey(booleanPreferencesKey("kbfr_auto_format"), false)
val KbfrFormatStyle = SettingsKey(stringPreferencesKey("kbfr_format_style"), "clean")
val KbfrFormatFixGrammar = SettingsKey(booleanPreferencesKey("kbfr_format_fix_grammar"), false)

private val RuleChoices = listOf("block" to R.string.kbfr_rule_block, "ask" to R.string.kbfr_rule_ask, "ignore" to R.string.kbfr_rule_ignore)

val KbfrSettingsMenu = UserSettingsMenu(
    title = R.string.kbfr_settings_title,
    navPath = "kbfr",
    registerNavPath = true,
    settings = listOf(
        userSettingDecorationOnly { ScreenTitle(stringResource(R.string.kbfr_connection_title)) },
        UserSetting(name = R.string.kbfr_endpoint_title) {
            // Read-only: the backend is fixed in the build (kbfr.properties, read by build.gradle).
            SettingItem(
                title = stringResource(R.string.kbfr_endpoint_title),
                subtitle = BuildConfig.KBFR_ENDPOINT.ifEmpty { stringResource(R.string.kbfr_endpoint_not_configured) },
            ) {}
        },
        UserSetting(name = R.string.kbfr_device_key_title) { DeviceKeyAndConnection() },

        userSettingDecorationOnly { ScreenTitle(stringResource(R.string.kbfr_privacy_title)) },
        userSettingToggleDataStore(
            title = R.string.kbfr_online_title,
            setting = KbfrOnlineEnabled,
            subtitle = R.string.kbfr_online_subtitle,
        ),
        // FUTO Keyboard's crash dialog, which offers to email a report to FUTO: off unless turned on here.
        // The key is ACRA's own preference, so the change takes effect at once (see CrashLoggingApplication).
        userSettingToggleSharedPrefs(
            title = R.string.kbfr_crash_reports_title,
            key = "acra.enable",
            default = { false },
            subtitle = R.string.kbfr_crash_reports_subtitle,
        ),
        UserSetting(name = R.string.kbfr_max_chars_title) {
            SettingSlider(
                stringResource(R.string.kbfr_max_chars_title),
                KbfrMaxChars,
                range = 100f..4000f,
                transform = { (it / 100f).roundToInt() * 100 },
                steps = 38,
            )
        },
        UserSetting(name = R.string.kbfr_confirm_title) {
            Choice(
                R.string.kbfr_confirm_title, KbfrConfirmMode,
                listOf("never" to R.string.kbfr_confirm_never, "always" to R.string.kbfr_confirm_always, "above" to R.string.kbfr_confirm_above),
            )
        },
        UserSetting(
            name = R.string.kbfr_confirm_above_title,
            visibilityCheck = { useDataStoreValue(KbfrConfirmMode) == "above" },
        ) {
            SettingSlider(
                stringResource(R.string.kbfr_confirm_above_title),
                KbfrConfirmAboveChars,
                range = 100f..4000f,
                transform = { (it / 100f).roundToInt() * 100 },
                steps = 38,
            )
        },
        userSettingDecorationOnly { ScreenTitle(stringResource(R.string.kbfr_sensitive_title)) },
        *SensitiveKind.entries.map { kind ->
            UserSetting(name = kind.label()) { Choice(kind.label(), KbfrSensitiveRules.getValue(kind), RuleChoices) }
        }.toTypedArray(),
        userSettingDecorationOnly { Tip(stringResource(R.string.kbfr_sensitive_note)) },
        UserSetting(name = R.string.kbfr_app_lists_title) { AppLists() },

        userSettingDecorationOnly { ScreenTitle(stringResource(R.string.kbfr_correct_title)) },
        UserSetting(name = R.string.kbfr_explain_in_title) {
            Choice(
                R.string.kbfr_explain_in_title, KbfrExplainIn,
                listOf("en" to R.string.kbfr_lang_en, "fr" to R.string.kbfr_lang_fr, "de" to R.string.kbfr_lang_de, "es" to R.string.kbfr_lang_es, "it" to R.string.kbfr_lang_it),
            )
        },
        UserSetting(name = R.string.kbfr_target_lang_title) {
            Choice(
                R.string.kbfr_target_lang_title, KbfrTargetLang,
                listOf("auto" to R.string.kbfr_target_lang_auto, "fr" to R.string.kbfr_target_lang_fr),
            )
        },
        userSettingToggleDataStore(title = R.string.kbfr_translate_foreign_title, setting = KbfrTranslateForeign),
        UserSetting(name = R.string.kbfr_register_title) {
            Choice(
                R.string.kbfr_register_title, KbfrRegister,
                listOf("auto" to R.string.kbfr_register_auto, "tu" to R.string.kbfr_register_tu, "vous" to R.string.kbfr_register_vous),
            )
        },
        UserSetting(name = R.string.kbfr_detail_title) {
            Choice(
                R.string.kbfr_detail_title, KbfrDetail,
                listOf("short" to R.string.kbfr_detail_short, "long" to R.string.kbfr_detail_long),
            )
        },
        UserSetting(name = R.string.kbfr_profile_title) {
            Choice(
                R.string.kbfr_profile_title, KbfrProfile,
                listOf("fast" to R.string.kbfr_profile_fast, "accurate" to R.string.kbfr_profile_accurate),
            )
        },
        userSettingToggleDataStore(
            title = R.string.kbfr_harmonize_variant_title,
            setting = KbfrHarmonizeVariant,
            subtitle = R.string.kbfr_harmonize_variant_subtitle,
        ),
        UserSetting(name = R.string.kbfr_insert_title) {
            Choice(
                R.string.kbfr_insert_title, KbfrInsertMode,
                listOf("diff" to R.string.kbfr_insert_diff, "replace" to R.string.kbfr_insert_replace),
            )
        },

        userSettingDecorationOnly { ScreenTitle(stringResource(R.string.kbfr_action_translate)) },
        UserSetting(name = R.string.kbfr_translate_to_title) {
            Choice(
                R.string.kbfr_translate_to_title, KbfrTranslateTo,
                listOf("fr" to R.string.kbfr_lang_fr, "en" to R.string.kbfr_lang_en, "de" to R.string.kbfr_lang_de, "es" to R.string.kbfr_lang_es, "it" to R.string.kbfr_lang_it),
            )
        },

        userSettingDecorationOnly { ScreenTitle(stringResource(R.string.kbfr_voice_title)) },
        userSettingToggleDataStore(
            title = R.string.kbfr_auto_format_title,
            setting = KbfrAutoFormat,
            subtitle = R.string.kbfr_auto_format_subtitle,
        ),
        UserSetting(name = R.string.kbfr_format_style_title) {
            Choice(
                R.string.kbfr_format_style_title, KbfrFormatStyle,
                listOf("minimal" to R.string.kbfr_format_style_minimal, "clean" to R.string.kbfr_format_style_clean),
            )
        },
        userSettingToggleDataStore(title = R.string.kbfr_format_fix_grammar_title, setting = KbfrFormatFixGrammar),

        UserSetting(name = R.string.kbfr_security_title) { SecurityStatus() },

        // The FUTO Source First License asks for a prominent notice that the software was modified.
        userSettingDecorationOnly { ScreenTitle(stringResource(R.string.kbfr_about_title)) },
        UserSetting(name = R.string.kbfr_about_title) {
            SettingItem(
                title = stringResource(R.string.kbfr_about_fork_title),
                subtitle = stringResource(R.string.kbfr_about_fork_body),
            ) {}
        },
    ),
)

@StringRes
fun IntegrityProblem.label(): Int = when (this) {
    IntegrityProblem.WRONG_SIGNATURE -> R.string.kbfr_problem_wrong_signature
    IntegrityProblem.DEBUGGABLE -> R.string.kbfr_problem_debuggable
    IntegrityProblem.KEY_NOT_IN_HARDWARE -> R.string.kbfr_problem_key
}

@StringRes
fun PingResult.label(): Int = when (this) {
    PingResult.REGISTERED -> R.string.kbfr_ping_registered
    PingResult.NOT_REGISTERED -> R.string.kbfr_ping_not_registered
    PingResult.INVALID_ENDPOINT -> R.string.kbfr_ping_invalid_endpoint
    PingResult.UNREACHABLE -> R.string.kbfr_ping_unreachable
    PingResult.UNEXPECTED -> R.string.kbfr_ping_unexpected
}

@StringRes
fun SensitiveKind.label(): Int = when (this) {
    SensitiveKind.CARD -> R.string.kbfr_kind_card
    SensitiveKind.IBAN -> R.string.kbfr_kind_iban
    SensitiveKind.SECRET -> R.string.kbfr_kind_secret
    SensitiveKind.TAX_CODE -> R.string.kbfr_kind_tax_id
    SensitiveKind.OTP -> R.string.kbfr_kind_otp
    SensitiveKind.EMAIL -> R.string.kbfr_kind_email
    SensitiveKind.PHONE -> R.string.kbfr_kind_phone
}

@StringRes
private fun KeyStorage.label(): Int = when (this) {
    KeyStorage.STRONGBOX -> R.string.kbfr_storage_strongbox
    KeyStorage.TEE -> R.string.kbfr_storage_tee
    KeyStorage.SOFTWARE -> R.string.kbfr_storage_software
}

@StringRes
private fun AppCategory.label(): Int = when (this) {
    AppCategory.MESSAGING -> R.string.kbfr_category_messaging
    AppCategory.SOCIAL -> R.string.kbfr_category_social
    AppCategory.EMAIL -> R.string.kbfr_category_email
    AppCategory.CHATBOT -> R.string.kbfr_category_chatbot
    AppCategory.DOCUMENTS -> R.string.kbfr_category_documents
}

@Composable
private fun <T> Choice(@StringRes label: Int, setting: SettingsKey<T>, options: List<Pair<T, Int>>) {
    val item = useDataStore(setting)
    val names = options.associate { (value, name) -> value to stringResource(name) }
    DropDownPickerSettingItem(
        label = stringResource(label),
        options = options.map { it.first },
        selection = item.value,
        onSet = { item.setValue(it) },
        getDisplayName = { names[it] ?: "?" },
    )
}

@Composable
private fun DeviceKeyAndConnection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var spki by remember { mutableStateOf<ByteArray?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var pingText by remember { mutableStateOf<Int?>(null) }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(reload) {
        // Integrity.check also creates the key the first time.
        KbfrState.refreshIntegrity(context)
        spki = withContext(Dispatchers.IO) { try { DeviceKey.publicKeySpki() } catch (_: Exception) { null } }
    }

    ScreenTitle(stringResource(R.string.kbfr_device_key_title))
    val key = spki
    if (key == null) {
        SettingItem(title = stringResource(R.string.kbfr_device_key_loading)) {}
    } else {
        val publicKey = Base64.encodeToString(key, Base64.NO_WRAP)
        SettingItem(
            title = stringResource(R.string.kbfr_device_key_id, RequestSigning.keyId(key).chunked(4).joinToString(" ")),
            subtitle = KbfrState.integrity?.keyStorage?.let { stringResource(R.string.kbfr_device_key_storage, stringResource(it.label())) },
        ) {}
        SettingItem(title = stringResource(R.string.kbfr_device_key_share), onClick = { sharePublicKey(context, publicKey) }) {}
        SettingItem(
            title = stringResource(R.string.kbfr_device_key_copy),
            subtitle = if (copied) stringResource(R.string.kbfr_device_key_copied) else null,
            onClick = {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("DEVICE_PUBLIC_KEY_SPKI", publicKey))
                copied = true
            },
        ) {}
        Tip(stringResource(R.string.kbfr_device_key_public_note))
        SettingItem(title = stringResource(R.string.kbfr_device_key_regenerate), onClick = { confirmRegenerate = true }) {}
    }

    SettingSlider(
        stringResource(R.string.kbfr_timeout_title),
        KbfrTimeoutSeconds,
        range = 5f..30f,
        transform = { it.roundToInt() },
        indicator = { "$it s" },
        steps = 24,
    )
    SettingToggleDataStore(
        title = stringResource(R.string.kbfr_wifi_only_title),
        setting = KbfrWifiOnly,
        subtitle = stringResource(R.string.kbfr_wifi_only_subtitle),
    )

    SettingItem(
        title = stringResource(R.string.kbfr_test_connection),
        subtitle = pingText?.let { stringResource(it) },
        onClick = {
            if (KbfrState.integrity?.ok != true) {
                // Online features are off while integrity fails (spec 8), and the ping would only
                // trigger an "unregistered device" alert.
                pingText = R.string.kbfr_ping_blocked
                return@SettingItem
            }
            if (!OnlineGate.networkAllowed(context)) {
                pingText = R.string.kbfr_block_network
                return@SettingItem
            }
            pingText = R.string.kbfr_test_running
            scope.launch {
                val endpoint = BuildConfig.KBFR_ENDPOINT
                val timeoutMs = context.getSetting(KbfrTimeoutSeconds) * 1000
                pingText = withContext(Dispatchers.IO) { BackendClient.ping(endpoint, timeoutMs) }.label()
            }
        },
    ) {}

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            text = { Text(stringResource(R.string.kbfr_device_key_regenerate_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRegenerate = false
                    scope.launch {
                        withContext(Dispatchers.IO) { DeviceKey.regenerate() }
                        pingText = null
                        copied = false
                        reload++
                    }
                }) { Text(stringResource(R.string.kbfr_device_key_regenerate)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerate = false }) { Text(stringResource(R.string.kbfr_cancel)) }
            },
        )
    }
}

// R1 and R2, read-only: they are fixed in the code (spec 4, 6).
@Composable
private fun AppLists() {
    val allowed = PrivacyPolicy.ALLOWED_APPS
    CollapsibleSection(stringResource(R.string.kbfr_allowed_apps_title, allowed.size)) {
        AppCategory.entries.forEach { category ->
            SettingItem(
                title = stringResource(category.label()),
                subtitle = allowed.filterValues { it == category }.keys.joinToString("\n"),
            ) {}
        }
    }
    val blocked = PrivacyPolicy.ALWAYS_BLOCKED_APPS
    CollapsibleSection(stringResource(R.string.kbfr_blocked_apps_title, blocked.size)) {
        SettingItem(title = stringResource(R.string.kbfr_blocked_listed), subtitle = blocked.joinToString("\n")) {}
        SettingItem(
            title = stringResource(R.string.kbfr_blocked_pattern),
            subtitle = PrivacyPolicy.ALWAYS_BLOCKED_PACKAGE_PATTERN.pattern.replace("|", ", "),
        ) {}
    }
    Tip(stringResource(R.string.kbfr_app_lists_note))
}

@Composable
private fun SecurityStatus() {
    ScreenTitle(stringResource(R.string.kbfr_security_title))
    val problems = KbfrState.integrity?.problems ?: return
    IntegrityRow(R.string.kbfr_integrity_signature, IntegrityProblem.WRONG_SIGNATURE !in problems)
    IntegrityRow(R.string.kbfr_integrity_not_debuggable, IntegrityProblem.DEBUGGABLE !in problems)
    IntegrityRow(R.string.kbfr_integrity_hardware_key, IntegrityProblem.KEY_NOT_IN_HARDWARE !in problems)
}

@Composable
private fun IntegrityRow(@StringRes title: Int, ok: Boolean) {
    SettingItem(title = (if (ok) "✓ " else "✗ ") + stringResource(title)) {}
}

private fun sharePublicKey(context: Context, publicKey: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, publicKey)
    }
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
