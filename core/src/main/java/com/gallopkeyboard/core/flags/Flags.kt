package com.gallopkeyboard.core.flags

/**
 * Feature flags shared across modules. Plan 007 flips [polishEnabled] to true.
 */
object Flags {
    /** When false, streaming transcriber commits final text after a short defer (Plan 006 default). */
    var polishEnabled: Boolean = false
}
