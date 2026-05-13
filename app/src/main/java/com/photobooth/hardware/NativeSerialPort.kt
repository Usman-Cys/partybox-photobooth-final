package com.photobooth.hardware

object NativeSerialPort {
    private val loaded: Boolean

    init {
        loaded = runCatching {
            System.loadLibrary("serialport")
            true
        }.getOrDefault(false)
    }

    fun open(path: String, baudRate: Int): Int = if (loaded) openNative(path, baudRate) else -1
    fun close(fd: Int) {
        if (loaded) closeNative(fd)
    }
    fun write(fd: Int, data: ByteArray, timeoutMs: Int): Int = if (loaded) writeNative(fd, data, timeoutMs) else -1
    fun read(fd: Int, count: Int, timeoutMs: Int): ByteArray = if (loaded) readNative(fd, count, timeoutMs) else ByteArray(0)

    private external fun openNative(path: String, baudRate: Int): Int
    private external fun closeNative(fd: Int)
    private external fun writeNative(fd: Int, data: ByteArray, timeoutMs: Int): Int
    private external fun readNative(fd: Int, count: Int, timeoutMs: Int): ByteArray
}
