package com.gallopkeyboard.ime.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Voice-panel color tokens — light surface for the thin voice bar.
 */
object GallopColors {
    val Surface = Color(0xFFF5F5F7)
    val OnSurface = Color(0xFF1C1C1E)
    /** Reserved for muted secondary labels. */
    val Placeholder = Color(0xFF8E8E93)
    val Accent = Color(0xFF007AFF)
    val AccentOn = Color(0xFFFFFFFF)
    val RecordingAccent = Color(0xFFE85D5D)
}

private val GallopLightScheme = lightColorScheme(
    background = GallopColors.Surface,
    surface = GallopColors.Surface,
    onSurface = GallopColors.OnSurface,
    primary = GallopColors.Accent,
    onPrimary = GallopColors.AccentOn,
    error = GallopColors.RecordingAccent,
    onError = GallopColors.AccentOn,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = GallopColors.OnSurface,
)

/**
 * Minimal theme wrapper for the voice panel — flat light surface, single accent.
 */
@Composable
fun GallopVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GallopLightScheme,
        content = content,
    )
}
