package com.gallopkeyboard.ime.ui

/**
 * Pure helpers for turning a swipe path over letter keys into commit text.
 */
object SwipePathHelper {

    /**
     * Collapse consecutive duplicate letters (e.g. slide jitter on the same key).
     * Dwell-emitted doubles are preserved when the path is built without dedupe first.
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
     * Keep only alphabetic characters and join.
     *
     * Slide dedupe is handled in [SwipeTypingController]; dwell adds intentional doubles.
     */
    fun pathToWord(path: List<Char>): String =
        path.filter { it.isLetter() }.joinToString("")
}
