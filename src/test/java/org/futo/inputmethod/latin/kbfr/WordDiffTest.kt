package org.futo.inputmethod.latin.kbfr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDiffTest {
    private fun changes(original: String, result: String) =
        WordDiff.diff(original, result).filterIsInstance<DiffPart.Changed>().map { it.change }

    @Test
    fun tokenizeIsLossless() {
        val text = "Je suis allé à l'université, hier… 🙂\n\tAujourd'hui: 3,5 € — est-ce ok? fin\u0085"
        assertEquals(text, WordDiff.tokenize(text).joinToString(""))
    }

    @Test
    fun identicalTextsHaveNoChanges() {
        assertTrue(changes("Bonjour à tous", "Bonjour à tous").isEmpty())
    }

    @Test
    fun elisionIsOneSmallChange() {
        val c = changes("Je suis allé à la université hier", "Je suis allé à l'université hier").single()
        assertEquals("la ", c.from)
        assertEquals("l'", c.to)
        assertEquals("Je suis allé à ".length, c.origStart)
    }

    @Test
    fun wordsSeparatedByOneSpaceAreSeparateChanges() {
        val c = changes("une le petit maison", "une la petite maison")
        assertEquals(listOf("le" to "la", "petit" to "petite"), c.map { it.from to it.to })
    }

    @Test
    fun prepositionAndElisionStayTwoChanges() {
        val c = changes("Je vais a la école", "Je vais à l'école")
        assertEquals(listOf("a" to "à", "la " to "l'"), c.map { it.from to it.to })
    }

    @Test
    fun distantChangesStaySeparate() {
        val c = changes("Je suis allé a Paris avec mon amis", "Je suis allé à Paris avec mes amis")
        assertEquals(listOf("a" to "à", "mon" to "mes"), c.map { it.from to it.to })
    }

    @Test
    fun applyAllGivesTheResultAndApplyNoneTheOriginal() {
        val original = "Je suis allé a la université avec mon amis hier soir"
        val result = "Je suis allé à l'université avec mes amis hier soir."
        val parts = WordDiff.diff(original, result)
        val all = parts.filterIsInstance<DiffPart.Changed>().map { it.change.index }.toSet()
        assertEquals(result, WordDiff.apply(parts, all))
        assertEquals(original, WordDiff.apply(parts, emptySet()))
    }

    @Test
    fun applyASubsetKeepsTheRejectedChangesOriginal() {
        val parts = WordDiff.diff("Je suis allé a Paris avec mon amis", "Je suis allé à Paris avec mes amis")
        assertEquals("Je suis allé à Paris avec mon amis", WordDiff.apply(parts, setOf(0)))
    }

    @Test
    fun offsetsPointAtTheChangeInBothTexts() {
        val original = "Il a manger une pomme"
        val result = "Il a mangé une pomme"
        val c = changes(original, result).single()
        assertEquals("manger", original.substring(c.origStart, c.origEnd))
        assertEquals("mangé", result.substring(c.resStart, c.resEnd))
    }

    @Test
    fun hugeRewriteFallsBackToOneChange() {
        val original = (1..2500).joinToString(" ") { "a$it" }
        val result = (1..2500).joinToString(" ") { "b$it" }
        val parts = WordDiff.diff(original, result)
        assertEquals(1, parts.count { it is DiffPart.Changed })
        assertEquals(result, WordDiff.apply(parts, setOf(0)))
    }
}
