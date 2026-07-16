package com.gallopkeyboard.core.flags

/**
 * Feature flags shared across modules. Plan 007 flips [polishEnabled] to true.
 */
object Flags {
    /** Kill switch for Whisper polish on stop (Plan 007 default: on). */
    var polishEnabled: Boolean = true
}
