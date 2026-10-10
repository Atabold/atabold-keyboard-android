package org.futo.inputmethod.latin.kbfr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionTest {
    private fun target(text: String, selStart: Int = text.length, selEnd: Int = selStart, selection: Boolean = false, max: Int = 4000) =
        Correction.target(FieldText(text, selStart, selEnd), selection, max)

    @Test
    fun chatSendsTheWholeFieldAndIgnoresTheSelection() {
        assertEquals(CorrectTarget.Ok(0, 17, "Je suis allé hier"), target("Je suis allé hier", 3, 7, selection = false))
    }

    @Test
    fun emailSendsTheSelectionOrElseTheWholeField() {
        val text = "Bonjour, je suis allé hier."
        assertEquals(CorrectTarget.Ok(9, 21, "je suis allé"), target(text, 9, 21, selection = true))
        assertEquals(CorrectTarget.Ok(9, 21, "je suis allé"), target(text, 21, 9, selection = true))
        assertEquals(CorrectTarget.Ok(0, text.length, text), target(text, selection = true))
    }

    @Test
    fun surroundingWhitespaceIsNotSent() {
        assertEquals(CorrectTarget.Ok(2, 9, "Bonjour"), target("  Bonjour \n"))
    }

    @Test
    fun emptyOrBlankIsEmpty() {
        assertEquals(CorrectTarget.Empty, target(""))
        assertEquals(CorrectTarget.Empty, target("  \n "))
    }

    @Test
    fun tooLongIsRefusedNotTruncated() {
        assertEquals(CorrectTarget.TooLong(10, false), target("a".repeat(11), max = 10))
        assertEquals(CorrectTarget.TooLong(10, true), target("a".repeat(11), selection = true, max = 10))
        assertEquals(CorrectTarget.Ok(0, 10, "a".repeat(10)), target("a".repeat(10), max = 10))
    }

    @Test
    fun selectionOffsetsOutOfRangeAreClamped() {
        assertEquals(CorrectTarget.Ok(0, 7, "Bonjour"), target("Bonjour", -1, -1, selection = true))
    }

    @Test
    fun requestMatchesTheBackendContract() {
        val options = KbOptions("en", "auto", true, "auto", "short", "fast", "fr", "clean", false, false)
        val body = Correction.requestBody(TextAction.CORRECT, "Je suis \"allé\"", options).decodeToString()
        assertEquals(
            """{"action":"correct","text":"Je suis \"allé\"","options":{"explain_in":"en","target_lang":"auto","translate_foreign":true,"register":"auto","detail":"short","profile":"fast","translate_to":"fr","format_style":"clean","format_fix_grammar":false,"harmonize_variant":false}}""",
            body,
        )
        assertTrue(Correction.requestBody(TextAction.IMPROVE, "x", options).decodeToString().startsWith("""{"action":"improve","""))
        assertTrue(Correction.requestBody(TextAction.TRANSLATE, "x", options).decodeToString().startsWith("""{"action":"translate","""))
        assertTrue(Correction.requestBody(TextAction.FORMAT, "x", options).decodeToString().startsWith("""{"action":"format","""))
    }

    @Test
    fun responseIsParsedAndUnknownFieldsIgnored() {
        val parsed = Correction.parseResponse(
            """{"result":"l'université","detected_lang":"fr","edits":[{"from":"la université","to":"l'université","category":"elision","explanation":"x"}]}"""
        )!!
        assertEquals("l'université", parsed.result)
        assertEquals("elision", parsed.edits.single().category)
        assertTrue(parsed.notes.isEmpty())
        val withNote = Correction.parseResponse(
            """{"result":"my favorite colour","edits":[],"notes":[{"from":"colour","to":"color","explanation":"y"}]}"""
        )!!
        assertEquals(KbNote("colour", "color", "y"), withNote.notes.single())
        assertNull(Correction.parseResponse("""{"edits":[]}"""))
        assertNull(Correction.parseResponse("not json"))
    }

    @Test
    fun errorsMapToTheBackendCodes() {
        assertEquals(KbError.NOT_REGISTERED, Correction.error(401, """{"error":"unauthorized"}"""))
        assertEquals(KbError.DAILY_CAP, Correction.error(429, """{"error":"daily_cap"}"""))
        assertEquals(KbError.PROVIDER_LIMIT, Correction.error(429, """{"error":"provider_limit"}"""))
        assertEquals(KbError.RATE_LIMITED, Correction.error(429, """{"error":"rate_limited"}"""))
        assertEquals(KbError.RATE_LIMITED, Correction.error(429, ""))
        assertEquals(KbError.TOO_LARGE, Correction.error(413, ""))
        assertEquals(KbError.REJECTED, Correction.error(400, ""))
        assertEquals(KbError.UPSTREAM_ERROR, Correction.error(502, ""))
        assertEquals(KbError.UPSTREAM_TIMEOUT, Correction.error(504, ""))
        assertEquals(KbError.UNEXPECTED, Correction.error(500, "<html>"))
    }

    private fun edit(from: String, to: String) = KbEdit(from, to, "vocabulary", "why")

    @Test
    fun explanationsAttachToTheirChanges() {
        val original = "Je suis allé à la université hier pour l'approvvigionamento"
        val result = "Je suis allé à l'université hier pour l'approvisionnement"
        val e1 = edit("à la université", "à l'université")
        val e2 = edit("approvvigionamento", "approvisionnement")
        val explained = Correction.explain(original, result, listOf(e1, e2))
        assertEquals(listOf(listOf(e1), listOf(e2)), explained.groups.map { it.edits })
        assertTrue(explained.unmatched.isEmpty())
    }

    @Test
    fun aShortEditOnlyClaimsWholeWords() {
        // The "a" inside "vais" and "la" must not pull the elision into the preposition's group.
        val preposition = edit("a", "à")
        val elision = edit("la école", "l'école")
        val explained = Correction.explain("Je vais a la école", "Je vais à l'école", listOf(preposition, elision))
        assertEquals(listOf(listOf(preposition), listOf(elision)), explained.groups.map { it.edits })
        assertEquals(listOf("a", "la "), explained.groups.map { it.changes.single().from })
    }

    @Test
    fun aContextReachingANearbyErrorDoesNotMergeTheTwo() {
        // The first edit's context covers the second error too: each error must keep its own checkbox.
        val original = "Hier je suis allé a la plage avec mes amie."
        val result = "Hier je suis allé à la plage avec mes amies."
        val wide = edit("je suis allé a la plage avec mes amie", "je suis allé à la plage avec mes amies")
        val narrow = edit("mes amie", "mes amies")
        val explained = Correction.explain(original, result, listOf(wide, narrow))
        assertEquals(listOf(listOf(wide), listOf(narrow)), explained.groups.map { it.edits })
        assertEquals(listOf("a", "amie"), explained.groups.map { it.changes.single().from })
    }

    @Test
    fun oneEditOverTwoChangesMakesOneGroup() {
        val e = edit("le petit maison", "la petite maison")
        val explained = Correction.explain("C'est le petit maison bleue", "C'est la petite maison bleue", listOf(e))
        assertEquals(1, explained.groups.size)
        assertEquals(listOf(e), explained.groups.single().edits)
    }

    @Test
    fun aNotedWordTheModelChangedAnywayStaysAsWritten() {
        // Seen on a phone with harmonize_variant off: a note and the change anyway.
        val original = "I realized my favorite color is the colour of the ocean."
        val result = "I realized my favorite color is the color of the ocean."
        val note = KbNote("colour", "color", "In American English, the spelling is 'color'.")
        val explained = Correction.explain(original, result, listOf(KbEdit("colour", "color", "variant", "x")), listOf(note))
        assertTrue(explained.groups.isEmpty())
        assertTrue(explained.unmatched.isEmpty())
        assertEquals(original, WordDiff.apply(explained.parts, emptySet()))
    }

    @Test
    fun aRealErrorNextToANotedWordIsStillProposed() {
        val original = "I realized my favorite colour is to dark."
        val result = "I realized my favorite color is too dark."
        val fix = edit("to dark", "too dark")
        val explained = Correction.explain(original, result, listOf(fix), listOf(KbNote("colour", "color", "x")))
        assertEquals(listOf(listOf(fix)), explained.groups.map { it.edits })
        assertEquals(0, explained.groups.single().changes.single().index)
        assertEquals("I realized my favorite colour is too dark.", WordDiff.apply(explained.parts, setOf(0)))
    }

    @Test
    fun aCheckedNoteChangesItsWholeWordOnly() {
        val note = KbNote("colour", "color", "x")
        val parts = WordDiff.diff("The colour of colours is to dark", "The colour of colours is too dark")
        assertEquals("The color of colours is too dark", Correction.apply(parts, setOf(0), listOf(note)))
        assertEquals("The colour of colours is too dark", Correction.apply(parts, setOf(0), emptyList()))
        assertEquals("The color of colours is to dark", Correction.apply(parts, emptySet(), listOf(note)))
    }

    @Test
    fun editsThatMatchNothingAreListedApart() {
        val stray = edit("xyz", "abc")
        val explained = Correction.explain("Je suis allé a Paris", "Je suis allé à Paris", listOf(stray))
        assertEquals(listOf(stray), explained.unmatched)
        assertTrue(explained.groups.single().edits.isEmpty())
    }
}
