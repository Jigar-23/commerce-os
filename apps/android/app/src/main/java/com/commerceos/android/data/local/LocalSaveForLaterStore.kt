package com.commerceos.android.data.local

import android.content.Context
import com.commerceos.android.model.CartItem
import com.commerceos.android.session.SecureCredentialStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object LocalSaveForLaterStore {
    private const val PREFS_NAME = "commerce_os_save_for_later"
    private const val SECURE_KEY_PREFIX = "saved_items_sec_"
    private const val LEGACY_KEY_PREFIX = "saved_items_"
    private const val PENDING_MOVE_KEY_PREFIX = "pending_move_key_"
    private val gson = Gson()

    internal var secureStoreProvider: ((Context) -> SecureCredentialStore)? = null

    private fun secureKey(customerId: String): String =
        SECURE_KEY_PREFIX + customerId.trim().ifBlank { "guest" }

    private fun legacyKey(customerId: String): String =
        LEGACY_KEY_PREFIX + customerId.trim().ifBlank { "guest" }

    fun getSavedItems(context: Context, customerId: String): List<CartItem> {
        return try {
            val key = secureKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            val json = secureStore.read(key) ?: run {
                // Check legacy/plaintext prefs for migration
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val legKey = legacyKey(customerId)
                val legacyJson = prefs.getString(legKey, null)
                if (legacyJson != null) {
                    val migrated = secureStore.save(key, legacyJson)
                    if (migrated) prefs.edit().remove(legKey).commit()
                }
                legacyJson
            } ?: return emptyList()

            val type = object : TypeToken<List<CartItem>>() {}.type
            gson.fromJson<List<CartItem>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveItems(context: Context, customerId: String, items: List<CartItem>): Boolean {
        return try {
            val key = secureKey(customerId)
            val legKey = legacyKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            if (items.isEmpty()) {
                secureStore.clear(key)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(legKey).commit()
                true
            } else {
                val json = gson.toJson(items)
                val savedSecurely = secureStore.save(key, json)
                if (savedSecurely) {
                    // Clean legacy if it existed
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().remove(legKey).commit()
                    true
                } else {
                    // FAIL CLOSED: Never persist sensitive medical/cart items in plaintext
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getPendingMoveKey(context: Context, customerId: String, sku: String): String? {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.read(PENDING_MOVE_KEY_PREFIX + "${safeId}_$sku")
        } catch (_: Exception) {
            null
        }
    }

    fun setPendingMoveKey(context: Context, customerId: String, sku: String, key: String): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.save(PENDING_MOVE_KEY_PREFIX + "${safeId}_$sku", key)
        } catch (_: Exception) {
            false
        }
    }

    fun clearPendingMoveKey(context: Context, customerId: String, sku: String): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.clear(PENDING_MOVE_KEY_PREFIX + "${safeId}_$sku")
            true
        } catch (_: Exception) {
            false
        }
    }
}
