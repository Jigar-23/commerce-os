package com.commerceos.rider.util

import android.content.Context
import com.commerceos.rider.model.RiderNotificationItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * On-device local persistent notification database.
 * Silently ingests notifications when the rider is offline, and persists all alerts for the Alerts Hub.
 */
object RiderLocalNotificationStore {

    private const val PREFS_NAME = "commerce_rider_local_notifications"
    private const val KEY_NOTIFICATIONS_JSON = "notifications_json_array"
    private const val MAX_ENTRIES = 100

    @Synchronized
    fun saveNotification(context: Context, item: RiderNotificationItem) {
        val currentList = getStoredNotifications(context).toMutableList()
        val existingIndex = currentList.indexOfFirst {
            it.notificationId == item.notificationId ||
                (!it.offerId.isNullOrBlank() && it.offerId == item.offerId)
        }

        if (existingIndex >= 0) {
            currentList[existingIndex] = item
        } else {
            currentList.add(0, item)
        }

        val trimmed = if (currentList.size > MAX_ENTRIES) currentList.take(MAX_ENTRIES) else currentList
        writeListToPrefs(context, trimmed)
    }

    @Synchronized
    fun getStoredNotifications(context: Context): List<RiderNotificationItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_NOTIFICATIONS_JSON, null) ?: return emptyList()

        val result = mutableListOf<RiderNotificationItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    RiderNotificationItem(
                        notificationId = obj.optString("notificationId"),
                        eventId = obj.optString("eventId"),
                        type = obj.optString("type", "ORDER_OFFER"),
                        category = obj.optString("category", "ORDERS"),
                        priority = obj.optString("priority", "HIGH"),
                        riderId = obj.optString("riderId"),
                        orderId = obj.optString("orderId").takeIf { it.isNotBlank() },
                        deliveryId = obj.optString("deliveryId").takeIf { it.isNotBlank() },
                        offerId = obj.optString("offerId").takeIf { it.isNotBlank() },
                        title = obj.optString("title"),
                        body = obj.optString("body"),
                        deepLink = obj.optString("deepLink").takeIf { it.isNotBlank() },
                        createdAt = obj.optString("createdAt"),
                        expiresAt = if (obj.has("expiresAt") && !obj.isNull("expiresAt")) obj.optLong("expiresAt") else null,
                        readAt = obj.optString("readAt").takeIf { it.isNotBlank() },
                        offerStatus = obj.optString("offerStatus").takeIf { it.isNotBlank() },
                        orderStatus = obj.optString("orderStatus").takeIf { it.isNotBlank() },
                        orderRiderId = obj.optString("orderRiderId").takeIf { it.isNotBlank() },
                        isAvailable = if (obj.has("isAvailable")) obj.optBoolean("isAvailable") else true
                    )
                )
            }
        } catch (_: Exception) {}
        return result
    }

    @Synchronized
    fun markAsRead(context: Context, notificationId: String) {
        val current = getStoredNotifications(context).toMutableList()
        val index = current.indexOfFirst { it.notificationId == notificationId }
        if (index >= 0) {
            current[index] = current[index].copy(readAt = "read")
            writeListToPrefs(context, current)
        }
    }

    @Synchronized
    fun markAllAsRead(context: Context) {
        val current = getStoredNotifications(context).map { it.copy(readAt = "read") }
        writeListToPrefs(context, current)
    }

    private fun writeListToPrefs(context: Context, list: List<RiderNotificationItem>) {
        try {
            val array = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("notificationId", item.notificationId)
                    put("eventId", item.eventId)
                    put("type", item.type)
                    put("category", item.category)
                    put("priority", item.priority)
                    put("riderId", item.riderId)
                    put("orderId", item.orderId ?: "")
                    put("deliveryId", item.deliveryId ?: "")
                    put("offerId", item.offerId ?: "")
                    put("title", item.title)
                    put("body", item.body)
                    put("deepLink", item.deepLink ?: "")
                    put("createdAt", item.createdAt)
                    if (item.expiresAt != null) put("expiresAt", item.expiresAt)
                    if (item.readAt != null) put("readAt", item.readAt)
                    if (item.offerStatus != null) put("offerStatus", item.offerStatus)
                    if (item.orderStatus != null) put("orderStatus", item.orderStatus)
                    if (item.orderRiderId != null) put("orderRiderId", item.orderRiderId)
                    if (item.isAvailable != null) put("isAvailable", item.isAvailable)
                }
                array.put(obj)
            }
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_NOTIFICATIONS_JSON, array.toString())
                .apply()
        } catch (_: Exception) {}
    }
}
