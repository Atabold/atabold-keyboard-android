package org.futo.inputmethod.latin.kbfr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedFingerprintsTest {
    @Test
    fun aFieldIsContaminatedWhileItHoldsThePastedText() {
        val pasted = PastedFingerprints()
        assertTrue(pasted.isEmpty())
        pasted.add("482913")
        assertTrue(pasted.foundIn("Le code est 482913, merci"))
        assertFalse(pasted.foundIn("Le code est 48291, merci"))
        assertFalse(pasted.foundIn(""))
        assertFalse(pasted.foundIn("Bonjour"))
    }

    @Test
    fun surroundingWhitespaceOfTheClipDoesNotMatter() {
        val pasted = PastedFingerprints()
        pasted.add(" xK9#mP2\$vL5@nQ8\n")
        assertTrue(pasted.foundIn("pwd:xK9#mP2\$vL5@nQ8!"))
        pasted.add("   ")
        assertFalse(pasted.foundIn("   "))
    }

    @Test
    fun aLongClipIsRecognisedByItsBeginning() {
        val pasted = PastedFingerprints()
        val long = "a1".repeat(200)
        pasted.add(long)
        assertTrue(pasted.foundIn("x $long y"))
        assertTrue(pasted.foundIn(long.take(PastedFingerprints.MAX_CHARS)))
    }

    @Test
    fun onlyTheNewestFingerprintsAreKept() {
        val pasted = PastedFingerprints()
        pasted.add("code-0")
        for (i in 1..PastedFingerprints.MAX_PRINTS) pasted.add("code-$i")
        assertFalse(pasted.foundIn("code-0"))
        assertTrue(pasted.foundIn("code-1"))
        assertTrue(pasted.foundIn("code-${PastedFingerprints.MAX_PRINTS}"))
    }
}
