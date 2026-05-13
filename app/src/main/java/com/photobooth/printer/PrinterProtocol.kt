package com.photobooth.printer

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PrinterResponse(
    val isSuccess: Boolean,
    val errorCode: Int,
    val rawFrame: ByteArray
)

object PrinterProtocol {
    private val HEADER = byteArrayOf(0x1B, 0x2A, 0x43, 0x41)

    fun buildFrame(group: Int, id: Int, payloadUpdater: (ByteArray) -> Unit = {}): ByteArray {
        val frame = ByteArray(64)
        HEADER.copyInto(frame, 0)
        frame[4] = 0x00
        frame[5] = 0x01
        frame[6] = group.toByte()
        frame[7] = id.toByte()
        payloadUpdater(frame)
        return frame
    }

    fun imgiFrame(): ByteArray = buildFrame(0x02, 0x01) { frame ->
        val bytes = "print_1.jpg".toByteArray(Charsets.US_ASCII)
        val len = 8.coerceAtMost(bytes.size)
        bytes.copyInto(frame, destinationOffset = 8, endIndex = len)
    }

    fun prrqFrame(copies: Int): ByteArray = buildFrame(0x02, 0x00) { frame ->
        frame[8] = copies.coerceIn(1, 9).toByte()
    }

    fun goprFrame(): ByteArray = buildFrame(0x02, 0x02)

    fun heartbeatFrame(): ByteArray = buildFrame(0x04, 0x55)

    fun parseResponse(frame: ByteArray): PrinterResponse {
        if (frame.size != 64) {
            return PrinterResponse(isSuccess = false, errorCode = -1, rawFrame = frame)
        }
        val headerValid = frame.sliceArray(0..3).contentEquals(HEADER)
        if (!headerValid) {
            return PrinterResponse(isSuccess = false, errorCode = -2, rawFrame = frame)
        }
        val error = ByteBuffer.wrap(frame, 8, 4).order(ByteOrder.LITTLE_ENDIAN).int
        return PrinterResponse(isSuccess = error == 0, errorCode = error, rawFrame = frame)
    }
}
