package com.commerceos.android.data.local

import android.content.Context
import com.commerceos.android.model.CartItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object LocalSaveForLaterStore {
    private const val PREFS_NAME = "commerce_os_save_for_later"
    private const val KEY_PREFIX = "saved_items_"
    private val gson = Gson()

    private fun key(customerId: String): String =
        KEY_PREFIX + customerId.trim().ifBlank { "guest" }

    fun getSavedItems(context: Context, customerId: String): List<CartItem> {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(key(customerId), null) ?: return emptyList()
            val type = object : TypeToken<List<CartItem>>() {}.type
            gson.fromJson<List<CartItem>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveItems(context: Context, customerId: String, items: List<CartItem>) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (items.isEmpty()) {
                prefs.edit().remove(key(customerId)).apply()
            } else {
                val json = gson.toJson(items)
                prefs.edit().putString(key(customerId), json).apply()
            }
        } catch (_: Exception) {}
    }
}
