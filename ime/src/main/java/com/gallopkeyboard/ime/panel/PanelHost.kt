package com.gallopkeyboard.ime.panel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.gallopkeyboard.core.models.ModelInstaller
import com.gallopkeyboard.core.models.ModelRegistry
import com.gallopkeyboard.core.models.VoiceSetupIntents
import com.gallopkeyboard.core.theme.ThemeMode
import com.gallopkeyboard.ime.asr.ModelLifecycleController
import com.gallopkeyboard.ime.asr.VoiceModelPromptState
import com.gallopkeyboard.ime.audio.AudioRecorderEngine
import com.gallopkeyboard.ime.audio.Transcriber

/**
 * Voice-only dependencies. Resolved lazily when the voice panel opens so a
 * typing-only keyboard open does not construct ASR / recorder / Whisper graph.
 */
data class VoicePanelDependencies(
    val audioRecorderEngine: AudioRecorderEngine,
    val transcriber: Transcriber,
    val permissionRequester: PermissionRequester,
    val promptState: VoiceModelPromptState,
    val modelLifecycleManager: ModelLifecycleController,
)

/**
 * Root panel switcher for the IME keyboard view.
 *
 * [typingContent] wraps the existing typing keyboard (Compose).
 * [VoicePanel] is shown when [PanelController] state is [PanelState.VOICE].
 */
@Composable
fun PanelHost(
    controller: PanelController,
    @Suppress("UNUSED_PARAMETER") themeMode: ThemeMode,
    voiceDependencies: () -> VoicePanelDependencies,
    keyboardHeight: Dp = KEYBOARD_PANEL_HEIGHT_DP,
    typingContent: @Composable () -> Unit,
) {
    val state by controller.state.collectAsState()
    val context = LocalContext.current
    var cachedVoiceDeps by remember { mutableStateOf<VoicePanelDependencies?>(null) }
    var previousState by remember { mutableStateOf(state) }

    val voiceDeps = if (state == PanelState.VOICE) {
        cachedVoiceDeps ?: voiceDependencies().also { cachedVoiceDeps = it }
    } else {
        null
    }

    LaunchedEffect(state) {
        if (state == PanelState.VOICE) {
            val deps = voiceDeps ?: return@LaunchedEffect
            deps.modelLifecycleManager.onVoicePanelShown()
            // Cheap presence check (exists + size). Full SHA is daily / settings only.
            val installer = ModelInstaller(context)
            if (installer.areFilesPresent(ModelRegistry.defaultVoiceBundle)) {
                deps.promptState.dismissBanner()
            } else {
                deps.promptState.showBanner()
            }
        }
        if (previousState == PanelState.VOICE && state == PanelState.TYPING) {
            cachedVoiceDeps?.modelLifecycleManager?.onVoicePanelHidden()
            cachedVoiceDeps = null
        }
        previousState = state
    }

    when (state) {
        PanelState.TYPING -> typingContent()
        PanelState.VOICE -> {
            val deps = voiceDeps ?: return
            val showSetupBanner by deps.promptState.showSetupBanner.collectAsState()
            VoicePanel(
                onSwitchToTyping = controller::showTyping,
                audioRecorderEngine = deps.audioRecorderEngine,
                transcriber = deps.transcriber,
                permissionRequester = deps.permissionRequester,
                keyboardHeight = keyboardHeight,
                showSetupBanner = showSetupBanner,
                onSetupVoiceModels = {
                    context.startActivity(VoiceSetupIntents.onboardingIntent(context))
                },
            )
        }
    }
}
