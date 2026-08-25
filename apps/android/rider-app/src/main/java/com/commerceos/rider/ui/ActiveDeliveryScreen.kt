package com.commerceos.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.repository.RiderDeliveryRepository

@Composable
fun ActiveDeliveryScreen(
    session: ServerDeliverySession,
    repository: RiderDeliveryRepository,
    riderLat: Double?,
    riderLng: Double?,
    riderHeading: Float?,
    isStale: Boolean,
    onArrivedStore: () -> Unit,
    onConfirmPickup: () -> Unit,
    onArrivedCustomer: () -> Unit,
    onCompleteDelivery: () -> Unit,
    onViewOrderDetails: () -> Unit,
    enteredOtp: String,
    onOtpChange: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    resendCooldown: Int,
    enteredCodAmount: String,
    onCodAmountChange: (String) -> Unit,
    onReconcileCod: () -> Unit,
    onReportIssue: () -> Unit,
    onCancelDelivery: () -> Unit = {},
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Sticky Fixed Hero Viewport: Live Leaflet Map + Stage Banner + Primary 54dp Action Bar
        RiderLiveNavigationView(
            session = session,
            repository = repository,
            riderLat = riderLat,
            riderLng = riderLng,
            riderHeading = riderHeading,
            isStale = isStale,
            onArrivedStore = onArrivedStore,
            onConfirmPickup = onConfirmPickup,
            onArrivedCustomer = onArrivedCustomer,
            onCompleteDelivery = onCompleteDelivery,
            onViewOrderDetails = onViewOrderDetails,
            onCancelDelivery = onCancelDelivery,
            enteredOtp = enteredOtp,
            onVerifyOtp = onVerifyOtp
        )

        // Scrollable Contextual Stage Details (Pickup Checklist / OTP Handoff Form)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stage 1 Detail: Dark Store Pickup Flow (When Arrived at Pickup)
            if (session.state == "ARRIVED_PICKUP") {
                PickupFlowView(
                    session = session,
                    onConfirmPickup = onConfirmPickup,
                    onReportIssue = onReportIssue
                )
            }

            // Stage 2 Detail: Customer Doorstep Handoff Flow (When Arrived at Customer or Handoff Started)
            if (session.state in listOf("ARRIVED_CUSTOMER", "HANDOFF_STARTED")) {
                CustomerHandoffView(
                    session = session,
                    enteredOtp = enteredOtp,
                    onOtpChange = onOtpChange,
                    onVerifyOtp = onVerifyOtp,
                    onResendOtp = onResendOtp,
                    resendCooldown = resendCooldown,
                    enteredCodAmount = enteredCodAmount,
                    onCodAmountChange = onCodAmountChange,
                    onReconcileCod = onReconcileCod,
                    isLoading = isLoading,
                    errorMessage = errorMessage
                )
            }
        }
    }
}
