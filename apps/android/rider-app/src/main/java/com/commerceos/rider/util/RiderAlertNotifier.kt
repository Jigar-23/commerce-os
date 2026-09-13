package com.commerceos.rider.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object RiderAlertNotifier {

    private var lastAlertedOfferId = ""
    private var lastAlertTimeMs = 0L

    fun playNewJobAlert(context: Context, offerId: String? = null) {
        val now = System.currentTimeMillis()
        if (offerId != null && offerId.isNotBlank()) {
            if (offerId == lastAlertedOfferId) {
                return // Already alerted for this exact offer
            }
            lastAlertedOfferId = offerId
        } else {
            if (now - lastAlertTimeMs < 10000L) {
                return // Global 10s cooldown
            }
        }
        lastAlertTimeMs = now

        try {
            // 1. Play quick pleasant double-beep tone for riders
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        } catch (e: Exception) {
            try {
                val notificationUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val r = android.media.RingtoneManager.getRingtone(context, notificationUri)
                r.play()
            } catch (_: Exception) {}
        }

        try {
            // 2. Short crisp vibration pulse
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(300)
                }
            }
        } catch (e: Exception) {
            // Ignore vibration error
        }
    }

    fun reset() {
        lastAlertedOfferId = ""
        lastAlertTimeMs = 0L
    }
}
