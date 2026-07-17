package com.gallopkeyboard.ime.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.core.models.ModelRegistry
import com.gallopkeyboard.ime.R

/**
 * Shown when on-device voice models are missing.
 * Plain explanation + one download button — no branding chrome.
 */
@Composable
fun VoicePanelPromptBanner(
    onSetupVoiceModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeMb = (ModelRegistry.defaultBundleSizeBytes() / (1024L * 1024L)).toInt()
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_panel_setup_body, sizeMb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onSetupVoiceModels,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.voice_panel_setup_cta))
            }
        }
    }
}
