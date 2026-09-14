import Foundation

/**
 * Production State Machine steps for Commerce OS Address & Location Platform.
 * ViewModel is the SOLE source of truth for flow navigation.
 * 1:1 Parity with Android AddressPlatformStep in AddressPlatformState.kt
 */
public enum AddressPlatformStep: Equatable {
    /** Browsing saved addresses in address book */
    case addressBook

    /** Autocomplete place/location search */
    case searchingLocation(query: String)

    /** Location selected on real map, pin positioned */
    case locationSelected(point: GeoPoint)

    /** Async debounced reverse geocoding in progress */
    case reverseGeocoding(point: GeoPoint)

    /** Trust boundary: Location pin confirmation step */
    case confirmingPin(place: GeocodedPlace)

    /** Structured address details form */
    case editingDetails
}

/**
 * Explicit Save State for Address persistence operations.
 * 1:1 Parity with Android SaveState in AddressPlatformState.kt
 */
public enum SaveState: Equatable {
    case idle
    case saving
    case success(AddressDto)
    case error(String)
}

/**
 * Explicit GPS acquisition state.
 * 1:1 Parity with Android LocationAcquisitionState in AddressPlatformState.kt
 */
public enum LocationAcquisitionState: Equatable {
    case idle
    case acquiringGps
    case success(GeoPoint)
    case error(String)
}

/**
 * Single Canonical UI State Container for Address & Location Platform.
 * Maintains explicit separation between the immutable original edit snapshot
 * and the user's mutable draft address.
 * 1:1 Parity with Android AddressPlatformUiState in AddressPlatformState.kt
 */
public struct AddressPlatformUiState: Equatable {
    public var currentStep: AddressPlatformStep
    public var originalAddress: StructuredAddress?
    public var draftAddress: StructuredAddress
    public var isEditingExisting: Bool
    public var editingAddressId: String?
    public var locationSearchQuery: String
    public var placeSearchResults: [PlaceSearchResult]
    public var isSearchingPlaces: Bool
    public var activeGeocodedPlace: GeocodedPlace?
    public var isReverseGeocoding: Bool
    public var locationAcquisitionState: LocationAcquisitionState
    public var saveState: SaveState
    public var addressConflict: AddressConflictWarning
    public var locationErrorMessage: String?

    public init(
        currentStep: AddressPlatformStep = .addressBook,
        originalAddress: StructuredAddress? = nil,
        draftAddress: StructuredAddress = StructuredAddress(),
        isEditingExisting: Bool = false,
        editingAddressId: String? = nil,
        locationSearchQuery: String = "",
        placeSearchResults: [PlaceSearchResult] = [],
        isSearchingPlaces: Bool = false,
        activeGeocodedPlace: GeocodedPlace? = nil,
        isReverseGeocoding: Bool = false,
        locationAcquisitionState: LocationAcquisitionState = .idle,
        saveState: SaveState = .idle,
        addressConflict: AddressConflictWarning = AddressConflictWarning(hasConflict: false),
        locationErrorMessage: String? = nil
    ) {
        self.currentStep = currentStep
        self.originalAddress = originalAddress
        self.draftAddress = draftAddress
        self.isEditingExisting = isEditingExisting
        self.editingAddressId = editingAddressId
        self.locationSearchQuery = locationSearchQuery
        self.placeSearchResults = placeSearchResults
        self.isSearchingPlaces = isSearchingPlaces
        self.activeGeocodedPlace = activeGeocodedPlace
        self.isReverseGeocoding = isReverseGeocoding
        self.locationAcquisitionState = locationAcquisitionState
        self.saveState = saveState
        self.addressConflict = addressConflict
        self.locationErrorMessage = locationErrorMessage
    }

    public var isFlowActive: Bool {
        currentStep != .addressBook
    }

    public var isDirty: Bool {
        originalAddress != nil && draftAddress != originalAddress
    }

    public var activeFormAddress: StructuredAddress {
        draftAddress
    }
}
