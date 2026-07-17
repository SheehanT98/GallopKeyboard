package com.gallopkeyboard.core.models

import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ModelInstallerTest {

    @Test
    fun verifyInstalledIfDue_skipsWhenRecentlyVerified() {
        val context = RuntimeEnvironment.getApplication()
        val installer = ModelInstaller(context)

        // First call stamps last_verify_ms (missing files → not corrupt).
        assertFalse(installer.verifyInstalledIfDue())
        // Second call within 24 h returns immediately without re-hashing.
        assertFalse(installer.verifyInstalledIfDue())
    }
}
