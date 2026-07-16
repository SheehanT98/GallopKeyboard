package com.gallopkeyboard.ime.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Voice-panel color tokens inspired by a clean dark voice UI (not brand-identical).
 *
 * Single source of truth for the voice panel until a formal design system lands.
 */
object GallopColors {
    val Surface = Color(0xFF1A1A1D)
    val OnSurface = Color(0xFFE8E8EA)
    /** Reserved for muted secondary labels. */
    val Placeholder = Color(0xFF6B6B70)
    val Accent = Color(0xFF4A9EFF)
    val AccentOn = Color(0xFFFFFFFF)
    val RecordingAccent = Color(0xFFE85D5D)
}

private val GallopDarkScheme = darkColorScheme(
    background = GallopColors.Surface,
    surface = GallopColors.Surface,
    onSurface = GallopColors.OnSurface,
    primary = GallopColors.Accent,
    onPrimary = GallopColors.AccentOn,
    error = GallopColors.RecordingAccent,
    onError = GallopColors.AccentOn,
    surfaceVariant = Color(0xFF2A2A2E),
    onSurfaceVariant = GallopColors.OnSurface,
)

/**
 * Minimal theme wrapper for the voice panel — flat dark surface, single accent.
 */
@Composable
fun GallopVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GallopDarkScheme,
        content = content,
    )
}
