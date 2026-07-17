package com.gallopkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallopkeyboard.ime.R
import com.gallopkeyboard.ime.audio.AudioRecorderEngine
import com.gallopkeyboard.ime.audio.Transcriber
import com.gallopkeyboard.ime.haptics.HapticHelper
import com.gallopkeyboard.ime.panel.PermissionRequester
import com.gallopkeyboard.ime.panel.SmartVoiceButton
import com.gallopkeyboard.ime.panel.SmartVoiceButtonStyle

/**
 * Slim toolbar above the keys: **Voice panel** (left) opens the dedicated voice
 * panel; **Voice** (right) runs inline dictation without leaving the typing panel.
 */
@Composable
fun MicButtonRow(
    onVoicePanelToggle: () -> Unit,
    audioRecorderEngine: AudioRecorderEngine,
    transcriber: Transcriber,
    permissionRequester: PermissionRequester,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.toolbar_voice_panel),
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            modifier = Modifier
                .clickable {
                    HapticHelper.performMicHaptic(view)
                    onVoicePanelToggle()
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        )

        SmartVoiceButton(
            audioRecorderEngine = audioRecorderEngine,
            transcriber = transcriber,
            permissionRequester = permissionRequester,
            style = SmartVoiceButtonStyle.Toolbar,
        )
    }
}
