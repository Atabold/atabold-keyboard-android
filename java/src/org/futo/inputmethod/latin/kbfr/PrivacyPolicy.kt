package org.futo.inputmethod.latin.kbfr

import android.text.InputType
import android.view.inputmethod.EditorInfo

// kbfr fork: hardcoded privacy rules (spec section 4). Not configurable: changing them means
// rebuilding and re-signing the APK. Every feature that sends, learns or stores text asks here.

enum class BlockReason { APP_NOT_ALLOWED, APP_ALWAYS_BLOCKED, SENSITIVE_FIELD, INCOGNITO }

enum class AppCategory { MESSAGING, SOCIAL, EMAIL, CHATBOT, DOCUMENTS }

data class InputContextPolicy(
    val onlineAllowed: Boolean,      // Corriger, Améliorer, Traduire, Format, Warm up
    val learningAllowed: Boolean,    // any learning or storing of typed text
    val clipboardSensitive: Boolean, // what is copied here is sensitive (spec 5.5)
    val selectionEnabled: Boolean,   // email and document apps: actions use the selection (spec 5.1)
    val reason: BlockReason?,        // shown when the user taps a blocked action
)

object PrivacyPolicy {
    // R1: the only apps where online features and learning are allowed. Public only so the settings
    // can show the lists read-only (spec 6).
    val ALLOWED_APPS: Map<String, AppCategory> = mapOf(
        // Package names as in the address of each app's Play Store page. To add an app, see the customise guide.
        "com.whatsapp" to AppCategory.MESSAGING,
        "com.whatsapp.w4b" to AppCategory.MESSAGING,
        "org.telegram.messenger" to AppCategory.MESSAGING,
        "org.telegram.messenger.web" to AppCategory.MESSAGING,
        "org.thoughtcrime.securesms" to AppCategory.MESSAGING,
        "com.google.android.apps.messaging" to AppCategory.MESSAGING,
        "com.google.android.apps.dynamite" to AppCategory.MESSAGING,
        "com.facebook.orca" to AppCategory.MESSAGING,
        "com.discord" to AppCategory.MESSAGING,
        "com.Slack" to AppCategory.MESSAGING,
        "com.microsoft.teams" to AppCategory.MESSAGING,
        "com.viber.voip" to AppCategory.MESSAGING,
        "ch.threema.app" to AppCategory.MESSAGING,
        "ch.threema.app.work" to AppCategory.MESSAGING,
        "im.vector.app" to AppCategory.MESSAGING,
        "io.element.android.x" to AppCategory.MESSAGING,
        "com.wire" to AppCategory.MESSAGING,
        "io.olvid.messenger" to AppCategory.MESSAGING,
        "com.nextcloud.talk2" to AppCategory.MESSAGING,
        "eu.siacs.conversations" to AppCategory.MESSAGING,
        "org.briarproject.briar.android" to AppCategory.MESSAGING,
        "fr.gouv.tchap.a" to AppCategory.MESSAGING,
        "com.instagram.android" to AppCategory.SOCIAL,
        "com.facebook.katana" to AppCategory.SOCIAL,
        "com.instagram.barcelona" to AppCategory.SOCIAL,
        "com.twitter.android" to AppCategory.SOCIAL,
        "com.linkedin.android" to AppCategory.SOCIAL,
        "com.reddit.frontpage" to AppCategory.SOCIAL,
        "com.snapchat.android" to AppCategory.SOCIAL,
        "com.zhiliaoapp.musically" to AppCategory.SOCIAL,
        "com.pinterest" to AppCategory.SOCIAL,
        "org.joinmastodon.android" to AppCategory.SOCIAL,
        "xyz.blueskyweb.app" to AppCategory.SOCIAL,
        "com.tumblr" to AppCategory.SOCIAL,
        "com.xing.android" to AppCategory.SOCIAL,
        "com.bereal.ft" to AppCategory.SOCIAL,
        "com.keylesspalace.tusky" to AppCategory.SOCIAL,
        "com.google.android.gm" to AppCategory.EMAIL,
        "com.microsoft.office.outlook" to AppCategory.EMAIL,
        "ch.protonmail.android" to AppCategory.EMAIL,
        "com.yahoo.mobile.client.android.mail" to AppCategory.EMAIL,
        "net.thunderbird.android" to AppCategory.EMAIL,
        "com.fsck.k9" to AppCategory.EMAIL,
        "eu.faircode.email" to AppCategory.EMAIL,
        "de.tutao.tutanota" to AppCategory.EMAIL,
        "com.readdle.spark" to AppCategory.EMAIL,
        "com.samsung.android.email.provider" to AppCategory.EMAIL,
        "de.gmx.mobile.android.mail" to AppCategory.EMAIL,
        "de.web.mobile.android.mail" to AppCategory.EMAIL,
        "com.anthropic.claude" to AppCategory.CHATBOT,
        "com.openai.chatgpt" to AppCategory.CHATBOT,
        "com.google.android.apps.bard" to AppCategory.CHATBOT,
        "ai.mistral.chat" to AppCategory.CHATBOT,
        "ai.perplexity.app.android" to AppCategory.CHATBOT,
        "com.deepseek.chat" to AppCategory.CHATBOT,
        "ai.x.grok" to AppCategory.CHATBOT,
        "me.proton.android.lumo" to AppCategory.CHATBOT,
        "com.google.android.apps.docs.editors.docs" to AppCategory.DOCUMENTS,
        "com.microsoft.office.word" to AppCategory.DOCUMENTS,
        "notion.id" to AppCategory.DOCUMENTS,
        "com.google.android.keep" to AppCategory.DOCUMENTS,
        "com.microsoft.office.onenote" to AppCategory.DOCUMENTS,
        "com.evernote" to AppCategory.DOCUMENTS,
        "md.obsidian" to AppCategory.DOCUMENTS,
        "com.automattic.simplenote" to AppCategory.DOCUMENTS,
        "net.cozic.joplin" to AppCategory.DOCUMENTS,
        "com.samsung.android.app.notes" to AppCategory.DOCUMENTS,
        "it.niedermann.owncloud.notes" to AppCategory.DOCUMENTS,
        "com.onlyoffice.documents" to AppCategory.DOCUMENTS,
        "com.collabora.libreoffice" to AppCategory.DOCUMENTS,
        "io.anytype.app" to AppCategory.DOCUMENTS,
        "io.capacities.mobile" to AppCategory.DOCUMENTS,
    )

    // R2: always fully blocked, even if listed above.
    val ALWAYS_BLOCKED_APPS: Set<String> = setOf(
        "proton.android.pass",
        "proton.android.authenticator",
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.authy.authy",
        "com.beemdevelopment.aegis",
        "com.twofasapp",
        "com.x8bit.bitwarden",
        "com.bitwarden.authenticator",
        "com.onepassword.android",
        "com.kunzisoft.keepass.free",
        "com.kunzisoft.keepass.libre",
        "keepass2android.keepass2android",
        "com.lastpass.lpandroid",
        "com.lastpass.authenticator",
        "com.dashlane",
        "com.callpod.android_apps.keeper",
        "io.enpass.app",
        "com.siber.roboform",
        "com.duosecurity.duomobile",
        "com.okta.android.auth",
        "com.yubico.yubioath",
        "io.ente.auth",
        "com.stratumauth.app",
        "org.fedorahosted.freeotp",
    )

    // R2: catches authenticators and password managers not listed above.
    val ALWAYS_BLOCKED_PACKAGE_PATTERN = Regex(
        "authenticator|authy|otp|2fa|totp|keepass|bitwarden|1password|lastpass|dashlane|password|vault",
        RegexOption.IGNORE_CASE,
    )

    private val PASSWORD_TEXT_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
    )

    // Plain values instead of EditorInfo, so the rules run in JVM unit tests.
    @JvmStatic
    fun evaluate(packageName: String?, inputType: Int, imeOptions: Int): InputContextPolicy {
        val reason = when {
            packageName != null && isAlwaysBlockedApp(packageName) -> BlockReason.APP_ALWAYS_BLOCKED
            isSensitiveField(inputType) -> BlockReason.SENSITIVE_FIELD
            (imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0 -> BlockReason.INCOGNITO
            packageName == null || packageName !in ALLOWED_APPS -> BlockReason.APP_NOT_ALLOWED
            else -> null
        }
        if (reason == null) {
            val category = ALLOWED_APPS.getValue(packageName!!)
            return InputContextPolicy(
                onlineAllowed = true,
                learningAllowed = true,
                clipboardSensitive = false,
                selectionEnabled = category == AppCategory.EMAIL || category == AppCategory.DOCUMENTS,
                reason = null,
            )
        }
        return InputContextPolicy(
            onlineAllowed = false,
            learningAllowed = false,
            // R2 contexts are a total block; an app merely missing from R1 is not sensitive.
            clipboardSensitive = reason != BlockReason.APP_NOT_ALLOWED,
            selectionEnabled = false,
            reason = reason,
        )
    }

    private fun isAlwaysBlockedApp(packageName: String): Boolean =
        packageName in ALWAYS_BLOCKED_APPS || ALWAYS_BLOCKED_PACKAGE_PATTERN.containsMatchIn(packageName)

    // Passwords, and number/phone/date fields (PINs, codes, OTPs).
    private fun isSensitiveField(inputType: Int): Boolean =
        when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
            InputType.TYPE_CLASS_TEXT -> (inputType and InputType.TYPE_MASK_VARIATION) in PASSWORD_TEXT_VARIATIONS
            else -> false
        }
}
