package com.gallopkeyboard.ime.suggestion

/**
 * Opt-in on-device autocorrect decision helpers (Plan 026).
 *
 * Pure functions — no Android / IME dependencies. Aggressiveness is intentionally
 * conservative: prefer under-correcting over random replacements.
 */

data class AutoCorrectDecision(
    /** Corrected word, or null to leave the typed word unchanged. */
    val replaceWith: String?,
)

/**
 * Decide whether [typed] should be replaced by a dictionary candidate on space.
 *
 * @param typed Word before the cursor (no trailing space).
 * @param candidates Word → frequency pairs (same first letter preferred by caller).
 * @param maxEditDistance Maximum Levenshtein distance to consider (default 2).
 */
fun decideAutoCorrect(
    typed: String,
    candidates: List<Pair<String, Int>>,
    maxEditDistance: Int = 2,
): AutoCorrectDecision {
    if (typed.isBlank() || typed.length < 2) {
        return AutoCorrectDecision(replaceWith = null)
    }

    val typedLower = typed.lowercase()
    val firstLetter = typedLower.first()

    // Already a known word → never replace.
    if (candidates.any { it.first.lowercase() == typedLower }) {
        return AutoCorrectDecision(replaceWith = null)
    }

    val scored = candidates
        .asSequence()
        .filter { candidate ->
            val word = candidate.first
            word.isNotEmpty() &&
                word.lowercase().first() == firstLetter &&
                levenshtein(typedLower, word.lowercase()) <= maxEditDistance
        }
        .map { (word, freq) -> ScoredCandidate(word, freq) }
        .sortedByDescending { it.frequency }
        .toList()

    if (scored.isEmpty()) {
        return AutoCorrectDecision(replaceWith = null)
    }

    val top = scored[0]
    // Ambiguity: top two frequencies too close → leave typed alone.
    if (scored.size >= 2 && frequenciesTooClose(top.frequency, scored[1].frequency)) {
        return AutoCorrectDecision(replaceWith = null)
    }

    return AutoCorrectDecision(replaceWith = preserveInitialCapital(typed, top.word))
}

/**
 * Plan the space-commit action: either replace the typed word + space, or just insert space.
 */
sealed class SpaceCommitPlan {
    data class Replace(val typed: String, val replacement: String) : SpaceCommitPlan()
    data object JustSpace : SpaceCommitPlan()
}

/**
 * Build a [SpaceCommitPlan] from the current before-cursor word and dictionary candidates.
 *
 * @param autocorrectEnabled When false, always [SpaceCommitPlan.JustSpace].
 * @param wordBeforeCursor Word fragment immediately before the cursor (no space).
 * @param candidates Word → frequency candidates from [DictionaryEngine.candidatesNear].
 */
fun planSpaceCommit(
    autocorrectEnabled: Boolean,
    wordBeforeCursor: String,
    candidates: List<Pair<String, Int>>,
): SpaceCommitPlan {
    if (!autocorrectEnabled) return SpaceCommitPlan.JustSpace
    val decision = decideAutoCorrect(wordBeforeCursor, candidates)
    val replacement = decision.replaceWith ?: return SpaceCommitPlan.JustSpace
    return SpaceCommitPlan.Replace(typed = wordBeforeCursor, replacement = replacement)
}

/**
 * Remembered autocorrect for a single undo via immediate backspace.
 */
data class LastAutoCorrect(
    val original: String,
    val replacement: String,
)

/**
 * UTF-16 length to delete when undoing an autocorrect (`replacement` + trailing space).
 */
fun autocorrectUndoDeleteLength(replacement: String): Int = replacement.length + 1

/**
 * Levenshtein edit distance between [a] and [b].
 */
internal fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)

    for (i in 1..a.length) {
        curr[0] = i
        val ca = a[i - 1]
        for (j in 1..b.length) {
            val cost = if (ca == b[j - 1]) 0 else 1
            curr[j] = minOf(
                curr[j - 1] + 1,
                prev[j] + 1,
                prev[j - 1] + cost,
            )
        }
        for (j in prev.indices) {
            prev[j] = curr[j]
        }
    }
    return prev[b.length]
}

private data class ScoredCandidate(val word: String, val frequency: Int)

/**
 * Two frequencies are "too close" when the gap is under 15 absolute or under 20% of the top.
 */
private fun frequenciesTooClose(top: Int, second: Int): Boolean {
    val gap = top - second
    val relativeThreshold = (top * 0.2).toInt().coerceAtLeast(1)
    return gap < 15 || gap < relativeThreshold
}

private fun preserveInitialCapital(typed: String, replacement: String): String {
    if (typed.isEmpty() || replacement.isEmpty()) return replacement
    if (!typed[0].isUpperCase()) return replacement
    return replacement[0].uppercaseChar() + replacement.substring(1)
}
