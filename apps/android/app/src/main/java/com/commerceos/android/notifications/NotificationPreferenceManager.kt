package com.commerceos.android.notifications

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user notification preference toggles on-device via SharedPreferences.
 * Controls whether local and incoming notifications for specific categories
 * are presented or suppressed.
 */
object NotificationPreferenceManager {
    private const val PREFS_NAME = "commerce_os_notification_preferences"
    
    // Preference keys matching the 3 healthcare alert features
    const val PREF_ORDER_DISPATCH_SLA = "pref_order_dispatch_sla_enabled"
    const val PREF_PHARMACIST_RX_REVIEW = "pref_pharmacist_rx_review_enabled"
    const val PREF_CHRONIC_REFILL_REMINDERS = "pref_chronic_refill_reminders_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOrderAlertsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(PREF_ORDER_DISPATCH_SLA, true)
    }

    fun setOrderAlertsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_ORDER_DISPATCH_SLA, enabled).apply()
    }

    fun isRxAlertsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(PREF_PHARMACIST_RX_REVIEW, true)
    }

    fun setRxAlertsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_PHARMACIST_RX_REVIEW, enabled).apply()
    }

    fun isRefillAlertsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(PREF_CHRONIC_REFILL_REMINDERS, true)
    }

    fun setRefillAlertsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(PREF_CHRONIC_REFILL_REMINDERS, enabled).apply()
    }

    /**
     * Determines whether an incoming alert for [category] should be posted or suppressed
     * based on user preference settings.
     */
    fun shouldDisplayNotification(context: Context, category: String): Boolean {
        return when (category.lowercase().trim()) {
            "orders", "order", "dispatch", "sla" -> isOrderAlertsEnabled(context)
            "prescriptions", "prescription", "rx", "pharmacist" -> isRxAlertsEnabled(context)
            "refills", "refill", "chronic" -> isRefillAlertsEnabled(context)
            else -> true
        }
    }
}
