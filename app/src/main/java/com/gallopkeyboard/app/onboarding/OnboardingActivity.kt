package com.gallopkeyboard.app.onboarding

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.app.R
import com.gallopkeyboard.app.settings.ModelsSettingsActivity
import com.gallopkeyboard.core.models.DownloadState
import com.gallopkeyboard.core.models.ModelDownloader
import com.gallopkeyboard.core.models.ModelInstaller
import com.gallopkeyboard.core.models.ModelRegistry
import com.gallopkeyboard.core.models.VoiceSetupPrefs
import com.gallopkeyboard.core.theme.DictusTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * First-launch voice model download screen.
 *
 * Launcher entry point; routes to [ModelsSettingsActivity] when models are ready.
 */
class OnboardingActivity : ComponentActivity() {

    private val installer by lazy { ModelInstaller(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DictusTheme {
                OnboardingScreen(
                    installer = installer,
                    isMetered = ModelDownloader.isMeteredNetwork(this),
                    bundleSizeMb = ModelRegistry.defaultBundleSizeBytes() / (1024 * 1024),
                    onOpenKeyboardSettings = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onSkip = { finish() },
                    onComplete = {
                        VoiceSetupPrefs.markSetupCompleted(this)
                        startActivity(Intent(this, ModelsSettingsActivity::class.java))
                        finish()
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (installer.isInstalled(ModelRegistry.defaultVoiceBundle)) {
            VoiceSetupPrefs.markSetupCompleted(this)
            startActivity(Intent(this, ModelsSettingsActivity::class.java))
            finish()
        }
    }
}

private enum class OnboardingUiState {
    Ready,
    Downloading,
    Error,
    Success,
}

@Composable
private fun OnboardingScreen(
    installer: ModelInstaller,
    isMetered: Boolean,
    bundleSizeMb: Long,
    onOpenKeyboardSettings: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
) {
    var uiState by remember { mutableStateOf(OnboardingUiState.Ready) }
    var progress by remember { mutableStateOf(0f) }
    var currentLabel by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun startDownload() {
        downloadJob?.cancel()
        uiState = OnboardingUiState.Downloading
        errorMessage = null
        downloadJob = scope.launch {
            installer.install(ModelRegistry.defaultVoiceBundle)
                .catch { e ->
                    errorMessage = e.message
                    uiState = OnboardingUiState.Error
                }
                .collect { install ->
                    val dl = install.download
                    currentLabel = install.currentSpec.id
                    progress = if (install.specCount > 0) {
                        (install.specIndex + when (dl.state) {
                            DownloadState.Done -> 1f
                            else -> dl.bytesDone.toFloat() / dl.bytesTotal.coerceAtLeast(1)
                        }) / install.specCount
                    } else {
                        0f
                    }
                    when (dl.state) {
                        DownloadState.Failed -> {
                            errorMessage = dl.errorMessage
                            uiState = OnboardingUiState.Error
                        }
                        DownloadState.Done -> {
                            if (install.specIndex == install.specCount - 1) {
                                uiState = OnboardingUiState.Success
                            }
                        }
                        else -> Unit
                    }
                }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (uiState) {
                OnboardingUiState.Ready, OnboardingUiState.Downloading, OnboardingUiState.Error -> {
                    Text(
                        text = stringResource(R.string.voice_onboarding_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.voice_onboarding_subtitle, bundleSizeMb),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (isMetered) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.voice_onboarding_metered_warning, bundleSizeMb),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(24.dp))

                    when (uiState) {
                        OnboardingUiState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.voice_onboarding_downloading, currentLabel),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(16.dp))
                            CircularProgressIndicator()
                        }
                        OnboardingUiState.Error -> {
                            Text(
                                text = errorMessage ?: stringResource(R.string.voice_onboarding_error_generic),
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { startDownload() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.voice_onboarding_retry))
                            }
                        }
                        else -> {
                            Button(onClick = { startDownload() }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.voice_onboarding_download))
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = onSkip,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = uiState != OnboardingUiState.Downloading,
                            ) {
                                Text(stringResource(R.string.voice_onboarding_skip))
                            }
                        }
                    }
                }
                OnboardingUiState.Success -> {
                    Text(
                        text = stringResource(R.string.voice_onboarding_success),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            onComplete()
                            onOpenKeyboardSettings()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.voice_onboarding_open_keyboard_settings))
                    }
                }
            }
        }
    }
}
