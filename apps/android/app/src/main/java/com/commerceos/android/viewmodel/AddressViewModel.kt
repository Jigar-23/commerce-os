package com.commerceos.android.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.location.*
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AddressViewModel(
    private val repository: AppRepository,
    private val locationProvider: LocationProvider? = null,
    private val geocodingProvider: GeocodingProvider? = null,
    private val placeSearchProvider: PlaceSearchProvider? = null
) : ViewModel() {

    private var activeLocationProvider: LocationProvider? = locationProvider
    private var activeGeocodingProvider: GeocodingProvider? = geocodingProvider
    private var activePlaceSearchProvider: PlaceSearchProvider? = placeSearchProvider

    var customerId by mutableStateOf("")
        private set

    var addresses by mutableStateOf<List<ApiAddress>>(emptyList())
        private set

    var selectedAddress by mutableStateOf<ApiAddress?>(null)
        private set

    var profile by mutableStateOf<CustomerProfile?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Single Canonical UI State Container for Platform State Machine
    var platformUiState by mutableStateOf(AddressPlatformUiState())
        private set

    val activeFormAddress: StructuredAddress
        get() = platformUiState.draftAddress

    private var reverseGeocodeJob: Job? = null
    private var placeSearchJob: Job? = null

    fun attachContext(context: Context) {
        if (activeLocationProvider == null) {
            activeLocationProvider = DefaultLocationProvider(context.applicationContext)
        }
        if (activeGeocodingProvider == null) {
            activeGeocodingProvider = DefaultGeocodingProvider(context.applicationContext)
        }
        if (activePlaceSearchProvider == null && activeGeocodingProvider != null) {
            activePlaceSearchProvider = DefaultPlaceSearchProvider(activeGeocodingProvider!!)
        }
    }

    fun init(customerId: String) {
        if (this.customerId != customerId) {
            this.customerId = customerId
            loadProfile()
            loadAddresses()
        }
    }

    fun loadProfile() {
        if (customerId.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.getCustomerProfile(customerId)) {
                is ApiResult.Success -> profile = result.data
                is ApiResult.Failure -> {
                    if (profile == null) errorMessage = result.error.message
                }
            }
        }
    }

    fun loadAddresses() {
        if (customerId.isBlank()) return
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.getAddresses(customerId)) {
                is ApiResult.Success -> {
                    addresses = result.data
                    selectedAddress = result.data.firstOrNull { it.isDefault } ?: result.data.firstOrNull()
                    errorMessage = if (result.data.isEmpty()) "No saved addresses found" else null
                }
                is ApiResult.Failure -> errorMessage = result.error.message
            }
            isLoading = false
        }
    }

    fun select(address: ApiAddress) {
        selectedAddress = address
    }

    // --- Single Source of Truth State Machine Transitions ---

    fun startAddAddressFlow() {
        platformUiState = AddressPlatformUiState(
            currentStep = AddressPlatformStep.SearchingLocation(""),
            originalAddress = null,
            draftAddress = StructuredAddress(),
            isEditingExisting = false,
            editingAddressId = null
        )
    }

    fun startEditAddressFlow(apiAddress: ApiAddress) {
        val structured = StructuredAddress.fromApiAddress(apiAddress)
        platformUiState = AddressPlatformUiState(
            currentStep = AddressPlatformStep.EditingDetails,
            originalAddress = structured,
            draftAddress = structured,
            isEditingExisting = true,
            editingAddressId = apiAddress.id
        )

        val geoPoint = structured.geoLocation
        if (geoPoint != null) {
            onMapCameraSettled(geoPoint.latitude, geoPoint.longitude)
        }
    }

    fun navigateBack() {
        when (platformUiState.currentStep) {
            is AddressPlatformStep.SearchingLocation -> {
                platformUiState = platformUiState.copy(currentStep = AddressPlatformStep.AddressBook)
            }
            is AddressPlatformStep.LocationSelected -> {
                platformUiState = platformUiState.copy(currentStep = AddressPlatformStep.SearchingLocation(platformUiState.locationSearchQuery))
            }
            is AddressPlatformStep.ReverseGeocoding -> {
                platformUiState = platformUiState.copy(currentStep = AddressPlatformStep.SearchingLocation(platformUiState.locationSearchQuery))
            }
            is AddressPlatformStep.ConfirmingPin -> {
                platformUiState = platformUiState.copy(currentStep = AddressPlatformStep.SearchingLocation(platformUiState.locationSearchQuery))
            }
            is AddressPlatformStep.EditingDetails -> {
                if (platformUiState.isEditingExisting) {
                    platformUiState = platformUiState.copy(currentStep = AddressPlatformStep.AddressBook)
                } else {
                    val place = platformUiState.activeGeocodedPlace
                    if (place != null) {
                        platformUiState = platformUiState.copy(currentStep = AddressPlatformStep.ConfirmingPin(place))
                    } else {
                        platformUiState = platformUiState.copy(currentStep = AddressPlatformStep.SearchingLocation(platformUiState.locationSearchQuery))
                    }
                }
            }
            AddressPlatformStep.AddressBook -> {}
        }
    }

    fun searchLocationPlaces(query: String) {
        platformUiState = platformUiState.copy(
            currentStep = AddressPlatformStep.SearchingLocation(query),
            locationSearchQuery = query,
            isSearchingPlaces = query.trim().length >= 2
        )

        placeSearchJob?.cancel()
        if (query.trim().length < 2) {
            platformUiState = platformUiState.copy(placeSearchResults = emptyList(), isSearchingPlaces = false)
            return
        }

        placeSearchJob = viewModelScope.launch {
            delay(300) // Debounce search
            val provider = activePlaceSearchProvider
            if (provider != null) {
                when (val result = provider.searchPlaces(query)) {
                    is ApiResult.Success -> {
                        platformUiState = platformUiState.copy(
                            placeSearchResults = result.data,
                            isSearchingPlaces = false
                        )
                    }
                    is ApiResult.Failure -> {
                        platformUiState = platformUiState.copy(
                            placeSearchResults = emptyList(),
                            isSearchingPlaces = false
                        )
                    }
                }
            } else {
                platformUiState = platformUiState.copy(isSearchingPlaces = false)
            }
        }
    }

    fun selectPlaceSearchResult(result: PlaceSearchResult) {
        val geoPoint = result.geoPoint
        platformUiState = platformUiState.copy(
            currentStep = AddressPlatformStep.LocationSelected(geoPoint)
        )
        onMapCameraSettled(geoPoint.latitude, geoPoint.longitude)
    }

    fun requestCurrentGpsLocation() {
        viewModelScope.launch {
            platformUiState = platformUiState.copy(
                locationAcquisitionState = LocationAcquisitionState.AcquiringGps,
                locationErrorMessage = null
            )
            val provider = activeLocationProvider
            if (provider == null) {
                platformUiState = platformUiState.copy(
                    locationAcquisitionState = LocationAcquisitionState.Error("Location provider uninitialized"),
                    locationErrorMessage = "Location provider uninitialized"
                )
                return@launch
            }

            when (val locResult = provider.getCurrentLocation()) {
                is LocationResult.Success -> {
                    val geoPoint = locResult.location
                    platformUiState = platformUiState.copy(
                        locationAcquisitionState = LocationAcquisitionState.Success(geoPoint),
                        currentStep = AddressPlatformStep.LocationSelected(geoPoint)
                    )
                    onMapCameraSettled(geoPoint.latitude, geoPoint.longitude)
                }
                is LocationResult.Failure -> {
                    platformUiState = platformUiState.copy(
                        locationAcquisitionState = LocationAcquisitionState.Error(locResult.reason),
                        locationErrorMessage = locResult.reason
                    )
                }
            }
        }
    }

    fun onMapCameraSettled(lat: Double, lng: Double) {
        reverseGeocodeJob?.cancel()
        val geoPoint = GeoPoint(latitude = lat, longitude = lng, accuracyMeters = null, provider = "user_pin")
        platformUiState = platformUiState.copy(
            currentStep = AddressPlatformStep.ReverseGeocoding(geoPoint),
            isReverseGeocoding = true
        )

        reverseGeocodeJob = viewModelScope.launch {
            delay(350) // 350ms debounce
            val geocoder = activeGeocodingProvider
            if (geocoder != null) {
                when (val result = geocoder.reverseGeocode(lat, lng)) {
                    is ApiResult.Success -> {
                        val place = result.data
                        val updatedForm = platformUiState.draftAddress.copy(
                            street = place.street ?: place.subLocality ?: platformUiState.draftAddress.street,
                            subLocality = place.subLocality ?: platformUiState.draftAddress.subLocality,
                            locality = place.locality ?: platformUiState.draftAddress.locality,
                            city = place.city.ifBlank { platformUiState.draftAddress.city },
                            state = place.state.ifBlank { platformUiState.draftAddress.state },
                            postalCode = place.postalCode.ifBlank { platformUiState.draftAddress.postalCode },
                            country = place.country,
                            geoLocation = place.geoPoint,
                            placeId = place.placeId
                        )
                        platformUiState = platformUiState.copy(
                            draftAddress = updatedForm,
                            activeGeocodedPlace = place,
                            isReverseGeocoding = false,
                            currentStep = AddressPlatformStep.ConfirmingPin(place)
                        )
                    }
                    is ApiResult.Failure -> {
                        val fallbackPlace = GeocodedPlace(
                            formattedAddress = "Selected Pin Location",
                            city = platformUiState.draftAddress.city.ifBlank { "Selected City" },
                            state = platformUiState.draftAddress.state,
                            postalCode = platformUiState.draftAddress.postalCode,
                            geoPoint = geoPoint
                        )
                        platformUiState = platformUiState.copy(
                            draftAddress = platformUiState.draftAddress.copy(geoLocation = geoPoint),
                            activeGeocodedPlace = fallbackPlace,
                            isReverseGeocoding = false,
                            currentStep = AddressPlatformStep.ConfirmingPin(fallbackPlace)
                        )
                    }
                }
            } else {
                platformUiState = platformUiState.copy(
                    draftAddress = platformUiState.draftAddress.copy(geoLocation = geoPoint),
                    isReverseGeocoding = false
                )
            }
        }
    }

    fun confirmPinLocation() {
        val place = platformUiState.activeGeocodedPlace
        if (place != null) {
            val conflict = platformUiState.draftAddress.detectAddressConflict(place)
            platformUiState = platformUiState.copy(
                addressConflict = conflict,
                currentStep = AddressPlatformStep.EditingDetails
            )
        } else {
            platformUiState = platformUiState.copy(currentStep = AddressPlatformStep.EditingDetails)
        }
    }

    fun updateFormAddress(updated: StructuredAddress) {
        val conflict = updated.detectAddressConflict(platformUiState.activeGeocodedPlace)
        platformUiState = platformUiState.copy(
            draftAddress = updated,
            addressConflict = conflict
        )
    }

    fun applyConflictSuggestion() {
        val place = platformUiState.activeGeocodedPlace ?: return
        val updated = platformUiState.draftAddress.copy(
            city = place.city,
            postalCode = place.postalCode,
            state = place.state
        )
        platformUiState = platformUiState.copy(
            draftAddress = updated,
            addressConflict = AddressConflictWarning(hasConflict = false)
        )
    }

    fun submitSaveFormAddress() {
        val validation = platformUiState.draftAddress.validate()
        if (!validation.isValid) {
            return
        }

        val req = platformUiState.draftAddress.toAddAddressRequest()
        platformUiState = platformUiState.copy(saveState = SaveState.Saving)

        viewModelScope.launch {
            isLoading = true
            if (platformUiState.isEditingExisting && !platformUiState.editingAddressId.isNullOrBlank()) {
                val addressId = platformUiState.editingAddressId!!
                when (val result = repository.updateAddress(customerId, addressId, req)) {
                    is ApiResult.Success -> {
                        val updatedList = addresses.map { if (it.id == addressId) result.data else it }
                        addresses = updatedList
                        selectedAddress = result.data
                        errorMessage = null
                        isLoading = false
                        platformUiState = platformUiState.copy(
                            saveState = SaveState.Success(result.data),
                            currentStep = AddressPlatformStep.AddressBook
                        )
                    }
                    is ApiResult.Failure -> {
                        errorMessage = result.error.message
                        isLoading = false
                        platformUiState = platformUiState.copy(saveState = SaveState.Error(result.error.message))
                    }
                }
            } else {
                when (val result = repository.addAddress(customerId, req)) {
                    is ApiResult.Success -> {
                        val updatedList = listOf(result.data) + addresses.filter { !it.isDefault || !result.data.isDefault }
                        addresses = updatedList
                        selectedAddress = result.data
                        errorMessage = null
                        isLoading = false
                        platformUiState = platformUiState.copy(
                            saveState = SaveState.Success(result.data),
                            currentStep = AddressPlatformStep.AddressBook
                        )
                    }
                    is ApiResult.Failure -> {
                        errorMessage = result.error.message
                        isLoading = false
                        platformUiState = platformUiState.copy(saveState = SaveState.Error(result.error.message))
                    }
                }
            }
        }
    }

    fun addAddress(request: AddAddressRequest, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.addAddress(customerId, request)) {
                is ApiResult.Success -> {
                    addresses = listOf(result.data) + addresses.filter { !it.isDefault || !result.data.isDefault }
                    selectedAddress = result.data
                    errorMessage = null
                    isLoading = false
                    onComplete(true)
                }
                is ApiResult.Failure -> {
                    errorMessage = result.error.message
                    isLoading = false
                    onComplete(false)
                }
            }
        }
    }

    fun updateAddress(addressId: String, request: AddAddressRequest, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.updateAddress(customerId, addressId, request)) {
                is ApiResult.Success -> {
                    addresses = addresses.map { if (it.id == addressId) result.data else it }
                    if (selectedAddress?.id == addressId) {
                        selectedAddress = result.data
                    }
                    errorMessage = null
                    isLoading = false
                    onComplete(true)
                }
                is ApiResult.Failure -> {
                    errorMessage = result.error.message
                    isLoading = false
                    onComplete(false)
                }
            }
        }
    }

    fun deleteAddress(addressId: String) {
        val target = addresses.find { it.id == addressId }
        val wasDefault = target?.isDefault == true

        viewModelScope.launch {
            when (val result = repository.deleteAddress(customerId, addressId)) {
                is ApiResult.Success -> {
                    val remaining = addresses.filter { it.id != addressId }
                    if (wasDefault && remaining.isNotEmpty()) {
                        val newDefaultId = remaining.first().id
                        setDefaultAddress(newDefaultId)
                    } else {
                        addresses = remaining
                        if (selectedAddress?.id == addressId) {
                            selectedAddress = remaining.firstOrNull()
                        }
                    }
                }
                is ApiResult.Failure -> errorMessage = result.error.message
            }
        }
    }

    fun setDefaultAddress(addressId: String) {
        viewModelScope.launch {
            when (val result = repository.setDefaultAddress(customerId, addressId)) {
                is ApiResult.Success -> {
                    addresses = addresses.map { it.copy(isDefault = it.id == addressId) }
                    selectedAddress = result.data
                }
                is ApiResult.Failure -> errorMessage = result.error.message
            }
        }
    }

    fun reset() {
        customerId = ""
        addresses = emptyList()
        selectedAddress = null
        profile = null
        isLoading = false
        errorMessage = null
        platformUiState = AddressPlatformUiState()
    }
}
