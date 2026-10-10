package org.futo.inputmethod.latin.kbfr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardSensitivityTest {
    @Test
    fun otpCodesOfSixToEightDigits() {
        for (code in listOf("123456", "1234567", "12345678", " 482913\n")) {
            assertTrue(code, ClipboardSensitivity.looksSensitive(code))
        }
        for (code in listOf("12345", "123456789", "123 456")) {
            assertFalse(code, ClipboardSensitivity.looksSensitive(code))
        }
    }

    @Test
    fun randomLookingSecrets() {
        for (secret in listOf(
            "sk-proj-abc123DEF456ghi",
            "ghp_1234567890abcdefABCDEF",
            "AKIAIOSFODNN7EXAMPLE",
            "Tr0ub4dor&3xyz",
            "GB82WEST12345698765432",
        )) {
            assertTrue(secret, ClipboardSensitivity.looksSensitive(secret))
        }
    }

    @Test
    fun ordinaryTextLinksAndEmailsAreNotSensitive() {
        for (text in listOf(
            "Hi, see you tomorrow at 8",
            "https://example.com/watch?v=abc123def456",
            "www.example.com/abc123def456",
            "jane.doe1985@example.com",
            "correcthorsebatterystaple",
            "approvisionnement",
            "hello",
            "",
        )) {
            assertFalse(text, ClipboardSensitivity.looksSensitive(text))
        }
    }
}
