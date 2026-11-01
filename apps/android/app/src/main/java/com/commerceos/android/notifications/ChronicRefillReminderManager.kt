package com.commerceos.android.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.commerceos.android.MainActivity
import com.commerceos.android.R
import java.util.Calendar

/**
 * On-Device Chronic Medicine Refill Reminder Scheduler.
 * Schedules local alarms entirely on the device without requiring server round-trips.
 * For chronic medications (e.g. Metformin, Telmisartan, Amlodipine), schedules an advance
 * reminder 3 days prior to supply depletion (Day 27 at 9:00 AM).
 */
object ChronicRefillReminderManager {
    private const val ACTION_REFILL_ALARM = "com.commerceos.android.ACTION_CHRONIC_REFILL_ALARM"
    const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
    const val EXTRA_MEDICINE_SKU = "extra_medicine_sku"
    const val EXTRA_SUPPLY_DAYS = "extra_supply_days"

    // Common chronic medicine identifiers/keywords for intelligent detection
    private val CHRONIC_KEYWORDS = listOf(
        "metformin", "telmisartan", "amlodipine", "atorvastatin", "glimepiride",
        "thyronorm", "losartan", "pantoprazole", "aspirin", "clopidogrel",
        "insulin", "rosuvastatin", "montelukast", "bp", "sugar", "diabetes", "hypertension"
    )

    fun isChronicMedication(name: String, sku: String): Boolean {
        val lower = (name + " " + sku).lowercase()
        return CHRONIC_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Schedules a local on-device refill reminder for [medicineName] for Day 27 at 9:00 AM
     * (or advance days based on total supply).
     */
    fun scheduleRefillReminder(
        context: Context,
        medicineName: String,
        sku: String,
        totalSupplyDays: Int = 30
    ) {
        if (!NotificationPreferenceManager.isRefillAlertsEnabled(context)) {
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val reminderDay = (totalSupplyDays - 3).coerceAtLeast(1)
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, reminderDay)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // If calculated time is in the past, schedule for tomorrow 9 AM
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.timeInMillis = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
        }

        val intent = Intent(context, RefillReminderReceiver::class.java).apply {
            action = ACTION_REFILL_ALARM
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_MEDICINE_SKU, sku)
            putExtra(EXTRA_SUPPLY_DAYS, totalSupplyDays)
        }

        val requestCode = (medicineName + sku).hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (_: Exception) {}
    }
}

/**
 * BroadcastReceiver triggered by AlarmManager on-device.
 * Posts the local notification to `channel_refill_reminders` and saves to local notification history.
 */
class RefillReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!NotificationPreferenceManager.isRefillAlertsEnabled(context)) {
            return
        }

        val medicineName = intent.getStringExtra(ChronicRefillReminderManager.EXTRA_MEDICINE_NAME) ?: "Chronic Medicine"
        val sku = intent.getStringExtra(ChronicRefillReminderManager.EXTRA_MEDICINE_SKU) ?: ""

        val title = "Refill Alert: $medicineName"
        val message = "Your $medicineName supply is estimated to run out in 3 days. Tap to reorder with 1-click express delivery."

        // 1. Record to persistent in-app notification center history
        LocalNotificationStorage.saveNotification(
            context,
            InAppNotificationItem(
                title = title,
                message = message,
                category = "Refills",
                deepLinkAction = if (sku.isNotBlank()) "REFILL_CART:$sku" else "CART"
            )
        )

        // 2. Post Android system status bar tray notification
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

        val notification = NotificationCompat.Builder(context, NotificationChannelHelper.CHANNEL_REFILL_REMINDERS)
            .setSmallIcon(R.drawable.ic_medicine)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(sku.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted
        }
    }
}
