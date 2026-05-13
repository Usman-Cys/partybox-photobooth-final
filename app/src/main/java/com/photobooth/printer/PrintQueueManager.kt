package com.photobooth.printer

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed class PrinterStatus {
    data object Idle : PrinterStatus()
    data object Printing : PrinterStatus()
    data class Busy(val code: Int = UartPrinterClient.BUSY_CODE) : PrinterStatus()
    data object Done : PrinterStatus()
    data class Error(val message: String) : PrinterStatus()
}

class PrintQueueManager(
    private val printerClient: UartPrinterClient,
    private val scope: CoroutineScope
) {
    private val queue = Channel<Int>(Channel.UNLIMITED)
    private val _status = MutableStateFlow<PrinterStatus>(PrinterStatus.Idle)
    val status: StateFlow<PrinterStatus> = _status.asStateFlow()
    private val worker: Job

    init {
        worker = scope.launch {
            for (copies in queue) {
                _status.value = PrinterStatus.Printing
                val result = printInternal(copies)
                _status.value = result.fold(
                    onSuccess = { PrinterStatus.Done },
                    onFailure = {
                        if (it.message?.contains("busy", ignoreCase = true) == true) {
                            PrinterStatus.Busy()
                        } else {
                            PrinterStatus.Error(it.message ?: "Unknown printer error")
                        }
                    }
                )
                delay(1000)
                _status.value = PrinterStatus.Idle
            }
        }
        scope.launch {
            while (isActive) {
                printerClient.send(PrinterProtocol.heartbeatFrame())
                delay(30_000)
            }
        }
    }

    fun enqueue(copies: Int = 1) {
        queue.trySend(copies.coerceIn(1, 9))
    }

    private suspend fun printInternal(copies: Int): Result<Unit> {
        val img = printerClient.sendWithBusyRetry(PrinterProtocol.imgiFrame())
        val imgResponse = img.getOrElse { return Result.failure(it) }
        if (!imgResponse.isSuccess) {
            if (imgResponse.errorCode == UartPrinterClient.BUSY_CODE) {
                return Result.failure(IllegalStateException("busy"))
            }
            return Result.failure(IllegalStateException("IMGI error ${imgResponse.errorCode}"))
        }

        val prrq = printerClient.sendWithBusyRetry(PrinterProtocol.prrqFrame(copies))
        val prrqResponse = prrq.getOrElse { return Result.failure(it) }
        if (!prrqResponse.isSuccess) {
            return Result.failure(IllegalStateException("PRRQ error ${prrqResponse.errorCode}"))
        }

        val gopr = printerClient.sendWithBusyRetry(PrinterProtocol.goprFrame())
        val goprResponse = gopr.getOrElse { return Result.failure(it) }
        if (!goprResponse.isSuccess) {
            return Result.failure(IllegalStateException("GOPR error ${goprResponse.errorCode}"))
        }

        return Result.success(Unit)
    }

    suspend fun shutdown() {
        worker.cancel()
        printerClient.close()
    }

    companion object {
        const val PRINT_DIR = "/sdcard/DCIM/Printer/Image"
        const val PRINT_FILE = "print_1.jpg"

        fun ensurePrintFile(): File {
            val dir = File(PRINT_DIR)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val file = File(dir, PRINT_FILE)
            if (file.exists()) {
                file.delete()
            }
            return file
        }
    }
}
