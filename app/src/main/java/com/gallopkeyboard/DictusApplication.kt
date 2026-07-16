package com.gallopkeyboard

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import com.gallopkeyboard.app.BuildConfig
import com.gallopkeyboard.core.log.CrashHandler
import com.gallopkeyboard.core.logging.TimberSetup
import timber.log.Timber

/**
 * Main application entry point for Dictus.
 *
 * @HiltAndroidApp triggers Hilt code generation, creating a base class
 * that serves as the application-level dependency container. All Hilt
 * components attach to this application lifecycle.
 */
@HiltAndroidApp
class DictusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build(),
            )
        }
        TimberSetup.init(BuildConfig.DEBUG, filesDir)
        Timber.d("Dictus application started")
    }
}
