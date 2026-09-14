import SwiftUI
import MapKit

/**
 * 1:1 Parity with Android AddressSelectionBottomSheet.kt
 * Displays:
 * - Header with dismiss chevron and "Select a location"
 * - Search bar with clear button
 * - Standalone "Use current location" card (Zomato pattern)
 * - Autocomplete place search predictions with proximity distance & saved address matching
 * - "+ Add Address" card
 * - Saved Addresses list with full `AddressCardView` hierarchy (Edit, Delete, Set Default)
 */
public struct AddressSelectionBottomSheet: View {
    @ObservedObject var addressRepository: AddressRepository
    @ObservedObject var addressViewModel: AddressViewModel = .shared
    let customerId: String
    let onDismiss: () -> Void
    let onAddNewAddress: () -> Void
    var onEditAddress: ((AddressDto) -> Void)? = nil

    @State private var searchQuery: String = ""
    @State private var searchResults: [PlaceSearchResult] = []
    @State private var isSearching: Bool = false
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var addressToDelete: AddressDto? = nil

    public init(
        addressRepository: AddressRepository = .shared,
        customerId: String,
        onDismiss: @escaping () -> Void,
        onAddNewAddress: @escaping () -> Void,
        onEditAddress: ((AddressDto) -> Void)? = nil
    ) {
        self.addressRepository = addressRepository
        self.customerId = customerId
        self.onDismiss = onDismiss
        self.onAddNewAddress = onAddNewAddress
        self.onEditAddress = onEditAddress
    }

    private var matchingSavedAddresses: [AddressDto] {
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        if q.isEmpty { return [] }
        return addressRepository.addresses.filter {
            $0.addressLine.localizedCaseInsensitiveContains(q) ||
            ($0.city?.localizedCaseInsensitiveContains(q) ?? false) ||
            $0.displayTag.localizedCaseInsensitiveContains(q) ||
            ($0.recipientName?.localizedCaseInsensitiveContains(q) ?? false)
        }
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Drag Handle
            Capsule()
                .fill(Color(hex: "CBD5E1"))
                .frame(width: 40, height: 4)
                .padding(.top, 10)
                .padding(.bottom, 10)

            // Title Header (1:1 with Android)
            HStack(spacing: 6) {
                Button(action: onDismiss) {
                    Image(systemName: "chevron.down")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                        .padding(4)
                }

                Text("Select a location")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundColor(Color(hex: "0F172A"))

                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 12)

            // Search Bar (1:1 with Android OutlinedTextField)
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Color(hex: "059669"))

                TextField("Search for area, street name...", text: $searchQuery)
                    .font(.system(size: 14))
                    .onChange(of: searchQuery) { val in
                        performPlacesSearch(query: val)
                    }

                if !searchQuery.isEmpty {
                    Button(action: {
                        searchQuery = ""
                        searchResults = []
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color.white)
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1.2)
            )
            .padding(.horizontal, 16)
            .padding(.bottom, 14)

            // Scrollable Content
            ScrollView(showsIndicators: false) {
                VStack(spacing: 12) {
                    // 1. Standalone "Use Current Location" Card (Zomato Pattern matching Android)
                    Button(action: {
                        Task {
                            await addressRepository.useCurrentLocation()
                            onDismiss()
                        }
                    }) {
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(Color(hex: "DCFCE7"))
                                    .frame(width: 36, height: 36)
                                Image(systemName: "location.fill")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(Color(hex: "059669"))
                            }

                            VStack(alignment: .leading, spacing: 2) {
                                Text("Use current location")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(Color(hex: "059669"))
                                Text("Using GPS for doorstep accuracy")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color(hex: "64748B"))
                            }

                            Spacer()

                            Image(systemName: "chevron.right")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "94A3B8"))
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(16)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                        )
                    }
                    .buttonStyle(PlainButtonStyle())

                    // 2. Active Search Mode: Priority Saved Addresses + Places Autocomplete
                    if searchQuery.trimmingCharacters(in: .whitespaces).count >= 2 {
                        VStack(spacing: 0) {
                            // A. Priority Saved Address Matches
                            ForEach(matchingSavedAddresses) { saved in
                                Button(action: {
                                    addressRepository.selectAddress(saved)
                                    onDismiss()
                                }) {
                                    HStack(spacing: 12) {
                                        VStack(alignment: .center, spacing: 2) {
                                            Image(systemName: "house.fill")
                                                .font(.system(size: 16))
                                                .foregroundColor(Color(hex: "0F172A"))
                                            Text("0 m")
                                                .font(.system(size: 10, weight: .medium))
                                                .foregroundColor(Color(hex: "64748B"))
                                        }
                                        .frame(width: 36)

                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(saved.displayTag.isEmpty ? "Saved Address" : saved.displayTag)
                                                .font(.system(size: 14, weight: .bold))
                                                .foregroundColor(Color(hex: "0F172A"))
                                            Text(saved.addressLine)
                                                .font(.system(size: 12))
                                                .foregroundColor(Color(hex: "64748B"))
                                                .lineLimit(1)
                                        }

                                        Spacer()
                                    }
                                    .padding(14)
                                }
                                .buttonStyle(PlainButtonStyle())

                                Divider()
                                    .background(Color(hex: "F1F5F9"))
                            }

                            // B. Places Autocomplete Predictions
                            if isSearching {
                                HStack {
                                    Spacer()
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "059669")))
                                        .padding(24)
                                    Spacer()
                                }
                            } else if searchResults.isEmpty && matchingSavedAddresses.isEmpty {
                                Text("No matching locations found")
                                    .font(.system(size: 13))
                                    .foregroundColor(Color(hex: "64748B"))
                                    .padding(16)
                            } else {
                                ForEach(searchResults) { place in
                                    Button(action: {
                                        selectPlaceResult(place)
                                    }) {
                                        HStack(spacing: 12) {
                                            VStack(alignment: .center, spacing: 2) {
                                                Image(systemName: "mappin.circle.fill")
                                                    .font(.system(size: 16))
                                                    .foregroundColor(Color(hex: "64748B"))
                                                if let d = place.distance {
                                                    Text(d)
                                                        .font(.system(size: 10, weight: .medium))
                                                        .foregroundColor(Color(hex: "64748B"))
                                                }
                                            }
                                            .frame(width: 36)

                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(place.primaryText)
                                                    .font(.system(size: 14, weight: .bold))
                                                    .foregroundColor(Color(hex: "0F172A"))
                                                Text(place.secondaryText)
                                                    .font(.system(size: 12))
                                                    .foregroundColor(Color(hex: "64748B"))
                                                    .lineLimit(1)
                                            }

                                            Spacer()
                                        }
                                        .padding(14)
                                    }
                                    .buttonStyle(PlainButtonStyle())

                                    if place.id != searchResults.last?.id {
                                        Divider()
                                            .background(Color(hex: "F1F5F9"))
                                    }
                                }
                            }
                        }
                        .background(Color.white)
                        .cornerRadius(16)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                        )
                    } else {
                        // 3. Default Mode: Add Address Button & Saved Addresses
                        Button(action: {
                            addressViewModel.startAddAddressFlow()
                            onAddNewAddress()
                            onDismiss()
                        }) {
                            HStack(spacing: 12) {
                                ZStack {
                                    Circle()
                                        .fill(Color(hex: "DCFCE7"))
                                        .frame(width: 36, height: 36)
                                    Image(systemName: "plus")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(Color(hex: "059669"))
                                }

                                Text("Add Address")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(Color(hex: "059669"))

                                Spacer()

                                Image(systemName: "chevron.right")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: "94A3B8"))
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(16)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                        }
                        .buttonStyle(PlainButtonStyle())

                        if !addressRepository.addresses.isEmpty {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("SAVED ADDRESSES")
                                    .font(.system(size: 11, weight: .black))
                                    .foregroundColor(Color(hex: "64748B"))
                                    .tracking(1)
                                    .padding(.horizontal, 4)
                                    .padding(.top, 4)

                                VStack(spacing: 10) {
                                    ForEach(addressRepository.addresses) { addr in
                                        AddressCardView(
                                            address: addr,
                                            isSelected: addr.id == addressRepository.selectedAddress?.id,
                                            onSelect: {
                                                addressRepository.selectAddress(addr)
                                                onDismiss()
                                            },
                                            onEdit: {
                                                addressViewModel.startEditAddressFlow(addr)
                                                if let onEdit = onEditAddress {
                                                    onEdit(addr)
                                                } else {
                                                    onAddNewAddress()
                                                }
                                                onDismiss()
                                            },
                                            onDelete: {
                                                addressToDelete = addr
                                            },
                                            onSetDefault: {
                                                Task {
                                                    try? await addressRepository.setDefaultAddress(customerId: customerId, addressId: addr.id)
                                                    addressViewModel.setDefaultAddress(addressId: addr.id)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
        }
        .background(Color(hex: "F8FAFC").ignoresSafeArea())
        .alert(item: $addressToDelete) { addr in
            Alert(
                title: Text("Delete Address"),
                message: Text("Are you sure you want to remove '\(addr.addressLine)'? This action cannot be undone."),
                primaryButton: .destructive(Text("Delete")) {
                    Task {
                        try? await addressRepository.deleteAddress(customerId: customerId, addressId: addr.id)
                        addressViewModel.deleteAddress(addressId: addr.id)
                    }
                },
                secondaryButton: .cancel()
            )
        }
    }

    private func performPlacesSearch(query: String) {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard clean.count >= 2 else {
            searchResults = []
            isSearching = false
            return
        }

        isSearching = true
        searchTask?.cancel()
        searchTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 300_000_000)
            guard !Task.isCancelled else { return }

            let request = MKLocalSearch.Request()
            request.naturalLanguageQuery = clean
            let search = MKLocalSearch(request: request)
            do {
                let response = try await search.start()
                guard !Task.isCancelled else { return }

                self.searchResults = response.mapItems.map { item in
                    let coord = item.placemark.coordinate
                    let primary = item.name ?? "Location"
                    let secondary = [item.placemark.subLocality, item.placemark.locality, item.placemark.administrativeArea]
                        .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")

                    var distStr: String? = nil
                    if let userLoc = addressViewModel.liveGpsLocation {
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
                self.isSearching = false
            } catch {
                if !Task.isCancelled {
                    self.searchResults = []
                    self.isSearching = false
                }
            }
        }
    }

    private func selectPlaceResult(_ place: PlaceSearchResult) {
        let coord = place.geoPoint.coordinate
        let selected = AddressDto(
            id: "temp_place_\(Int(Date().timeIntervalSince1970))",
            tag: "Selected Location",
            addressType: "OTHER",
            addressLine: place.fullAddress,
            city: place.secondaryText.components(separatedBy: ",").first?.trimmingCharacters(in: .whitespaces) ?? "Gurugram",
            postalCode: "",
            latitude: coord.latitude,
            longitude: coord.longitude,
            isDefault: false
        )
        addressRepository.selectAddress(selected)
        onDismiss()
    }
}
