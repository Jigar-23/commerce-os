package com.commerceos.android.notifications

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class InAppNotificationItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val category: String, // "Orders", "Prescriptions", "Refills", "Offers"
    val timestamp: Long = System.currentTimeMillis(),
    val formattedTime: String = formatTimestamp(System.currentTimeMillis()),
    val isRead: Boolean = false,
    val deepLinkAction: String? = null // e.g. "ORDERS", "PRESCRIPTIONS", "REFILL_CART"
) {
    companion object {
        fun formatTimestamp(millis: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - millis
            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                diff < 86400_000 -> "${diff / 3600_000}h ago"
                else -> SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(millis))
            }
        }
    }
}

/**
 * Manages persistent in-app notification center history in device local storage.
 * Eliminates fake/hardcoded clutter and stores genuine user notifications locally
 * for instantaneous offline-first display.
 */
object LocalNotificationStorage {
    private const val PREFS_NAME = "commerce_os_in_app_notifications"
    private const val KEY_NOTIFICATIONS = "stored_notifications_list"
    private const val MAX_STORED_NOTIFICATIONS = 50

    private val gson = Gson()

    fun getNotifications(context: Context): List<InAppNotificationItem> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_NOTIFICATIONS, null) ?: return emptyList()
            val type = object : TypeToken<List<InAppNotificationItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveNotification(context: Context, item: InAppNotificationItem) {
        try {
            val existing = getNotifications(context).toMutableList()
            existing.removeAll { it.id == item.id }
            existing.add(0, item) // Insert newest first
            val trimmed = if (existing.size > MAX_STORED_NOTIFICATIONS) {
                existing.take(MAX_STORED_NOTIFICATIONS)
            } else {
                existing
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(trimmed)).apply()
        } catch (_: Exception) {}
    }

    fun markAsRead(context: Context, notificationId: String) {
        try {
            val updated = getNotifications(context).map {
                if (it.id == notificationId) it.copy(isRead = true) else it
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(updated)).apply()
        } catch (_: Exception) {}
    }

    fun markAllAsRead(context: Context) {
        try {
            val updated = getNotifications(context).map { it.copy(isRead = true) }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(updated)).apply()
        } catch (_: Exception) {}
    }

    fun clearAll(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_NOTIFICATIONS).apply()
        } catch (_: Exception) {}
    }

    fun deleteNotification(context: Context, notificationId: String) {
        try {
            val updated = getNotifications(context).filterNot { it.id == notificationId }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_NOTIFICATIONS, gson.toJson(updated)).apply()
        } catch (_: Exception) {}
    }
}
