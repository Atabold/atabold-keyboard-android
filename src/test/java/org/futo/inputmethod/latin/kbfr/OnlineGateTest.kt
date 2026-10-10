package org.futo.inputmethod.latin.kbfr

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineGateTest {
    private val text = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
    private val whatsapp = PrivacyPolicy.evaluate("com.whatsapp", text, 0)

    private fun block(
        policy: InputContextPolicy? = whatsapp,
        contaminated: Boolean = false,
        onlineEnabled: Boolean = true,
        integrityOk: Boolean? = true,
        networkAllowed: Boolean = true,
    ) = OnlineGate.block(policy, contaminated, onlineEnabled, integrityOk, networkAllowed)

    @Test
    fun allowedAppWithEverythingOkIsNotBlocked() {
        assertNull(block())
    }

    @Test
    fun policyReasonsCarryOver() {
        assertEquals(OnlineBlock.APP_NOT_ALLOWED, block(PrivacyPolicy.evaluate("com.android.chrome", text, 0)))
        assertEquals(OnlineBlock.APP_ALWAYS_BLOCKED, block(PrivacyPolicy.evaluate("proton.android.pass", text, 0)))
        val password = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        assertEquals(OnlineBlock.SENSITIVE_FIELD, block(PrivacyPolicy.evaluate("com.whatsapp", password, 0)))
    }

    @Test
    fun noFieldYetIsBlocked() {
        assertEquals(OnlineBlock.APP_NOT_ALLOWED, block(policy = null))
    }

    @Test
    fun switchIntegrityAndNetworkEachBlock() {
        assertEquals(OnlineBlock.ONLINE_OFF, block(onlineEnabled = false))
        assertEquals(OnlineBlock.INTEGRITY, block(integrityOk = false))
        assertEquals(OnlineBlock.NETWORK_NOT_ALLOWED, block(networkAllowed = false))
    }

    @Test
    fun aContaminatedFieldBlocksRightAfterThePolicy() {
        assertEquals(OnlineBlock.CONTAMINATED, block(contaminated = true))
        assertEquals(OnlineBlock.CONTAMINATED, block(contaminated = true, onlineEnabled = false, integrityOk = false))
        val chrome = PrivacyPolicy.evaluate("com.android.chrome", text, 0)
        assertEquals(OnlineBlock.APP_NOT_ALLOWED, block(chrome, contaminated = true))
    }

    @Test
    fun integrityNotCheckedYetShowsNoLock() {
        assertNull(block(integrityOk = null))
    }

    @Test
    fun theFieldRuleComesFirst() {
        val chrome = PrivacyPolicy.evaluate("com.android.chrome", text, 0)
        assertEquals(OnlineBlock.APP_NOT_ALLOWED, block(chrome, onlineEnabled = false, integrityOk = false, networkAllowed = false))
        assertEquals(OnlineBlock.ONLINE_OFF, block(onlineEnabled = false, integrityOk = false, networkAllowed = false))
    }
}
