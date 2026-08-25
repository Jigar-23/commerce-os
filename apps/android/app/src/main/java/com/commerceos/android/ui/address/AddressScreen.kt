package com.commerceos.android.ui.address

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.ui.address.create.AddAddressFlow
import com.commerceos.android.viewmodel.AddressViewModel
import com.commerceos.android.viewmodel.ServiceabilityState

/**
 * Top-Level Production Address Screen Orchestrator.
 * Driven by AddressViewModel as the Single Source of Truth for state machine steps & address mutations.
 */
@Composable
fun AddressScreen(
    viewModel: AddressViewModel,
    serviceability: ServiceabilityState,
    onSelectAddress: (ApiAddress) -> Unit,
    onProceedToPayment: (() -> Unit)? = null,
    onSavedSuccessfully: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        viewModel.attachContext(context)
    }

    val state = viewModel.platformUiState

    LaunchedEffect(state.saveState) {
        if (state.saveState is com.commerceos.android.viewmodel.SaveState.Success) {
            onSavedSuccessfully?.invoke()
        }
    }

    if (state.isFlowActive) {
        AddAddressFlow(
            currentStep = state.currentStep,
            existingAddress = if (state.isEditingExisting) state.originalAddress else null,
            formAddress = state.activeFormAddress,
            searchQuery = state.locationSearchQuery,
            searchResults = state.placeSearchResults,
            isSearchingPlaces = state.isSearchingPlaces,
            locationAcquisitionState = state.locationAcquisitionState,
            gpsErrorMessage = state.locationErrorMessage,
            isReverseGeocoding = state.isReverseGeocoding,
            saveState = state.saveState,
            conflictWarning = state.addressConflict,
            onBack = { viewModel.navigateBack() },
            onSearchQueryChanged = { query -> viewModel.searchLocationPlaces(query) },
            onRequestGps = { viewModel.requestCurrentGpsLocation() },
            onSelectSearchResult = { result -> viewModel.selectPlaceSearchResult(result) },
            onMapCameraSettled = { lat, lng -> viewModel.onMapCameraSettled(lat, lng) },
            onRecenterGps = { viewModel.requestCurrentGpsLocation() },
            onConfirmLocationPin = { viewModel.confirmPinLocation() },
            onFormAddressChanged = { updated -> viewModel.updateFormAddress(updated) },
            onApplyConflictSuggestion = { viewModel.applyConflictSuggestion() },
            onSaveSubmitted = { viewModel.submitSaveFormAddress() },
            modifier = modifier
        )
    } else {
        AddressBookContent(
            addresses = viewModel.addresses,
            selectedAddressId = viewModel.selectedAddress?.id,
            isLoading = viewModel.isLoading,
            errorMessage = viewModel.errorMessage,
            serviceability = serviceability,
            onSelectAddress = { addr ->
                viewModel.select(addr)
                onSelectAddress(addr)
            },
            onEditAddress = { addr -> viewModel.startEditAddressFlow(addr) },
            onDeleteAddress = { id -> viewModel.deleteAddress(id) },
            onSetDefaultAddress = { id -> viewModel.setDefaultAddress(id) },
            onAddNewLocation = { viewModel.startAddAddressFlow() },
            onProceedToPayment = onProceedToPayment,
            modifier = modifier
        )
    }
}
