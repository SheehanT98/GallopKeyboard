# added by plan-010

# Keep JNI entry points
-keepclasseswithmembernames class * {
    native <methods>;
}

# sherpa-onnx
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class com.gallopkeyboard.asr.sherpa.** { *; }
-keep class com.gallopkeyboard.asr.parakeet.** { *; }

# whisper.cpp binding
-keep class com.gallopkeyboard.whisper.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.EntryPoint interface * { *; }
-keep @dagger.hilt.InstallIn class * { *; }

# IME entry path — must survive R8 or the keyboard window never draws
-keep class com.gallopkeyboard.ime.DictusImeService { *; }
-keep class com.gallopkeyboard.ime.LifecycleInputMethodService { *; }
-keep class com.gallopkeyboard.ime.di.DictusImeEntryPoint { *; }

# DictationService binder is reached via reflection from the IME module
# (ime must not compile against the app module).
-keep class com.gallopkeyboard.service.DictationService { *; }
-keep class com.gallopkeyboard.service.DictationService$LocalBinder { *; }
-keepclassmembers class com.gallopkeyboard.service.DictationService$LocalBinder {
    public com.gallopkeyboard.service.DictationService getService();
}

# Compose runtime already handled by AGP default rules
