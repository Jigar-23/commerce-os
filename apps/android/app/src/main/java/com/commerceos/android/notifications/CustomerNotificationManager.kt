package com.commerceos.android.notifications

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.commerceos.android.MainActivity
import com.commerceos.android.R

/**
 * Production-grade Notification Engine for CommerceOS.
 * Delivers benchmark-matching healthcare notifications with:
 * - Pure monochrome white status bar silhouette (ic_notification_small)
 * - High-DPI circular Deep Emerald brand badge (ic_notification_brand_badge)
 * - Deep Emerald system accent (#0E675E)
 * - High-importance heads-up banners on Android 8+
 * - Rich BigTextStyle expansions with OTP badges & delivery ETAs
 * - Actionable direct-route PendingIntents ("⚡ Track Live", "Reorder")
 * - 100% sync with in-app notification center (LocalNotificationStorage)
 */
object CustomerNotificationManager {

    const val BRAND_EMERALD_COLOR = 0xFF0E675E.toInt()

    private var cachedBrandBadge: Bitmap? = null

    @Synchronized
    private fun getBrandBadgeBitmap(context: Context): Bitmap? {
        if (cachedBrandBadge != null && !cachedBrandBadge!!.isRecycled) {
            return cachedBrandBadge
        }
        return try {
            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_notification_brand_badge) ?: return null
            val size = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(96)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            cachedBrandBadge = bitmap
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Posts immediate high-importance Order Placed confirmation notification.
     */
    fun postOrderPlacedNotification(
        context: Context,
        orderId: String,
        itemCount: Int,
        totalAmount: String,
        etaMins: Int = 12
    ) {
        if (!NotificationPreferenceManager.isOrderAlertsEnabled(context)) return

        NotificationChannelHelper.createChannels(context)

        val orderCode = orderId.takeLast(6).uppercase()
        val title = "💊 Order #$orderCode Confirmed"
        val summaryText = "$itemCount items • ₹$totalAmount"
        val shortContent = "Order #$orderCode confirmed. Express dark store packing started (~$etaMins mins)."
        val bigContent = buildString {
            append("Your pharmacy order has been confirmed and assigned to our local dark store.\n")
            append("📦 Package: $itemCount medication(s) • ₹$totalAmount\n")
            append("⚡ Express Delivery: Estimated in ~$etaMins mins\n")
            append("🛡️ Temperature-controlled & tamper-evident sealed.")
        }

        // 1. Record in local persistent Notification Center
        LocalNotificationStorage.saveNotification(
            context,
            InAppNotificationItem(
                title = title,
                message = "$shortContent (Total: ₹$totalAmount)",
                category = "Orders",
                deepLinkAction = "TRACK_ORDER:$orderId"
            )
        )

        if (!hasPostNotificationPermission(context)) return

        // 2. PendingIntents
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_action", "TRACK_ORDER")
            putExtra("notification_order_id", orderId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val trackActionIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_action", "TRACK_ORDER")
            putExtra("notification_order_id", orderId)
        }
        val trackActionPendingIntent = PendingIntent.getActivity(
            context,
            (orderId + "_track").hashCode(),
            trackActionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, NotificationChannelHelper.CHANNEL_ORDER_UPDATES)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setColor(BRAND_EMERALD_COLOR)
            .setContentTitle(title)
            .setContentText(shortContent)
            .setSubText(summaryText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .setSummaryText(summaryText)
                    .bigText(bigContent)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                R.drawable.ic_notification_small,
                "⚡ Track Live",
                trackActionPendingIntent
            )

        getBrandBadgeBitmap(context)?.let {
            builder.setLargeIcon(it)
        }

        try {
            NotificationManagerCompat.from(context).notify(orderId.hashCode(), builder.build())
        } catch (_: SecurityException) {}
    }

    /**
     * Posts order status update (Packed, Out for Delivery, Delivered, Cancelled).
     */
    fun postOrderStatusNotification(
        context: Context,
        orderId: String,
        status: String,
        riderName: String? = null,
        otp: String? = null,
        etaMins: Int? = null
    ) {
        if (!NotificationPreferenceManager.isOrderAlertsEnabled(context)) return

        NotificationChannelHelper.createChannels(context)

        val orderCode = orderId.takeLast(6).uppercase()
        val upperStatus = status.uppercase()

        val (title, shortContent, bigContent) = when (upperStatus) {
            "OUT_FOR_DELIVERY", "RIDER_DISPATCHED", "DISPATCHED" -> {
                val riderStr = if (!riderName.isNullOrBlank()) "Rider $riderName" else "Delivery partner"
                val etaStr = if (etaMins != null && etaMins > 0) "Estimated arrival in ~$etaMins mins." else "On the way to your doorstep."
                val otpStr = if (!otp.isNullOrBlank()) "\n🔑 Share PIN: $otp with rider upon handover." else ""
                Triple(
                    "🛵 Order #$orderCode Out for Delivery",
                    "$riderStr is on the way. $etaStr",
                    buildString {
                        append("$riderStr has picked up your medicine package.\n")
                        append("📍 $etaStr$otpStr\n")
                        append("🛡️ Contactless and tamper-sealed delivery guaranteed.")
                    }
                )
            }
            "DELIVERED" -> {
                Triple(
                    "✅ Order #$orderCode Delivered",
                    "Your medicines have been safely delivered. Thank you!",
                    buildString {
                        append("Your package was handed over successfully.\n")
                        append("🧾 Invoice and digital prescription are archived in your Account Vault.\n")
                        append("Wishing you a speedy recovery!")
                    }
                )
            }
            "CANCELLED" -> {
                Triple(
                    "❌ Order #$orderCode Cancelled",
                    "Your order was cancelled. Any debited amount will be refunded to source.",
                    buildString {
                        append("Order #$orderCode has been cancelled as requested.\n")
                        append("💳 Any online payment will be refunded within 24-48 hours.\n")
                        append("Need assistance? Contact our 24x7 Pharmacy Helpdesk.")
                    }
                )
            }
            "PACKED", "READY_FOR_PICKUP" -> {
                Triple(
                    "📦 Order #$orderCode Packed & Verified",
                    "Our pharmacist has verified and packed your medicines. Rider arriving shortly.",
                    buildString {
                        append("All items have passed double-check verification by our pharmacist.\n")
                        append("🛵 Awaiting doorstep delivery rider pickup.")
                    }
                )
            }
            else -> {
                Triple(
                    "🔔 Order #$orderCode Update",
                    "Order status is now $status.",
                    "Your order status is now $status. Tap to view details."
                )
            }
        }

        // Save to persistent notification center
        LocalNotificationStorage.saveNotification(
            context,
            InAppNotificationItem(
                title = title,
                message = shortContent,
                category = "Orders",
                deepLinkAction = "TRACK_ORDER:$orderId"
            )
        )

        if (!hasPostNotificationPermission(context)) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_action", "TRACK_ORDER")
            putExtra("notification_order_id", orderId)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            orderId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, NotificationChannelHelper.CHANNEL_ORDER_UPDATES)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setColor(BRAND_EMERALD_COLOR)
            .setContentTitle(title)
            .setContentText(shortContent)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(bigContent)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        getBrandBadgeBitmap(context)?.let {
            builder.setLargeIcon(it)
        }

        if (upperStatus != "DELIVERED" && upperStatus != "CANCELLED") {
            val trackIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_action", "TRACK_ORDER")
                putExtra("notification_order_id", orderId)
            }
            val trackPendingIntent = PendingIntent.getActivity(
                context,
                (orderId + "_track").hashCode(),
                trackIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            builder.addAction(R.drawable.ic_notification_small, "⚡ Track Live", trackPendingIntent)
        } else if (upperStatus == "DELIVERED") {
            val reorderIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("notification_action", "REORDER")
                putExtra("notification_order_id", orderId)
            }
            val reorderPendingIntent = PendingIntent.getActivity(
                context,
                (orderId + "_reorder").hashCode(),
                reorderIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            builder.addAction(R.drawable.ic_notification_small, "🔄 Reorder", reorderPendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(orderId.hashCode(), builder.build())
        } catch (_: SecurityException) {}
    }

    /**
     * Posts Refill Reminder notification with 1-click reorder action.
     */
    fun postRefillNotification(
        context: Context,
        medicineName: String,
        sku: String,
        daysRemaining: Int = 3
    ) {
        if (!NotificationPreferenceManager.isRefillAlertsEnabled(context)) return

        NotificationChannelHelper.createChannels(context)

        val title = "⏰ Refill Alert: $medicineName"
        val message = "Your supply of $medicineName is estimated to run low in $daysRemaining days. Reorder today for uninterrupted dosage."
        val bigMessage = buildString {
            append("Refill Alert: $medicineName\n")
            append("📅 Supply Status: Estimated ~${daysRemaining} days remaining.\n")
            append("⚡ Express Delivery: 10-15 minute delivery from your nearest dark store pharmacy.\n")
            append("Tap below to add to cart instantly.")
        }

        LocalNotificationStorage.saveNotification(
            context,
            InAppNotificationItem(
                title = title,
                message = message,
                category = "Refills",
                deepLinkAction = if (sku.isNotBlank()) "REFILL_CART:$sku" else "CART"
            )
        )

        if (!hasPostNotificationPermission(context)) return

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_action", "REFILL_CART")
            putExtra("notification_sku", sku)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            sku.hashCode(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val builder = NotificationCompat.Builder(context, NotificationChannelHelper.CHANNEL_REFILL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setColor(BRAND_EMERALD_COLOR)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(bigMessage)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification_small, "🛒 Reorder Now", pendingIntent)

        getBrandBadgeBitmap(context)?.let {
            builder.setLargeIcon(it)
        }

        try {
            NotificationManagerCompat.from(context).notify(sku.hashCode(), builder.build())
        } catch (_: SecurityException) {}
    }
}
