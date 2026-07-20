package com.gallopkeyboard.ime.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RingByteBufferTest {

    @Test
    fun `writeShorts matches byte write equivalence`() {
        val samples = shortArrayOf(0, 1, -1, 256, -32768, 32767)
        val viaBytes = RingByteBuffer(32)
        val viaShorts = RingByteBuffer(32)
        val scratch = ByteArray(samples.size * 2)

        val encoded = ByteArray(samples.size * 2)
        java.nio.ByteBuffer.wrap(encoded).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer().put(samples)
        viaBytes.write(encoded, 0, encoded.size)
        viaShorts.writeShorts(samples, scratch)

        assertArrayEquals(viaBytes.snapshot(), viaShorts.snapshot())
        assertEquals(viaBytes.size(), viaShorts.size())
        assertEquals(0L, viaShorts.droppedBytes())
    }

    @Test
    fun `writeShorts without scratch still writes`() {
        val buffer = RingByteBuffer(8)
        buffer.writeShorts(shortArrayOf(0x0102, 0x0304))
        assertEquals(4, buffer.size())
        assertArrayEquals(byteArrayOf(0x02, 0x01, 0x04, 0x03), buffer.snapshot())
    }

    @Test
    fun `write less than capacity returns exact snapshot`() {
        val buffer = RingByteBuffer(10)
        buffer.write(byteArrayOf(1, 2, 3), 0, 3)
        assertEquals(3, buffer.size())
        assertArrayEquals(byteArrayOf(1, 2, 3), buffer.snapshot())
        assertEquals(0L, buffer.droppedBytes())
    }

    @Test
    fun `write equal to capacity fills buffer without drops`() {
        val buffer = RingByteBuffer(4)
        buffer.write(byteArrayOf(10, 11, 12, 13), 0, 4)
        assertEquals(4, buffer.size())
        assertArrayEquals(byteArrayOf(10, 11, 12, 13), buffer.snapshot())
        assertEquals(0L, buffer.droppedBytes())
    }

    @Test
    fun `write twice capacity keeps newest bytes and counts drops`() {
        val buffer = RingByteBuffer(4)
        buffer.write(byteArrayOf(1, 2, 3, 4), 0, 4)
        buffer.write(byteArrayOf(5, 6, 7, 8), 0, 4)
        assertEquals(4, buffer.size())
        assertArrayEquals(byteArrayOf(5, 6, 7, 8), buffer.snapshot())
        assertEquals(4L, buffer.droppedBytes())
    }

    @Test
    fun `concurrent write and read stress`() {
        val buffer = RingByteBuffer(1024)
        val latch = CountDownLatch(2)
        val writer = thread {
            repeat(500) { i ->
                buffer.write(byteArrayOf(i.toByte()), 0, 1)
            }
            latch.countDown()
        }
        val reader = thread {
            repeat(500) {
                buffer.snapshot()
                buffer.size()
            }
            latch.countDown()
        }
        assertEquals(true, latch.await(500, TimeUnit.MILLISECONDS))
        writer.join(500)
        reader.join(500)
        assertEquals(500, buffer.size())
    }

    @Test
    fun `clear resets size`() {
        val buffer = RingByteBuffer(8)
        buffer.write(byteArrayOf(1, 2), 0, 2)
        buffer.clear()
        assertEquals(0, buffer.size())
        assertArrayEquals(byteArrayOf(), buffer.snapshot())
    }
}
