package com.commerceos.rider.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.commerceos.rider.MainActivity
import com.commerceos.rider.model.ServerOffer

enum class NotificationPostResult {
    POSTED,
    PERMISSION_DENIED,
    POST_FAILED
}

object RiderNotificationManager {

    private const val TAG = "RiderNotifMgr"
    private const val CHANNEL_ID = "rider_dispatch_offers_v2"
    private const val CHANNEL_NAME = "Rider Delivery Dispatch Offers"
    private const val PREFS_NAME = "commerce_rider_notif_dedup"
    private const val KEY_PROCESSED_EVENTS = "processed_event_keys"
    private const val KEY_ACKED_RECEIPTS = "acked_receipt_keys"

    private val inMemoryDeduplicationSet = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val inMemoryAckedReceipts = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority actionable notifications for quick-commerce delivery dispatch offers"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Checks whether an offer event has already been successfully delivered to the notification tray.
     */
    @Synchronized
    fun isEventSuccessfullyPosted(context: Context, key: String): Boolean {
        if (inMemoryDeduplicationSet.contains(key)) {
            return true
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_PROCESSED_EVENTS, emptySet()) ?: emptySet()
        if (saved.contains(key)) {
            inMemoryDeduplicationSet.add(key)
            return true
        }
        return false
    }

    /**
     * Atomically marks an event as successfully delivered only after NotificationManager.notify() succeeds.
     */
    @Synchronized
    fun markEventSuccessfullyPosted(context: Context, key: String) {
        inMemoryDeduplicationSet.add(key)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_PROCESSED_EVENTS, emptySet()) ?: emptySet()
        val updated = HashSet(saved)
        if (updated.size > 200) {
            updated.clear()
        }
        updated.add(key)
        prefs.edit().putStringSet(KEY_PROCESSED_EVENTS, updated).apply()
    }

    /**
     * Idempotency check for transport reception ack telemetry (DEVICE_RECEIVED).
     */
    @Synchronized
    fun isDeviceReceiptAcked(context: Context, eventId: String): Boolean {
        if (inMemoryAckedReceipts.contains(eventId)) {
            return true
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_ACKED_RECEIPTS, emptySet()) ?: emptySet()
        if (saved.contains(eventId)) {
            inMemoryAckedReceipts.add(eventId)
            return true
        }
        return false
    }

    /**
     * Atomically marks DEVICE_RECEIVED as acknowledged to prevent duplicate telemetry on notification retries.
     */
    @Synchronized
    fun markDeviceReceiptAcked(context: Context, eventId: String) {
        inMemoryAckedReceipts.add(eventId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(KEY_ACKED_RECEIPTS, emptySet()) ?: emptySet()
        val updated = HashSet(saved)
        if (updated.size > 200) {
            updated.clear()
        }
        updated.add(eventId)
        prefs.edit().putStringSet(KEY_ACKED_RECEIPTS, updated).apply()
    }

    fun postDirectOfferNotification(context: Context, offer: ServerOffer): NotificationPostResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "NOTIFICATION_PERMISSION_DENIED: Cannot post offer notification to system tray.")
                return NotificationPostResult.PERMISSION_DENIED
            }
        }

        return try {
            initChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("offerId", offer.offerId)
                putExtra("deliveryId", offer.deliveryId)
                putExtra("orderId", offer.orderId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                offer.offerId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val earningsText = "₹${offer.earningsAmount.toInt()}"

            val appIconRes = context.applicationInfo.icon.takeIf { it != 0 }
                ?: android.R.drawable.stat_notify_chat

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(appIconRes)
                .setContentTitle("🚀 NEW DELIVERY · $earningsText")
                .setContentText("${offer.totalDistanceKm} km • ~${offer.estimatedDurationMins} min | ${offer.merchantName}")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${offer.totalDistanceKm} km • ~${offer.estimatedDurationMins} min\nPickup: ${offer.merchantName}\nDrop: ${offer.customerAddress}\nTap to accept")
                )
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 400, 200, 400))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false)

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(offer.offerId.hashCode(), builder.build())

            // Audible alert tone and vibration pulse
            RiderAlertNotifier.playNewJobAlert(context, offer.offerId)
            Log.d(TAG, "NOTIFICATION_POSTED_SUCCESS for offer: ${offer.offerId}")
            NotificationPostResult.POSTED
        } catch (e: Exception) {
            Log.e(TAG, "NOTIFICATION_POST_FAILED: ${e.message}", e)
            NotificationPostResult.POST_FAILED
        }
    }

    fun cancelOfferNotification(context: Context, offerId: String?) {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!offerId.isNullOrBlank()) {
                manager.cancel(offerId.hashCode())
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
}
