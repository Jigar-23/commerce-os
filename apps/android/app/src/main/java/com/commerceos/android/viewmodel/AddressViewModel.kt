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

    var liveGpsLocation by mutableStateOf<GeoPoint?>(null)
        private set

    var liveGpsPlace by mutableStateOf<GeocodedPlace?>(null)
        private set

    var isAcquiringLiveGps by mutableStateOf(false)
        private set

    val calculatedEtaMinutes: Int
        get() {
            val addr = selectedAddress
            if (addr != null && addr.latitude != null && addr.longitude != null && addr.latitude != 0.0 && addr.longitude != 0.0) {
                return RealTimeEtaEngine.calculateEtaMinutes(addr.latitude, addr.longitude)
            }
            val live = liveGpsLocation
            if (live != null && live.latitude != 0.0 && live.longitude != 0.0) {
                return RealTimeEtaEngine.calculateEtaMinutes(live.latitude, live.longitude)
            }
            return 0
        }

    val activeLocationHeaderLabel: String
        get() {
            val addr = selectedAddress ?: addresses.firstOrNull()
            if (addr != null && addr.addressLine.isNotBlank()) {
                if (addr.id.startsWith("temp_gps_") || addr.tag.equals("Current Location", ignoreCase = true)) {
                    val place = liveGpsPlace
                    val colony = place?.subLocality?.takeIf { it.isNotBlank() }
                        ?: place?.locality?.takeIf { it.isNotBlank() }
                        ?: addr.addressLine.split(",").firstOrNull()?.trim()
                        ?: "Current Location"
                    val city = place?.city?.takeIf { it.isNotBlank() } ?: addr.city
                    return cleanAddressSegments(colony, city).ifBlank { "Current Location" }
                }
                val tag = addr.tag.ifBlank { "Home" }
                val userDetails = addr.addressLine.trim()
                return if (userDetails.isNotBlank()) {
                    "$tag - $userDetails"
                } else {
                    val cleanFallback = cleanAddressSegments(addr.city, addr.postalCode)
                    if (cleanFallback.isNotBlank()) "$tag - $cleanFallback" else "Current Location"
                }
            }
            val place = liveGpsPlace
            if (place != null) {
                val colony = place.subLocality ?: place.locality ?: place.street ?: place.city
                val label = cleanAddressSegments(colony, place.city)
                if (label.isNotBlank()) return label
            }
            if (isAcquiringLiveGps) {
                return "Detecting location..."
            }
            return "Current Location"
        }

    // Single Canonical UI State Container for Platform State Machine
    var platformUiState by mutableStateOf(AddressPlatformUiState())
        private set

    // In-memory cache for preserving uncommitted draft address across navigation / backstack pops
    var cachedDraftAddress: StructuredAddress? = null
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
        fetchLiveGpsFallback()
    }

    fun fetchLiveGpsFallback() {
        useCurrentGpsLocationAndMatchSaved()
    }

    private fun matchProximityOrFallbackPinpoint(geoPoint: GeoPoint) {
        // Check if any saved address is within a 100-meter radius of the current GPS pinpoint
        val matchingSaved = addresses
            .mapNotNull { addr ->
                val aLat = addr.latitude
                val aLng = addr.longitude
                if (aLat != null && aLng != null && aLat != 0.0 && aLng != 0.0) {
                    val distMeters = RealTimeEtaEngine.calculateDistanceKm(geoPoint.latitude, geoPoint.longitude, aLat, aLng) * 1000.0
                    if (distMeters <= 100.0) addr to distMeters else null
                } else null
            }
            .minByOrNull { it.second }
            ?.first

        if (matchingSaved != null) {
            selectedAddress = matchingSaved
        } else {
            val place = liveGpsPlace
            val fullArea = cleanAddressSegments(
                place?.subLocality,
                place?.locality,
                place?.city
            ).ifBlank { cleanAddressSegments(place?.formattedAddress).ifBlank { "Current Location" } }

            selectedAddress = ApiAddress(
                id = "temp_gps_${System.currentTimeMillis()}",
                tag = "Current Location",
                addressType = "OTHER",
                addressLine = fullArea,
                city = place?.city ?: "",
                state = place?.state ?: "",
                postalCode = place?.postalCode ?: "",
                latitude = geoPoint.latitude,
                longitude = geoPoint.longitude,
                isDefault = false
            )
        }
    }

    fun useCurrentGpsLocationAndMatchSaved(onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            val provider = activeLocationProvider
            if (provider == null || !provider.hasPermission()) {
                onCompleted()
                return@launch
            }
            isAcquiringLiveGps = true
            when (val locResult = provider.getCurrentLocation(5000L)) {
                is LocationResult.Success -> {
                    val geoPoint = locResult.location
                    liveGpsLocation = geoPoint
                    val geocoder = activeGeocodingProvider
                    if (geocoder != null) {
                        when (val placeResult = geocoder.reverseGeocode(geoPoint.latitude, geoPoint.longitude)) {
                            is ApiResult.Success -> {
                                liveGpsPlace = placeResult.data
                            }
                            is ApiResult.Failure -> {}
                        }
                    }
                    matchProximityOrFallbackPinpoint(geoPoint)
                }
                is LocationResult.Failure -> {}
            }
            isAcquiringLiveGps = false
            onCompleted()
        }
    }

    /**
     * Explicit pinpoint selection: Sets active delivery location strictly to the
     * exact GPS pinpoint, bypassing proximity snap to nearby saved addresses.
     */
    fun useCurrentGpsLocationPinpoint(onCompleted: () -> Unit = {}) {
        viewModelScope.launch {
            val provider = activeLocationProvider
            if (provider == null || !provider.hasPermission()) {
                onCompleted()
                return@launch
            }
            isAcquiringLiveGps = true
            when (val locResult = provider.getCurrentLocation(5000L)) {
                is LocationResult.Success -> {
                    val geoPoint = locResult.location
                    liveGpsLocation = geoPoint
                    val geocoder = activeGeocodingProvider
                    if (geocoder != null) {
                        when (val placeResult = geocoder.reverseGeocode(geoPoint.latitude, geoPoint.longitude)) {
                            is ApiResult.Success -> {
                                liveGpsPlace = placeResult.data
                            }
                            is ApiResult.Failure -> {}
                        }
                    }

                    val place = liveGpsPlace
                    val fullArea = cleanAddressSegments(
                        place?.subLocality,
                        place?.locality,
                        place?.city
                    ).ifBlank { cleanAddressSegments(place?.formattedAddress).ifBlank { "Current Location" } }

                    selectedAddress = ApiAddress(
                        id = "temp_gps_${System.currentTimeMillis()}",
                        tag = "Current Location",
                        addressType = "OTHER",
                        addressLine = fullArea,
                        city = place?.city ?: "",
                        state = place?.state ?: "",
                        postalCode = place?.postalCode ?: "",
                        latitude = geoPoint.latitude,
                        longitude = geoPoint.longitude,
                        isDefault = false
                    )
                }
                is LocationResult.Failure -> {}
            }
            isAcquiringLiveGps = false
            onCompleted()
        }
    }

    fun getDistanceToSavedAddressMeters(address: ApiAddress): Double? {
        val live = liveGpsLocation ?: return null
        val lat = address.latitude ?: return null
        val lng = address.longitude ?: return null
        if (lat == 0.0 || lng == 0.0) return null
        return RealTimeEtaEngine.calculateDistanceKm(live.latitude, live.longitude, lat, lng) * 1000.0
    }

    fun cancelFlow() {
        platformUiState = AddressPlatformUiState(
            currentStep = AddressPlatformStep.AddressBook,
            originalAddress = null,
            draftAddress = StructuredAddress()
        )
    }

    fun init(customerId: String, force: Boolean = false) {
        val idChanged = this.customerId != customerId
        this.customerId = customerId
        if (idChanged || force || addresses.isEmpty()) {
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
                    val live = liveGpsLocation
                    if (live != null) {
                        matchProximityOrFallbackPinpoint(live)
                    } else {
                        val currentId = selectedAddress?.id
                        selectedAddress = result.data.firstOrNull { it.id == currentId }
                            ?: result.data.firstOrNull { it.isDefault }
                            ?: result.data.firstOrNull()
                        useCurrentGpsLocationAndMatchSaved()
                    }
                    errorMessage = if (result.data.isEmpty()) "No saved addresses yet" else null
                }
                is ApiResult.Failure -> {
                    errorMessage = result.error.message
                }
            }
            isLoading = false
        }
    }

    fun select(address: ApiAddress) {
        selectedAddress = address
    }

    // --- Single Source of Truth State Machine Transitions ---

    fun discardDraft() {
        cachedDraftAddress = null
    }

    fun startAddAddressFlow(forceReset: Boolean = false) {
        val existingDraft = cachedDraftAddress
        if (!forceReset && existingDraft != null && (existingDraft.houseNumber.isNotBlank() || existingDraft.geoLocation != null)) {
            val resumeGeo = existingDraft.geoLocation ?: liveGpsLocation
            platformUiState = AddressPlatformUiState(
                currentStep = if (resumeGeo != null) AddressPlatformStep.LocationSelected(resumeGeo) else AddressPlatformStep.SearchingLocation(""),
                originalAddress = null,
                draftAddress = existingDraft,
                activeGeocodedPlace = liveGpsPlace,
                isEditingExisting = false,
                editingAddressId = null
            )
            return
        }

        val initialGeo = liveGpsLocation ?: selectedAddress?.let {
            val lat = it.latitude
            val lng = it.longitude
            if (lat != null && lng != null && lat != 0.0 && lng != 0.0) GeoPoint(lat, lng) else null
        }
        val initialPlace = liveGpsPlace
        val initialDraft = if (initialPlace != null) {
            StructuredAddress(
                geoLocation = initialGeo,
                street = initialPlace.street.orEmpty(),
                subLocality = initialPlace.subLocality.orEmpty(),
                locality = initialPlace.locality.orEmpty(),
                city = initialPlace.city,
                state = initialPlace.state,
                postalCode = initialPlace.postalCode,
                contactPhone = profile?.phone?.replace("+91", "")?.trim() ?: ""
            )
        } else {
            StructuredAddress(
                geoLocation = initialGeo,
                contactPhone = profile?.phone?.replace("+91", "")?.trim() ?: ""
            )
        }

        cachedDraftAddress = initialDraft
        platformUiState = AddressPlatformUiState(
            currentStep = if (initialGeo != null) AddressPlatformStep.LocationSelected(initialGeo) else AddressPlatformStep.SearchingLocation(""),
            originalAddress = null,
            draftAddress = initialDraft,
            activeGeocodedPlace = initialPlace,
            isEditingExisting = false,
            editingAddressId = null
        )

        // Only acquire GPS if no initial location is available
        if (initialGeo == null) {
            requestCurrentGpsLocation()
        }
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
        viewModelScope.launch {
            val provider = activePlaceSearchProvider
            if (provider is GooglePlacesSearchProvider && result.placeId.isNotBlank()) {
                platformUiState = platformUiState.copy(isReverseGeocoding = true)
                when (val placeRes = provider.fetchPlaceDetails(result.placeId)) {
                    is ApiResult.Success -> {
                        val place = placeRes.data
                        val structured = StructuredAddress(
                            street = place.street.orEmpty(),
                            subLocality = place.subLocality.orEmpty(),
                            locality = place.locality.orEmpty(),
                            city = place.city,
                            state = place.state,
                            postalCode = place.postalCode,
                            country = place.country,
                            geoLocation = place.geoPoint,
                            placeId = place.placeId,
                            houseNumber = platformUiState.draftAddress.houseNumber
                        )
                        platformUiState = platformUiState.copy(
                            currentStep = AddressPlatformStep.ConfirmingPin(place),
                            activeGeocodedPlace = place,
                            draftAddress = structured,
                            isReverseGeocoding = false
                        )
                        provider.activeCameraCenter = com.google.android.gms.maps.model.LatLng(
                            place.geoPoint.latitude,
                            place.geoPoint.longitude
                        )
                    }
                    is ApiResult.Failure -> {
                        if (result.geoPoint != null) {
                            onMapCameraSettled(result.geoPoint.latitude, result.geoPoint.longitude)
                        }
                        platformUiState = platformUiState.copy(isReverseGeocoding = false)
                    }
                }
            } else if (result.geoPoint != null) {
                val geoPoint = result.geoPoint
                platformUiState = platformUiState.copy(
                    currentStep = AddressPlatformStep.LocationSelected(geoPoint),
                    draftAddress = platformUiState.draftAddress.copy(geoLocation = geoPoint)
                )
                onMapCameraSettled(geoPoint.latitude, geoPoint.longitude)
            }
        }
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
                    liveGpsLocation = geoPoint
                    platformUiState = platformUiState.copy(
                        locationAcquisitionState = LocationAcquisitionState.Success(geoPoint),
                        currentStep = AddressPlatformStep.LocationSelected(geoPoint),
                        draftAddress = platformUiState.draftAddress.copy(geoLocation = geoPoint)
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
        val provider = activePlaceSearchProvider
        if (provider is GooglePlacesSearchProvider) {
            provider.activeCameraCenter = com.google.android.gms.maps.model.LatLng(lat, lng)
        }
        val geoPoint = GeoPoint(latitude = lat, longitude = lng, accuracyMeters = null, provider = "user_pin")
        platformUiState = platformUiState.copy(
            currentStep = AddressPlatformStep.ReverseGeocoding(geoPoint),
            isReverseGeocoding = true,
            draftAddress = platformUiState.draftAddress.copy(geoLocation = geoPoint)
        )

        reverseGeocodeJob = viewModelScope.launch {
            delay(150) // Responsive debounce
            val geocoder = activeGeocodingProvider
            if (geocoder != null) {
                when (val result = geocoder.reverseGeocode(lat, lng)) {
                    is ApiResult.Success -> {
                        val place = result.data
                        val resolvedSubLocality = place.subLocality.orEmpty()
                        val resolvedStreet = place.street.orEmpty()
                        val resolvedLocality = place.locality.orEmpty()
                        val resolvedCity = place.city.ifBlank { resolvedLocality }

                        // Strictly bind to the new coordinates' geocoded values without leaking previous pin data
                        val updatedForm = platformUiState.draftAddress.copy(
                            street = resolvedStreet,
                            subLocality = resolvedSubLocality,
                            locality = resolvedLocality,
                            city = resolvedCity,
                            state = place.state,
                            postalCode = place.postalCode,
                            country = place.country.ifBlank { "India" },
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
                        val degradedPlace = GeocodedPlace(
                            formattedAddress = "Pinned Location (${String.format(java.util.Locale.US, "%.5f", lat)}, ${String.format(java.util.Locale.US, "%.5f", lng)})",
                            city = platformUiState.draftAddress.city,
                            state = platformUiState.draftAddress.state,
                            postalCode = platformUiState.draftAddress.postalCode,
                            geoPoint = geoPoint
                        )
                        platformUiState = platformUiState.copy(
                            draftAddress = platformUiState.draftAddress.copy(
                                geoLocation = geoPoint,
                                street = "",
                                subLocality = ""
                            ),
                            activeGeocodedPlace = degradedPlace,
                            isReverseGeocoding = false,
                            locationErrorMessage = "Couldn't auto-resolve address details. Please verify and enter street and landmark manually.",
                            currentStep = AddressPlatformStep.ConfirmingPin(degradedPlace)
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
        cachedDraftAddress = updated
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
        val draft = platformUiState.draftAddress
        val effectivePhone = draft.contactPhone.ifBlank {
            profile?.phone?.replace("+91", "")?.trim() ?: ""
        }
        val formToValidate = draft.copy(contactPhone = effectivePhone)
        val validation = formToValidate.validate()
        if (!validation.isValid) {
            val firstError = validation.houseNumberError
                ?: validation.postalCodeError
                ?: validation.contactPhoneError
                ?: validation.contactNameError
                ?: "Please enter required address details"
            errorMessage = firstError
            platformUiState = platformUiState.copy(saveState = SaveState.Error(firstError))
            return
        }

        val req = formToValidate.toAddAddressRequest()
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
                        cachedDraftAddress = null
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
                        val listWithoutNew = addresses.filter { it.id != result.data.id }
                        val updatedList = if (result.data.isDefault) {
                            listOf(result.data) + listWithoutNew.map { it.copy(isDefault = false) }
                        } else {
                            listOf(result.data) + listWithoutNew
                        }
                        addresses = updatedList
                        selectedAddress = result.data
                        cachedDraftAddress = null
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

    fun resetSaveState() {
        platformUiState = platformUiState.copy(saveState = SaveState.Idle)
    }

    fun addAddress(request: AddAddressRequest, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            isLoading = true
            when (val result = repository.addAddress(customerId, request)) {
                is ApiResult.Success -> {
                    val listWithoutNew = addresses.filter { it.id != result.data.id }
                    val updatedList = if (result.data.isDefault) {
                        listOf(result.data) + listWithoutNew.map { it.copy(isDefault = false) }
                    } else {
                        listOf(result.data) + listWithoutNew
                    }
                    addresses = updatedList
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
        val previousAddresses = addresses
        val previousSelected = selectedAddress
        val target = addresses.find { it.id == addressId }
        val wasDefault = target?.isDefault == true

        // 1. Optimistic Local State Update (Instant 0ms UI update)
        val remaining = addresses.filter { it.id != addressId }
        if (wasDefault && remaining.isNotEmpty()) {
            val newDefault = remaining.first().copy(isDefault = true)
            addresses = listOf(newDefault) + remaining.drop(1).map { it.copy(isDefault = false) }
            selectedAddress = newDefault
        } else {
            addresses = remaining
            if (selectedAddress?.id == addressId) {
                selectedAddress = remaining.firstOrNull()
            }
        }

        // Fallback to Live GPS location immediately if no saved address remains
        if (selectedAddress == null) {
            val live = liveGpsLocation
            val place = liveGpsPlace
            if (live != null) {
                val fullArea = cleanAddressSegments(
                    place?.subLocality,
                    place?.locality,
                    place?.city
                ).ifBlank { cleanAddressSegments(place?.formattedAddress).ifBlank { "Current Location" } }

                selectedAddress = ApiAddress(
                    id = "temp_gps_${System.currentTimeMillis()}",
                    tag = "Current Location",
                    addressType = "OTHER",
                    addressLine = fullArea,
                    city = place?.city ?: "",
                    state = place?.state ?: "",
                    postalCode = place?.postalCode ?: "",
                    latitude = live.latitude,
                    longitude = live.longitude,
                    isDefault = false
                )
            } else {
                useCurrentGpsLocationPinpoint()
            }
        }

        // 2. Server Synchronization
        viewModelScope.launch {
            val targetCustId = customerId.ifBlank { "usr_383700" }
            when (val result = repository.deleteAddress(targetCustId, addressId)) {
                is ApiResult.Success -> {
                    // If the deleted address was default and there is a new default, sync with server
                    if (wasDefault && remaining.isNotEmpty()) {
                        val newDefaultId = addresses.firstOrNull()?.id
                        if (!newDefaultId.isNullOrBlank()) {
                            repository.setDefaultAddress(targetCustId, newDefaultId)
                        }
                    }
                }
                is ApiResult.Failure -> {
                    // If the address was already deleted or not found on server (404), do not rollback
                    val msg = result.error.message.lowercase()
                    if (!msg.contains("404") && !msg.contains("not found")) {
                        // Rollback only on actual critical failure
                        addresses = previousAddresses
                        selectedAddress = previousSelected
                        errorMessage = "Failed to delete address: ${result.error.message}"
                    }
                }
            }
        }
    }

    fun setDefaultAddress(addressId: String) {
        // Optimistic UI update
        addresses = addresses.map { it.copy(isDefault = it.id == addressId) }
        val target = addresses.find { it.id == addressId }
        if (target != null) {
            selectedAddress = target
        }

        viewModelScope.launch {
            when (val result = repository.setDefaultAddress(customerId, addressId)) {
                is ApiResult.Success -> {
                    addresses = addresses.map { it.copy(isDefault = it.id == addressId) }
                    selectedAddress = result.data
                }
                is ApiResult.Failure -> {
                    // Non-fatal if address exists locally
                }
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
        cachedDraftAddress = null
        platformUiState = AddressPlatformUiState()
    }
}
