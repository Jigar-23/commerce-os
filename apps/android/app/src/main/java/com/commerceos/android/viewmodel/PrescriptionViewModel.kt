package com.commerceos.android.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.data.local.LocalPrescriptionManager
import com.commerceos.android.data.local.LocalSaveForLaterStore.StorageResult
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
import java.security.MessageDigest
import java.util.UUID

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
    private var currentUploadKey: String? = null
    private var currentUploadFingerprint: String? = null
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
                val localResult = if (localPrescriptionsOverride != null) {
                    StorageResult.Success(localPrescriptionsOverride!!.invoke(id))
                } else if (appContext != null) {
                    LocalPrescriptionManager.getPrescriptionsWithResult(appContext!!, id)
                } else {
                    StorageResult.Success(emptyList())
                }

                if (generation != loadGeneration) return@launch
                val local = when (localResult) {
                    is StorageResult.Success -> {
                        val filtered = localResult.data.filter { it.customerId == id }
                        prescriptions = filtered
                        errorMessage = null
                        filtered
                    }
                    is StorageResult.DecryptError -> {
                        errorMessage = "Storage error: could not read encrypted local prescriptions. Please retry."
                        prescriptions
                    }
                    is StorageResult.NotFound -> {
                        prescriptions = emptyList()
                        errorMessage = null
                        emptyList()
                    }
                }

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

                            if (appContext != null) {
                                val pendingRx = LocalPrescriptionManager.getPendingUpload(appContext, id)
                                if (pendingRx != null) {
                                    val committedOnServer = server.any { rx ->
                                        rx.note.contains(pendingRx.idempotencyKey)
                                    }
                                    if (committedOnServer) {
                                        LocalPrescriptionManager.clearPendingUpload(appContext, id)
                                        if (activeCustomerId == id) {
                                            currentUploadKey = null
                                            currentUploadFingerprint = null
                                        }
                                    }
                                }
                            }
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
        require(file.exists()) { "Prescription image could not be read." }
        require(file.length() <= maxBytes.toLong()) { "Prescription image must be 5 MiB or smaller." }
        val bytes = LocalPrescriptionManager.readAttachmentBytes(appContext, file)
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

    private fun computeUploadFingerprint(customerId: String, patientName: String, attachments: List<String>): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(customerId.trim().toByteArray(Charsets.UTF_8))
        md.update(0.toByte())
        md.update(patientName.trim().toByteArray(Charsets.UTF_8))
        md.update(0.toByte())
        for (att in attachments) {
            md.update(att.toByteArray(Charsets.UTF_8))
            md.update(0.toByte())
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun upload(
        customerId: String,
        patientName: String,
        attachments: List<String>,
        operationKey: String? = null,
        onError: ((String) -> Unit)? = null,
        onUploaded: ((Prescription) -> Unit)? = null
    ) {
        val safeCustomerId = customerId.trim()
        if (safeCustomerId.isBlank()) {
            val err = "Sign in to upload prescriptions"
            errorMessage = err
            onError?.invoke(err)
            return
        }
        if (attachments.isEmpty()) {
            val err = "Attach at least one prescription image"
            errorMessage = err
            onError?.invoke(err)
            return
        }
        if (isUploading && activeCustomerId == safeCustomerId) {
            return
        }
        if (activeCustomerId != safeCustomerId) {
            activeCustomerId = safeCustomerId
            currentUploadKey = null
            currentUploadFingerprint = null
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
                val uploadFingerprint = computeUploadFingerprint(safeCustomerId, patientName, lightweightAttachments)
                val pendingResult = appContext?.let { LocalPrescriptionManager.getPendingUploadWithResult(it, safeCustomerId) }
                if (pendingResult is LocalPrescriptionManager.PendingUploadResult.StorageError) {
                    val err = "Storage error: could not read pending prescription upload state. Please retry."
                    errorMessage = err
                    onError?.invoke(err)
                    isUploading = false
                    return@launch
                }
                val persistedUpload = (pendingResult as? LocalPrescriptionManager.PendingUploadResult.Found)?.record
                val stableIdempotencyKey = operationKey
                    ?: if (currentUploadKey != null && currentUploadFingerprint == uploadFingerprint) {
                        currentUploadKey!!
                    } else if (persistedUpload != null && persistedUpload.fingerprint == uploadFingerprint) {
                        currentUploadKey = persistedUpload.idempotencyKey
                        currentUploadFingerprint = persistedUpload.fingerprint
                        persistedUpload.idempotencyKey
                    } else {
                        val newKey = "rx_upload_${safeCustomerId}_${UUID.randomUUID()}"
                        if (appContext != null) {
                            val saved = LocalPrescriptionManager.setPendingUpload(
                                appContext,
                                safeCustomerId,
                                LocalPrescriptionManager.PendingUploadRecord(newKey, uploadFingerprint)
                            )
                            if (!saved) {
                                val err = "Could not securely save prescription upload state. Please retry."
                                errorMessage = err
                                onError?.invoke(err)
                                isUploading = false
                                return@launch
                            }
                        }
                        currentUploadKey = newKey
                        currentUploadFingerprint = uploadFingerprint
                        newKey
                    }

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
                            currentUploadKey = null
                            currentUploadFingerprint = null
                            appContext?.let { ctx ->
                                val cleared = LocalPrescriptionManager.clearPendingUpload(ctx, safeCustomerId)
                                if (!cleared) {
                                    android.util.Log.w("PrescriptionViewModel", "Warning: could not clear pending upload record from disk")
                                }
                                LocalPrescriptionManager.clearDraft(ctx, safeCustomerId)
                            }
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
                            val err = result.error.message.ifBlank { "Failed to upload prescription. Please try again." }
                            errorMessage = err
                            onError?.invoke(err)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestGeneration == uploadGeneration && activeCustomerId == safeCustomerId) {
                    val err = "Could not upload prescription: ${e.message}"
                    errorMessage = err
                    onError?.invoke(err)
                }
            } finally {
                if (requestGeneration == uploadGeneration) {
                    isUploading = false
                    uploadJob = null
                }
            }
        }
    }

    fun reset() {
        uploadGeneration++
        loadGeneration++
        activeCustomerId = ""
        currentUploadKey = null
        currentUploadFingerprint = null
        loadJob?.cancel()
        uploadJob?.cancel()
        prescriptions = emptyList()
        isLoading = false
        isUploading = false
        errorMessage = null
    }
}
