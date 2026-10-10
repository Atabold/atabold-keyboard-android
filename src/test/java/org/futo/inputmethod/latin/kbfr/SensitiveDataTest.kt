package org.futo.inputmethod.latin.kbfr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveDataTest {
    private fun found(text: String) = SensitiveData.find(text)

    private fun assertFound(kind: SensitiveKind, text: String) = assertTrue("$kind in: $text", kind in found(text))

    private fun assertNotFound(kind: SensitiveKind, text: String) = assertFalse("$kind in: $text", kind in found(text))

    @Test
    fun cardsNeedTheLuhnCheck() {
        assertFound(SensitiveKind.CARD, "the card is 4111111111111111 ok")
        assertFound(SensitiveKind.CARD, "4111 1111 1111 1111")
        assertFound(SensitiveKind.CARD, "5555-5555-5555-4444")
        assertFound(SensitiveKind.CARD, "Amex 3782 822463 10005")
        assertNotFound(SensitiveKind.CARD, "4111 1111 1111 1112")
        assertNotFound(SensitiveKind.CARD, "order no. 123456789012")
        // A phone number is neither a card nor in card groups.
        assertNotFound(SensitiveKind.CARD, "+44 7911 123456")
    }

    @Test
    fun ibanNeedsTheMod97Check() {
        assertFound(SensitiveKind.IBAN, "IBAN: DE89370400440532013000.")
        assertFound(SensitiveKind.IBAN, "FR76 3000 6000 0112 3456 7890 189 merci")
        assertFound(SensitiveKind.IBAN, "GB82 WEST 1234 5698 7654 32 cheers")
        assertFound(SensitiveKind.IBAN, "DE89 3704 0044 0532 0130 00 dans tout")
        assertNotFound(SensitiveKind.IBAN, "DE90370400440532013000")
        assertNotFound(SensitiveKind.IBAN, "Le FR24 dans tout pour moi")
    }

    @Test
    fun taxCodeNeedsItsCheckCharacter() {
        assertFound(SensitiveKind.TAX_CODE, "tax ID RSSMRA80A01H501U thanks")
        assertFound(SensitiveKind.TAX_CODE, "rssmra80a01h501u")
        // A variant of the format: the last digit written as a letter, with its own check character.
        assertFound(SensitiveKind.TAX_CODE, "RSSMRA80A01H50MM")
        assertNotFound(SensitiveKind.TAX_CODE, "RSSMRA80A01H501X")
        assertNotFound(SensitiveKind.SECRET, "RSSMRA80A01H501U")
    }

    @Test
    fun idsOfOtherCountriesNeedTheirOwnChecks() {
        // ES: DNI and NIE, the letter derived from the number.
        assertFound(SensitiveKind.TAX_CODE, "dni 12345678Z gracias")
        assertFound(SensitiveKind.TAX_CODE, "X-1234567-L")
        assertNotFound(SensitiveKind.TAX_CODE, "12345678A")
        assertNotFound(SensitiveKind.TAX_CODE, "X1234567A")
        // FR: social security number, in groups or in one piece, with its key.
        assertFound(SensitiveKind.TAX_CODE, "mon numéro: 2 55 08 14 168 025 38")
        assertFound(SensitiveKind.TAX_CODE, "255081416802538")
        assertNotFound(SensitiveKind.TAX_CODE, "2 55 08 14 168 025 39")
        // DE: tax identification number.
        assertFound(SensitiveKind.TAX_CODE, "Steuer-ID 65 929 970 489")
        assertFound(SensitiveKind.TAX_CODE, "65929970489")
        assertNotFound(SensitiveKind.TAX_CODE, "65929970488")
        assertNotFound(SensitiveKind.TAX_CODE, "12345678901")
        // GB: National Insurance number.
        assertFound(SensitiveKind.TAX_CODE, "NI number AB 12 34 56 C")
        assertFound(SensitiveKind.TAX_CODE, "ab123456c")
        assertNotFound(SensitiveKind.TAX_CODE, "QQ123456C")
        assertNotFound(SensitiveKind.TAX_CODE, "BG123456C")
        // US: Social Security number.
        assertFound(SensitiveKind.TAX_CODE, "SSN 123-45-6789")
        assertNotFound(SensitiveKind.TAX_CODE, "000-12-3456")
        assertNotFound(SensitiveKind.TAX_CODE, "666-12-3456")
        assertNotFound(SensitiveKind.TAX_CODE, "900-12-3456")
        assertNotFound(SensitiveKind.TAX_CODE, "123-00-6789")
        // CH: AHV number.
        assertFound(SensitiveKind.TAX_CODE, "AHV 756.9217.0769.85")
        assertNotFound(SensitiveKind.TAX_CODE, "756.9217.0769.86")
        // None of them is taken for a secret.
        for (id in listOf("12345678Z", "X1234567L", "AB123456C", "65929970489", "255081416802538", "756.9217.0769.85")) {
            assertNotFound(SensitiveKind.SECRET, id)
        }
    }

    @Test
    fun otpIsSixToEightDigitsOnTheirOwn() {
        assertFound(SensitiveKind.OTP, "The code is 482913.")
        assertFound(SensitiveKind.OTP, "code: 12345678")
        assertNotFound(SensitiveKind.OTP, "12345")
        assertNotFound(SensitiveKind.OTP, "123456789")
        assertNotFound(SensitiveKind.OTP, "call me at 333 1234567")
        assertNotFound(SensitiveKind.OTP, "le 05/08/2025 à 14:30")
        assertNotFound(SensitiveKind.OTP, "jane123456@example.com")
    }

    @Test
    fun secretsByPrefixOrRandomLook() {
        assertFound(SensitiveKind.SECRET, "the key is sk-proj-AbCdEf0123456789xyz")
        assertFound(SensitiveKind.SECRET, "ghp_0123456789abcdefghijABCDEFGHIJ")
        assertFound(SensitiveKind.SECRET, "AKIAIOSFODNN7EXAMPLE")
        assertFound(SensitiveKind.SECRET, "-----BEGIN RSA PRIVATE KEY-----")
        assertFound(SensitiveKind.SECRET, "token \"q8Zp3LxK9vW2mN7tR4yB6cD1fG5hJ0sA\"")
        assertFound(SensitiveKind.SECRET, "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
        assertFound(SensitiveKind.SECRET, "f47ac10b-58cc-4372-a567-0e02b2c3d479")
        assertFound(SensitiveKind.SECRET, "password xK9#mP2\$vL5@nQ8!")
    }

    @Test
    fun ordinaryWordsWithDigitsAreNotSecrets() {
        for (text in listOf(
            "I use claude-opus-4-1-20250805 for the code",
            "sending you IMG_20250805_123456.jpg",
            "COVID19-pandemic",
            "approvisionnement2024",
            "https://example.com/a1b2c3d4e5f6g7h8i9",
            "#Paris2024Olympics @user12345678abc",
            "Je suis allé à l'université hier",
        )) assertNotFound(SensitiveKind.SECRET, text)
        // The accepted limit of the rule: a human-made password does not look random.
        assertNotFound(SensitiveKind.SECRET, "Elizabeth2024Paris!")
    }

    @Test
    fun emailAndInternationalPhone() {
        assertFound(SensitiveKind.EMAIL, "write to jane.doe@example.com")
        assertFound(SensitiveKind.PHONE, "+44 7911 123456")
        assertFound(SensitiveKind.PHONE, "0033612345678")
        assertNotFound(SensitiveKind.PHONE, "333 1234567")
    }

    @Test
    fun plainTextHasNothing() {
        assertEquals(emptySet<SensitiveKind>(), found("Je suis allé à la université hier pour retrouver mon friend, 3 fois en 2024."))
    }

    private val defaults: (SensitiveKind) -> String = { it.defaultRule }

    @Test
    fun blockWinsOverAsk() {
        assertEquals(
            SendCheck.Blocked(listOf(SensitiveKind.IBAN)),
            SensitiveData.check("GB82WEST12345698765432 and the code 482913", defaults, confirm = false),
        )
    }

    @Test
    fun askCategoriesOrTheSettingOpenTheConfirmation() {
        assertEquals(SendCheck.Confirm(listOf(SensitiveKind.OTP)), SensitiveData.check("code 482913", defaults, confirm = false))
        assertEquals(SendCheck.Confirm(emptyList()), SensitiveData.check("Bonjour", defaults, confirm = true))
        assertEquals(SendCheck.Send, SensitiveData.check("Bonjour jane@example.com", defaults, confirm = false))
        assertEquals(SendCheck.Send, SensitiveData.check("code 482913", { "ignore" }, confirm = false))
    }

    @Test
    fun confirmBeforeSendingModes() {
        assertFalse(SensitiveData.confirmNeeded("never", 1000, 4000))
        assertTrue(SensitiveData.confirmNeeded("always", 1000, 1))
        assertTrue(SensitiveData.confirmNeeded("above", 1000, 1001))
        assertFalse(SensitiveData.confirmNeeded("above", 1000, 1000))
    }
}
