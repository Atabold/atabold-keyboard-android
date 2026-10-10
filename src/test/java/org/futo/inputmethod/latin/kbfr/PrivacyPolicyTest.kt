package org.futo.inputmethod.latin.kbfr

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyPolicyTest {
    private val text = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL

    private fun evaluate(packageName: String?, inputType: Int = text, imeOptions: Int = 0) =
        PrivacyPolicy.evaluate(packageName, inputType, imeOptions)

    @Test
    fun allowedChatAppAllowsEverythingButSelection() {
        val policy = evaluate("com.whatsapp")
        assertEquals(InputContextPolicy(true, true, false, false, null), policy)
    }

    @Test
    fun selectionOnlyInEmailAndDocumentApps() {
        for (pkg in listOf("com.google.android.gm", "ch.protonmail.android", "com.google.android.apps.docs.editors.docs", "md.obsidian")) {
            assertTrue(pkg, evaluate(pkg).selectionEnabled)
        }
        for (pkg in listOf("com.whatsapp", "com.instagram.android", "com.anthropic.claude")) {
            assertFalse(pkg, evaluate(pkg).selectionEnabled)
        }
    }

    @Test
    fun unknownOrNullPackageIsBlockedButNotSensitive() {
        for (pkg in listOf(null, "com.android.chrome", "com.example.bank")) {
            val policy = evaluate(pkg)
            assertEquals(InputContextPolicy(false, false, false, false, BlockReason.APP_NOT_ALLOWED), policy)
        }
    }

    @Test
    fun packageNamesAreMatchedExactly() {
        assertEquals(BlockReason.APP_NOT_ALLOWED, evaluate("com.whatsapp.fake").reason)
        assertEquals(BlockReason.APP_NOT_ALLOWED, evaluate("COM.WHATSAPP").reason)
    }

    @Test
    fun alwaysBlockedAppsAreATotalBlock() {
        val policy = evaluate("proton.android.pass")
        assertEquals(InputContextPolicy(false, false, true, false, BlockReason.APP_ALWAYS_BLOCKED), policy)
    }

    @Test
    fun heuristicCatchesUnlistedAuthenticatorsAndPasswordManagers() {
        for (pkg in listOf("org.example.id.authenticator", "com.authenticator", "com.example.TOTP", "com.lastpass.lpandroid", "com.example.passwordmanager", "com.example.vault")) {
            assertEquals(pkg, BlockReason.APP_ALWAYS_BLOCKED, evaluate(pkg).reason)
        }
    }

    @Test
    fun noAllowedAppMatchesTheBlockingHeuristic() {
        // Guards the allowlist against an entry that the blocked list or the heuristic would silently block.
        for (pkg in PrivacyPolicy.ALLOWED_APPS.keys) {
            assertTrue(pkg, evaluate(pkg).onlineAllowed)
        }
    }

    @Test
    fun everyAlwaysBlockedAppIsATotalBlock() {
        for (pkg in PrivacyPolicy.ALWAYS_BLOCKED_APPS) {
            assertEquals(pkg, BlockReason.APP_ALWAYS_BLOCKED, evaluate(pkg).reason)
        }
    }

    @Test
    fun sensitiveFieldTypesBlockEvenInAllowedApps() {
        val sensitive = listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
        )
        for (inputType in sensitive) {
            val policy = evaluate("com.whatsapp", inputType)
            assertEquals(Integer.toHexString(inputType), InputContextPolicy(false, false, true, false, BlockReason.SENSITIVE_FIELD), policy)
        }
    }

    @Test
    fun ordinaryTextVariationsStayAllowed() {
        for (variation in listOf(InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE, InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, InputType.TYPE_TEXT_VARIATION_EMAIL_SUBJECT)) {
            assertTrue(evaluate("com.google.android.gm", InputType.TYPE_CLASS_TEXT or variation).onlineAllowed)
        }
    }

    @Test
    fun incognitoFlagIsATotalBlock() {
        val policy = evaluate("com.whatsapp", imeOptions = EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertEquals(InputContextPolicy(false, false, true, false, BlockReason.INCOGNITO), policy)
    }

    @Test
    fun blockedAppWinsOverSensitiveFieldAndAllowlist() {
        assertEquals(BlockReason.APP_ALWAYS_BLOCKED, evaluate("com.bitwarden.authenticator", InputType.TYPE_CLASS_NUMBER).reason)
    }
}
