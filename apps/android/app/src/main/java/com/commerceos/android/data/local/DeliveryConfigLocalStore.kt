package com.commerceos.android.data.local

import android.content.Context
import com.commerceos.android.network.DeliveryConfigResponse
import com.commerceos.android.network.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/**
 * Local storage / database cache for store and app delivery policies.
 * Caches free delivery threshold and delivery charge with a 30-day (1 month) refresh cadence.
 */
object DeliveryConfigLocalStore {
    private const val PREFS_NAME = "commerce_os_delivery_config"
    private const val KEY_FREE_THRESHOLD = "free_delivery_threshold"
    private const val KEY_STANDARD_FEE = "standard_delivery_fee"
    private const val KEY_EXPRESS_FEE = "express_delivery_fee"
    private const val KEY_COLD_CHAIN_FEE = "cold_chain_fee"
    private const val KEY_LAST_SYNCED = "last_synced_timestamp"

    // 30 days in milliseconds (~1 month query cadence)
    private const val REFRESH_INTERVAL_MS = 30L * 24 * 60 * 60 * 1000

    @Volatile
    private var cachedThreshold: BigDecimal? = null

    @Volatile
    private var cachedFee: BigDecimal? = null

    fun getFreeDeliveryThreshold(context: Context?): BigDecimal {
        cachedThreshold?.let { return it }
        if (context == null) return BigDecimal("199.00")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val thresholdVal = prefs.getFloat(KEY_FREE_THRESHOLD, 199.0f).toDouble()
        val bd = BigDecimal.valueOf(thresholdVal).setScale(2, java.math.RoundingMode.HALF_UP)
        cachedThreshold = bd
        return bd
    }

    fun getStandardDeliveryFee(context: Context?): BigDecimal {
        cachedFee?.let { return it }
        if (context == null) return BigDecimal("2.00")
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val feeVal = prefs.getFloat(KEY_STANDARD_FEE, 2.0f).toDouble()
        val bd = BigDecimal.valueOf(feeVal).setScale(2, java.math.RoundingMode.HALF_UP)
        cachedFee = bd
        return bd
    }

    fun shouldRefresh(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSynced = prefs.getLong(KEY_LAST_SYNCED, 0L)
        return (System.currentTimeMillis() - lastSynced) > REFRESH_INTERVAL_MS
    }

    fun saveConfig(context: Context, config: DeliveryConfigResponse) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat(KEY_FREE_THRESHOLD, config.freeDeliveryThreshold.toFloat())
            .putFloat(KEY_STANDARD_FEE, config.standardDeliveryFee.toFloat())
            .putFloat(KEY_EXPRESS_FEE, config.expressDeliveryFee.toFloat())
            .putFloat(KEY_COLD_CHAIN_FEE, config.coldChainPackagingFee.toFloat())
            .putLong(KEY_LAST_SYNCED, System.currentTimeMillis())
            .apply()

        cachedThreshold = BigDecimal.valueOf(config.freeDeliveryThreshold).setScale(2, java.math.RoundingMode.HALF_UP)
        cachedFee = BigDecimal.valueOf(config.standardDeliveryFee).setScale(2, java.math.RoundingMode.HALF_UP)
    }

    suspend fun syncIfStale(context: Context, force: Boolean = false) {
        if (!force && !shouldRefresh(context)) return
        withContext(Dispatchers.IO) {
            try {
                val resp = NetworkClient.promotionsApi.getDeliveryConfig()
                saveConfig(context, resp)
            } catch (_: Exception) {
                // Keep local cache offline
            }
        }
    }
}
