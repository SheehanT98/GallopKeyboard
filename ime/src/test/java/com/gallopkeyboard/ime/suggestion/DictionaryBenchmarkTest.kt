package com.gallopkeyboard.ime.suggestion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Microbenchmark for DictionaryEngine.getSuggestions() throughput.
 *
 * Verifies that 1000 sequential prefix lookups complete in under 100ms total,
 * which corresponds to ~100µs per keystroke — well within the 16ms frame budget.
 *
 * WHY this matters: getSuggestions() is called on the main thread from
 * onUpdateSelection() on every keystroke. It must be fast enough to not cause
 * IME frame drops or visible lag.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DictionaryBenchmarkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var context: Context

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("bench_prefs.preferences_pb") },
        )
    }

    @Test
    fun `1000 sequential getSuggestions calls complete in under 100ms`() = runTest(testDispatcher) {
        val engine = DictionaryEngine(
            context = context,
            dataStore = dataStore,
            coroutineScope = testScope,
            assetName = "dict_test.txt",
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        // Warm up: one call to ensure JIT compilation is done
        engine.getSuggestions("bon", 3)

        val startMs = System.currentTimeMillis()
        repeat(1000) {
            engine.getSuggestions("bon", 3)
        }
        val elapsedMs = System.currentTimeMillis() - startMs

        assertTrue(
            "1000 getSuggestions calls should complete in under 100ms, took: ${elapsedMs}ms",
            elapsedMs < 100,
        )
    }

    /**
     * STOP gate (Plan 026): first-letter bucket scan for candidatesNear must stay
     * under 5 ms on the JVM test host for the worst letter in dict_en.txt.
     */
    @Test
    fun `candidatesNear worst letter on dict_en under 5ms`() = runTest(testDispatcher) {
        val engine = DictionaryEngine(
            context = context,
            dataStore = dataStore,
            coroutineScope = testScope,
            assetName = "dict_en.txt",
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()

        // 's' is the largest first-letter bucket in dict_en.txt (~5k entries).
        val typed = "sxyz" // forces near-full bucket scan (few distance≤2 hits)
        repeat(5) { engine.candidatesNear(typed, max = 25) } // warm-up / JIT

        val samplesMs = mutableListOf<Double>()
        repeat(30) {
            val startNs = System.nanoTime()
            engine.candidatesNear(typed, max = 25)
            samplesMs.add((System.nanoTime() - startNs) / 1_000_000.0)
        }
        val sorted = samplesMs.sorted()
        val worstMs = sorted.last()
        val medianMs = sorted[sorted.size / 2]

        // STOP condition targets algorithmic cost (>5 ms), not single GC spikes.
        // Median of 30 samples is the gate; worst is logged for visibility.
        assertTrue(
            "candidatesNear worst-letter scan median must be <=5ms (STOP if >5); " +
                "median=${"%.2f".format(medianMs)}ms worst=${"%.2f".format(worstMs)}ms samples=$samplesMs",
            medianMs <= 5.0,
        )
    }
}
