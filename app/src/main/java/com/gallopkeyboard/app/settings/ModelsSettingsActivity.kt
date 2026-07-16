package com.gallopkeyboard.app.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gallopkeyboard.app.onboarding.OnboardingActivity
import com.gallopkeyboard.core.theme.DictusTheme

/** Host activity for voice model storage settings (launcher after setup). */
class ModelsSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DictusTheme {
                ModelsSettingsScreen(
                    onDeletedAll = {
                        startActivity(
                            Intent(this, OnboardingActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                        finish()
                    },
                )
            }
        }
    }
}
