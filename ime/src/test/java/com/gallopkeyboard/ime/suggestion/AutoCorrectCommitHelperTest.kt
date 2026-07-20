package com.gallopkeyboard.ime.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Service-level helper coverage for autocorrect replace + single undo (Plan 026).
 *
 * Mirrors the InputConnection sequence without Android IME plumbing.
 */
class AutoCorrectCommitHelperTest {

    private val dict = listOf(
        "the" to 250,
        "hello" to 210,
        "help" to 180,
    )

    /** Simulates editor buffer before cursor for space-commit + undo tests. */
    private class FakeBuffer(initial: String = "") {
        var text: String = initial

        fun wordBeforeCursor(): String =
            text.split(" ", "\n").lastOrNull().orEmpty()

        fun applySpacePlan(plan: SpaceCommitPlan): LastAutoCorrect? {
            return when (plan) {
                is SpaceCommitPlan.Replace -> {
                    require(text.endsWith(plan.typed)) {
                        "buffer '$text' should end with typed '${plan.typed}'"
                    }
                    text = text.dropLast(plan.typed.length) + plan.replacement + " "
                    LastAutoCorrect(plan.typed, plan.replacement)
                }
                SpaceCommitPlan.JustSpace -> {
                    text += " "
                    null
                }
            }
        }

        fun undoOrDelete(last: LastAutoCorrect?): LastAutoCorrect? {
            if (last != null) {
                val deleteLen = autocorrectUndoDeleteLength(last.replacement)
                text = text.dropLast(deleteLen) + last.original
                return null
            }
            if (text.isNotEmpty()) {
                text = text.dropLast(1)
            }
            return null
        }
    }

    @Test
    fun `pref off never replaces on space`() {
        val buf = FakeBuffer("teh")
        val plan = planSpaceCommit(false, buf.wordBeforeCursor(), dict)
        val last = buf.applySpacePlan(plan)
        assertEquals("teh ", buf.text)
        assertNull(last)
    }

    @Test
    fun `pref on replaces teh then undo restores original`() {
        val buf = FakeBuffer("teh")
        val plan = planSpaceCommit(true, buf.wordBeforeCursor(), dict)
        assertEquals(SpaceCommitPlan.Replace("teh", "the"), plan)
        var last = buf.applySpacePlan(plan)
        assertEquals("the ", buf.text)
        assertEquals(LastAutoCorrect("teh", "the"), last)

        last = buf.undoOrDelete(last)
        assertEquals("teh", buf.text)
        assertNull(last)
    }

    @Test
    fun `second delete after undo is normal backspace`() {
        val buf = FakeBuffer("teh")
        var last = buf.applySpacePlan(
            planSpaceCommit(true, buf.wordBeforeCursor(), dict),
        )
        last = buf.undoOrDelete(last) // undo → "teh"
        last = buf.undoOrDelete(last) // normal delete → "te"
        assertEquals("te", buf.text)
        assertNull(last)
    }
}
