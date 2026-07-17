package com.gallopkeyboard.app.onboarding

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * Launcher setup screen: enable Gallop Keyboard + download voice models.
 */
class OnboardingActivity : ComponentActivity() {

    private val installer by lazy { ModelInstaller(this) }
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val tick by resumeTick
            DictusTheme {
                SetupScreen(
                    installer = installer,
                    isMetered = ModelDownloader.isMeteredNetwork(this),
                    bundleSizeMb = ModelRegistry.defaultBundleSizeBytes() / (1024 * 1024),
                    isImeEnabled = remember(tick) { isGallopImeEnabled() },
                    modelsInstalled = remember(tick) {
                        installer.areFilesPresent(ModelRegistry.defaultVoiceBundle)
                    },
                    onOpenKeyboardSettings = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    onManageModels = {
                        startActivity(Intent(this, ModelsSettingsActivity::class.java))
                    },
                    onDownloadComplete = {
                        VoiceSetupPrefs.markSetupCompleted(this)
                        resumeTick.intValue++
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    private fun isGallopImeEnabled(): Boolean {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }
}

private enum class DownloadUiState {
    Idle,
    Downloading,
    Error,
    Success,
}

@Composable
private fun SetupScreen(
    installer: ModelInstaller,
    isMetered: Boolean,
    bundleSizeMb: Long,
    isImeEnabled: Boolean,
    modelsInstalled: Boolean,
    onOpenKeyboardSettings: () -> Unit,
    onManageModels: () -> Unit,
    onDownloadComplete: () -> Unit,
) {
    var uiState by remember {
        mutableStateOf(
            if (modelsInstalled) DownloadUiState.Success else DownloadUiState.Idle,
        )
    }
    // Keep Success in sync when returning from manage-models / reinstall.
    if (modelsInstalled && uiState == DownloadUiState.Idle) {
        uiState = DownloadUiState.Success
    }

    var progress by remember { mutableStateOf(0f) }
    var currentLabel by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun startDownload() {
        downloadJob?.cancel()
        uiState = DownloadUiState.Downloading
        errorMessage = null
        downloadJob = scope.launch {
            installer.install(ModelRegistry.defaultVoiceBundle)
                .catch { e ->
                    errorMessage = e.message
                    uiState = DownloadUiState.Error
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
                            uiState = DownloadUiState.Error
                        }
                        DownloadState.Done -> {
                            if (install.specIndex == install.specCount - 1) {
                                uiState = DownloadUiState.Success
                                onDownloadComplete()
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
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.voice_onboarding_title),
                style = MaterialTheme.typography.headlineLarge,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.voice_onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.voice_onboarding_step1_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.voice_onboarding_step1_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenKeyboardSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.voice_onboarding_step1_cta))
            }
            if (isImeEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ime_status_active),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.voice_onboarding_step2_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.voice_onboarding_step2_body, bundleSizeMb),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isMetered && uiState != DownloadUiState.Success) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.voice_onboarding_metered_warning, bundleSizeMb),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            when (uiState) {
                DownloadUiState.Idle -> {
                    Button(onClick = { startDownload() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.voice_onboarding_download))
                    }
                }
                DownloadUiState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.voice_onboarding_downloading, currentLabel),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                DownloadUiState.Error -> {
                    Text(
                        text = errorMessage
                            ?: stringResource(R.string.voice_onboarding_error_generic),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { startDownload() }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.voice_onboarding_retry))
                    }
                }
                DownloadUiState.Success -> {
                    Text(
                        text = stringResource(R.string.voice_onboarding_models_ready),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onManageModels,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.voice_onboarding_manage_models))
                    }
                }
            }
        }
    }
}
