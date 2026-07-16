package com.gallopkeyboard.core.models

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Deep-link targets for voice model setup (app module activities). */
object VoiceSetupIntents {
    const val PACKAGE = "com.gallopkeyboard.ime"
    const val ONBOARDING_CLASS = "com.gallopkeyboard.app.onboarding.OnboardingActivity"
    const val SETTINGS_CLASS = "com.gallopkeyboard.app.settings.ModelsSettingsActivity"

    fun onboardingIntent(context: Context): Intent =
        Intent().setComponent(ComponentName(PACKAGE, ONBOARDING_CLASS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun settingsIntent(context: Context): Intent =
        Intent().setComponent(ComponentName(PACKAGE, SETTINGS_CLASS))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
