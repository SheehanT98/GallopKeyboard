package com.gallopkeyboard.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.app.R
import com.gallopkeyboard.core.models.DownloadState
import com.gallopkeyboard.core.models.ModelFileStatus
import com.gallopkeyboard.core.models.ModelInstaller
import com.gallopkeyboard.core.models.ModelRegistry
import com.gallopkeyboard.core.models.ModelSpec
import com.gallopkeyboard.core.models.VoiceSetupPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsSettingsScreen(
    onDeletedAll: () -> Unit,
) {
    val context = LocalContext.current
    val installer = remember { ModelInstaller(context) }
    val scope = rememberCoroutineScope()

    var statuses by remember {
        mutableStateOf(ModelRegistry.allSpecs.associateWith { installer.fileStatus(it) })
    }
    var tier by remember { mutableStateOf(VoiceSetupPrefs.whisperTier(context)) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var fixingSpec by remember { mutableStateOf<ModelSpec?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var downloadLabel by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }

    fun refresh() {
        statuses = ModelRegistry.allSpecs.associateWith { installer.fileStatus(it) }
    }

    fun installBundle(bundle: List<ModelSpec>, onDone: () -> Unit = { refresh() }) {
        downloadJob?.cancel()
        isDownloading = true
        downloadJob = scope.launch {
            installer.install(bundle)
                .catch { isDownloading = false }
                .collect { install ->
                    downloadLabel = install.currentSpec.id
                    if (install.download.state == DownloadState.Failed ||
                        (install.download.state == DownloadState.Done &&
                            install.specIndex == install.specCount - 1)
                    ) {
                        isDownloading = false
                        onDone()
                    }
                }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.voice_settings_delete_all_title)) },
            text = { Text(stringResource(R.string.voice_settings_delete_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        installer.delete(ModelRegistry.allSpecs)
                        VoiceSetupPrefs.clearSetupCompleted(context)
                        onDeletedAll()
                    },
                ) {
                    Text(stringResource(R.string.voice_settings_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.voice_settings_cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.voice_settings_title)) })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_settings_installed_header),
                style = MaterialTheme.typography.titleMedium,
            )

            ModelRegistry.allSpecs.forEach { spec ->
                val status = statuses[spec] ?: ModelFileStatus.Missing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(spec.id, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = stringResource(
                                R.string.voice_settings_spec_meta,
                                spec.sizeBytes / (1024 * 1024),
                                statusLabel(status),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (status == ModelFileStatus.Corrupt || status == ModelFileStatus.Missing) {
                        OutlinedButton(
                            onClick = {
                                fixingSpec = spec
                                installBundle(listOf(spec)) { fixingSpec = null; refresh() }
                            },
                            enabled = !isDownloading,
                        ) {
                            Text(stringResource(R.string.voice_settings_fix))
                        }
                    }
                }
            }

            if (isDownloading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = stringResource(R.string.voice_settings_downloading, downloadLabel),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                text = stringResource(R.string.voice_settings_tier_header),
                style = MaterialTheme.typography.titleMedium,
            )

            TierRow(
                label = stringResource(R.string.voice_settings_tier_base),
                selected = tier == VoiceSetupPrefs.TIER_BASE,
                enabled = !isDownloading,
                onSelect = {
                    if (tier != VoiceSetupPrefs.TIER_BASE) {
                        tier = VoiceSetupPrefs.TIER_BASE
                        VoiceSetupPrefs.setWhisperTier(context, VoiceSetupPrefs.TIER_BASE)
                        installer.delete(listOf(ModelRegistry.whisperSmall))
                        installBundle(listOf(ModelRegistry.whisperBase)) { refresh() }
                    }
                },
            )
            TierRow(
                label = stringResource(R.string.voice_settings_tier_small),
                selected = tier == VoiceSetupPrefs.TIER_SMALL,
                enabled = !isDownloading,
                onSelect = {
                    if (tier != VoiceSetupPrefs.TIER_SMALL) {
                        tier = VoiceSetupPrefs.TIER_SMALL
                        VoiceSetupPrefs.setWhisperTier(context, VoiceSetupPrefs.TIER_SMALL)
                        installer.delete(listOf(ModelRegistry.whisperBase))
                        installBundle(listOf(ModelRegistry.whisperSmall)) { refresh() }
                    }
                },
            )

            Text(
                text = stringResource(
                    R.string.voice_settings_storage_usage,
                    installer.diskUsageBytes() / (1024 * 1024),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                enabled = !isDownloading,
            ) {
                Text(stringResource(R.string.voice_settings_delete_all))
            }
        }
    }
}

@Composable
private fun TierRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun statusLabel(status: ModelFileStatus): String = when (status) {
    ModelFileStatus.Installed -> stringResource(R.string.voice_settings_status_installed)
    ModelFileStatus.Missing -> stringResource(R.string.voice_settings_status_missing)
    ModelFileStatus.Corrupt -> stringResource(R.string.voice_settings_status_corrupt)
}
