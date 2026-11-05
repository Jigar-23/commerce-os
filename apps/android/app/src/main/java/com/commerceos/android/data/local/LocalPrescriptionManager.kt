package com.commerceos.android.data.local

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.commerceos.android.data.local.LocalSaveForLaterStore.StorageResult
import com.commerceos.android.model.Prescription
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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
    private const val ORPHAN_CLEANUP_PREFS = "commerce_os_rx_orphan_cleanup"
    private const val KEY_PENDING_ORPHAN_FILES = "pending_orphan_files"
    private const val PENDING_UPLOAD_PREFIX = "pending_rx_upload_"
    private const val DRAFT_KEY = "rx_upload_draft"

    data class PrescriptionDraft(
        val patientName: String = "",
        val attachmentPath: String? = null,
        val attachmentName: String? = null
    )

    private val gson = Gson()
    internal var secureStoreProvider: ((Context) -> com.commerceos.android.session.SecureCredentialStore)? = null

    data class PendingUploadRecord(
        val idempotencyKey: String,
        val fingerprint: String
    )

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

    private const val ATTACHMENT_KEY_ALIAS = "rx_attachment_vault_key"
    private const val MAX_ATTACHMENT_BYTES = 5 * 1024 * 1024L

    /**
     * Moves a full-resolution camera captured image to permanent encrypted private prescription storage.
     */
    fun saveCameraCaptureFile(context: Context, sourceFile: File): File? {
        return try {
            if (!sourceFile.exists() || sourceFile.length() == 0L || sourceFile.length() > MAX_ATTACHMENT_BYTES) return null
            val dir = getPrescriptionsDir(context)
            val fileName = "rx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val tempFile = File(dir, "$fileName.tmp")
            val targetFile = File(dir, fileName)

            val rawBytes = sourceFile.readBytes()
            if (rawBytes.isEmpty() || rawBytes.size > MAX_ATTACHMENT_BYTES) return null

            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val encryptedBytes = secureStore.encryptBytes(ATTACHMENT_KEY_ALIAS, rawBytes) ?: run {
                Log.e(TAG, "Encryption failed; failing closed to prevent plaintext storage")
                return null
            }

            FileOutputStream(tempFile).use { out ->
                out.write(encryptedBytes)
                out.flush()
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (tempFile.renameTo(targetFile)) {
                    targetFile
                } else {
                    tempFile.delete()
                    null
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save camera capture file", e)
            null
        }
    }

    /**
     * Saves a captured prescription [Bitmap] to the app's encrypted private files directory.
     * Retains pristine clarity for clinical readability.
     */
    fun savePrescriptionBitmap(context: Context, bitmap: Bitmap): File? {
        return try {
            val dir = getPrescriptionsDir(context)
            val fileName = "rx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val tempFile = File(dir, "$fileName.tmp")
            val targetFile = File(dir, fileName)

            val byteStream = ByteArrayOutputStream()
            val compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 95, byteStream)
            if (!compressed) return null
            val rawBytes = byteStream.toByteArray()
            if (rawBytes.isEmpty() || rawBytes.size > MAX_ATTACHMENT_BYTES) return null

            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val encryptedBytes = secureStore.encryptBytes(ATTACHMENT_KEY_ALIAS, rawBytes) ?: run {
                Log.e(TAG, "Encryption failed; failing closed to prevent plaintext storage")
                return null
            }

            FileOutputStream(tempFile).use { out ->
                out.write(encryptedBytes)
                out.flush()
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (tempFile.renameTo(targetFile)) {
                    targetFile
                } else {
                    tempFile.delete()
                    null
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prescription bitmap", e)
            null
        }
    }

    /**
     * Copies a picked gallery image [Uri] directly into encrypted app private storage with 100% original fidelity.
     * Uses bounded streaming to prevent OOM on large attachments.
     */
    suspend fun savePrescriptionUriAsync(context: Context, uri: Uri): File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        savePrescriptionUri(context, uri)
    }

    fun savePrescriptionUri(context: Context, uri: Uri): File? {
        return try {
            val dir = getPrescriptionsDir(context)
            val fileName = "rx_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
            val tempFile = File(dir, "$fileName.tmp")
            val targetFile = File(dir, fileName)

            val pfd = try { context.contentResolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null }
            if (pfd != null) {
                val size = pfd.statSize
                pfd.close()
                if (size > MAX_ATTACHMENT_BYTES) {
                    Log.e(TAG, "Picked prescription image exceeds 5 MiB: $size bytes")
                    return null
                }
            }

            // Stream bounded chunks to prevent high memory allocation / OOM
            val buffer = ByteArray(8192)
            val baos = java.io.ByteArrayOutputStream()
            var totalRead = 0
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { input ->
                var n: Int
                while (input.read(buffer).also { n = it } != -1) {
                    totalRead += n
                    if (totalRead > MAX_ATTACHMENT_BYTES) {
                        Log.e(TAG, "Streamed prescription exceeds 5 MiB: $totalRead bytes")
                        return null
                    }
                    baos.write(buffer, 0, n)
                }
            }
            val rawBytes = baos.toByteArray()
            if (rawBytes.isEmpty()) return null

            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val encryptedBytes = secureStore.encryptBytes(ATTACHMENT_KEY_ALIAS, rawBytes) ?: run {
                Log.e(TAG, "Encryption failed; failing closed to prevent plaintext storage")
                return null
            }

            FileOutputStream(tempFile).use { out ->
                out.write(encryptedBytes)
                out.flush()
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (tempFile.renameTo(targetFile)) {
                    targetFile
                } else {
                    tempFile.delete()
                    null
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prescription URI", e)
            null
        }
    }

    private fun isRecognizedLegacyFormat(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) return true
        // PNG: 89 50 4E 47
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) return true
        // PDF: %PDF (25 50 44 46)
        if (bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x44.toByte() && bytes[3] == 0x46.toByte()) return true
        // GIF: GIF8 (47 49 46 38)
        if (bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x38.toByte()) return true
        // WEBP: RIFF....WEBP
        if (bytes.size >= 12 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() && bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()) return true
        return false
    }

    /**
     * Reads and decrypts attachment bytes if encrypted, preserving plaintext for legacy/unencrypted images.
     * Fails closed if ciphertext cannot be decrypted and data does not match a recognized image header.
     */
    fun readAttachmentBytes(context: Context?, file: File): ByteArray {
        if (!file.exists() || file.length() == 0L) return ByteArray(0)
        val fileBytes = file.readBytes()
        if (context == null) return fileBytes
        val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
        val decrypted = secureStore.decryptBytes(ATTACHMENT_KEY_ALIAS, fileBytes)
        if (decrypted != null) return decrypted
        if (isRecognizedLegacyFormat(fileBytes)) return fileBytes
        return ByteArray(0)
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
    @Throws(Exception::class)
    private fun loadPrescriptionsInternal(context: Context, customerId: String): List<Prescription> {
        val exactRaw = customerId.trim()
        if (exactRaw.isBlank()) return emptyList()

        // Best-effort cleanup of any previously failed file deletions
        cleanupOrphanedFiles(context)

        val storageKey = customerStorageKey(exactRaw)
        val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
        val type = object : TypeToken<List<Prescription>>() {}.type

        val readRes = secureStore.readWithResult(storageKey)
        if (readRes is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.DecryptError) {
            throw IllegalStateException("Decryption error for $storageKey", readRes.cause)
        }
        var json = (readRes as? com.commerceos.android.session.SecureCredentialStore.SecureReadResult.Success)?.value

        if (json == null) {
            // Check old sanitized encrypted key for migration with exact-ID check
            val oldSafeId = sanitizeCustomerId(exactRaw)
            val oldKey = KEY_PREFIX + oldSafeId
            if (oldKey != storageKey) {
                val oldRead = secureStore.readWithResult(oldKey)
                if (oldRead is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.DecryptError) {
                    throw IllegalStateException("Decryption error for $oldKey", oldRead.cause)
                }
                val oldJson = (oldRead as? com.commerceos.android.session.SecureCredentialStore.SecureReadResult.Success)?.value
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
                        var committed = false
                        for (attempt in 1..3) {
                            val editor = prefs.edit()
                            if (remaining.isEmpty()) {
                                editor.remove(legacyPrefKey)
                            } else {
                                editor.putString(legacyPrefKey, gson.toJson(remaining, type))
                            }
                            committed = editor.commit()
                            if (committed) break
                        }
                        if (committed) {
                            json = migratedJson
                        } else {
                            // Fail closed: if plaintext cleanup fails, do not expose migratedJson or leave cleanup ignored
                            json = null
                        }
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
        return list.filter { rx ->
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
    }

    /**
     * Retrieves all prescriptions stored locally for [customerId] with a typed storage result.
     */
    fun getPrescriptionsWithResult(context: Context, customerId: String): StorageResult<List<Prescription>> {
        return try {
            val list = loadPrescriptionsInternal(context, customerId)
            StorageResult.Success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get local prescriptions with result", e)
            StorageResult.DecryptError(e)
        }
    }

    /**
     * Retrieves all prescriptions stored locally for [customerId].
     */
    fun getPrescriptions(context: Context, customerId: String): List<Prescription> {
        return when (val res = getPrescriptionsWithResult(context, customerId)) {
            is StorageResult.Success -> res.data
            else -> emptyList()
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
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            when (secureStore.readWithResult(storageKey)) {
                is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.DecryptError -> {
                    Log.e(TAG, "Decryption error for storageKey=$storageKey; preserving existing ciphertext to prevent data loss")
                    return false
                }
                else -> {}
            }
            val existing = getPrescriptions(context, exactRaw).toMutableList()
            existing.removeAll { it.id == rx.id }
            existing.add(0, rx) // Most recent first
            val type = object : TypeToken<List<Prescription>>() {}.type
            val json = gson.toJson(existing, type)

            // Persist to Keystore-backed AES-GCM encrypted store
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
                    var committed = false
                    for (attempt in 1..3) {
                        val editor = prefs.edit()
                        if (remaining.isEmpty()) {
                            editor.remove(legacyPrefKey)
                        } else {
                            editor.putString(legacyPrefKey, gson.toJson(remaining, type))
                        }
                        committed = editor.commit()
                        if (committed) break
                    }
                    if (!committed) {
                        Log.e(TAG, "Failed to cleanup legacy plaintext prescription after encrypted save")
                        return false
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
                val failedDeletions = mutableListOf<String>()
                target.attachments.forEach { path ->
                    val cleanPath = path.removePrefix("file://")
                    if (cleanPath.startsWith("/")) {
                        try {
                            val file = File(cleanPath).canonicalFile
                            if (file.canonicalPath.startsWith(rxDirCanonical + File.separator)) {
                                if (file.exists() && !file.delete()) {
                                    failedDeletions.add(file.absolutePath)
                                }
                            }
                        } catch (_: Exception) {
                            failedDeletions.add(cleanPath)
                        }
                    }
                }
                if (failedDeletions.isNotEmpty()) {
                    scheduleOrphanCleanup(context, failedDeletions)
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

    private fun scheduleOrphanCleanup(context: Context, failedPaths: List<String>) {
        if (failedPaths.isEmpty()) return
        try {
            val prefs = context.getSharedPreferences(ORPHAN_CLEANUP_PREFS, Context.MODE_PRIVATE)
            val existing = prefs.getStringSet(KEY_PENDING_ORPHAN_FILES, emptySet())?.toMutableSet() ?: mutableSetOf()
            existing.addAll(failedPaths)
            var committed = false
            for (attempt in 1..3) {
                committed = prefs.edit().putStringSet(KEY_PENDING_ORPHAN_FILES, existing).commit()
                if (committed) break
            }
            if (!committed) {
                Log.e(TAG, "Failed to commit scheduled orphan cleanup after 3 attempts")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule orphan cleanup", e)
        }
    }

    /**
     * Attempts to delete any orphaned prescription image files that previously failed deletion.
     * Returns the count of successfully cleaned up files.
     */
    fun cleanupOrphanedFiles(context: Context): Int {
        return try {
            val rxDirCanonical = getPrescriptionsDir(context).canonicalPath
            val prefs = context.getSharedPreferences(ORPHAN_CLEANUP_PREFS, Context.MODE_PRIVATE)
            val pending = prefs.getStringSet(KEY_PENDING_ORPHAN_FILES, emptySet())?.toSet() ?: emptySet()
            if (pending.isEmpty()) return 0
            var cleanedCount = 0
            val remaining = mutableSetOf<String>()
            for (path in pending) {
                try {
                    val file = File(path).canonicalFile
                    if (!file.exists()) {
                        cleanedCount++
                    } else if (file.canonicalPath.startsWith(rxDirCanonical + File.separator)) {
                        if (file.delete()) {
                            cleanedCount++
                        } else {
                            remaining.add(path)
                        }
                    } else {
                        cleanedCount++
                    }
                } catch (_: Exception) {
                    remaining.add(path)
                }
            }
            var committed = false
            for (attempt in 1..3) {
                committed = prefs.edit().putStringSet(KEY_PENDING_ORPHAN_FILES, remaining).commit()
                if (committed) break
            }
            if (!committed) {
                Log.w(TAG, "Failed to commit remaining pending orphan files after cleanup attempts")
            }
            cleanedCount
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up orphaned prescription files", e)
            0
        }
    }

    sealed interface PendingUploadResult {
        data class Found(val record: PendingUploadRecord) : PendingUploadResult
        object None : PendingUploadResult
        data class StorageError(val cause: Throwable) : PendingUploadResult
    }

    fun getPendingUploadWithResult(context: Context, customerId: String): PendingUploadResult {
        return try {
            val safeId = customerStorageKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            when (val res = secureStore.readWithResult(PENDING_UPLOAD_PREFIX + safeId)) {
                is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.Success -> {
                    val record = gson.fromJson(res.value, PendingUploadRecord::class.java)
                    if (record != null) PendingUploadResult.Found(record) else PendingUploadResult.None
                }
                is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.DecryptError -> PendingUploadResult.StorageError(res.cause)
                is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.NotFound -> {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val raw = prefs.getString(PENDING_UPLOAD_PREFIX + safeId, null)
                    if (raw != null) {
                        val record = gson.fromJson(raw, PendingUploadRecord::class.java)
                        if (record != null) {
                            val migrated = secureStore.save(PENDING_UPLOAD_PREFIX + safeId, raw)
                            if (migrated) {
                                prefs.edit().remove(PENDING_UPLOAD_PREFIX + safeId).commit()
                                PendingUploadResult.Found(record)
                            } else {
                                PendingUploadResult.None
                            }
                        } else {
                            prefs.edit().remove(PENDING_UPLOAD_PREFIX + safeId).commit()
                            PendingUploadResult.None
                        }
                    } else {
                        PendingUploadResult.None
                    }
                }
            }
        } catch (e: Exception) {
            PendingUploadResult.StorageError(e)
        }
    }

    fun getPendingUpload(context: Context, customerId: String): PendingUploadRecord? {
        return when (val res = getPendingUploadWithResult(context, customerId)) {
            is PendingUploadResult.Found -> res.record
            else -> null
        }
    }

    fun setPendingUpload(context: Context, customerId: String, record: PendingUploadRecord): Boolean {
        return try {
            val safeId = customerStorageKey(customerId)
            val json = gson.toJson(record)
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val saved = secureStore.save(PENDING_UPLOAD_PREFIX + safeId, json)
            if (saved) {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    if (prefs.contains(PENDING_UPLOAD_PREFIX + safeId)) {
                        prefs.edit().remove(PENDING_UPLOAD_PREFIX + safeId).commit()
                    }
                } catch (_: Exception) {}
            }
            saved
        } catch (_: Exception) {
            false
        }
    }

    fun clearPendingUpload(context: Context, customerId: String): Boolean {
        return try {
            val safeId = customerStorageKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            val secureCleared = secureStore.clear(PENDING_UPLOAD_PREFIX + safeId)
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(PENDING_UPLOAD_PREFIX + safeId).commit()
            } catch (_: Exception) {}
            secureCleared
        } catch (_: Exception) {
            false
        }
    }

    private fun customerDraftKey(customerId: String): String {
        val safeId = customerStorageKey(customerId)
        return "rx_draft_$safeId"
    }

    sealed interface DraftResult {
        data class Found(val draft: PrescriptionDraft) : DraftResult
        object None : DraftResult
        data class StorageError(val cause: Throwable) : DraftResult
    }

    fun getDraftWithResult(context: Context, customerId: String): DraftResult {
        return try {
            val key = customerDraftKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            when (val res = secureStore.readWithResult(key)) {
                is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.Success -> {
                    val draft = gson.fromJson(res.value, PrescriptionDraft::class.java)
                    if (draft != null) DraftResult.Found(draft) else DraftResult.None
                }
                is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.DecryptError -> {
                    Log.e(TAG, "Decryption error reading draft for key=$key", res.cause)
                    DraftResult.StorageError(res.cause)
                }
                is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.NotFound -> DraftResult.None
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read prescription draft", e)
            DraftResult.StorageError(e)
        }
    }

    fun getDraftWithResult(context: Context): DraftResult {
        return getDraftWithResult(context, "default_user")
    }

    fun saveDraft(context: Context, customerId: String, draft: PrescriptionDraft): Boolean {
        return try {
            val key = customerDraftKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            when (secureStore.readWithResult(key)) {
                is com.commerceos.android.session.SecureCredentialStore.SecureReadResult.DecryptError -> {
                    Log.e(TAG, "Existing prescription draft ciphertext could not be decrypted; refusing overwrite to prevent data loss")
                    return false
                }
                else -> Unit
            }
            secureStore.save(key, gson.toJson(draft))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist prescription draft", e)
            false
        }
    }

    fun saveDraft(context: Context, draft: PrescriptionDraft): Boolean {
        return saveDraft(context, "default_user", draft)
    }

    fun getDraft(context: Context, customerId: String): PrescriptionDraft? {
        return when (val res = getDraftWithResult(context, customerId)) {
            is DraftResult.Found -> res.draft
            is DraftResult.None -> null
            is DraftResult.StorageError -> throw IllegalStateException("Failed to decrypt prescription draft", res.cause)
        }
    }

    fun getDraft(context: Context): PrescriptionDraft? {
        return getDraft(context, "default_user")
    }

    fun clearDraft(context: Context, customerId: String): Boolean {
        return try {
            val key = customerDraftKey(customerId)
            val secureStore = secureStoreProvider?.invoke(context) ?: com.commerceos.android.session.SecureCredentialStore(context)
            secureStore.clear(key)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear prescription draft", e)
            false
        }
    }

    fun clearDraft(context: Context): Boolean {
        return clearDraft(context, "default_user")
    }
}
