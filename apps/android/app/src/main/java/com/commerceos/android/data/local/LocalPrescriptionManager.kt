package com.commerceos.android.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.commerceos.android.model.Prescription
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Manages customer prescriptions entirely within the app's personal local storage.
 * Physical prescription slips and camera captures are saved in `context.filesDir/prescriptions/`,
 * completely eliminating backend memory overhead, cloud file hosting bills, and large Base64 payloads.
 */
object LocalPrescriptionManager {
    private const val TAG = "LocalRxManager"
    private const val PREFS_NAME = "commerce_os_local_prescriptions"
    private const val KEY_PREFIX = "rx_records_"
    private const val PRESCRIPTIONS_DIR = "prescriptions"

    private val gson = Gson()

    private fun sanitizeCustomerId(customerId: String): String {
        return customerId.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "default_user" }
    }

    fun getPrescriptionsDir(context: Context): File {
        val dir = File(context.filesDir, PRESCRIPTIONS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Saves a captured prescription [Bitmap] to the app's private files directory.
     * Retains high clarity for medical readability while optimizing byte size.
     */
    fun savePrescriptionBitmap(context: Context, bitmap: Bitmap): File? {
        return try {
            val dir = getPrescriptionsDir(context)
            val fileName = "rx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val targetFile = File(dir, fileName)
            val scaled = scaleDownBitmap(bitmap, 1600)
            FileOutputStream(targetFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.flush()
            }
            if (scaled != bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prescription bitmap", e)
            null
        }
    }

    /**
     * Copies and compresses a picked gallery image [Uri] into app private storage.
     */
    fun savePrescriptionUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val decoded = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (decoded != null) {
                val savedFile = savePrescriptionBitmap(context, decoded)
                if (!decoded.isRecycled) {
                    decoded.recycle()
                }
                savedFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prescription URI", e)
            null
        }
    }

    /**
     * Retrieves all prescriptions stored locally for [customerId].
     */
    fun getPrescriptions(context: Context, customerId: String): List<Prescription> {
        return try {
            val safeId = sanitizeCustomerId(customerId)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(KEY_PREFIX + safeId, null) ?: return emptyList()
            val type = object : TypeToken<List<Prescription>>() {}.type
            val list: List<Prescription> = gson.fromJson(json, type) ?: emptyList()
            // Filter to only prescriptions whose attachment files exist on disk if they point locally
            list.filter { rx ->
                val firstAtt = rx.attachments.firstOrNull()
                if (firstAtt != null && (firstAtt.startsWith("/") || firstAtt.startsWith("file:"))) {
                    val path = firstAtt.removePrefix("file://")
                    File(path).exists()
                } else {
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local prescriptions", e)
            emptyList()
        }
    }

    /**
     * Stores or updates a [Prescription] in the device app's personal storage.
     */
    fun savePrescription(context: Context, rx: Prescription) {
        try {
            val safeId = sanitizeCustomerId(rx.customerId)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = getPrescriptions(context, rx.customerId).toMutableList()
            existing.removeAll { it.id == rx.id }
            existing.add(0, rx) // Most recent first
            val type = object : TypeToken<List<Prescription>>() {}.type
            val json = gson.toJson(existing, type)
            prefs.edit().putString(KEY_PREFIX + safeId, json).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prescription locally", e)
        }
    }

    /**
     * Removes a prescription and its associated local file from device storage.
     */
    fun deletePrescription(context: Context, customerId: String, rxId: String) {
        try {
            val safeId = sanitizeCustomerId(customerId)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = getPrescriptions(context, customerId).toMutableList()
            val target = existing.firstOrNull { it.id == rxId }
            if (target != null) {
                target.attachments.forEach { path ->
                    val cleanPath = path.removePrefix("file://")
                    if (cleanPath.startsWith("/")) {
                        try { File(cleanPath).delete() } catch (_: Exception) {}
                    }
                }
                existing.removeAll { it.id == rxId }
                val type = object : TypeToken<List<Prescription>>() {}.type
                val json = gson.toJson(existing, type)
                prefs.edit().putString(KEY_PREFIX + safeId, json).apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete prescription locally", e)
        }
    }

    private fun scaleDownBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val width = src.width
        val height = src.height
        if (width <= maxDim && height <= maxDim) return src

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDim
            newHeight = (maxDim / ratio).roundToInt().coerceAtLeast(1)
        } else {
            newHeight = maxDim
            newWidth = (maxDim * ratio).roundToInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(src, newWidth, newHeight, true)
    }
}
