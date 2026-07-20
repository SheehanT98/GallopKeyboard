package com.gallopkeyboard.ime.suggestion

/**
 * Pure swipe-path → dictionary word resolver (Plan 032).
 *
 * Scores candidates that contain the raw path letters as a subsequence,
 * preferring first/last letter match and higher dictionary frequency.
 * Returns the raw path when no candidate clears the confidence bar.
 */
object SwipeWordResolver {

    /** Minimum frequency-score gap between #1 and #2 before accepting a correction. */
    private const val MIN_SCORE_GAP = 15

    /**
     * Return the best dictionary word for [rawPath], or [rawPath] when under-confident.
     */
    fun resolve(rawPath: String, candidates: List<WordEntry>): String {
        if (rawPath.length < 2 || candidates.isEmpty()) return rawPath

        val pathLower = rawPath.lowercase()
        val scored = candidates
            .asSequence()
            .filter { isSubsequence(pathLower, it.strippedLower) }
            .map { entry -> entry to score(pathLower, entry) }
            .sortedByDescending { it.second }
            .toList()

        if (scored.isEmpty()) return rawPath

        val (best, bestScore) = scored.first()
        if (scored.size >= 2) {
            val runnerUpScore = scored[1].second
            if (bestScore - runnerUpScore < MIN_SCORE_GAP) return rawPath
        }
        return best.word
    }

    /**
     * True when every letter of [path] appears in order inside [word] (casefolded).
     */
    fun isSubsequence(path: String, word: String): Boolean {
        if (path.isEmpty()) return true
        var pathIndex = 0
        for (ch in word) {
            if (ch == path[pathIndex]) {
                pathIndex++
                if (pathIndex == path.length) return true
            }
        }
        return false
    }

    private fun score(path: String, entry: WordEntry): Int {
        val word = entry.strippedLower
        var score = entry.frequency
        if (word.firstOrNull() == path.firstOrNull()) score += 50
        if (word.lastOrNull() == path.lastOrNull()) score += 50
        score -= kotlin.math.abs(word.length - path.length) * 5
        return score
    }
}
