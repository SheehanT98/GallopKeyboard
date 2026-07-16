package com.gallopkeyboard.ime.panel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.ime.R

/**
 * Persistent banner shown in the voice panel when on-device models are missing.
 * Tapping opens the launcher app onboarding flow (user-initiated; required on Android 12+).
 */
@Composable
fun VoicePanelPromptBanner(
    onSetupVoiceModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSetupVoiceModels),
        color = MaterialTheme.colorScheme.errorContainer,
        tonalElevation = 2.dp,
    ) {
        Text(
            text = stringResource(R.string.voice_panel_setup_models),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
