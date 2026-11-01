package com.commerceos.android.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Manages Android System Notification Channels (Android 8.0+ / API 26+).
 * Creates distinct system channels in Android Settings so users can customize
 * ringtones, vibrations, and lock-screen visibility per alert category.
 */
object NotificationChannelHelper {
    const val CHANNEL_DISPATCH_SLA = "channel_dispatch_sla"
    const val CHANNEL_PRESCRIPTION_REVIEW = "channel_prescription_review"
    const val CHANNEL_REFILL_REMINDERS = "channel_refill_reminders"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Order Dispatch & Live Rider SLA (High Importance - Heads-up alert)
            val dispatchChannel = NotificationChannel(
                CHANNEL_DISPATCH_SLA,
                "Order Dispatch & Live Rider SLA",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live updates when your order is packed, rider is dispatched, and real-time SLA countdown"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            // 2. Pharmacist Prescription Review (High Importance)
            val rxChannel = NotificationChannel(
                CHANNEL_PRESCRIPTION_REVIEW,
                "Pharmacist Prescription Review",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Status updates from licensed pharmacists on uploaded doctor slips and order approvals"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            // 3. Chronic Refill Reminders (Default Importance)
            val refillChannel = NotificationChannel(
                CHANNEL_REFILL_REMINDERS,
                "Chronic Refill Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Timely 3-day advance alerts to reorder chronic medications before running out"
                enableLights(true)
                enableVibration(false)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(dispatchChannel, rxChannel, refillChannel)
            )
        }
    }
}
