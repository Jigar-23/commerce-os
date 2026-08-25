package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.launch

class PrescriptionViewModel(private val repository: AppRepository) : ViewModel() {

    var prescriptions by mutableStateOf<List<Prescription>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load(customerId: String) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getCustomerPrescriptions(customerId)) {
                is ApiResult.Success -> {
                    prescriptions = result.data
                    errorMessage = null
                }
                is ApiResult.Failure -> errorMessage = result.error.message
            }
            isLoading = false
        }
    }

    fun upload(customerId: String, patientName: String, attachments: List<String>) {
        if (attachments.isEmpty()) {
            errorMessage = "Attach at least one prescription image"
            return
        }
        viewModelScope.launch {
            isLoading = true
            when (val created = repository.uploadPrescription(
                UploadPrescriptionRequest(
                    customerId = customerId,
                    patientName = patientName.ifBlank { "Patient" },
                    attachments = attachments
                )
            )) {
                is ApiResult.Success -> {
                    prescriptions = listOf(created.data) + prescriptions
                    errorMessage = null
                }
                is ApiResult.Failure -> errorMessage = created.error.message
            }
            isLoading = false
        }
    }

    fun reset() {
        prescriptions = emptyList()
        isLoading = false
        errorMessage = null
    }
}
