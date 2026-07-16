package com.gallopkeyboard.ime.panel

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests [Manifest.permission.RECORD_AUDIO] from an IME via [PermissionProxyActivity].
 */
@Singleton
class PermissionRequester @Inject constructor() {

    companion object {
        const val ACTION_PERMISSION_RESULT =
            "com.gallopkeyboard.ime.panel.PERMISSION_RESULT"
        const val EXTRA_GRANTED = "granted"
        const val REQUEST_CODE = 9001
        private const val TIMEOUT_MS = 30_000L
    }

    suspend fun request(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }

        val deferred = CompletableDeferred<Boolean>()
        val appContext = context.applicationContext

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_PERMISSION_RESULT) return
                val granted = intent.getBooleanExtra(EXTRA_GRANTED, false)
                deferred.complete(granted)
            }
        }

        val filter = IntentFilter(ACTION_PERMISSION_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(receiver, filter)
        }

        val launchIntent = Intent(appContext, PermissionProxyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(launchIntent)

        val granted = withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: false
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered if the activity finished twice.
        }
        return granted
    }
}

/**
 * Translucent proxy that shows the system mic permission dialog for the IME.
 */
class PermissionProxyActivity : Activity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            sendResult(true)
            finish()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PermissionRequester.REQUEST_CODE,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PermissionRequester.REQUEST_CODE) return
        val granted = grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        sendResult(granted)
        finish()
    }

    private fun sendResult(granted: Boolean) {
        val intent = Intent(PermissionRequester.ACTION_PERMISSION_RESULT).apply {
            putExtra(PermissionRequester.EXTRA_GRANTED, granted)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
