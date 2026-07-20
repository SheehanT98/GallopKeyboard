package com.gallopkeyboard.ime.di

import android.content.Context
import com.gallopkeyboard.asr.parakeet.ParakeetEngine
import com.gallopkeyboard.asr.parakeet.StreamingAsrEngine
import com.gallopkeyboard.ime.asr.ImeTextCommitter
import com.gallopkeyboard.ime.asr.InputConnectionSupplier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AsrModule {

    @Provides
    @Singleton
    fun provideStreamingAsrEngine(
        @ApplicationContext context: Context,
    ): StreamingAsrEngine = ParakeetEngine(context)

    @Provides
    @Singleton
    fun provideImeTextCommitter(
        inputConnectionSupplier: InputConnectionSupplier,
    ): ImeTextCommitter = ImeTextCommitter(inputConnectionSupplier::connection)
}
