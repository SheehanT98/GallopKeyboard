package com.gallopkeyboard.core.log

import android.content.Context
import android.util.Log
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight in-process crash handler that writes stack traces to
 * `filesDir/crashes/` for local inspection (no network upload).
 */
object CrashHandler {

    private const val MAX_CRASH_FILES = 20
    private val installed = AtomicBoolean(false)

    private val timestampFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss.SSS'Z'")
            .withZone(ZoneOffset.UTC)

    /**
     * Installs the handler once per process. Safe to call from both
     * [android.app.Application] and IME service [onCreate].
     */
    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val prior = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrashFile(appContext, thread.name, throwable)
            } catch (e: Exception) {
                Log.e("CrashHandler", "failed to write crash file", e)
            }
            prior?.uncaughtException(thread, throwable)
        }
    }

    /** Directory where crash `.txt` files are stored. */
    fun crashDir(context: Context): File =
        File(context.applicationContext.filesDir, "crashes")

    private fun writeCrashFile(context: Context, threadName: String, throwable: Throwable) {
        val dir = crashDir(context)
        dir.mkdirs()

        val safeThread = threadName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val isoTs = timestampFormatter.format(Instant.now())
        val file = File(dir, "$isoTs-$safeThread.txt")

        file.writeText(
            buildString {
                appendLine("timestamp: $isoTs")
                appendLine("thread: $threadName")
                appendLine()
                appendLine(throwable.stackTraceToString())
            },
        )

        trimOldFiles(dir)
    }

    private fun trimOldFiles(dir: File) {
        val files = dir.listFiles { f -> f.isFile && f.extension == "txt" }
            ?.sortedBy { it.lastModified() }
            ?: return
        if (files.size <= MAX_CRASH_FILES) return
        files.take(files.size - MAX_CRASH_FILES).forEach { it.delete() }
    }
}
