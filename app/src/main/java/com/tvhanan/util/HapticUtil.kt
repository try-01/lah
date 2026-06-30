package com.tvhanan.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticUtil {

    private var vibrator: Vibrator? = null
    private var appContext: Context? = null
    private var toastShown: Boolean = false
    var isEnabled: Boolean = true

    fun init(context: Context) {
        appContext = context.applicationContext
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

        appContext?.let { ctx ->
            val systemHaptic = android.provider.Settings.System.getInt(
                ctx.contentResolver,
                android.provider.Settings.System.HAPTIC_FEEDBACK_ENABLED, 1
            )
            if (systemHaptic == 0 && !toastShown) {
                android.widget.Toast.makeText(ctx, "Aktifkan 'Getar saat disentuh' di Pengaturan HP untuk efek getar", android.widget.Toast.LENGTH_LONG).show()
                toastShown = true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(50, 255)
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(50)
        }
    }
}
