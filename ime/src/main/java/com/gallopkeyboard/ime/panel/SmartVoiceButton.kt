package com.gallopkeyboard.ime.panel

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gallopkeyboard.ime.R
import com.gallopkeyboard.ime.audio.AudioRecorderEngine
import com.gallopkeyboard.ime.audio.AudioSession
import com.gallopkeyboard.ime.audio.RING_BUFFER_CAPACITY_BYTES
import com.gallopkeyboard.ime.audio.RingByteBuffer
import com.gallopkeyboard.ime.audio.Transcriber
import com.gallopkeyboard.ime.di.DictusImeEntryPoint
import com.gallopkeyboard.ime.theme.GallopColors
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SmartVoiceButtonStyle {
    /** Full-width bar in a tall dedicated voice panel. */
    Panel,
    /** Full-width speak button in the thin voice panel. */
    PanelCompact,
}

@Composable
fun SmartVoiceButton(
    audioRecorderEngine: AudioRecorderEngine,
    transcriber: Transcriber,
    permissionRequester: PermissionRequester,
    modifier: Modifier = Modifier,
    style: SmartVoiceButtonStyle = SmartVoiceButtonStyle.Panel,
    /** Outlives this composable so stop/polish survives panel leave. */
    sessionScope: CoroutineScope = voiceStopScope,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val cancelSlopPx = with(density) { CANCEL_SLOP_DP.dp.toPx() }

    val recorderDispatcher = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DictusImeEntryPoint::class.java,
        ).recorderCoroutineDispatcher()
    }

    var visualRecording by remember { mutableStateOf(false) }
    var activeSession by remember { mutableStateOf<AudioSession?>(null) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var stoppingJob by remember { mutableStateOf<Job?>(null) }
    var holdTimerJob by remember { mutableStateOf<Job?>(null) }
    var pointerPressed by remember { mutableStateOf(false) }
    var permissionRequestInFlight by remember { mutableStateOf(false) }

    val fsm = remember(cancelSlopPx, recorderDispatcher) {
        GestureFsm(
            cancelSlopPx = cancelSlopPx,
            onSessionStart = {
                visualRecording = true
                val session = AudioSession(
                    startedAtElapsedMs = SystemClock.elapsedRealtime(),
                    buffer = RingByteBuffer(RING_BUFFER_CAPACITY_BYTES),
                )
                activeSession = session
                transcriber.onSessionStart(session)
                recordingJob?.cancel()
                // Collect PCM on RecorderCoroutineDispatcher — never Compose Main (Plan 027).
                // Job still owned by sessionScope so it is not tied to composition dispose
                // the same way as stop/polish (Plan 024); cancel explicitly on stop/cancel/dispose.
                recordingJob = sessionScope.launch(recorderDispatcher.dispatcher) {
                    // Reusable LE PCM scratch — one alloc per session, not per ~100 ms frame.
                    var scratch = ByteArray(0)
                    try {
                        audioRecorderEngine.start().collect { frame ->
                            val sessionRef = activeSession ?: return@collect
                            val need = frame.size * 2
                            if (scratch.size < need) {
                                scratch = ByteArray(need)
                            }
                            sessionRef.buffer.writeShorts(frame, scratch)
                            val dropped = sessionRef.buffer.droppedBytes()
                            if (dropped > 0L) {
                                Log.w(
                                    "AudioRecorder",
                                    "ring buffer dropped $dropped oldest bytes (5 min ceiling)",
                                )
                            }
                            transcriber.onAudioFrame(sessionRef, frame)
                        }
                    } catch (e: Exception) {
                        Log.e("AudioRecorder", "recording failed", e)
                        withContext(Dispatchers.Main) {
                            visualRecording = false
                            activeSession = null
                        }
                    }
                }
            },
            onSessionStop = { _ ->
                visualRecording = false
                recordingJob?.cancel()
                recordingJob = null
                val session = activeSession
                activeSession = null
                if (session != null) {
                    session.stoppedAtElapsedMs = SystemClock.elapsedRealtime()
                    // Launch on sessionScope so polish survives SmartVoiceButton dispose.
                    stoppingJob = sessionScope.launch { transcriber.onSessionStop(session) }
                }
            },
            onSessionCancel = {
                visualRecording = false
                recordingJob?.cancel()
                recordingJob = null
                stoppingJob?.cancel()
                stoppingJob = null
                activeSession = cancelActiveSession(transcriber, activeSession)
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            recordingJob?.cancel()
            recordingJob = null
            holdTimerJob?.cancel()
            visualRecording = false
            // Mid-recording → cancel. Mid-stop/polish → let stoppingJob finish.
            if (shouldCancelRecordingOnDispose(
                    recordingSessionActive = activeSession != null,
                    stoppingJobActive = stoppingJob?.isActive == true,
                )
            ) {
                activeSession = cancelActiveSession(transcriber, activeSession)
            }
            // Do not cancel stoppingJob — polish must outlive panel leave.
            fsm.reset()
        }
    }

    val isRecordingVisual = visualRecording ||
        fsm.state == GestureState.RECORDING ||
        fsm.state == GestureState.TAP_TOGGLE_ON ||
        fsm.state == GestureState.HOLDING

    val buttonColors = if (isRecordingVisual) {
        ButtonDefaults.buttonColors(
            containerColor = GallopColors.RecordingAccent,
            contentColor = GallopColors.AccentOn,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = GallopColors.Accent,
            contentColor = GallopColors.AccentOn,
        )
    }

    val label = when {
        isRecordingVisual -> stringResource(R.string.voice_panel_recording)
        else -> stringResource(R.string.voice_panel_placeholder_button)
    }

    val boxHeight = when (style) {
        SmartVoiceButtonStyle.Panel -> 72.dp
        // Solid speak control — reads as one full-width button in the thin panel.
        SmartVoiceButtonStyle.PanelCompact -> 56.dp
    }

    val cornerRadius = when (style) {
        SmartVoiceButtonStyle.Panel -> 36.dp
        SmartVoiceButtonStyle.PanelCompact -> 14.dp
    }

    val horizontalPadding = when (style) {
        SmartVoiceButtonStyle.PanelCompact -> 0.dp
        SmartVoiceButtonStyle.Panel -> 16.dp
    }

    val gestureModifier = Modifier.pointerInput(cancelSlopPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            pointerPressed = true
            holdTimerJob?.cancel()

            if (!AudioRecorderEngine.checkPermission(context)) {
                pointerPressed = false
                if (!permissionRequestInFlight) {
                    permissionRequestInFlight = true
                    scope.launch {
                        try {
                            val granted = permissionRequester.request(context)
                            if (!granted) {
                                context.showToast(R.string.mic_permission_denied)
                            }
                            // Second-press pattern: on grant the user taps again to record.
                        } finally {
                            permissionRequestInFlight = false
                        }
                    }
                }
                return@awaitEachGesture
            }

            val downMs = SystemClock.elapsedRealtime()
            val downPos = down.position
            fsm.onEvent(GestureEvent.Down(downMs, downPos.x, downPos.y))

            holdTimerJob = scope.launch {
                delay(HOLD_THRESHOLD_MS)
                if (pointerPressed) {
                    fsm.onEvent(
                        GestureEvent.HoldThresholdElapsed(
                            SystemClock.elapsedRealtime(),
                        ),
                    )
                }
            }

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    pointerPressed = false
                    holdTimerJob?.cancel()
                    fsm.onEvent(
                        GestureEvent.Up(
                            SystemClock.elapsedRealtime(),
                            change.position.x,
                            change.position.y,
                        ),
                    )
                    break
                }
                fsm.onEvent(
                    GestureEvent.Move(change.position.x, change.position.y),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(boxHeight),
        contentAlignment = Alignment.Center,
    ) {
        // Child composable owns rememberInfiniteTransition — only composed while recording
        // (same gating pattern as RecordingDot; avoids conditional hooks in this parent).
        if (isRecordingVisual) {
            RecordingPulseHalo(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(boxHeight),
            )
        }
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(boxHeight)
                .then(gestureModifier),
            shape = RoundedCornerShape(cornerRadius),
            colors = buttonColors,
            contentPadding = ButtonDefaults.ContentPadding,
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = if (style == SmartVoiceButtonStyle.PanelCompact) 2.dp else 0.dp,
                pressedElevation = if (style == SmartVoiceButtonStyle.PanelCompact) 4.dp else 0.dp,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(
                        if (style == SmartVoiceButtonStyle.PanelCompact) 22.dp else 24.dp,
                    ),
                )
                Spacer(
                    modifier = Modifier.width(
                        if (style == SmartVoiceButtonStyle.PanelCompact) 10.dp else 12.dp,
                    ),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (isRecordingVisual) {
                    Spacer(modifier = Modifier.width(8.dp))
                    RecordingDot()
                }
            }
        }
    }
}

/**
 * Recording pulse halo. Always calls [rememberInfiniteTransition] unconditionally;
 * parent must only compose this while recording (idle panel must not keep it alive).
 */
@Composable
private fun RecordingPulseHalo(modifier: Modifier = Modifier) {
    val pulseTransition = rememberInfiniteTransition(label = "recording-pulse")
    val pulseRadius by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse-radius",
    )
    val pulseAlpha = 0.15f + pulseRadius * 0.25f
    Box(
        modifier = modifier.drawBehind {
            val baseRadius = size.minDimension / 2f
            val extra = pulseRadius * 24.dp.toPx()
            drawCircle(
                color = GallopColors.RecordingAccent.copy(alpha = pulseAlpha),
                radius = baseRadius + extra,
                center = center,
            )
        },
    )
}

@Composable
private fun RecordingDot() {
    val transition = rememberInfiniteTransition(label = "recording-dot")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recording-dot-alpha",
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(GallopColors.AccentOn, CircleShape),
    )
}

private fun Context.showToast(messageRes: Int) {
    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}
