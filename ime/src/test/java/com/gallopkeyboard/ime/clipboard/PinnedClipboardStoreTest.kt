package com.gallopkeyboard.ime.clipboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.gallopkeyboard.core.preferences.PreferenceKeys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PinnedClipboardStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>

    @Before
    fun setUp() {
        testScope = TestScope(testDispatcher)
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tempFolder.newFile("pinned_clipboard_test.preferences_pb") },
        )
    }

    @Test
    fun `pin persists entry to DataStore`() = runTest(testDispatcher) {
        val store = PinnedClipboardStore(dataStore, testScope)
        advanceUntilIdle()

        store.pin("user@example.com")
        advanceUntilIdle()

        assertEquals(1, store.entriesFlow.value.size)
        assertEquals("user@example.com", store.entriesFlow.value.first().text)
        assertTrue(store.isPinned("user@example.com"))

        val storedSet = dataStore.data.first()[PreferenceKeys.PINNED_CLIPBOARD_ENTRIES]
        val decoded = ClipboardEntryCodec.decode(storedSet)
        assertEquals(1, decoded.size)
        assertEquals("user@example.com", decoded.first().text)
    }

    @Test
    fun `unpin removes entry from DataStore`() = runTest(testDispatcher) {
        val store = PinnedClipboardStore(dataStore, testScope)
        advanceUntilIdle()

        store.pin("hello")
        advanceUntilIdle()
        val id = store.entriesFlow.value.first().id

        store.unpin(id)
        advanceUntilIdle()

        assertTrue(store.entriesFlow.value.isEmpty())
        assertFalse(store.isPinned("hello"))

        val storedSet = dataStore.data.first()[PreferenceKeys.PINNED_CLIPBOARD_ENTRIES]
        assertTrue(storedSet.isNullOrEmpty())
    }

    @Test
    fun `togglePin round-trips pin and unpin`() = runTest(testDispatcher) {
        val store = PinnedClipboardStore(dataStore, testScope)
        advanceUntilIdle()

        store.togglePin("phrase")
        advanceUntilIdle()
        assertTrue(store.isPinned("phrase"))

        store.togglePin("phrase")
        advanceUntilIdle()
        assertFalse(store.isPinned("phrase"))
    }

    @Test
    fun `new store instance loads pinned entries from DataStore`() = runTest(testDispatcher) {
        val store1 = PinnedClipboardStore(dataStore, testScope)
        advanceUntilIdle()
        store1.pin("persist me")
        advanceUntilIdle()

        val store2 = PinnedClipboardStore(dataStore, testScope)
        advanceUntilIdle()

        assertEquals(1, store2.entriesFlow.value.size)
        assertEquals("persist me", store2.entriesFlow.value.first().text)
    }
}
