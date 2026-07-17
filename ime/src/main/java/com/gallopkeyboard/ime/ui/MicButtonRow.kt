package com.gallopkeyboard.ime.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.ime.R
import com.gallopkeyboard.ime.haptics.HapticHelper
import com.gallopkeyboard.ime.theme.GallopColors

/**
 * Slim toolbar above the keys. A single **Voice panel** button opens the
 * dedicated thin voice panel (hybrid STT lives there — no inline Voice).
 */
@Composable
fun MicButtonRow(
    onVoicePanelToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = {
                HapticHelper.performMicHaptic(view)
                onVoicePanelToggle()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GallopColors.Accent,
                contentColor = GallopColors.AccentOn,
            ),
            contentPadding = ButtonDefaults.ContentPadding,
        ) {
            Text(
                text = stringResource(R.string.toolbar_voice_panel),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
