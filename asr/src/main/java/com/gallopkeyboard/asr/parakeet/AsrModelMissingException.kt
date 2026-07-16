package com.gallopkeyboard.asr.parakeet

class AsrModelMissingException(
    val files: List<String>,
) : Exception("ASR model files missing: ${files.joinToString()}")
