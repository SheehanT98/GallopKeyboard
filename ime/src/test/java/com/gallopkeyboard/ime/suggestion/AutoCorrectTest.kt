package com.gallopkeyboard.ime.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Table-driven tests for [decideAutoCorrect] — trust core of Plan 026 autocorrect.
 */
class AutoCorrectTest {

    private val commonDict = listOf(
        "the" to 250,
        "then" to 180,
        "them" to 170,
        "hello" to 210,
        "help" to 180,
        "hell" to 40,
        "world" to 200,
        "word" to 150,
        "would" to 160,
        "there" to 190,
        "their" to 185,
    )

    @Test
    fun `teh corrects to the`() {
        val decision = decideAutoCorrect("teh", commonDict)
        assertEquals("the", decision.replaceWith)
    }

    @Test
    fun `helllo corrects to hello`() {
        val decision = decideAutoCorrect("helllo", commonDict)
        assertEquals("hello", decision.replaceWith)
    }

    @Test
    fun `single letter a does not replace`() {
        val decision = decideAutoCorrect("a", listOf("a" to 255, "an" to 200, "at" to 190))
        assertNull(decision.replaceWith)
    }

    @Test
    fun `blank typed does not replace`() {
        assertNull(decideAutoCorrect("", commonDict).replaceWith)
        assertNull(decideAutoCorrect("  ", commonDict).replaceWith)
    }

    @Test
    fun `exact dictionary word the does not replace`() {
        val decision = decideAutoCorrect("the", commonDict)
        assertNull(decision.replaceWith)
    }

    @Test
    fun `exact dictionary word casefold does not replace`() {
        val decision = decideAutoCorrect("The", commonDict)
        assertNull(decision.replaceWith)
    }

    @Test
    fun `ambiguous near-equal frequency pair does not replace`() {
        // "wold" is edit-distance 1 from both "world" and "would" with near-equal freqs.
        val ambiguous = listOf(
            "world" to 200,
            "would" to 195,
            "word" to 80,
        )
        val decision = decideAutoCorrect("wold", ambiguous)
        assertNull(decision.replaceWith)
    }

    @Test
    fun `preserves initial capital`() {
        val decision = decideAutoCorrect("Teh", commonDict)
        assertEquals("The", decision.replaceWith)
    }

    @Test
    fun `different first letter candidates are ignored`() {
        // "cat" mistype toward "bat" but first letter must match.
        val decision = decideAutoCorrect(
            "cat",
            listOf("bat" to 200, "hat" to 180, "car" to 50),
        )
        // "car" is distance 1 but much lower freq; still valid sole same-first-letter hit.
        assertEquals("car", decision.replaceWith)
    }

    @Test
    fun `no candidates within edit distance leaves typed`() {
        val decision = decideAutoCorrect("zzzzz", commonDict)
        assertNull(decision.replaceWith)
    }

    @Test
    fun `planSpaceCommit respects pref off`() {
        val plan = planSpaceCommit(
            autocorrectEnabled = false,
            wordBeforeCursor = "teh",
            candidates = commonDict,
        )
        assertEquals(SpaceCommitPlan.JustSpace, plan)
    }

    @Test
    fun `planSpaceCommit replaces when enabled`() {
        val plan = planSpaceCommit(
            autocorrectEnabled = true,
            wordBeforeCursor = "teh",
            candidates = commonDict,
        )
        assertEquals(SpaceCommitPlan.Replace("teh", "the"), plan)
    }

    @Test
    fun `autocorrectUndoDeleteLength includes trailing space`() {
        assertEquals(4, autocorrectUndoDeleteLength("the"))
    }

    @Test
    fun `levenshtein known distances`() {
        assertEquals(0, levenshtein("the", "the"))
        // Classic Levenshtein (no transposition): teh → the is 2 substitutions.
        assertEquals(2, levenshtein("teh", "the"))
        assertEquals(1, levenshtein("helllo", "hello"))
        assertTrue(levenshtein("abc", "xyz") == 3)
    }
}
