package com.gallopkeyboard.ime.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.gallopkeyboard.core.models.VoiceSetupIntents
import com.gallopkeyboard.core.theme.ThemeMode
import com.gallopkeyboard.ime.asr.VoiceModelPromptState
import com.gallopkeyboard.ime.audio.AudioRecorderEngine
import com.gallopkeyboard.ime.audio.Transcriber

/**
 * Root panel switcher for the IME keyboard view.
 *
 * [typingContent] wraps the existing Dictus typing keyboard (Compose).
 * [VoicePanel] is shown when [PanelController] state is [PanelState.VOICE].
 */
@Composable
fun PanelHost(
    controller: PanelController,
    themeMode: ThemeMode,
    audioRecorderEngine: AudioRecorderEngine,
    transcriber: Transcriber,
    permissionRequester: PermissionRequester,
    promptState: VoiceModelPromptState,
    keyboardHeight: Dp = KEYBOARD_PANEL_HEIGHT_DP,
    typingContent: @Composable () -> Unit,
) {
    val state by controller.state.collectAsState()
    val showSetupBanner by promptState.showSetupBanner.collectAsState()
    val context = LocalContext.current
    when (state) {
        PanelState.TYPING -> typingContent()
        PanelState.VOICE -> VoicePanel(
            onSwitchToTyping = controller::showTyping,
            audioRecorderEngine = audioRecorderEngine,
            transcriber = transcriber,
            permissionRequester = permissionRequester,
            keyboardHeight = keyboardHeight,
            showSetupBanner = showSetupBanner,
            onSetupVoiceModels = {
                context.startActivity(VoiceSetupIntents.onboardingIntent(context))
            },
        )
    }
}
