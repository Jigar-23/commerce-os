package com.commerceos.android.viewmodel

import com.commerceos.android.location.GeoPoint
import com.commerceos.android.location.GeocodedPlace
import com.commerceos.android.location.PlaceSearchResult
import com.commerceos.android.model.AddressConflictWarning
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.model.StructuredAddress

/**
 * Production State Machine steps for Commerce OS Address & Location Platform.
 * ViewModel is the SOLE source of truth for flow navigation.
 */
sealed class AddressPlatformStep {
    /** Browsing saved addresses in address book */
    object AddressBook : AddressPlatformStep()

    /** Autocomplete place/location search */
    data class SearchingLocation(val query: String = "") : AddressPlatformStep()

    /** Location selected on real map, pin positioned */
    data class LocationSelected(val point: GeoPoint) : AddressPlatformStep()

    /** Async debounced reverse geocoding in progress */
    data class ReverseGeocoding(val point: GeoPoint) : AddressPlatformStep()

    /** Trust boundary: Location pin confirmation step */
    data class ConfirmingPin(val place: GeocodedPlace) : AddressPlatformStep()

    /** Structured address details form */
    object EditingDetails : AddressPlatformStep()
}

/**
 * Explicit Save State for Address persistence operations.
 */
sealed class SaveState {
    object Idle : SaveState()
    object Saving : SaveState()
    data class Success(val savedAddress: ApiAddress) : SaveState()
    data class Error(val message: String) : SaveState()
}

/**
 * Explicit GPS acquisition state.
 */
sealed class LocationAcquisitionState {
    object Idle : LocationAcquisitionState()
    object AcquiringGps : LocationAcquisitionState()
    data class Success(val point: GeoPoint) : LocationAcquisitionState()
    data class Error(val message: String) : LocationAcquisitionState()
}

/**
 * Single Canonical UI State Container for Address & Location Platform.
 * Maintains explicit separation between the immutable original edit snapshot
 * and the user's mutable draft address.
 */
data class AddressPlatformUiState(
    val currentStep: AddressPlatformStep = AddressPlatformStep.AddressBook,
    val originalAddress: StructuredAddress? = null,
    val draftAddress: StructuredAddress = StructuredAddress(),
    val isEditingExisting: Boolean = false,
    val editingAddressId: String? = null,
    val locationSearchQuery: String = "",
    val placeSearchResults: List<PlaceSearchResult> = emptyList(),
    val isSearchingPlaces: Boolean = false,
    val activeGeocodedPlace: GeocodedPlace? = null,
    val isReverseGeocoding: Boolean = false,
    val locationAcquisitionState: LocationAcquisitionState = LocationAcquisitionState.Idle,
    val saveState: SaveState = SaveState.Idle,
    val addressConflict: AddressConflictWarning = AddressConflictWarning(hasConflict = false),
    val locationErrorMessage: String? = null
) {
    val isFlowActive: Boolean
        get() = currentStep !is AddressPlatformStep.AddressBook

    val isDirty: Boolean
        get() = originalAddress != null && draftAddress != originalAddress

    val activeFormAddress: StructuredAddress
        get() = draftAddress
}
