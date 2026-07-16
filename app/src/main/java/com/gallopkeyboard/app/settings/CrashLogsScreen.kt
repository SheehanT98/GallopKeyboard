package com.gallopkeyboard.app.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gallopkeyboard.app.R
import com.gallopkeyboard.core.log.CrashHandler
import com.gallopkeyboard.core.theme.LocalDictusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lists crash files from [CrashHandler.crashDir] and shows detail with
 * copy, share, and delete actions.
 */
@Composable
fun CrashLogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val crashDir = remember { CrashHandler.crashDir(context) }
    val files = remember { mutableStateListOf<File>() }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var detailText by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    suspend fun reloadFiles() {
        val list = withContext(Dispatchers.IO) {
            crashDir.listFiles { f -> f.isFile && f.extension == "txt" }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
        }
        files.clear()
        files.addAll(list)
        if (selectedFile != null && selectedFile !in list) {
            selectedFile = null
            detailText = ""
        }
    }

    LaunchedEffect(Unit) {
        reloadFiles()
    }

    if (selectedFile != null) {
        CrashLogDetailScreen(
            file = selectedFile!!,
            onBack = {
                selectedFile = null
                detailText = ""
            },
            onCopy = { clipboardManager.setText(AnnotatedString(detailText)) },
            onShare = {
                val file = selectedFile!!
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                    putExtra(Intent.EXTRA_TEXT, detailText)
                }
                context.startActivity(Intent.createChooser(share, context.getString(R.string.crash_logs_share_chooser)))
            },
            onDelete = {
                val file = selectedFile ?: return@CrashLogDetailScreen
                scope.launch {
                    withContext(Dispatchers.IO) { file.delete() }
                    selectedFile = null
                    detailText = ""
                    reloadFiles()
                }
            },
            onTextLoaded = { detailText = it },
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 56.dp),
        ) {
            if (files.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.crash_logs_empty),
                            color = LocalDictusColors.current.textSecondary,
                            fontSize = 16.sp,
                        )
                    }
                }
            } else {
                items(files, key = { it.absolutePath }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedFile = file
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = file.name,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(
                        color = LocalDictusColors.current.borderSubtle,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.crash_logs_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.crash_logs_title),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CrashLogDetailScreen(
    file: File,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onTextLoaded: (String) -> Unit,
) {
    var text by remember(file) { mutableStateOf("") }

    LaunchedEffect(file) {
        text = withContext(Dispatchers.IO) { file.readText() }
        onTextLoaded(text)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.crash_logs_back_cd),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = file.name,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Button(onClick = onCopy, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.crash_logs_copy))
            }
            Spacer(modifier = Modifier.padding(4.dp))
            Button(onClick = onShare, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.crash_logs_share))
            }
            Spacer(modifier = Modifier.padding(4.dp))
            Button(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.crash_logs_delete))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            item {
                Text(
                    text = text,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
