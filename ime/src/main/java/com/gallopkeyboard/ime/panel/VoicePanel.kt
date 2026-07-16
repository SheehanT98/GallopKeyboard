package com.gallopkeyboard.ime.panel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.core.theme.DictusTheme
import com.gallopkeyboard.core.theme.ThemeMode
import com.gallopkeyboard.ime.R

/** Matches [com.gallopkeyboard.ime.ui.KeyboardScreen] max height (suggestion + mic row + keys). */
val KEYBOARD_PANEL_HEIGHT_DP: Dp = 346.dp

/**
 * Placeholder voice panel per HANDOFF.md UX spec.
 *
 * Plan 005 replaces the center [Button] with [SmartVoiceButton] gesture handling.
 */
@Composable
fun VoicePanel(
    onSwitchToTyping: () -> Unit,
    keyboardHeight: Dp = KEYBOARD_PANEL_HEIGHT_DP,
    themeMode: ThemeMode = ThemeMode.DARK,
    modifier: Modifier = Modifier,
) {
    DictusTheme(themeMode = themeMode) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(keyboardHeight)
                .padding(horizontal = 8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = { /* Plan 005: smart button gestures */ },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(64.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(text = stringResource(R.string.voice_panel_placeholder_button))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {}, enabled = false) {
                            Text("Think?")
                        }
                        TextButton(onClick = {}, enabled = false) {
                            Text("Search?")
                        }
                    }
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

@Preview(widthDp = 360, heightDp = 346)
@Composable
private fun VoicePanelPreview() {
    VoicePanel(onSwitchToTyping = {})
}
