package com.gallopkeyboard.ime

/**
 * Pure editing helpers for code-point/word delete and space-bar cursor drag.
 *
 * Word-delete length math uses Java [String.length] (UTF-16 code units), matching
 * [android.view.inputmethod.InputConnection.deleteSurroundingText]. That is correct
 * for whitespace word boundaries; surrogate-pair emoji inside a "word" are counted
 * as two units each, which is what the InputConnection API expects.
 */
enum class DeleteRepeatMode {
    CHAR,
    WORD,
}

/**
 * Counts UTF-16 code units to remove for a backward word delete.
 *
 * Trims trailing whitespace, then deletes back to the previous whitespace
 * (or the start of [before]). Example: `"hello world "` → deletes `"world "`.
 */
fun countCharsToDeleteForWord(before: String): Int {
    if (before.isEmpty()) return 0
    var end = before.length
    while (end > 0 && before[end - 1].isWhitespace()) {
        end--
    }
    var start = end
    while (start > 0 && !before[start - 1].isWhitespace()) {
        start--
    }
    return before.length - start
}

/**
 * Delete-hold acceleration policy.
 *
 * [repeatIndex] is 0 for the initial press, then 1, 2, … for each repeat tick.
 * Switches to word delete after ~8 character deletes or once the hold exceeds 900 ms.
 */
fun deleteMode(repeatIndex: Int, heldMs: Long): DeleteRepeatMode {
    return if (repeatIndex >= 8 || heldMs >= 900L) {
        DeleteRepeatMode.WORD
    } else {
        DeleteRepeatMode.CHAR
    }
}

/**
 * Absolute cursor offset after a horizontal drag of [deltaChars] from the current
 * position ([beforeLen] chars before the cursor). Clamped to `[0, beforeLen + afterLen]`.
 */
fun cursorOffsetAfterDrag(beforeLen: Int, afterLen: Int, deltaChars: Int): Int {
    val total = beforeLen + afterLen
    return (beforeLen + deltaChars).coerceIn(0, total)
}
