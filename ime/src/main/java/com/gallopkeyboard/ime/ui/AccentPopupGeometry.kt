package com.gallopkeyboard.ime.ui

import androidx.compose.ui.unit.dp

/** Width of each accent cell — must match [AccentPopup] layout. */
val ACCENT_CELL_WIDTH_DP = 44.dp

/**
 * Horizontal shift (px) to keep the accent popup strip within [parentWidthPx].
 *
 * Positive values shift the popup right; negative shift left.
 */
fun computeAccentShiftPx(
    keyLeftPx: Float,
    keyWidthPx: Float,
    accentCount: Int,
    accentCellWidthPx: Float,
    parentWidthPx: Float,
): Float {
    if (accentCount <= 0 || keyWidthPx <= 0f) return 0f
    val popupWidthPx = accentCount * accentCellWidthPx
    val naturalLeftPx = keyLeftPx + (keyWidthPx - popupWidthPx) / 2f
    val clampedLeftPx = naturalLeftPx.coerceIn(
        0f,
        (parentWidthPx - popupWidthPx).coerceAtLeast(0f),
    )
    return clampedLeftPx - naturalLeftPx
}

/**
 * Maps a pointer X coordinate to an accent cell index using popup strip geometry.
 *
 * [pointerX] and [keyLeftPx] must share the same coordinate space (key-local or parent).
 */
fun resolveAccentIndex(
    pointerX: Float,
    keyLeftPx: Float,
    keyWidthPx: Float,
    accentCount: Int,
    accentCellWidthPx: Float,
    accentShiftPx: Float,
): Int? {
    if (accentCount <= 0 || keyWidthPx <= 0f) return null
    val popupWidthPx = accentCount * accentCellWidthPx
    val popupLeftPx = keyLeftPx + (keyWidthPx - popupWidthPx) / 2f + accentShiftPx
    val index = ((pointerX - popupLeftPx) / accentCellWidthPx).toInt()
    return index.takeIf { it in 0 until accentCount }
}
