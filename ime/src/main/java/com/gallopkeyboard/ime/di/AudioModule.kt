package com.gallopkeyboard.ime.di

import com.gallopkeyboard.ime.audio.StubTranscriber
import com.gallopkeyboard.ime.audio.Transcriber
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindTranscriber(impl: StubTranscriber): Transcriber
}
