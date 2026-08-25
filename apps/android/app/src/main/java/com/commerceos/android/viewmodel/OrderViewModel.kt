package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Cancellation dialog state owned by the order domain, not the root composable.
 *  Outcome is delivered as a one-shot event (see [OrderViewModel.events]); this
 *  state only drives dialog visibility and the submitting flag. */
data class CancellationUiState(
    val orderToCancel: CustomerOrderApiResponse? = null,
    val policy: CancellationPolicy? = null,
    val reasons: List<CancellationReason> = emptyList(),
    val selectedReasonCode: String? = null,
    val reasonNote: String = "",
    val isSubmitting: Boolean = false
) {
    val isVisible: Boolean get() = orderToCancel != null
}

/** One-shot cancellation outcome, collected at the root (not coupled to dialog visibility). */
sealed interface CancellationEvent {
    data class Success(val message: String) : CancellationEvent
    data class Failure(val message: String) : CancellationEvent
}

/** History list: loading / empty / content / error — never a bare list. */
sealed interface OrderHistoryUiState {
    data object Loading : OrderHistoryUiState
    data object Empty : OrderHistoryUiState
    data class Content(val orders: List<CustomerOrderApiResponse>) : OrderHistoryUiState
    data class Error(val message: String) : OrderHistoryUiState
}

/** Detail view: loading / not found / content / error — no isLoading==null ambiguity. */
sealed interface OrderDetailUiState {
    data object Loading : OrderDetailUiState
    data object NotFound : OrderDetailUiState
    data class Content(val order: CustomerOrderApiResponse) : OrderDetailUiState
    data class Error(val message: String) : OrderDetailUiState
}

class OrderViewModel(private val repository: AppRepository) : ViewModel() {

    var history by mutableStateOf<OrderHistoryUiState>(OrderHistoryUiState.Loading)
        private set

    var detail by mutableStateOf<OrderDetailUiState>(OrderDetailUiState.Loading)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var cancellation by mutableStateOf(CancellationUiState())
        private set

    private val _events = Channel<CancellationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var customerId = ""

    fun loadHistory(customerId: String) {
        this.customerId = customerId
        viewModelScope.launch {
            history = OrderHistoryUiState.Loading
            when (val result = repository.getCustomerOrders(customerId)) {
                is ApiResult.Success -> {
                    history = if (result.data.isEmpty()) {
                        OrderHistoryUiState.Empty
                    } else {
                        OrderHistoryUiState.Content(result.data)
                    }
                }
                is ApiResult.Failure -> history = OrderHistoryUiState.Error(result.error.message)
            }
        }
    }

    var liveTracking by mutableStateOf<CustomerOrderTrackingDto?>(null)
        private set

    private var trackingJob: Job? = null

    fun loadDetail(orderId: String) {
        viewModelScope.launch {
            detail = OrderDetailUiState.Loading
            when (val result = repository.getOrderById(orderId)) {
                is ApiResult.Success -> {
                    detail = OrderDetailUiState.Content(result.data)
                    startLiveTrackingPolling(orderId)
                }
                is ApiResult.Failure -> {
                    detail = if (result.error is AppError.Server && result.error.httpCode == 404) {
                        OrderDetailUiState.NotFound
                    } else {
                        OrderDetailUiState.Error(result.error.message)
                    }
                }
            }
        }
    }

    private fun startLiveTrackingPolling(orderId: String) {
        trackingJob?.cancel()
        trackingJob = viewModelScope.launch {
            // 1. Initial REST Snapshot fetch
            when (val result = repository.getLiveTracking(orderId)) {
                is ApiResult.Success -> {
                    liveTracking = result.data
                }
                else -> {}
            }

            // 2. Realtime SSE Event Stream (Sub-second live telemetry pushes)
            launch {
                repository.streamLiveOrderTracking(orderId).collect { update ->
                    liveTracking = update
                    val state = (update.state ?: update.stage ?: "").uppercase()
                    if (state == "DELIVERED" || state == "CANCELLED" || state == "FAILED") {
                        trackingJob?.cancel()
                    }
                }
            }

            // 3. Heartbeat Reconciliation Fallback (Every 12 seconds)
            while (isActive) {
                delay(12000)
                when (val orderResult = repository.getOrderById(orderId)) {
                    is ApiResult.Success -> {
                        detail = OrderDetailUiState.Content(orderResult.data)
                        val status = orderResult.data.orderStatus.uppercase()
                        if (status == "DELIVERED" || status == "CANCELLED" || status == "FAILED") {
                            break
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    fun openCancelDialog(order: CustomerOrderApiResponse) {
        cancellation = CancellationUiState(orderToCancel = order)
        viewModelScope.launch {
            when (val result = repository.getCancellationPolicy(order.id)) {
                is ApiResult.Success -> cancellation = cancellation.copy(
                    policy = result.data,
                    reasons = result.data.reasons
                )
                is ApiResult.Failure -> cancellation = cancellation.copy(
                    policy = null,
                    reasons = emptyList()
                )
            }
        }
    }

    fun dismissCancelDialog() {
        cancellation = CancellationUiState()
    }

    fun selectReason(code: String) {
        cancellation = cancellation.copy(selectedReasonCode = code)
    }

    fun setReasonNote(note: String) {
        cancellation = cancellation.copy(reasonNote = note)
    }

    /** Sends the canonical reason code + optional free-text note; free text never overrides the code. */
    fun confirmCancel() {
        val target = cancellation.orderToCancel ?: return
        val reasonCode = cancellation.selectedReasonCode
        if (reasonCode.isNullOrBlank()) {
            _events.trySend(CancellationEvent.Failure("Select a cancellation reason"))
            return
        }
        if (cancellation.isSubmitting) return
        cancellation = cancellation.copy(isSubmitting = true)
        viewModelScope.launch {
            val note = cancellation.reasonNote.trim()
            val reasonText = if (note.isNotBlank()) "$reasonCode: $note" else reasonCode
            val res = repository.cancelOrder(target.id, reasonText)
            when (res) {
                is ApiResult.Success -> {
                    cancellation = CancellationUiState()
                    _events.trySend(CancellationEvent.Success("Order cancelled"))
                }
                is ApiResult.Failure -> {
                    cancellation = cancellation.copy(isSubmitting = false)
                    _events.trySend(CancellationEvent.Failure(res.error.message))
                }
            }
            if (customerId.isNotBlank()) {
                loadHistory(customerId)
            }
        }
    }

    fun reset() {
        history = OrderHistoryUiState.Loading
        detail = OrderDetailUiState.Loading
        errorMessage = null
        cancellation = CancellationUiState()
        customerId = ""
    }
}
