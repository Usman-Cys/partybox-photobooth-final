package com.photobooth.printer

import android.util.Log
import com.photobooth.hardware.NativeSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class UartPrinterClient(
    private val devicePath: String = "/dev/ttyS3",
    private val baudRate: Int = 115200
) {
    private val mutex = Mutex()
    private var fd: Int = -1

    suspend fun ensureOpen(): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            openLocked()
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (fd >= 0) {
                NativeSerialPort.close(fd)
                fd = -1
            }
        }
    }

    suspend fun send(frame: ByteArray, timeoutMs: Int = 2_000): Result<PrinterResponse> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!openLocked()) {
                return@withContext Result.failure(IllegalStateException("UART unavailable"))
            }
            val wrote = NativeSerialPort.write(fd, frame, timeoutMs)
            if (wrote != frame.size) {
                return@withContext Result.failure(IllegalStateException("Write failed: $wrote"))
            }
            val read = NativeSerialPort.read(fd, 64, timeoutMs)
            if (read.size != 64) {
                return@withContext Result.failure(IllegalStateException("Timeout or short response: ${read.size}"))
            }
            Result.success(PrinterProtocol.parseResponse(read))
        }
    }

    private fun openLocked(): Boolean {
        if (fd >= 0) return true
        val opened = NativeSerialPort.open(devicePath, baudRate)
        if (opened < 0) {
            Log.e(TAG, "Failed to open UART device $devicePath")
            return false
        }
        fd = opened
        return true
    }

    suspend fun sendWithBusyRetry(frame: ByteArray, retries: Int = 6): Result<PrinterResponse> {
        repeat(retries) { attempt ->
            val result = send(frame)
            val response = result.getOrNull()
            if (response == null) {
                return result
            }
            if (response.errorCode != BUSY_CODE) {
                return result
            }
            Log.w(TAG, "Printer busy, retrying attempt=${attempt + 1}")
            delay(400)
        }
        return Result.failure(IllegalStateException("Printer remained busy"))
    }

    companion object {
        private const val TAG = "UartPrinterClient"
        const val BUSY_CODE = 999
    }
}
