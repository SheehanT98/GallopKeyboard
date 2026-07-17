package com.gallopkeyboard.ime.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.ime.R
import com.gallopkeyboard.ime.audio.AudioRecorderEngine
import com.gallopkeyboard.ime.audio.Transcriber
import com.gallopkeyboard.ime.theme.GallopColors
import com.gallopkeyboard.ime.theme.GallopVoiceTheme

/** Typing keyboard total height (toolbar + keys). */
val KEYBOARD_PANEL_HEIGHT_DP: Dp = 346.dp

/** Thin dedicated voice bar — full-width speak button + keyboard return. */
val VOICE_PANEL_HEIGHT_DP: Dp = 148.dp

@Composable
fun VoicePanel(
    onSwitchToTyping: () -> Unit,
    audioRecorderEngine: AudioRecorderEngine,
    transcriber: Transcriber,
    permissionRequester: PermissionRequester,
    keyboardHeight: Dp = VOICE_PANEL_HEIGHT_DP,
    showSetupBanner: Boolean = false,
    onSetupVoiceModels: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    GallopVoiceTheme {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(keyboardHeight)
                .background(GallopColors.Surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (showSetupBanner) {
                VoicePanelPromptBanner(
                    onSetupVoiceModels = onSetupVoiceModels,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 0.dp),
                )
            }
            SmartVoiceButton(
                audioRecorderEngine = audioRecorderEngine,
                transcriber = transcriber,
                permissionRequester = permissionRequester,
                style = SmartVoiceButtonStyle.PanelCompact,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(end = 48.dp),
            )
            IconButton(
                onClick = onSwitchToTyping,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Keyboard,
                    contentDescription = stringResource(R.string.panel_toggle_typing),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
