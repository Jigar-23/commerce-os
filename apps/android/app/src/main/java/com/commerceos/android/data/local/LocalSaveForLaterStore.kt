package com.commerceos.android.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.commerceos.android.model.CartItem
import com.commerceos.android.session.SecureCredentialStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object LocalSaveForLaterStore {
    private const val TAG = "LocalSaveForLaterStore"
    private const val PREFS_NAME = "commerce_os_save_for_later"
    private const val SECURE_KEY_PREFIX = "saved_items_sec_"
    private const val LEGACY_KEY_PREFIX = "saved_items_"
    private const val PENDING_MOVE_KEY_PREFIX = "pending_move_key_"
    private const val PENDING_ADD_PREFIX = "pending_add_key_"
    private const val PENDING_REORDER_ITEM_PREFIX = "pending_reorder_item_key_"
    private const val PENDING_REORDER_BATCH_PREFIX = "pending_reorder_batch_"
    private const val UNCERTAIN_MOVES_KEY_PREFIX = "uncertain_moves_"
    private const val PENDING_CART_MUTATIONS_PREFIX = "pending_cart_mutations_"
    private val gson = Gson()

    internal var secureStoreProvider: ((Context) -> SecureCredentialStore)? = null

    enum class CartOpType { ADD, UPDATE_QTY, REMOVE }

    data class PendingCartMutation(
        val opId: String,
        val type: CartOpType,
        val customerId: String,
        val sku: String,
        val quantity: Int = 1,
        val item: CartItem? = null,
        val idempotencyKey: String,
        val createdAt: Long = System.currentTimeMillis()
    )

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

    sealed interface StorageResult<out T> {
        data class Success<T>(val data: T) : StorageResult<T>
        object NotFound : StorageResult<Nothing>
        data class DecryptError(val cause: Throwable) : StorageResult<Nothing>
    }

    fun getSavedItemsWithResult(context: Context, customerId: String): StorageResult<List<CartItem>> {
        return try {
            val key = secureKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            when (val check = secureStore.readWithResult(key)) {
                is SecureCredentialStore.SecureReadResult.Success -> {
                    val type = object : TypeToken<List<CartItem>>() {}.type
                    val list: List<CartItem> = gson.fromJson(check.value, type) ?: emptyList()
                    StorageResult.Success(list)
                }
                is SecureCredentialStore.SecureReadResult.DecryptError -> {
                    StorageResult.DecryptError(check.cause)
                }
                is SecureCredentialStore.SecureReadResult.NotFound -> {
                    // Check legacy/plaintext prefs for migration ONLY on NotFound
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val legKey = legacyKey(customerId)
                    val legacyJson = prefs.getString(legKey, null)
                    if (legacyJson != null) {
                        val migrated = secureStore.save(key, legacyJson)
                        if (migrated) {
                            val cleaned = commitWithRetry(prefs.edit().remove(legKey))
                            if (cleaned) {
                                val type = object : TypeToken<List<CartItem>>() {}.type
                                val list: List<CartItem> = gson.fromJson(legacyJson, type) ?: emptyList()
                                StorageResult.Success(list)
                            } else {
                                StorageResult.DecryptError(IllegalStateException("Could not clean legacy plaintext cart items"))
                            }
                        } else {
                            StorageResult.DecryptError(IllegalStateException("Could not migrate legacy cart items to encrypted store"))
                        }
                    } else {
                        StorageResult.NotFound
                    }
                }
            }
        } catch (e: Exception) {
            StorageResult.DecryptError(e)
        }
    }

    fun getSavedItems(context: Context, customerId: String): List<CartItem> {
        return when (val res = getSavedItemsWithResult(context, customerId)) {
            is StorageResult.Success -> res.data
            else -> emptyList()
        }
    }

    fun saveItems(context: Context, customerId: String, items: List<CartItem>): Boolean {
        return try {
            val key = secureKey(customerId)
            val legKey = legacyKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)

            // If ciphertext has DecryptError, preserve existing ciphertext to prevent permanent data loss!
            val currentCheck = secureStore.readWithResult(key)
            if (currentCheck is SecureCredentialStore.SecureReadResult.DecryptError) {
                Log.e(TAG, "Decryption error for storageKey=$key; preserving existing ciphertext to prevent data loss")
                return false
            }

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

    data class PendingAddRecord(val key: String, val item: CartItem? = null)

    fun getPendingAddRecord(context: Context, customerId: String, sku: String): StorageResult<PendingAddRecord> {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            when (val res = secureStore.readWithResult(PENDING_ADD_PREFIX + "${safeId}_$sku")) {
                is SecureCredentialStore.SecureReadResult.Success -> {
                    val stored = res.value
                    val record = if (stored.startsWith("{") && stored.contains("\"key\"")) {
                        gson.fromJson(stored, PendingAddRecord::class.java)
                    } else {
                        PendingAddRecord(stored, null)
                    }
                    if (record != null) StorageResult.Success(record) else StorageResult.NotFound
                }
                is SecureCredentialStore.SecureReadResult.NotFound -> StorageResult.NotFound
                is SecureCredentialStore.SecureReadResult.DecryptError -> StorageResult.DecryptError(res.cause)
            }
        } catch (e: Exception) {
            StorageResult.DecryptError(e)
        }
    }

    fun getPendingAddKey(context: Context, customerId: String, sku: String): String? {
        return when (val res = getPendingAddRecord(context, customerId, sku)) {
            is StorageResult.Success -> res.data.key
            else -> null
        }
    }

    fun setPendingAddKey(context: Context, customerId: String, sku: String, key: String, item: CartItem? = null): Boolean {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            val payload = if (item != null) gson.toJson(PendingAddRecord(key, item)) else key
            secureStore.save(PENDING_ADD_PREFIX + "${safeId}_$sku", payload)
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

    fun getUncertainMoveSkusWithResult(context: Context, customerId: String): StorageResult<Set<String>> {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            when (val res = secureStore.readWithResult(UNCERTAIN_MOVES_KEY_PREFIX + safeId)) {
                is SecureCredentialStore.SecureReadResult.Success -> {
                    val type = object : TypeToken<Set<String>>() {}.type
                    val set: Set<String> = gson.fromJson(res.value, type) ?: emptySet()
                    StorageResult.Success(set)
                }
                is SecureCredentialStore.SecureReadResult.NotFound -> StorageResult.NotFound
                is SecureCredentialStore.SecureReadResult.DecryptError -> StorageResult.DecryptError(res.cause)
            }
        } catch (e: Exception) {
            StorageResult.DecryptError(e)
        }
    }

    fun getUncertainMoveSkus(context: Context, customerId: String): Set<String> {
        return when (val res = getUncertainMoveSkusWithResult(context, customerId)) {
            is StorageResult.Success -> res.data
            else -> emptySet()
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

    private val mutationJournalLock = Any()

    fun getPendingCartMutationsWithResult(context: Context, customerId: String): StorageResult<List<PendingCartMutation>> {
        return try {
            val safeId = customerId.trim().ifBlank { "guest" }
            val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
            when (val res = secureStore.readWithResult(PENDING_CART_MUTATIONS_PREFIX + safeId)) {
                is SecureCredentialStore.SecureReadResult.Success -> {
                    val type = object : TypeToken<List<PendingCartMutation>>() {}.type
                    val list: List<PendingCartMutation>? = gson.fromJson(res.value, type)
                    StorageResult.Success(list ?: emptyList())
                }
                is SecureCredentialStore.SecureReadResult.NotFound -> StorageResult.NotFound
                is SecureCredentialStore.SecureReadResult.DecryptError -> StorageResult.DecryptError(res.cause)
            }
        } catch (e: Exception) {
            StorageResult.DecryptError(e)
        }
    }

    fun getPendingCartMutations(context: Context, customerId: String): List<PendingCartMutation> {
        return when (val res = getPendingCartMutationsWithResult(context, customerId)) {
            is StorageResult.Success -> res.data
            else -> emptyList()
        }
    }

    fun savePendingCartMutation(context: Context, customerId: String, mutation: PendingCartMutation): Boolean {
        return synchronized(mutationJournalLock) {
            try {
                val safeId = customerId.trim().ifBlank { "guest" }
                val existing = when (val res = getPendingCartMutationsWithResult(context, customerId)) {
                    is StorageResult.Success -> res.data.toMutableList()
                    is StorageResult.NotFound -> mutableListOf()
                    is StorageResult.DecryptError -> return@synchronized false
                }
                existing.removeAll { it.opId == mutation.opId || (it.type == mutation.type && it.sku == mutation.sku) }
                existing.add(mutation)
                val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
                secureStore.save(PENDING_CART_MUTATIONS_PREFIX + safeId, gson.toJson(existing))
            } catch (_: Exception) {
                false
            }
        }
    }

    fun removePendingCartMutation(context: Context, customerId: String, opId: String): Boolean {
        return synchronized(mutationJournalLock) {
            try {
                val safeId = customerId.trim().ifBlank { "guest" }
                val existing = when (val res = getPendingCartMutationsWithResult(context, customerId)) {
                    is StorageResult.Success -> res.data.toMutableList()
                    is StorageResult.NotFound -> return@synchronized true
                    is StorageResult.DecryptError -> return@synchronized false
                }
                val removed = existing.removeAll { it.opId == opId }
                if (!removed) return@synchronized true
                val secureStore = secureStoreProvider?.invoke(context) ?: SecureCredentialStore(context)
                if (existing.isEmpty()) {
                    secureStore.clear(PENDING_CART_MUTATIONS_PREFIX + safeId)
                } else {
                    secureStore.save(PENDING_CART_MUTATIONS_PREFIX + safeId, gson.toJson(existing))
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
