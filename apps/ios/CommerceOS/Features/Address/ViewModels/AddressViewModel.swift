import Foundation
import CoreLocation
import MapKit
import Combine

/**
 * Production ViewModel for Commerce OS Address & Location Subsystem.
 * 1:1 Parity with Android AddressViewModel.kt
 * Serves as the Single Source of Truth for:
 * - 20-meter GPS matching algorithm
 * - AddressPlatformStep state machine
 * - Place search with 300ms debounce
 * - Camera settle reverse geocoding with 350ms debounce
 * - Conflict detection & suggestion
 * - Instant 0ms optimistic address mutations (Add, Edit, Set Default, Delete)
 */
public final class AddressViewModel: ObservableObject {
    public static let shared = AddressViewModel()

    private let apiClient: APIClient
    private let locationService: LocationService

    @Published public private(set) var customerId: String = ""
    @Published public private(set) var addresses: [AddressDto] = []
    @Published public private(set) var selectedAddress: AddressDto? = nil
    @Published public private(set) var profile: CustomerProfileDto? = nil
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var errorMessage: String? = nil

    @Published public private(set) var liveGpsLocation: GeoPoint? = nil
    @Published public private(set) var liveGpsPlace: GeocodedPlace? = nil
    @Published public private(set) var isAcquiringLiveGps: Bool = false

    // Single Canonical UI State Container for Platform State Machine
    @Published public private(set) var platformUiState: AddressPlatformUiState = AddressPlatformUiState()

    private var placeSearchTask: Task<Void, Never>? = nil
    private var reverseGeocodeTask: Task<Void, Never>? = nil

    private let selectedAddressKey = "commerceos_selected_address_id"

    public init(
        apiClient: APIClient = .shared,
        locationService: LocationService = .shared
    ) {
        self.apiClient = apiClient
        self.locationService = locationService
    }

    public var activeFormAddress: StructuredAddress {
        platformUiState.draftAddress
    }

    public var calculatedEtaMinutes: Int {
        if let addr = selectedAddress, addr.latitude != 0.0, addr.longitude != 0.0 {
            return RealTimeEtaEngine.calculateEtaMinutes(userLat: addr.latitude, userLng: addr.longitude)
        }
        if let live = liveGpsLocation, live.latitude != 0.0, live.longitude != 0.0 {
            return RealTimeEtaEngine.calculateEtaMinutes(userLat: live.latitude, userLng: live.longitude)
        }
        return 11
    }

    public var activeLocationHeaderLabel: String {
        if let addr = selectedAddress {
            if addr.id.hasPrefix("temp_gps_") || addr.displayTag.caseInsensitiveCompare("Current Location") == .orderedSame {
                let place = liveGpsPlace
                let colony = place?.subLocality?.isEmpty == false ? place!.subLocality! :
                    (place?.locality?.isEmpty == false ? place!.locality! :
                    (addr.addressLine.components(separatedBy: ",").first?.trimmingCharacters(in: .whitespaces) ?? "Current Location"))
                let city = place?.city.isEmpty == false ? place!.city : (addr.city ?? "")
                return "\(colony), \(city)".trimmingCharacters(in: CharacterSet(charactersIn: ", "))
            }
            let tag = addr.displayTag.isEmpty ? "Home" : addr.displayTag
            let line = addr.addressLine.isEmpty ? "\(addr.city ?? "") \(addr.postalCode ?? "")".trimmingCharacters(in: .whitespaces) : addr.addressLine
            return "\(tag) - \(line)"
        }
        if let place = liveGpsPlace {
            let colony = place.subLocality ?? place.locality ?? place.street ?? place.city
            return "\(colony), \(place.city)"
        }
        return "Select Delivery Location"
    }

    public func initialize(customerId: String, force: Bool = false) {
        let idChanged = self.customerId != customerId
        self.customerId = customerId
        if idChanged || force || addresses.isEmpty {
            loadProfile()
            loadAddresses()
        }
    }

    public func fetchLiveGpsFallback() {
        if !addresses.isEmpty && selectedAddress != nil { return }
        useCurrentGpsLocationAndMatchSaved()
    }

    /**
     * 20-Meter GPS Pinpoint Matching Algorithm matching Android AddressViewModel.kt:
     * - Acquires high-precision device location
     * - Reverse geocodes coordinates via Apple CLGeocoder
     * - Checks if any saved customer address is within a 20-meter radius
     * - If matched: selects existing saved address
     * - Else: builds ephemeral temporary GPS address
     */
    public func useCurrentGpsLocationAndMatchSaved(onCompleted: @escaping () -> Void = {}) {
        Task { @MainActor in
            self.isAcquiringLiveGps = true
            do {
                let (loc, placemark) = try await locationService.requestCurrentLocation()
                let geoPoint = GeoPoint(
                    latitude: loc.coordinate.latitude,
                    longitude: loc.coordinate.longitude,
                    accuracyMeters: Float(loc.horizontalAccuracy),
                    timestamp: Int64(loc.timestamp.timeIntervalSince1970 * 1000),
                    provider: "core_location"
                )
                self.liveGpsLocation = geoPoint

                let geocodedPlace = parsePlacemarkToGeocodedPlace(placemark: placemark, geoPoint: geoPoint)
                self.liveGpsPlace = geocodedPlace

                // 20-meter saved address radius check
                let matchingSaved = self.addresses.compactMap { addr -> (AddressDto, Double)? in
                    guard addr.latitude != 0.0, addr.longitude != 0.0 else { return nil }
                    let distMeters = RealTimeEtaEngine.calculateDistanceKm(
                        lat1: geoPoint.latitude,
                        lon1: geoPoint.longitude,
                        lat2: addr.latitude,
                        lon2: addr.longitude
                    ) * 1000.0
                    return distMeters <= 20.0 ? (addr, distMeters) : nil
                }.min(by: { $0.1 < $1.1 })?.0

                if let matched = matchingSaved {
                    self.selectedAddress = matched
                    UserDefaults.standard.set(matched.id, forKey: self.selectedAddressKey)
                } else {
                    let fullArea = [
                        geocodedPlace.subLocality,
                        geocodedPlace.locality != geocodedPlace.subLocality ? geocodedPlace.locality : nil,
                        geocodedPlace.city
                    ].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")

                    let tempGpsAddress = AddressDto(
                        id: "temp_gps_\(Int(Date().timeIntervalSince1970))",
                        tag: "Current Location",
                        addressType: "OTHER",
                        addressLine: fullArea.isEmpty ? (geocodedPlace.formattedAddress.isEmpty ? "Current Location" : geocodedPlace.formattedAddress) : fullArea,
                        city: geocodedPlace.city,
                        state: geocodedPlace.state,
                        postalCode: geocodedPlace.postalCode,
                        latitude: geoPoint.latitude,
                        longitude: geoPoint.longitude,
                        isDefault: false
                    )
                    self.selectedAddress = tempGpsAddress
                    UserDefaults.standard.set(tempGpsAddress.id, forKey: self.selectedAddressKey)
                }
            } catch {
                self.errorMessage = error.localizedDescription
            }
            self.isAcquiringLiveGps = false
            onCompleted()
        }
    }

    public func loadProfile() {
        guard !customerId.isEmpty else { return }
        Task { @MainActor in
            do {
                let p: CustomerProfileDto = try await apiClient.get(endpoint: "/api/v1/customers/\(customerId)")
                self.profile = p
            } catch {
                // Non-fatal
            }
        }
    }

    public func loadAddresses() {
        guard !customerId.isEmpty else { return }
        Task { @MainActor in
            self.isLoading = true
            do {
                let list: [AddressDto] = try await apiClient.get(endpoint: "/api/v1/customers/\(customerId)/addresses")
                self.addresses = list
                let currentId = self.selectedAddress?.id ?? UserDefaults.standard.string(forKey: self.selectedAddressKey)
                self.selectedAddress = list.first(where: { $0.id == currentId }) ??
                    list.first(where: { $0.isDefault }) ??
                    list.first
                self.errorMessage = list.isEmpty ? "No saved addresses yet" : nil
            } catch {
                self.errorMessage = error.localizedDescription
            }
            self.isLoading = false
        }
    }

    public func select(_ address: AddressDto) {
        self.selectedAddress = address
        UserDefaults.standard.set(address.id, forKey: selectedAddressKey)
    }

    // MARK: - State Machine Flow Operations (1:1 Parity with Android AddressViewModel)

    public func startAddAddressFlow() {
        let initialGeo = liveGpsLocation ?? selectedAddress.flatMap { addr -> GeoPoint? in
            guard addr.latitude != 0.0, addr.longitude != 0.0 else { return nil }
            return GeoPoint(latitude: addr.latitude, longitude: addr.longitude, accuracyMeters: 10.0)
        }
        let initialPlace = liveGpsPlace
        let cleanPhone = (profile?.phone ?? UserDefaults.standard.string(forKey: "customer_phone") ?? "")
            .replacingOccurrences(of: "+91", with: "")
            .trimmingCharacters(in: .whitespaces)

        let initialDraft: StructuredAddress
        if let p = initialPlace {
            initialDraft = StructuredAddress(
                tag: "Home",
                street: p.street ?? "",
                subLocality: p.subLocality ?? "",
                locality: p.locality ?? "",
                city: p.city,
                state: p.state,
                postalCode: p.postalCode,
                country: p.country,
                contactPhone: cleanPhone,
                geoLocation: initialGeo
            )
        } else {
            initialDraft = StructuredAddress(
                tag: "Home",
                contactPhone: cleanPhone,
                geoLocation: initialGeo
            )
        }

        self.platformUiState = AddressPlatformUiState(
            currentStep: initialGeo != nil ? .locationSelected(point: initialGeo!) : .searchingLocation(query: ""),
            originalAddress: nil,
            draftAddress: initialDraft,
            isEditingExisting: false,
            editingAddressId: nil,
            activeGeocodedPlace: initialPlace
        )

        // Request high-precision GPS lock on opening Add Address flow
        requestCurrentGpsLocation()
    }

    public func startEditAddressFlow(_ address: AddressDto) {
        let structured = StructuredAddress.fromAddressDto(address)
        self.platformUiState = AddressPlatformUiState(
            currentStep: .editingDetails,
            originalAddress: structured,
            draftAddress: structured,
            isEditingExisting: true,
            editingAddressId: address.id
        )

        if let geoPoint = structured.geoLocation {
            onMapCameraSettled(lat: geoPoint.latitude, lng: geoPoint.longitude)
        }
    }

    public func navigateBack() {
        switch platformUiState.currentStep {
        case .searchingLocation:
            platformUiState.currentStep = .addressBook
        case .locationSelected, .reverseGeocoding, .confirmingPin:
            platformUiState.currentStep = .searchingLocation(query: platformUiState.locationSearchQuery)
        case .editingDetails:
            if platformUiState.isEditingExisting {
                platformUiState.currentStep = .addressBook
            } else if let place = platformUiState.activeGeocodedPlace {
                platformUiState.currentStep = .confirmingPin(place: place)
            } else {
                platformUiState.currentStep = .searchingLocation(query: platformUiState.locationSearchQuery)
            }
        case .addressBook:
            break
        }
    }

    public func searchLocationPlaces(_ query: String) {
        platformUiState.locationSearchQuery = query
        platformUiState.currentStep = .searchingLocation(query: query)
        platformUiState.isSearchingPlaces = query.trimmingCharacters(in: .whitespaces).count >= 2

        placeSearchTask?.cancel()
        let clean = query.trimmingCharacters(in: .whitespaces)
        guard clean.count >= 2 else {
            platformUiState.placeSearchResults = []
            platformUiState.isSearchingPlaces = false
            return
        }

        placeSearchTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 300_000_000) // 300ms debounce
            guard !Task.isCancelled else { return }

            let request = MKLocalSearch.Request()
            request.naturalLanguageQuery = clean
            let search = MKLocalSearch(request: request)
            do {
                let response = try await search.start()
                guard !Task.isCancelled else { return }

                self.platformUiState.placeSearchResults = response.mapItems.map { item in
                    let coord = item.placemark.coordinate
                    let primary = item.name ?? "Location"
                    let secondary = [item.placemark.subLocality, item.placemark.locality, item.placemark.administrativeArea]
                        .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")

                    var distStr: String? = nil
                    if let userLoc = self.liveGpsLocation {
                        let dKm = RealTimeEtaEngine.calculateDistanceKm(
                            lat1: userLoc.latitude, lon1: userLoc.longitude,
                            lat2: coord.latitude, lon2: coord.longitude
                        )
                        distStr = dKm < 1.0 ? "\(Int(dKm * 1000.0)) m" : String(format: "%.1f km", dKm)
                    }

                    return PlaceSearchResult(
                        placeId: "place_\(coord.latitude)_\(coord.longitude)",
                        primaryText: primary,
                        secondaryText: secondary.isEmpty ? (item.placemark.title ?? "") : secondary,
                        fullAddress: item.placemark.title ?? primary,
                        geoPoint: GeoPoint(latitude: coord.latitude, longitude: coord.longitude),
                        distance: distStr
                    )
                }
                self.platformUiState.isSearchingPlaces = false
            } catch {
                if !Task.isCancelled {
                    self.platformUiState.placeSearchResults = []
                    self.platformUiState.isSearchingPlaces = false
                }
            }
        }
    }

    public func selectPlaceSearchResult(_ result: PlaceSearchResult) {
        let geoPoint = result.geoPoint
        platformUiState.currentStep = .locationSelected(point: geoPoint)
        platformUiState.draftAddress.geoLocation = geoPoint
        onMapCameraSettled(lat: geoPoint.latitude, lng: geoPoint.longitude)
    }

    public func requestCurrentGpsLocation() {
        platformUiState.locationAcquisitionState = .acquiringGps
        platformUiState.locationErrorMessage = nil

        Task { @MainActor in
            do {
                let (loc, _) = try await locationService.requestCurrentLocation()
                let geoPoint = GeoPoint(
                    latitude: loc.coordinate.latitude,
                    longitude: loc.coordinate.longitude,
                    accuracyMeters: Float(loc.horizontalAccuracy),
                    timestamp: Int64(loc.timestamp.timeIntervalSince1970 * 1000),
                    provider: "core_location"
                )
                self.liveGpsLocation = geoPoint
                self.platformUiState.locationAcquisitionState = .success(geoPoint)
                self.platformUiState.currentStep = .locationSelected(point: geoPoint)
                self.platformUiState.draftAddress.geoLocation = geoPoint
                self.onMapCameraSettled(lat: geoPoint.latitude, lng: geoPoint.longitude)
            } catch {
                self.platformUiState.locationAcquisitionState = .error(error.localizedDescription)
                self.platformUiState.locationErrorMessage = error.localizedDescription
            }
        }
    }

    public func onMapCameraSettled(lat: Double, lng: Double) {
        reverseGeocodeTask?.cancel()
        let geoPoint = GeoPoint(latitude: lat, longitude: lng, accuracyMeters: nil, provider: "user_pin")
        platformUiState.currentStep = .reverseGeocoding(point: geoPoint)
        platformUiState.isReverseGeocoding = true

        reverseGeocodeTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 350_000_000) // 350ms debounce
            guard !Task.isCancelled else { return }

            let placemark = await locationService.reverseGeocode(coordinate: CLLocationCoordinate2D(latitude: lat, longitude: lng))
            guard !Task.isCancelled else { return }

            if let p = placemark {
                let place = self.parsePlacemarkToGeocodedPlace(placemark: p, geoPoint: geoPoint)
                var updatedForm = self.platformUiState.draftAddress
                updatedForm.street = place.street ?? place.subLocality ?? updatedForm.street
                updatedForm.subLocality = place.subLocality ?? updatedForm.subLocality
                updatedForm.locality = place.locality ?? updatedForm.locality
                if !place.city.isEmpty { updatedForm.city = place.city }
                if !place.state.isEmpty { updatedForm.state = place.state }
                if !place.postalCode.isEmpty { updatedForm.postalCode = place.postalCode }
                updatedForm.country = place.country
                updatedForm.geoLocation = geoPoint

                self.platformUiState.draftAddress = updatedForm
                self.platformUiState.activeGeocodedPlace = place
                self.platformUiState.isReverseGeocoding = false
                self.platformUiState.currentStep = .confirmingPin(place: place)
            } else {
                let degradedPlace = GeocodedPlace(
                    formattedAddress: String(format: "Pinned Location (%.5f, %.5f)", lat, lng),
                    city: self.platformUiState.draftAddress.city,
                    state: self.platformUiState.draftAddress.state,
                    postalCode: self.platformUiState.draftAddress.postalCode,
                    geoPoint: geoPoint
                )
                self.platformUiState.draftAddress.geoLocation = geoPoint
                self.platformUiState.activeGeocodedPlace = degradedPlace
                self.platformUiState.isReverseGeocoding = false
                self.platformUiState.locationErrorMessage = "Couldn't auto-resolve address details. Please verify and enter street and landmark manually."
                self.platformUiState.currentStep = .confirmingPin(place: degradedPlace)
            }
        }
    }

    public func confirmPinLocation() {
        if let place = platformUiState.activeGeocodedPlace {
            let conflict = platformUiState.draftAddress.detectAddressConflict(mapPlace: place)
            platformUiState.addressConflict = conflict
            platformUiState.currentStep = .editingDetails
        } else {
            platformUiState.currentStep = .editingDetails
        }
    }

    public func updateFormAddress(_ updated: StructuredAddress) {
        let conflict = updated.detectAddressConflict(mapPlace: platformUiState.activeGeocodedPlace)
        platformUiState.draftAddress = updated
        platformUiState.addressConflict = conflict
    }

    public func applyConflictSuggestion() {
        guard let place = platformUiState.activeGeocodedPlace else { return }
        platformUiState.draftAddress.city = place.city
        platformUiState.draftAddress.postalCode = place.postalCode
        platformUiState.draftAddress.state = place.state
        platformUiState.addressConflict = AddressConflictWarning(hasConflict: false)
    }

    public func submitSaveFormAddress(onCompleted: @escaping (Bool) -> Void = { _ in }) {
        let validation = platformUiState.draftAddress.validate()
        guard validation.isValid else { return }

        platformUiState.saveState = .saving
        let dto = platformUiState.draftAddress.toAddressDto()

        Task { @MainActor in
            self.isLoading = true
            let targetCustId = !self.customerId.isEmpty ? self.customerId : (UserDefaults.standard.string(forKey: "customer_id") ?? "usr_383700")

            if self.platformUiState.isEditingExisting, let addressId = self.platformUiState.editingAddressId, !addressId.isEmpty {
                do {
                    struct UpdateBody: Codable {
                        let addressLine: String
                        let city: String
                        let postalCode: String
                        let latitude: Double
                        let longitude: Double
                        let isDefault: Bool
                        let contactPhone: String
                    }
                    let body = UpdateBody(
                        addressLine: dto.addressLine,
                        city: dto.city ?? "Gurugram",
                        postalCode: dto.postalCode ?? "",
                        latitude: dto.latitude,
                        longitude: dto.longitude,
                        isDefault: dto.isDefault,
                        contactPhone: dto.contactPhone ?? dto.recipientPhone ?? ""
                    )
                    let updated: AddressDto = try await self.apiClient.request(
                        endpoint: "/api/v1/customers/\(targetCustId)/addresses/\(addressId)",
                        method: "PUT",
                        body: try? JSONEncoder().encode(body)
                    )
                    self.addresses = self.addresses.map { $0.id == addressId ? updated : $0 }
                    self.selectedAddress = updated
                    self.isLoading = false
                    self.platformUiState.saveState = .success(updated)
                    self.platformUiState.currentStep = .addressBook
                    onCompleted(true)
                } catch {
                    self.isLoading = false
                    self.platformUiState.saveState = .error(error.localizedDescription)
                    onCompleted(false)
                }
            } else {
                do {
                    let saved = try await AddressRepository.shared.addAddress(customerId: targetCustId, address: dto)
                    let listWithoutNew = self.addresses.filter { $0.id != saved.id }
                    if saved.isDefault {
                        self.addresses = [saved] + listWithoutNew.map { $0.updatingDefault(false) }
                    } else {
                        self.addresses = [saved] + listWithoutNew
                    }
                    self.selectedAddress = saved
                    self.isLoading = false
                    self.platformUiState.saveState = .success(saved)
                    self.platformUiState.currentStep = .addressBook
                    onCompleted(true)
                } catch {
                    self.isLoading = false
                    self.platformUiState.saveState = .error(error.localizedDescription)
                    onCompleted(false)
                }
            }
        }
    }

    public func resetSaveState() {
        platformUiState.saveState = .idle
    }

    /**
     * 1:1 Parity with Android deleteAddress:
     * - Instant 0ms optimistic local UI removal
     * - If deleted address was default, auto-promotes first remaining address as default
     * - Server synchronization; tolerant of 404
     */
    public func deleteAddress(addressId: String) {
        let previousAddresses = addresses
        let previousSelected = selectedAddress
        let target = addresses.first(where: { $0.id == addressId })
        let wasDefault = target?.isDefault == true

        // 1. Optimistic Local State Update (Instant 0ms UI update)
        let remaining = addresses.filter { $0.id != addressId }
        if wasDefault && !remaining.isEmpty {
            let newDefault = remaining[0].updatingDefault(true)
            let rest = remaining.dropFirst().map { $0.updatingDefault(false) }
            addresses = [newDefault] + rest
            selectedAddress = newDefault
            UserDefaults.standard.set(newDefault.id, forKey: selectedAddressKey)
        } else {
            addresses = remaining
            if selectedAddress?.id == addressId {
                selectedAddress = remaining.first
                if let next = remaining.first {
                    UserDefaults.standard.set(next.id, forKey: selectedAddressKey)
                }
            }
        }

        // 2. Server Synchronization
        Task { @MainActor in
            let targetCustId = !self.customerId.isEmpty ? self.customerId : (UserDefaults.standard.string(forKey: "customer_id") ?? "usr_383700")
            do {
                try await AddressRepository.shared.deleteAddress(customerId: targetCustId, addressId: addressId)
                if wasDefault && !remaining.isEmpty, let newDefaultId = self.addresses.first?.id {
                    try? await AddressRepository.shared.setDefaultAddress(customerId: targetCustId, addressId: newDefaultId)
                }
            } catch {
                let msg = error.localizedDescription.lowercased()
                if !msg.contains("404") && !msg.contains("not found") {
                    // Rollback only on critical failure
                    self.addresses = previousAddresses
                    self.selectedAddress = previousSelected
                    self.errorMessage = "Failed to delete address: \(error.localizedDescription)"
                }
            }
        }
    }

    /**
     * 1:1 Parity with Android setDefaultAddress:
     * - Instant 0ms optimistic UI update
     * - Server synchronization via setDefaultAddress
     */
    public func setDefaultAddress(addressId: String) {
        addresses = addresses.map { $0.updatingDefault($0.id == addressId) }
        if let target = addresses.first(where: { $0.id == addressId }) {
            selectedAddress = target
            UserDefaults.standard.set(target.id, forKey: selectedAddressKey)
        }

        Task { @MainActor in
            let targetCustId = !self.customerId.isEmpty ? self.customerId : (UserDefaults.standard.string(forKey: "customer_id") ?? "usr_383700")
            try? await AddressRepository.shared.setDefaultAddress(customerId: targetCustId, addressId: addressId)
        }
    }

    public func reset() {
        customerId = ""
        addresses = []
        selectedAddress = nil
        profile = nil
        isLoading = false
        errorMessage = nil
        platformUiState = AddressPlatformUiState()
    }

    private func parsePlacemarkToGeocodedPlace(placemark: CLPlacemark?, geoPoint: GeoPoint) -> GeocodedPlace {
        guard let p = placemark else {
            return GeocodedPlace(
                formattedAddress: String(format: "Location (%.4f, %.4f)", geoPoint.latitude, geoPoint.longitude),
                city: "Local Area",
                state: "",
                postalCode: "",
                geoPoint: geoPoint
            )
        }
        let houseNo = p.subThoroughfare
        let street = p.thoroughfare
        let subLocality = p.subLocality
        let locality = p.locality ?? p.subAdministrativeArea
        let city = locality ?? p.administrativeArea ?? "Gurugram"
        let state = p.administrativeArea ?? ""
        let postalCode = p.postalCode ?? ""
        let country = p.country ?? "India"

        var parts: [String] = []
        if let h = houseNo, !h.isEmpty { parts.append(h) }
        if let s = street, !s.isEmpty { parts.append(s) }
        if let sub = subLocality, !sub.isEmpty { parts.append(sub) }
        if let loc = locality, !loc.isEmpty && loc != subLocality { parts.append(loc) }
        if !state.isEmpty { parts.append(state) }
        if !postalCode.isEmpty { parts.append(postalCode) }

        return GeocodedPlace(
            placeId: nil,
            formattedAddress: parts.isEmpty ? (p.name ?? "Pinned Location") : parts.joined(separator: ", "),
            houseNumber: houseNo,
            street: street,
            subLocality: subLocality,
            locality: locality,
            city: city,
            district: p.subAdministrativeArea,
            state: state,
            postalCode: postalCode,
            country: country,
            confidence: (!postalCode.isEmpty && (street != nil || subLocality != nil)) ? .high : (locality != nil ? .medium : .low),
            geoPoint: geoPoint
        )
    }
}

// Helper extension on AddressDto for immutable default toggling
extension AddressDto {
    public func updatingDefault(_ newDefault: Bool) -> AddressDto {
        AddressDto(
            id: id,
            customerId: customerId,
            label: label,
            tag: tag,
            addressType: addressType,
            addressLine: addressLine,
            city: city,
            state: state,
            postalCode: postalCode,
            flatNumber: flatNumber,
            landmark: landmark,
            latitude: latitude,
            longitude: longitude,
            recipientName: recipientName,
            recipientPhone: recipientPhone,
            contactPhone: contactPhone,
            isDefault: newDefault
        )
    }
}
