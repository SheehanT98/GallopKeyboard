@file:Suppress("unused")

package com.gallopkeyboard.service

/**
 * Type alias for backward compatibility.
 *
 * DictationState has been moved to the core module (com.gallopkeyboard.core.service)
 * so both app and ime modules can reference it without circular dependencies.
 */
typealias DictationState = com.gallopkeyboard.core.service.DictationState
