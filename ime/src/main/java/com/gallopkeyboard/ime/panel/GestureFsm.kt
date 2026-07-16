package com.gallopkeyboard.ime.panel

import kotlin.math.hypot

/** ADR-0003 gesture constants. */
const val HOLD_THRESHOLD_MS = 400L
const val CANCEL_SLOP_DP = 48

enum class StartReason { TAP, HOLD }

enum class StopReason { TAP, HOLD }

enum class GestureState {
    IDLE,
    RECORDING,
    TAP_TOGGLE_ON,
    HOLDING,
}

sealed class GestureEvent {
    data class Down(val atMs: Long, val x: Float, val y: Float) : GestureEvent()
    data class Up(val atMs: Long, val x: Float, val y: Float) : GestureEvent()
    data class Move(val x: Float, val y: Float) : GestureEvent()
    data class Cancel(val atMs: Long) : GestureEvent()
    data class HoldThresholdElapsed(val atMs: Long) : GestureEvent()
}

/**
 * Pure Kotlin gesture FSM for the smart voice button (ADR-0003).
 *
 * [cancelSlopPx] is the drag radius from the initial down position that triggers cancel.
 */
class GestureFsm(
    private val cancelSlopPx: Float,
    private val onSessionStart: (StartReason) -> Unit,
    private val onSessionStop: (StopReason) -> Unit,
    private val onSessionCancel: () -> Unit,
) {
    var state: GestureState = GestureState.IDLE
        private set

    private var downAtMs: Long = 0L
    private var downX: Float = 0f
    private var downY: Float = 0f
    private var sessionAnnounced: Boolean = false

    fun reset() {
        state = GestureState.IDLE
        sessionAnnounced = false
    }

    fun onEvent(event: GestureEvent) {
        when (event) {
            is GestureEvent.Down -> handleDown(event)
            is GestureEvent.Up -> handleUp(event)
            is GestureEvent.Move -> handleMove(event)
            is GestureEvent.Cancel -> handleCancel()
            is GestureEvent.HoldThresholdElapsed -> handleHoldThreshold(event)
        }
    }

    private fun handleDown(event: GestureEvent.Down) {
        when (state) {
            GestureState.IDLE -> {
                state = GestureState.RECORDING
                downAtMs = event.atMs
                downX = event.x
                downY = event.y
                sessionAnnounced = false
            }
            GestureState.TAP_TOGGLE_ON -> {
                state = GestureState.IDLE
                onSessionStop(StopReason.TAP)
                sessionAnnounced = false
            }
            else -> Unit
        }
    }

    private fun handleUp(event: GestureEvent.Up) {
        when (state) {
            GestureState.RECORDING -> {
                val elapsed = event.atMs - downAtMs
                if (elapsed < HOLD_THRESHOLD_MS) {
                    announceStart(StartReason.TAP)
                    state = GestureState.TAP_TOGGLE_ON
                } else {
                    announceStart(StartReason.HOLD)
                    state = GestureState.IDLE
                    onSessionStop(StopReason.HOLD)
                    sessionAnnounced = false
                }
            }
            GestureState.HOLDING -> {
                state = GestureState.IDLE
                onSessionStop(StopReason.HOLD)
                sessionAnnounced = false
            }
            else -> Unit
        }
    }

    private fun handleHoldThreshold(event: GestureEvent.HoldThresholdElapsed) {
        if (state == GestureState.RECORDING && event.atMs - downAtMs >= HOLD_THRESHOLD_MS) {
            announceStart(StartReason.HOLD)
            state = GestureState.HOLDING
        }
    }

    private fun handleMove(event: GestureEvent.Move) {
        if (state == GestureState.RECORDING || state == GestureState.HOLDING) {
            if (distanceFromDown(event.x, event.y) > cancelSlopPx) {
                cancelSession()
            }
        }
    }

    private fun handleCancel() {
        if (state == GestureState.RECORDING || state == GestureState.HOLDING) {
            cancelSession()
        }
    }

    private fun cancelSession() {
        announceStart(StartReason.TAP)
        state = GestureState.IDLE
        onSessionCancel()
        sessionAnnounced = false
    }

    private fun announceStart(reason: StartReason) {
        if (!sessionAnnounced) {
            sessionAnnounced = true
            onSessionStart(reason)
        }
    }

    private fun distanceFromDown(x: Float, y: Float): Float =
        hypot(x - downX, y - downY)
}
