package com.gallopkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallopkeyboard.ime.R
import com.gallopkeyboard.ime.haptics.HapticHelper

/**
 * Slim toolbar above the keys: settings + optional voice-panel entry.
 * Mic lives on the bottom key row (classic phone layout).
 */
@Composable
fun MicButtonRow(
    onSwitchKeyboard: () -> Unit,
    onMicTap: () -> Unit = {},
    onVoicePanelToggle: (() -> Unit)? = null,
    isRecording: Boolean = false,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .clickable {
                    HapticHelper.performKeyHaptic(view)
                    onSwitchKeyboard()
                }
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Keyboard",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }

        if (onVoicePanelToggle != null) {
            Text(
                text = if (isRecording) "Listening…" else "Voice panel",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable {
                        HapticHelper.performMicHaptic(view)
                        onVoicePanelToggle()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        } else {
            Text(
                text = "Mic on bottom row",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                fontSize = 12.sp,
                modifier = Modifier
                    .clickable {
                        HapticHelper.performMicHaptic(view)
                        onMicTap()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
