package com.commerceos.android.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Manages Android System Notification Channels (Android 8.0+ / API 26+).
 * Creates distinct system channels in Android Settings with proper notification
 * badging, Deep Emerald LED indicators, and heads-up banner priorities.
 */
object NotificationChannelHelper {
    const val CHANNEL_DISPATCH_SLA = "channel_dispatch_sla"
    const val CHANNEL_ORDER_UPDATES = CHANNEL_DISPATCH_SLA
    const val CHANNEL_PRESCRIPTION_REVIEW = "channel_prescription_review"
    const val CHANNEL_REFILL_REMINDERS = "channel_refill_reminders"
    const val CHANNEL_OFFERS = "channel_offers_promotions"

    private const val BRAND_EMERALD_COLOR = 0xFF0E675E.toInt()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // 1. Order Updates & Live Tracking (High Importance - Heads-up banner alert)
            val dispatchChannel = NotificationChannel(
                CHANNEL_DISPATCH_SLA,
                "Order Updates & Live Tracking",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Live updates when your order is packed, rider is dispatched, and real-time SLA countdown"
                enableLights(true)
                lightColor = BRAND_EMERALD_COLOR
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 2. Pharmacist Prescription Review (High Importance)
            val rxChannel = NotificationChannel(
                CHANNEL_PRESCRIPTION_REVIEW,
                "Pharmacist Prescription Review",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Status updates from licensed pharmacists on uploaded doctor slips and order approvals"
                enableLights(true)
                lightColor = BRAND_EMERALD_COLOR
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 3. Chronic Refill Reminders (Default Importance)
            val refillChannel = NotificationChannel(
                CHANNEL_REFILL_REMINDERS,
                "Chronic Refill Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Timely 3-day advance alerts to reorder chronic medications before running out"
                enableLights(true)
                lightColor = BRAND_EMERALD_COLOR
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 4. Health Offers & Wellness Tips (Default Importance)
            val offersChannel = NotificationChannel(
                CHANNEL_OFFERS,
                "Health Offers & Wellness Tips",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Exclusive medicine discounts, seasonal health alerts, and wellness deals"
                enableLights(false)
                enableVibration(false)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(dispatchChannel, rxChannel, refillChannel, offersChannel)
            )
        }
    }
}
