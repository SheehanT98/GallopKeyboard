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

# Compose runtime already handled by AGP default rules
