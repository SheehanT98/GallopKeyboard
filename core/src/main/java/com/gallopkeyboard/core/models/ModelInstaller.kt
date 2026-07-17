package com.gallopkeyboard.core.models

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.security.MessageDigest

enum class ModelFileStatus {
    Installed,
    Missing,
    Corrupt,
}

data class InstallProgress(
    val currentSpec: ModelSpec,
    val specIndex: Int,
    val specCount: Int,
    val download: DownloadProgress,
)

class ModelInstaller(
    context: Context,
    private val downloader: ModelDownloader = ModelDownloader(context.filesDir),
) {
    private val filesRoot: File = context.filesDir
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "gallop_voice_models"
        private const val KEY_LAST_VERIFY_MS = "last_verify_ms"
        private const val VERIFY_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val BUFFER_SIZE = 8192
    }

    fun install(bundle: List<ModelSpec>): Flow<InstallProgress> = flow {
        bundle.forEachIndexed { index, spec ->
            var failed = false
            downloader.download(spec).collect { progress ->
                emit(
                    InstallProgress(
                        currentSpec = spec,
                        specIndex = index,
                        specCount = bundle.size,
                        download = progress,
                    ),
                )
                if (progress.state == DownloadState.Failed) {
                    failed = true
                    return@collect
                }
            }
            if (failed) return@flow
        }
    }

    fun isInstalled(bundle: List<ModelSpec>): Boolean =
        bundle.all { spec -> fileStatus(spec) == ModelFileStatus.Installed }

    /**
     * Fast readiness check for the IME hot path (voice panel open).
     *
     * Only checks that each file exists and matches the expected byte length —
     * no SHA-256. Full integrity stays on [isInstalled] / [verifyInstalledIfDue]
     * (settings + daily background verify scheduled from IME startup).
     */
    fun areFilesPresent(bundle: List<ModelSpec>): Boolean =
        bundle.all { spec ->
            val file = File(filesRoot, spec.relPath)
            file.isFile && file.length() == spec.sizeBytes
        }

    fun fileStatus(spec: ModelSpec): ModelFileStatus {
        val file = File(filesRoot, spec.relPath)
        if (!file.isFile) return ModelFileStatus.Missing
        return if (sha256Of(file) == spec.sha256.lowercase()) {
            ModelFileStatus.Installed
        } else {
            ModelFileStatus.Corrupt
        }
    }

    fun delete(bundle: List<ModelSpec>) {
        bundle.forEach { spec ->
            val file = File(filesRoot, spec.relPath)
            if (file.exists()) file.delete()
            File("${file.absolutePath}.part").delete()
        }
    }

    fun deleteSpec(spec: ModelSpec) = delete(listOf(spec))

    fun diskUsageBytes(): Long =
        ModelRegistry.allSpecs.sumOf { spec ->
            val file = File(filesRoot, spec.relPath)
            if (file.isFile) file.length() else 0L
        }

    /**
     * Re-verify installed files once per day. Intended to run off the IME
     * critical path (background coroutine), not synchronously on service create.
     * Returns true when a corrupt file was detected.
     */
    fun verifyInstalledIfDue(): Boolean {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_VERIFY_MS, 0L)
        if (now - last < VERIFY_INTERVAL_MS) return false
        prefs.edit().putLong(KEY_LAST_VERIFY_MS, now).apply()

        return ModelRegistry.defaultVoiceBundle.any { spec ->
            fileStatus(spec) == ModelFileStatus.Corrupt
        }
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
