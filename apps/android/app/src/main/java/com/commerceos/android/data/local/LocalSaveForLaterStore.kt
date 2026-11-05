package com.commerceos.android.data.local

import android.content.Context
import android.content.SharedPreferences
import com.commerceos.android.model.CartItem
import com.commerceos.android.session.SecureCredentialStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object LocalSaveForLaterStore {
    private const val PREFS_NAME = "commerce_os_save_for_later"
    private const val SECURE_KEY_PREFIX = "saved_items_sec_"
    private const val LEGACY_KEY_PREFIX = "saved_items_"
    private const val PENDING_MOVE_KEY_PREFIX = "pending_move_key_"
    private const val PENDING_ADD_PREFIX = "pending_add_key_"
    private const val PENDING_REORDER_ITEM_PREFIX = "pending_reorder_item_key_"
    private const val PENDING_REORDER_BATCH_PREFIX = "pending_reorder_batch_"
    private const val UNCERTAIN_MOVES_KEY_PREFIX = "uncertain_moves_"
    private val gson = Gson()

    internal var secureStoreProvider: ((Context) -> SecureCredentialStore)? = null

    data class ReorderBatchRecord(
        val batchId: String,
        val successfulSkus: Set<String>
    )

    private fun secureKey(customerId: String): String =
        SECURE_KEY_PREFIX + customerId.trim().ifBlank { "guest" }

    private fun legacyKey(customerId: String): String =
        LEGACY_KEY_PREFIX + customerId.trim().ifBlank { "guest" }

    private fun commitWithRetry(editor: SharedPreferences.Editor, maxAttempts: Int = 3): Boolean {
        for (attempt in 1..maxAttempts) {
            if (editor.commit()) return true
        }
        return false
    }

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
                    if (migrated) {
                        val cleaned = commitWithRetry(prefs.edit().remove(legKey))
                        if (cleaned) {
                            legacyJson
                        } else {
                            // Fail closed: Never return plaintext unencrypted data when legacy cleanup fails
                            null
                        }
                    } else {
                        // Fail closed: Never return plaintext unencrypted data when secure migration fails
                        null
                    }
                } else null
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
                val cleared = secureStore.clear(key)
                if (!cleared) return false
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                if (prefs.getString(legKey, null) != null) {
                    commitWithRetry(prefs.edit().remove(legKey))
                } else {
                    true
                }
            } else {
                val json = gson.toJson(items)
                val savedSecurely = secureStore.save(key, json)
                if (savedSecurely) {
                    // Clean legacy if it existed
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    if (prefs.getString(legKey, null) != null) {
                        commitWithRetry(prefs.edit().remove(legKey))
                    } else {
                        true
                    }
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
        } catch (_: Exception) {
            false
        }
    }

    fun getPendingAddKey(context: Context, customerId: String, sku: String): String? {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.read(PENDING_ADD_PREFIX + "${safeId}_$sku")
        } catch (_: Exception) {
            null
        }
    }

    fun setPendingAddKey(context: Context, customerId: String, sku: String, key: String): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.save(PENDING_ADD_PREFIX + "${safeId}_$sku", key)
        } catch (_: Exception) {
            false
        }
    }

    fun clearPendingAddKey(context: Context, customerId: String, sku: String): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.clear(PENDING_ADD_PREFIX + "${safeId}_$sku")
        } catch (_: Exception) {
            false
        }
    }

    fun getPendingReorderItemKey(context: Context, customerId: String, sku: String): String? {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.read(PENDING_REORDER_ITEM_PREFIX + "${safeId}_$sku")
        } catch (_: Exception) {
            null
        }
    }

    fun setPendingReorderItemKey(context: Context, customerId: String, sku: String, key: String): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.save(PENDING_REORDER_ITEM_PREFIX + "${safeId}_$sku", key)
        } catch (_: Exception) {
            false
        }
    }

    fun clearPendingReorderItemKey(context: Context, customerId: String, sku: String): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.clear(PENDING_REORDER_ITEM_PREFIX + "${safeId}_$sku")
        } catch (_: Exception) {
            false
        }
    }

    fun getPendingReorderBatch(context: Context, customerId: String, batchSignature: String): ReorderBatchRecord? {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            val json = secureStore.read(PENDING_REORDER_BATCH_PREFIX + "${safeId}_$batchSignature") ?: return null
            gson.fromJson(json, ReorderBatchRecord::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun setPendingReorderBatch(context: Context, customerId: String, batchSignature: String, record: ReorderBatchRecord): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.save(PENDING_REORDER_BATCH_PREFIX + "${safeId}_$batchSignature", gson.toJson(record))
        } catch (_: Exception) {
            false
        }
    }

    fun clearPendingReorderBatch(context: Context, customerId: String, batchSignature: String): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            secureStore.clear(PENDING_REORDER_BATCH_PREFIX + "${safeId}_$batchSignature")
        } catch (_: Exception) {
            false
        }
    }

    fun getUncertainMoveSkus(context: Context, customerId: String): Set<String> {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            val json = secureStore.read(UNCERTAIN_MOVES_KEY_PREFIX + safeId) ?: return emptySet()
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson<Set<String>>(json, type) ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setUncertainMoveSkus(context: Context, customerId: String, skus: Set<String>): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            val key = UNCERTAIN_MOVES_KEY_PREFIX + safeId
            if (skus.isEmpty()) {
                secureStore.clear(key)
            } else {
                val json = gson.toJson(skus)
                secureStore.save(key, json)
            }
        } catch (_: Exception) {
            false
        }
    }

    fun addUncertainMoveSku(context: Context, customerId: String, sku: String): Boolean {
        val existing = getUncertainMoveSkus(context, customerId).toMutableSet()
        existing.add(sku)
        return setUncertainMoveSkus(context, customerId, existing)
    }

    fun removeUncertainMoveSku(context: Context, customerId: String, sku: String): Boolean {
        val existing = getUncertainMoveSkus(context, customerId).toMutableSet()
        existing.remove(sku)
        return setUncertainMoveSkus(context, customerId, existing)
    }
}
