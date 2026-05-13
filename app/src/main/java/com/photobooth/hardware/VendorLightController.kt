package com.photobooth.hardware

import android.util.Log

class VendorLightController {

    fun setRingBrightness(value: Int) {
        val clamped = value.coerceIn(0, 100)
        runCatching {
            val clazz = Class.forName("android.os.LedControl")
            val method = clazz.getMethod("ledPowerOn", Int::class.javaPrimitiveType)
            method.invoke(null, clamped)
        }.onFailure {
            Log.w(TAG, "Ring LED reflection failed", it)
        }
    }

    fun setFlashEnabled(enabled: Boolean) {
        runCatching {
            val clazz = Class.forName("android.os.FlashLed")
            val method = clazz.getMethod("setLed", Boolean::class.javaPrimitiveType)
            method.invoke(null, enabled)
        }.onFailure {
            Log.w(TAG, "Flash LED reflection failed", it)
        }
    }

    companion object {
        private const val TAG = "VendorLightController"
    }
}
