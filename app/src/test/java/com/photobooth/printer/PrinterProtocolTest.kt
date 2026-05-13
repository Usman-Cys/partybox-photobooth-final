package com.photobooth.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrinterProtocolTest {

    @Test
    fun `imgi frame uses expected header and filename prefix`() {
        val frame = PrinterProtocol.imgiFrame()

        assertEquals(64, frame.size)
        assertEquals(0x1B.toByte(), frame[0])
        assertEquals(0x2A.toByte(), frame[1])
        assertEquals(0x43.toByte(), frame[2])
        assertEquals(0x41.toByte(), frame[3])
        assertEquals("print_1.", frame.copyOfRange(8, 16).toString(Charsets.US_ASCII))
    }

    @Test
    fun `parser validates header`() {
        val invalid = ByteArray(64)
        val result = PrinterProtocol.parseResponse(invalid)

        assertFalse(result.isSuccess)
        assertEquals(-2, result.errorCode)
    }

    @Test
    fun `parser marks success when errorCode zero`() {
        val ok = PrinterProtocol.buildFrame(0x01, 0x01)
        val result = PrinterProtocol.parseResponse(ok)

        assertTrue(result.isSuccess)
        assertEquals(0, result.errorCode)
    }
}
