package com.gallopkeyboard.ime.di

import android.content.Context
import com.gallopkeyboard.whisper.AsrPolishEngine
import com.gallopkeyboard.whisper.PolishEngine
import com.gallopkeyboard.whisper.WhisperConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WhisperPolishModule {

    @Provides
    @Singleton
    fun provideAsrPolishEngine(
        @ApplicationContext context: Context,
    ): AsrPolishEngine {
        val engine = PolishEngine()
        val modelDir = File(context.filesDir, "models/${WhisperConfig.MODEL_SUBDIR}")
        engine.init(WhisperConfig.fromModelDir(modelDir))
        return engine
    }
}
