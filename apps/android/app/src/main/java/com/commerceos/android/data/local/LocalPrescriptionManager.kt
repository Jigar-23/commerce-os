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
    internal var secureStoreProvider: ((Context) -> com.commerceos.android.session.SecureCredentialStore)? = null

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
     * Creates a temporary file and content [Uri] for hardware camera captures via FileProvider.
     * Guarantees 100% full hardware sensor resolution without thumbnail compression.
     */
    fun createCameraImageFile(context: Context): Pair<File, Uri> {
        val dir = File(context.cacheDir, "camera_photos")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "camera_rx_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(file, uri)
    }

    /**
     * Moves a full-resolution camera captured image from cache to permanent private prescription storage.
     */
    fun saveCameraCaptureFile(context: Context, sourceFile: File): File? {
        return try {
            if (!sourceFile.exists() || sourceFile.length() == 0L) return null
            val dir = getPrescriptionsDir(context)
            val fileName = "rx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val targetFile = File(dir, fileName)
            sourceFile.copyTo(targetFile, overwrite = true)
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save camera capture file", e)
            null
        }
    }

    /**
     * Saves a captured prescription [Bitmap] to the app's private files directory.
     * Retains pristine clarity for clinical readability.
     */
    fun savePrescriptionBitmap(context: Context, bitmap: Bitmap): File? {
        return try {
            val dir = getPrescriptionsDir(context)
            val fileName = "rx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val targetFile = File(dir, fileName)
            FileOutputStream(targetFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                out.flush()
            }
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prescription bitmap", e)
            null
        }
    }

    /**
     * Copies a picked gallery image [Uri] directly into app private storage with 100% original fidelity.
     */
    fun savePrescriptionUri(context: Context, uri: Uri): File? {
        return try {
            val dir = getPrescriptionsDir(context)
            val fileName = "rx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val targetFile = File(dir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            if (targetFile.exists() && targetFile.length() > 0L) {
                targetFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prescription URI", e)
            null
        }
    }

    /**
     * Retrieves prescriptions stored locally for the specified customer.
     * Enforces customer partition boundary so records from other accounts are never leaked.
     */
    fun getAllPrescriptions(context: Context, customerId: String = ""): List<Prescription> {
        val safeId = sanitizeCustomerId(customerId)
        if (safeId.isBlank()) return emptyList()
        return getPrescriptions(context, safeId)
    }

    /**
     * Retrieves all prescriptions stored locally for [customerId].
     */
    fun getPrescriptions(context: Context, customerId: String): List<Prescription> {
        return try {
            val safeId = sanitizeCustomerId(customerId)
            if (safeId.isBlank()) return emptyList()

            // Try reading encrypted storage first via SecureCredentialStore
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val json = secureStore.read(KEY_PREFIX + safeId)
                ?: run {
                    // Fallback / migration from legacy plaintext prefs
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.getString(KEY_PREFIX + safeId, null)?.also { legacyJson ->
                        val migrated = secureStore.save(KEY_PREFIX + safeId, legacyJson)
                        if (migrated) {
                            prefs.edit().remove(KEY_PREFIX + safeId).commit()
                        }
                    }
                }

            if (json.isNullOrBlank()) {
                return emptyList()
            }
            val type = object : TypeToken<List<Prescription>>() {}.type
            val list: List<Prescription> = gson.fromJson(json, type) ?: emptyList()
            list.filter { rx ->
                (rx.customerId == customerId || rx.customerId == safeId) && run {
                    val firstAtt = rx.attachments.firstOrNull()
                    if (firstAtt != null && (firstAtt.startsWith("/") || firstAtt.startsWith("file:"))) {
                        val path = firstAtt.removePrefix("file://")
                        File(path).exists()
                    } else {
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local prescriptions", e)
            emptyList()
        }
    }

    /**
     * Stores or updates a [Prescription] in the device app's personal encrypted storage.
     * Returns true if encrypted persistence succeeded; preserves legacy data and returns false on failure.
     */
    fun savePrescription(context: Context, rx: Prescription): Boolean {
        return try {
            val safeId = sanitizeCustomerId(rx.customerId)
            if (safeId.isBlank()) return false
            val existing = getPrescriptions(context, rx.customerId).toMutableList()
            existing.removeAll { it.id == rx.id }
            existing.add(0, rx) // Most recent first
            val type = object : TypeToken<List<Prescription>>() {}.type
            val json = gson.toJson(existing, type)

            // Persist to Keystore-backed AES-GCM encrypted store
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val saved = secureStore.save(KEY_PREFIX + safeId, json)

            if (saved) {
                // Clear any old unencrypted preference only after verified encrypted save
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(KEY_PREFIX + safeId).commit()
                true
            } else {
                Log.e(TAG, "Failed to securely save prescription")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prescription locally", e)
            false
        }
    }

    /**
     * Removes a prescription and its associated local file from device storage.
     */
    fun deletePrescription(context: Context, customerId: String, rxId: String) {
        try {
            val safeId = sanitizeCustomerId(customerId)
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
                val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
                secureStore.save(KEY_PREFIX + safeId, json)
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
