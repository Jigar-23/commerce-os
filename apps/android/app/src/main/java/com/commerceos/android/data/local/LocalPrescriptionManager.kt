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

    private fun customerStorageKey(customerId: String): String {
        val exactRaw = customerId.trim().ifBlank { "default_user" }
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(exactRaw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return KEY_PREFIX + hash
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
        val exactRaw = customerId.trim()
        if (exactRaw.isBlank()) return emptyList()
        return getPrescriptions(context, exactRaw)
    }

    /**
     * Retrieves all prescriptions stored locally for [customerId].
     */
    fun getPrescriptions(context: Context, customerId: String): List<Prescription> {
        return try {
            val exactRaw = customerId.trim()
            if (exactRaw.isBlank()) return emptyList()

            val storageKey = customerStorageKey(exactRaw)
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val type = object : TypeToken<List<Prescription>>() {}.type

            var json = secureStore.read(storageKey)

            if (json == null) {
                // Check old sanitized encrypted key for migration with exact-ID check
                val oldSafeId = sanitizeCustomerId(exactRaw)
                val oldKey = KEY_PREFIX + oldSafeId
                if (oldKey != storageKey) {
                    val oldJson = secureStore.read(oldKey)
                    if (oldJson != null) {
                        val oldList: List<Prescription> = gson.fromJson(oldJson, type) ?: emptyList()
                        val exactMatches = oldList.filter { it.customerId == exactRaw }
                        val remaining = oldList.filterNot { it.customerId == exactRaw }
                        if (exactMatches.isNotEmpty()) {
                            val migratedJson = gson.toJson(exactMatches, type)
                            if (secureStore.save(storageKey, migratedJson)) {
                                if (remaining.isEmpty()) {
                                    secureStore.deleteKeyAndValue(oldKey)
                                } else {
                                    secureStore.save(oldKey, gson.toJson(remaining, type))
                                }
                                json = migratedJson
                            }
                        }
                    }
                }
            }

            if (json == null) {
                // Check legacy plaintext prefs for migration with exact-ID check
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val oldSafeId = sanitizeCustomerId(exactRaw)
                val legacyPrefKey = KEY_PREFIX + oldSafeId
                val legacyJson = prefs.getString(legacyPrefKey, null)
                if (legacyJson != null) {
                    val legacyList: List<Prescription> = gson.fromJson(legacyJson, type) ?: emptyList()
                    val exactMatches = legacyList.filter { it.customerId == exactRaw }
                    val remaining = legacyList.filterNot { it.customerId == exactRaw }
                    if (exactMatches.isNotEmpty()) {
                        val migratedJson = gson.toJson(exactMatches, type)
                        val saved = secureStore.save(storageKey, migratedJson)
                        if (saved) {
                            if (remaining.isEmpty()) {
                                prefs.edit().remove(legacyPrefKey).commit()
                            } else {
                                prefs.edit().putString(legacyPrefKey, gson.toJson(remaining, type)).commit()
                            }
                            json = migratedJson
                        } else {
                            // Fail closed: never return plaintext fallback if secure store save fails
                            json = null
                        }
                    }
                }
            }

            if (json.isNullOrBlank()) {
                return emptyList()
            }
            val list: List<Prescription> = gson.fromJson(json, type) ?: emptyList()
            list.filter { rx ->
                rx.customerId == exactRaw && run {
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
            val exactRaw = rx.customerId.trim()
            if (exactRaw.isBlank()) return false
            val storageKey = customerStorageKey(exactRaw)
            val existing = getPrescriptions(context, exactRaw).toMutableList()
            existing.removeAll { it.id == rx.id }
            existing.add(0, rx) // Most recent first
            val type = object : TypeToken<List<Prescription>>() {}.type
            val json = gson.toJson(existing, type)

            // Persist to Keystore-backed AES-GCM encrypted store
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val saved = secureStore.save(storageKey, json)

            if (saved) {
                // Clear any old unencrypted preference only after verified encrypted save
                val oldSafeId = sanitizeCustomerId(exactRaw)
                val legacyPrefKey = KEY_PREFIX + oldSafeId
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val legacyJson = prefs.getString(legacyPrefKey, null)
                if (legacyJson != null) {
                    val legacyList: List<Prescription> = gson.fromJson(legacyJson, type) ?: emptyList()
                    val remaining = legacyList.filterNot { it.customerId == exactRaw }
                    if (remaining.isEmpty()) {
                        prefs.edit().remove(legacyPrefKey).commit()
                    } else {
                        prefs.edit().putString(legacyPrefKey, gson.toJson(remaining, type)).commit()
                    }
                }
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
    fun deletePrescription(context: Context, customerId: String, rxId: String): Boolean {
        return try {
            val exactRaw = customerId.trim()
            if (exactRaw.isBlank()) return false
            val storageKey = customerStorageKey(exactRaw)
            val existing = getPrescriptions(context, exactRaw).toMutableList()
            val target = existing.firstOrNull { it.id == rxId } ?: return false

            existing.removeAll { it.id == rxId }
            val type = object : TypeToken<List<Prescription>>() {}.type
            val json = gson.toJson(existing, type)
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val saved = secureStore.save(storageKey, json)

            if (saved) {
                // Only delete attachment files AFTER metadata save succeeds
                // Constrain file deletion to the prescriptions directory
                val rxDirCanonical = getPrescriptionsDir(context).canonicalPath
                target.attachments.forEach { path ->
                    val cleanPath = path.removePrefix("file://")
                    if (cleanPath.startsWith("/")) {
                        try {
                            val file = File(cleanPath).canonicalFile
                            if (file.canonicalPath.startsWith(rxDirCanonical + File.separator)) {
                                file.delete()
                            }
                        } catch (_: Exception) {}
                    }
                }
                true
            } else {
                Log.e(TAG, "Failed to update prescription metadata during deletion")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete prescription locally", e)
            false
        }
    }
}
