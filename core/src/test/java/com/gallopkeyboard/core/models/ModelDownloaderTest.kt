package com.gallopkeyboard.core.models

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class ModelDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var root: File
    private lateinit var downloader: ModelDownloader

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        root = RuntimeEnvironment.getApplication().filesDir
        downloader = ModelDownloader(root, okhttp3.OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun happyPath_downloadsRenamesAndVerifies() = runBlocking {
        val payload = ByteArray(1024 * 1024) { it.toByte() }
        val hash = sha256(payload)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))

        val spec = testSpec(hash, payload.size.toLong())
        val events = downloader.download(spec, server.url("/file").toString()).toList()

        assertTrue(events.any { it.state == DownloadState.Done })
        val dest = File(root, spec.relPath)
        assertTrue(dest.isFile)
        assertEquals(hash, sha256(dest.readBytes()))
        assertFalse(File("${dest.absolutePath}.part").exists())
    }

    @Test
    fun shaMismatch_deletesPartAndThrows() {
        val payload = ByteArray(4096) { 1 }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))

        val spec = testSpec("0".repeat(64), payload.size.toLong())
        assertThrows(HashMismatchException::class.java) {
            runBlocking {
                downloader.download(spec, server.url("/bad").toString()).toList()
            }
        }
        assertFalse(File(root, "${spec.relPath}.part").exists())
    }

    @Test
    fun interruptedResume_sendsRangeHeader() = runBlocking {
        val payload = ByteArray(8192) { it.toByte() }
        val hash = sha256(payload)
        val half = payload.size / 2

        val part = File(root, "${testSpec(hash, payload.size.toLong()).relPath}.part")
        part.parentFile?.mkdirs()
        part.outputStream().use { it.write(payload, 0, half) }

        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Range", "bytes $half-${payload.size - 1}/${payload.size}")
                .setBody(okio.Buffer().write(payload, half, payload.size - half)),
        )

        val spec = testSpec(hash, payload.size.toLong())
        val events = downloader.download(spec, server.url("/resume").toString()).toList()
        assertTrue(events.any { it.state == DownloadState.Done })
        assertEquals(hash, sha256(File(root, spec.relPath).readBytes()))
    }

    @Test
    fun notFound_clearsPart() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val spec = testSpec("a".repeat(64), 100)
        val part = File(root, "${spec.relPath}.part")
        part.parentFile?.mkdirs()
        part.writeText("partial")

        val events = downloader.download(spec, server.url("/missing").toString()).toList()
        assertTrue(events.any { it.state == DownloadState.Failed })
        assertFalse(part.exists())
    }

    @Test
    fun progress_emitsMonotonicBytesDoneAndTerminalDone() = runBlocking {
        val payload = ByteArray(16_384) { it.toByte() }
        val hash = sha256(payload)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))

        val spec = testSpec(hash, payload.size.toLong())
        val events = downloader.download(spec, server.url("/progress").toString()).toList()
        val running = events.filter { it.state == DownloadState.Running }
        val done = events.filter { it.state == DownloadState.Done }

        assertTrue(running.isNotEmpty())
        assertTrue(running.zipWithNext().all { (a, b) -> b.bytesDone >= a.bytesDone })
        assertEquals(1, done.size)
        assertEquals(payload.size.toLong(), done.single().bytesDone)
    }

    @Test
    fun isMeteredNetwork_returnsWithoutException() {
        val context = RuntimeEnvironment.getApplication()
        ModelDownloader.isMeteredNetwork(context)
    }

    private fun testSpec(hash: String, size: Long) = ModelSpec(
        id = "test-file",
        url = "http://unused",
        sha256 = hash,
        sizeBytes = size,
        relPath = "models/test/file.bin",
    )

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(file: File): String = sha256(file.readBytes())
}
