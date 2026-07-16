package com.gallopkeyboard.core.models

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

enum class DownloadState {
    Running,
    Verifying,
    Done,
    Failed,
}

data class DownloadProgress(
    val bytesDone: Long,
    val bytesTotal: Long,
    val speedBytesPerSec: Long,
    val state: DownloadState,
    val specId: String = "",
    val errorMessage: String? = null,
)

class HashMismatchException(specId: String) :
    Exception("SHA-256 mismatch for $specId")

class ModelDownloader(
    private val filesRoot: File,
    private val client: OkHttpClient = defaultClient(),
) {
    companion object {
        private const val TAG = "ModelDownloader"
        private const val BUFFER_SIZE = 8192

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .build()

        @SuppressLint("MissingPermission")
        fun isMeteredNetwork(context: Context): Boolean {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return true
            val caps = cm.getNetworkCapabilities(network) ?: return true
            return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
    }

    fun download(spec: ModelSpec, urlOverride: String? = null): Flow<DownloadProgress> = flow {
        val dest = File(filesRoot, spec.relPath)
        val part = File("${dest.absolutePath}.part")
        val url = urlOverride ?: spec.url
        val totalBytes = spec.sizeBytes

        dest.parentFile?.mkdirs()

        if (dest.isFile && sha256Of(dest) == spec.sha256.lowercase()) {
            emit(
                DownloadProgress(
                    bytesDone = dest.length(),
                    bytesTotal = totalBytes,
                    speedBytesPerSec = 0,
                    state = DownloadState.Done,
                    specId = spec.id,
                ),
            )
            return@flow
        }

        var resumeFrom = if (part.isFile) part.length() else 0L
        val requestBuilder = Request.Builder().url(url)
        if (resumeFrom > 0) {
            requestBuilder.header("Range", "bytes=$resumeFrom-")
            Timber.d("$TAG resume ${spec.id} from byte $resumeFrom")
        }

        emit(
            DownloadProgress(
                bytesDone = resumeFrom,
                bytesTotal = totalBytes,
                speedBytesPerSec = 0,
                state = DownloadState.Running,
                specId = spec.id,
            ),
        )

        val call = client.newCall(requestBuilder.build())
        val job = currentCoroutineContext()[kotlinx.coroutines.Job]
        job?.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }

        try {
            val response = call.execute()
            when {
                response.code == 416 && resumeFrom > 0 -> {
                    part.delete()
                    resumeFrom = 0L
                    emit(DownloadProgress(0, totalBytes, 0, DownloadState.Failed, spec.id, "Range not satisfiable"))
                    return@flow
                }
                !response.isSuccessful -> {
                    if (response.code == 404) part.delete()
                    emit(
                        DownloadProgress(
                            0,
                            totalBytes,
                            0,
                            DownloadState.Failed,
                            spec.id,
                            "HTTP ${response.code}",
                        ),
                    )
                    return@flow
                }
            }

            val body = response.body ?: run {
                part.delete()
                emit(DownloadProgress(0, totalBytes, 0, DownloadState.Failed, spec.id, "Empty body"))
                return@flow
            }

            val contentLength = body.contentLength()
            val expectedTotal = when {
                response.code == 206 -> resumeFrom + contentLength
                contentLength > 0 -> contentLength
                else -> totalBytes
            }

            if (response.code == 200 && resumeFrom > 0) {
                part.delete()
                resumeFrom = 0L
            }

            var bytesDone = resumeFrom
            val startTime = System.nanoTime()
            var lastEmit = startTime

            FileOutputStream(part, resumeFrom > 0).use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (coroutineContext.isActive) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesDone += read
                        val now = System.nanoTime()
                        if (now - lastEmit >= 100_000_000) {
                            val elapsedSec = (now - startTime) / 1_000_000_000.0
                            val speed = if (elapsedSec > 0) {
                                ((bytesDone - resumeFrom) / elapsedSec).toLong()
                            } else {
                                0L
                            }
                            emit(
                                DownloadProgress(
                                    bytesDone,
                                    expectedTotal,
                                    speed,
                                    DownloadState.Running,
                                    spec.id,
                                ),
                            )
                            lastEmit = now
                        }
                    }
                }
            }

            if (!coroutineContext.isActive) return@flow

            emit(
                DownloadProgress(
                    bytesDone,
                    expectedTotal,
                    0,
                    DownloadState.Verifying,
                    spec.id,
                ),
            )

            val digest = sha256Of(part)
            if (digest != spec.sha256.lowercase()) {
                part.delete()
                throw HashMismatchException(spec.id)
            }

            if (dest.exists()) dest.delete()
            if (!part.renameTo(dest)) {
                part.delete()
                emit(
                    DownloadProgress(
                        bytesDone,
                        expectedTotal,
                        0,
                        DownloadState.Failed,
                        spec.id,
                        "Failed to finalize download",
                    ),
                )
                return@flow
            }

            emit(
                DownloadProgress(
                    dest.length(),
                    expectedTotal,
                    0,
                    DownloadState.Done,
                    spec.id,
                ),
            )
        } catch (e: HashMismatchException) {
            emit(
                DownloadProgress(
                    0,
                    totalBytes,
                    0,
                    DownloadState.Failed,
                    spec.id,
                    e.message,
                ),
            )
            throw e
        } catch (e: Exception) {
            if (!call.isCanceled()) {
                Timber.e(e, "$TAG download failed for ${spec.id}")
                emit(
                    DownloadProgress(
                        part.length(),
                        totalBytes,
                        0,
                        DownloadState.Failed,
                        spec.id,
                        e.message ?: "Download failed",
                    ),
                )
            }
        }
    }.flowOn(Dispatchers.IO)

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
