package com.commerceos.android.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Manages customer profile customization entirely on the local device.
 * Storing avatars and local names in app-private storage incurs ZERO remote server
 * memory, bandwidth, or cloud storage costs.
 */
object LocalProfileManager {
    private const val TAG = "LocalProfileManager"
    private const val PREFS_NAME = "commerce_os_local_profiles"
    private const val KEY_PREFIX_NAME = "custom_name_"
    private const val PROFILES_DIR = "profiles"

    private fun sanitizeCustomerId(customerId: String): String {
        return customerId.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { "default_user" }
    }

    private fun getProfilesDir(context: Context): File {
        val dir = File(context.filesDir, PROFILES_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Retrieves the custom local profile name for [customerId], if set by the user.
     */
    fun getCustomName(context: Context, customerId: String): String? {
        val safeId = sanitizeCustomerId(customerId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PREFIX_NAME + safeId, null)?.trim()?.takeIf { it.isNotBlank() }
        if (saved != null && saved.startsWith("Customer", ignoreCase = true)) {
            prefs.edit().remove(KEY_PREFIX_NAME + safeId).apply()
            return null
        }
        return saved
    }

    /**
     * Saves an edited full name locally for [customerId].
     */
    fun saveCustomName(context: Context, customerId: String, name: String) {
        val safeId = sanitizeCustomerId(customerId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            prefs.edit().remove(KEY_PREFIX_NAME + safeId).apply()
        } else {
            prefs.edit().putString(KEY_PREFIX_NAME + safeId, cleanName).apply()
        }
    }

    /**
     * Gets the on-device avatar [File] for [customerId] if one exists.
     */
    fun getAvatarFile(context: Context, customerId: String): File? {
        val safeId = sanitizeCustomerId(customerId)
        val file = File(getProfilesDir(context), "avatar_$safeId.jpg")
        return if (file.exists() && file.length() > 0) file else null
    }

    /**
     * Saves a captured or cropped [Bitmap] as the user's on-device profile avatar.
     */
    fun saveAvatarBitmap(context: Context, customerId: String, bitmap: Bitmap): File? {
        return try {
            val safeId = sanitizeCustomerId(customerId)
            val targetFile = File(getProfilesDir(context), "avatar_$safeId.jpg")
            val scaled = scaleDownBitmap(bitmap, 512)
            FileOutputStream(targetFile).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
                out.flush()
            }
            if (scaled != bitmap && !scaled.isRecycled) {
                scaled.recycle()
            }
            targetFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save avatar bitmap", e)
            null
        }
    }

    /**
     * Reads a picked gallery image [Uri], downscales it, and saves it locally.
     */
    fun saveAvatarUri(context: Context, customerId: String, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val decoded = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (decoded != null) {
                val savedFile = saveAvatarBitmap(context, customerId, decoded)
                if (!decoded.isRecycled) {
                    decoded.recycle()
                }
                savedFile
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save avatar from URI", e)
            null
        }
    }

    /**
     * Deletes any locally saved avatar for [customerId].
     */
    fun removeAvatar(context: Context, customerId: String): Boolean {
        return try {
            val file = getAvatarFile(context, customerId)
            file?.delete() ?: true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove avatar", e)
            false
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
