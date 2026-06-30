package com.tvhanan.util

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticUtil {
    private var vibrator: Vibrator? = null
    var isEnabled: Boolean = true

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun tick() {
        val v = vibrator ?: return
        if (!isEnabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA)
                    .build()
                v.vibrate(effect, attributes)
            } else {
                v.vibrate(effect)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(70)
        }
    }
}
