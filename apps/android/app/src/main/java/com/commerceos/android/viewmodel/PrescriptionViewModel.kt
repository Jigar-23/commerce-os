package com.commerceos.android.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.data.local.LocalPrescriptionManager
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class PrescriptionViewModel(
    private val repository: AppRepository,
    private val appContext: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    var prescriptions by mutableStateOf<List<Prescription>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isUploading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var uploadJob: Job? = null
    private var uploadGeneration = 0L
    private var activeCustomerId: String = ""
    private var activeLogicalUploadKey: String? = null
    internal var localPrescriptionsOverride: ((String) -> List<Prescription>)? = null

    fun load(customerId: String) {
        val id = customerId.trim()
        if (activeCustomerId != id) {
            activeCustomerId = id
            uploadGeneration++
            uploadJob?.cancel()
            isUploading = false
            loadGeneration++
            loadJob?.cancel()
            prescriptions = emptyList()
            isLoading = false
        }
        loadGeneration++
        val generation = loadGeneration
        loadJob?.cancel()

        if (id.isBlank()) {
            prescriptions = emptyList()
            isLoading = false
            errorMessage = "Sign in to view prescriptions."
            return
        }

        loadJob = viewModelScope.launch {
            isLoading = true
            try {
                val local = localPrescriptionsOverride?.invoke(id) ?: if (appContext != null) {
                    LocalPrescriptionManager.getPrescriptions(appContext, id)
                        .filter { it.customerId == id }
                } else {
                    emptyList()
                }

                if (generation != loadGeneration) return@launch
                prescriptions = local
                errorMessage = null

                when (val result = repository.getCustomerPrescriptions(id)) {
                    is ApiResult.Success -> {
                        if (generation == loadGeneration) {
                            val server = result.data.filter { it.customerId == id }
                            val localById = local.associateBy { it.id }
                            val serverIds = server.mapTo(mutableSetOf()) { it.id }

                            val mergedServer = server.map { remote ->
                                remote.copy(
                                    attachments = localById[remote.id]?.attachments
                                        ?.takeIf { it.isNotEmpty() }
                                        ?: remote.attachments
                                )
                            }
                            prescriptions = mergedServer + local.filterNot { it.id in serverIds }
                        }
                    }
                    is ApiResult.Failure -> {
                        if (generation == loadGeneration) {
                            errorMessage = "Showing prescriptions saved on this device."
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    errorMessage = "Could not load prescriptions."
                }
            } finally {
                if (generation == loadGeneration) isLoading = false
            }
        }
    }

    private fun readBoundedBytes(file: File, maxBytes: Int = 5 * 1024 * 1024): ByteArray {
        val declaredLength = file.length()
        require(declaredLength <= maxBytes.toLong()) {
            "Prescription image must be 5 MiB or smaller."
        }
        val out = java.io.ByteArrayOutputStream(if (declaredLength in 1..maxBytes.toLong()) declaredLength.toInt() else 8192)
        val buffer = ByteArray(8192)
        var totalRead = 0L
        file.inputStream().use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                totalRead += read
                require(totalRead <= maxBytes.toLong()) {
                    "Prescription image must be 5 MiB or smaller."
                }
                out.write(buffer, 0, read)
            }
        }
        val bytes = out.toByteArray()
        require(bytes.isNotEmpty()) { "Prescription image could not be read." }
        return bytes
    }

    private suspend fun encodeAttachments(paths: List<String>): List<String> =
        withContext(ioDispatcher) {
            paths.map { path ->
                if (path.startsWith("data:image/")) {
                    require(path.length <= 7_000_000) { "Prescription image is too large." }
                    path
                } else {
                    val file = File(path.removePrefix("file://"))
                    require(file.isFile) { "Prescription image could not be read." }

                    val bytes = readBoundedBytes(file)

                    val mime = java.net.URLConnection.guessContentTypeFromStream(
                        java.io.ByteArrayInputStream(bytes)
                    )?.takeIf { it.startsWith("image/") }
                        ?: throw IllegalArgumentException("Unsupported prescription image format.")

                    val base64 = android.util.Base64.encodeToString(
                        bytes,
                        android.util.Base64.NO_WRAP
                    )
                    "data:$mime;base64,$base64"
                }
            }
        }

    fun upload(
        customerId: String,
        patientName: String,
        attachments: List<String>,
        onUploaded: ((Prescription) -> Unit)? = null
    ) {
        val safeCustomerId = customerId.trim()
        if (safeCustomerId.isBlank()) {
            errorMessage = "Sign in to upload prescriptions"
            return
        }
        if (attachments.isEmpty()) {
            errorMessage = "Attach at least one prescription image"
            return
        }
        if (isUploading && activeCustomerId == safeCustomerId) {
            return
        }
        if (activeCustomerId != safeCustomerId) {
            activeCustomerId = safeCustomerId
            activeLogicalUploadKey = null
            loadGeneration++
            loadJob?.cancel()
            prescriptions = emptyList()
            isLoading = false
            uploadJob?.cancel()
            isUploading = false
        }
        val requestGeneration = ++uploadGeneration
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            isUploading = true
            errorMessage = null
            try {
                val lightweightAttachments = encodeAttachments(attachments)
                val stableIdempotencyKey = computeUploadIdempotencyKey(
                    safeCustomerId,
                    patientName,
                    lightweightAttachments
                ).also { activeLogicalUploadKey = it }
                when (val result = repository.uploadPrescription(
                    UploadPrescriptionRequest(
                        customerId = safeCustomerId,
                        patientName = patientName.ifBlank { "Patient" },
                        attachments = lightweightAttachments,
                        note = "prescription_document_upload",
                        idempotencyKey = stableIdempotencyKey
                    )
                )) {
                    is ApiResult.Success -> {
                        if (requestGeneration == uploadGeneration && activeCustomerId == safeCustomerId) {
                            activeLogicalUploadKey = null
                            val serverRx = if (result.data.attachments.isNotEmpty()) result.data else result.data.copy(attachments = attachments)
                            prescriptions = listOf(serverRx) + prescriptions.filterNot { it.id == serverRx.id }
                            errorMessage = null

                            // Local persistence & notification side effects
                            try {
                                if (appContext != null) {
                                    val localCacheSaved = LocalPrescriptionManager.savePrescription(appContext, serverRx)
                                    if (!localCacheSaved) {
                                        errorMessage = "Prescription uploaded to server, but offline encrypted cache could not be saved."
                                    }
                                    com.commerceos.android.notifications.LocalNotificationStorage.saveNotification(
                                        appContext,
                                        com.commerceos.android.notifications.InAppNotificationItem(
                                            title = "Prescription Uploaded",
                                            message = "Prescription slip registered for ${patientName.ifBlank { "patient" }}. Pending review by pharmacy vendor.",
                                            category = "Prescriptions",
                                            deepLinkAction = "PRESCRIPTIONS"
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                            }

                            // Caller callback must not convert a successful upload into a failure
                            try {
                                onUploaded?.invoke(serverRx)
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                            }
                        }
                    }
                    is ApiResult.Failure -> {
                        if (requestGeneration == uploadGeneration && activeCustomerId == safeCustomerId) {
                            errorMessage = result.error.message.ifBlank { "Failed to upload prescription. Please try again." }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == uploadGeneration && activeCustomerId == safeCustomerId) {
                    errorMessage = "Could not upload prescription: ${e.message}"
                }
            } finally {
                if (requestGeneration == uploadGeneration) {
                    isUploading = false
                    uploadJob = null
                }
            }
        }
    }

    private fun computeUploadIdempotencyKey(
        customerId: String,
        patientName: String,
        attachments: List<String>
    ): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(customerId.trim().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        digest.update(patientName.trim().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        attachments.forEach { attachment ->
            val attBytes = attachment.toByteArray(Charsets.UTF_8)
            val attDigest = java.security.MessageDigest.getInstance("SHA-256").digest(attBytes)
            digest.update(attDigest)
            digest.update(0.toByte())
        }
        val finalDigest = digest.digest()
        return "rx_idemp_" + finalDigest.joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(32)
    }

    fun reset() {
        uploadGeneration++
        loadGeneration++
        activeCustomerId = ""
        activeLogicalUploadKey = null
        loadJob?.cancel()
        uploadJob?.cancel()
        prescriptions = emptyList()
        isLoading = false
        isUploading = false
        errorMessage = null
    }
}
