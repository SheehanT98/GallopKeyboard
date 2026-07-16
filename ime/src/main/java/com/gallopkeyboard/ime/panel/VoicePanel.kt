package com.gallopkeyboard.ime.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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

/** Matches [com.gallopkeyboard.ime.ui.KeyboardScreen] max height (suggestion + mic row + keys). */
val KEYBOARD_PANEL_HEIGHT_DP: Dp = 346.dp

@Composable
fun VoicePanel(
    onSwitchToTyping: () -> Unit,
    audioRecorderEngine: AudioRecorderEngine,
    transcriber: Transcriber,
    permissionRequester: PermissionRequester,
    keyboardHeight: Dp = KEYBOARD_PANEL_HEIGHT_DP,
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
                .padding(horizontal = 16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showSetupBanner) {
                    VoicePanelPromptBanner(onSetupVoiceModels = onSetupVoiceModels)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SmartVoiceButton(
                        audioRecorderEngine = audioRecorderEngine,
                        transcriber = transcriber,
                        permissionRequester = permissionRequester,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    IconButton(
                        onClick = onSwitchToTyping,
                        modifier = Modifier.size(40.dp),
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
    }
}
