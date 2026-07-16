package com.gallopkeyboard.ime.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Fixed-capacity thread-safe ring buffer for PCM bytes.
 *
 * When full, new writes overwrite the oldest data and [droppedBytes] increments.
 */
class RingByteBuffer(capacityBytes: Int) {

    private val lock = Any()
    private val buffer = ByteArray(capacityBytes)
    private var writeIndex = 0
    private var count = 0
    private var dropped: Long = 0

    val capacity: Int = capacityBytes

    fun write(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
        if (length == 0) return

        synchronized(lock) {
            for (i in 0 until length) {
                if (count == buffer.size) {
                    dropped++
                } else {
                    count++
                }
                buffer[writeIndex] = bytes[offset + i]
                writeIndex = (writeIndex + 1) % buffer.size
            }
        }
    }

    /** Snapshot of buffered PCM16 little-endian samples at 16 kHz mono. */
    fun snapshotShorts(): ShortArray {
        val bytes = snapshot()
        if (bytes.isEmpty()) return ShortArray(0)
        val shorts = ShortArray(bytes.size / 2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    fun snapshot(): ByteArray {
        synchronized(lock) {
            val result = ByteArray(count)
            if (count == 0) return result
            val start = (writeIndex - count + buffer.size) % buffer.size
            if (start + count <= buffer.size) {
                System.arraycopy(buffer, start, result, 0, count)
            } else {
                val firstPart = buffer.size - start
                System.arraycopy(buffer, start, result, 0, firstPart)
                System.arraycopy(buffer, 0, result, firstPart, count - firstPart)
            }
            return result
        }
    }

    fun size(): Int = synchronized(lock) { count }

    fun clear() {
        synchronized(lock) {
            writeIndex = 0
            count = 0
        }
    }

    fun droppedBytes(): Long = synchronized(lock) { dropped }
}

/** Five minutes of 16 kHz mono PCM16 — safety ceiling per Plan 005. */
const val RING_BUFFER_CAPACITY_BYTES: Int = 5 * 60 * 16_000 * 2
