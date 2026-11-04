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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class PrescriptionViewModel(
    private val repository: AppRepository,
    private val appContext: Context? = null
) : ViewModel() {

    var prescriptions by mutableStateOf<List<Prescription>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var loadJob: Job? = null
    private var loadGeneration = 0L
    private var uploadJob: Job? = null

    fun load(customerId: String) {
        val id = customerId.trim()
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
                val local = if (appContext != null) {
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
                            prescriptions = (local + server).distinctBy { it.id }
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
        uploadJob?.cancel()
        uploadJob = viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val lightweightAttachments = attachments.map { path ->
                    if (path.startsWith("data:")) {
                        path
                    } else {
                        val file = File(path.removePrefix("file://"))
                        if (file.exists() && file.length() in 1..(5 * 1024 * 1024)) {
                            try {
                                val bytes = file.readBytes()
                                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                "data:image/jpeg;base64,$base64"
                            } catch (_: Exception) {
                                file.name
                            }
                        } else {
                            file.name
                        }
                    }
                }
                when (val result = repository.uploadPrescription(
                    UploadPrescriptionRequest(
                        customerId = safeCustomerId,
                        patientName = patientName.ifBlank { "Patient" },
                        attachments = lightweightAttachments,
                        note = "prescription_document_upload"
                    )
                )) {
                    is ApiResult.Success -> {
                        val serverRx = result.data.copy(attachments = attachments)
                        if (appContext != null) {
                            LocalPrescriptionManager.savePrescription(appContext, serverRx)
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
                        prescriptions = listOf(serverRx) + prescriptions.filterNot { it.id == serverRx.id }
                        errorMessage = null
                        onUploaded?.invoke(serverRx)
                    }
                    is ApiResult.Failure -> {
                        errorMessage = result.error.message ?: "Failed to upload prescription. Please try again."
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = "Could not upload prescription: ${e.message}"
            } finally {
                isLoading = false
                uploadJob = null
            }
        }
    }

    fun reset() {
        loadJob?.cancel()
        uploadJob?.cancel()
        prescriptions = emptyList()
        isLoading = false
        errorMessage = null
    }
}
