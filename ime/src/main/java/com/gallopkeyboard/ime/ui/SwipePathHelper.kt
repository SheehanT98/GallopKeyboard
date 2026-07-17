package com.gallopkeyboard.ime.ui

/**
 * Pure helpers for turning a swipe path over letter keys into commit text.
 */
object SwipePathHelper {

    /**
     * Collapse consecutive duplicate letters (e.g. h-e-l-l-o path may visit "l" twice).
     */
    fun dedupeConsecutive(letters: List<Char>): List<Char> {
        if (letters.isEmpty()) return emptyList()
        val result = ArrayList<Char>(letters.size)
        var last: Char? = null
        for (letter in letters) {
            if (letter != last) {
                result.add(letter)
                last = letter
            }
        }
        return result
    }

    /**
     * Keep only alphabetic characters, dedupe consecutive duplicates, and join.
     */
    fun pathToWord(path: List<Char>): String =
        dedupeConsecutive(path.filter { it.isLetter() }).joinToString("")
}
