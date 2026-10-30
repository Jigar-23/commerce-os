package com.commerceos.android.ui.address

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.ui.address.create.AddAddressFlow
import com.commerceos.android.viewmodel.AddressPlatformStep
import com.commerceos.android.viewmodel.AddressViewModel
import com.commerceos.android.viewmodel.ServiceabilityState

/**
 * Top-Level Production Address Screen Orchestrator.
 * Driven by AddressViewModel as the Single Source of Truth for state machine steps & address mutations.
 * Supports direct single-click back exit when entered directly from bottom sheet or home screen.
 */
@Composable
fun AddressScreen(
    viewModel: AddressViewModel,
    serviceability: ServiceabilityState,
    onSelectAddress: (ApiAddress) -> Unit,
    onProceedToPayment: (() -> Unit)? = null,
    onSavedSuccessfully: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    fromProfile: Boolean = false,
    directAddFlow: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        viewModel.attachContext(context)
    }

    val state = viewModel.platformUiState

    val handleBackPress: () -> Unit = {
        if (state.currentStep is AddressPlatformStep.EditingDetails && !state.isEditingExisting) {
            // Stepping back from details form returns to pin on map
            viewModel.navigateBack()
        } else {
            // Direct single-click exit from Add Address / Pin on Map back to parent screen
            viewModel.cancelFlow()
            onBack?.invoke()
        }
    }

    // System Hardware/Gesture Back Navigation Interceptor
    BackHandler(enabled = true) {
        handleBackPress()
    }

    LaunchedEffect(state.saveState) {
        if (state.saveState is com.commerceos.android.viewmodel.SaveState.Success) {
            viewModel.resetSaveState()
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
            onBack = handleBackPress,
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
            getDistanceMeters = { addr -> viewModel.getDistanceToSavedAddressMeters(addr) },
            onSelectAddress = { addr ->
                viewModel.select(addr)
                onSelectAddress(addr)
            },
            onEditAddress = { addr -> viewModel.startEditAddressFlow(addr) },
            onDeleteAddress = { id -> viewModel.deleteAddress(id) },
            onSetDefaultAddress = { id -> viewModel.setDefaultAddress(id) },
            onAddNewLocation = { viewModel.startAddAddressFlow() },
            onProceedToPayment = onProceedToPayment,
            onBack = onBack,
            fromProfile = fromProfile,
            modifier = modifier
        )
    }
}
