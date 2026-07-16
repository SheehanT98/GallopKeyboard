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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Composable
fun SmartVoiceButton(
    audioRecorderEngine: AudioRecorderEngine,
    transcriber: Transcriber,
    permissionRequester: PermissionRequester,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val cancelSlopPx = with(density) { CANCEL_SLOP_DP.dp.toPx() }

    var visualRecording by remember { mutableStateOf(false) }
    var activeSession by remember { mutableStateOf<AudioSession?>(null) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var holdTimerJob by remember { mutableStateOf<Job?>(null) }
    var pointerPressed by remember { mutableStateOf(false) }

    val fsm = remember(cancelSlopPx) {
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
                recordingJob = scope.launch {
                    try {
                        audioRecorderEngine.start().collect { frame ->
                            val sessionRef = activeSession ?: return@collect
                            writeFrameToBuffer(sessionRef.buffer, frame)
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
                        visualRecording = false
                        activeSession = null
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
                    scope.launch { transcriber.onSessionStop(session) }
                }
            },
            onSessionCancel = {
                visualRecording = false
                recordingJob?.cancel()
                recordingJob = null
                val session = activeSession
                activeSession = null
                if (session != null) {
                    transcriber.onSessionCancel(session)
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            recordingJob?.cancel()
            holdTimerJob?.cancel()
            fsm.reset()
        }
    }

    val isRecordingVisual = visualRecording ||
        fsm.state == GestureState.RECORDING ||
        fsm.state == GestureState.TAP_TOGGLE_ON ||
        fsm.state == GestureState.HOLDING

    val buttonColors = if (isRecordingVisual) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors()
    }

    val label = if (isRecordingVisual) {
        stringResource(R.string.voice_panel_recording)
    } else {
        stringResource(R.string.voice_panel_placeholder_button)
    }

    Box(
        modifier = modifier
            .fillMaxWidth(0.8f)
            .height(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .pointerInput(cancelSlopPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        pointerPressed = true
                        holdTimerJob?.cancel()

                        if (!AudioRecorderEngine.checkPermission(context)) {
                            val granted = runBlocking {
                                permissionRequester.request(context)
                            }
                            if (!granted) {
                                context.showToast(R.string.mic_permission_denied)
                                pointerPressed = false
                                return@awaitEachGesture
                            }
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
                },
            shape = RoundedCornerShape(16.dp),
            colors = buttonColors,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = label)
                if (isRecordingVisual) {
                    Spacer(modifier = Modifier.width(8.dp))
                    RecordingDot()
                }
            }
        }
    }
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
            .background(MaterialTheme.colorScheme.onError, CircleShape),
    )
}

private fun writeFrameToBuffer(buffer: RingByteBuffer, frame: ShortArray) {
    val bytes = ByteArray(frame.size * 2)
    val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    frame.forEach { sample -> bb.putShort(sample) }
    buffer.write(bytes, 0, bytes.size)
}

private fun Context.showToast(messageRes: Int) {
    Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
}
