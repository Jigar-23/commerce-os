import Foundation
import CoreLocation
import Combine

public final class AddressRepository: ObservableObject {
    public static let shared = AddressRepository()

    private let apiClient: APIClient
    private let locationService: LocationService

    @Published public var addresses: [AddressDto] = []
    @Published public var selectedAddress: AddressDto? = nil
    @Published public var isLoading: Bool = false
    @Published public var calculatedEtaMinutes: Int = 11
    @Published public var errorMessage: String? = nil

    private let selectedAddressKey = "commerceos_selected_address_id"
    private var cancellables = Set<AnyCancellable>()

    public init(apiClient: APIClient = .shared, locationService: LocationService = .shared) {
        self.apiClient = apiClient
        self.locationService = locationService

        // Try restoring last selected address from cache
        restoreCachedSelection()
    }

    public var locationHeaderTitle: String {
        if let addr = selectedAddress {
            if addr.id.hasPrefix("temp_gps") || addr.displayTag.caseInsensitiveCompare("Current Location") == .orderedSame {
                return addr.addressLine.isEmpty ? "Current Location" : addr.addressLine
            }
            let tag = addr.displayTag
            let summary = addr.displaySummary
            return "\(tag) - \(summary)"
        }
        return "Select Delivery Location"
    }

    public var locationHeaderSubtitle: String {
        "Delivering in \(calculatedEtaMinutes) mins"
    }

    public func loadAddresses(customerId: String) async {
        guard !customerId.isEmpty else { return }
        await MainActor.run { self.isLoading = true }

        do {
            let list: [AddressDto] = try await apiClient.get(endpoint: "/api/v1/customers/\(customerId)/addresses")
            await MainActor.run {
                self.addresses = list
                self.isLoading = false

                // Select matching or default
                let savedId = UserDefaults.standard.string(forKey: self.selectedAddressKey)
                if let match = list.first(where: { $0.id == savedId }) {
                    self.selectedAddress = match
                } else if let def = list.first(where: { $0.isDefault }) {
                    self.selectedAddress = def
                } else if let first = list.first {
                    self.selectedAddress = first
                } else if self.selectedAddress == nil {
                    // Fallback to GPS
                    Task { await self.useCurrentLocationFallback() }
                }
                self.recalculateEta()
            }
        } catch {
            await MainActor.run {
                self.isLoading = false
                self.errorMessage = error.localizedDescription
                if self.selectedAddress == nil {
                    Task { await self.useCurrentLocationFallback() }
                }
            }
        }
    }

    public func selectAddress(_ address: AddressDto) {
        self.selectedAddress = address
        UserDefaults.standard.set(address.id, forKey: selectedAddressKey)
        recalculateEta()
    }

    public func addAddress(customerId: String, address: AddressDto) async throws -> AddressDto {
        struct AddBody: Codable {
            let addressType: String
            let addressLine: String
            let city: String
            let postalCode: String
            let latitude: Double
            let longitude: Double
            let isDefault: Bool
            let contactPhone: String
        }

        let summary = address.displaySummary
        let cleanLine = !summary.isEmpty ? summary : (!address.addressLine.isEmpty ? address.addressLine : "Rewari Central Hub, Model Town")
        let cleanCity = !(address.city ?? "").isEmpty ? (address.city ?? "Rewari") : "Rewari"
        let cleanPostal = !(address.postalCode ?? "").isEmpty ? (address.postalCode ?? "123401") : "123401"
        let cleanLat = (address.latitude != 0.0) ? address.latitude : 28.202224
        let cleanLng = (address.longitude != 0.0) ? address.longitude : 76.615418
        let phone = address.contactPhone ?? address.recipientPhone ?? UserDefaults.standard.string(forKey: "customer_phone") ?? "+919991416180"

        let body = AddBody(
            addressType: address.addressType ?? "HOME",
            addressLine: cleanLine,
            city: cleanCity,
            postalCode: cleanPostal,
            latitude: cleanLat,
            longitude: cleanLng,
            isDefault: address.isDefault,
            contactPhone: phone
        )

        let saved: AddressDto = try await apiClient.post(
            endpoint: "/api/v1/customers/\(customerId)/addresses",
            body: body
        )

        await MainActor.run {
            self.addresses.insert(saved, at: 0)
            self.selectAddress(saved)
        }
        return saved
    }

    public func deleteAddress(customerId: String, addressId: String) async throws {
        let _: [String: AnyCodableValue]? = try? await apiClient.request(
            endpoint: "/api/v1/customers/\(customerId)/addresses/\(addressId)",
            method: "DELETE"
        )

        await MainActor.run {
            self.addresses.removeAll(where: { $0.id == addressId })
            if self.selectedAddress?.id == addressId {
                if let next = self.addresses.first {
                    self.selectAddress(next)
                } else {
                    Task { await self.useCurrentLocationFallback() }
                }
            }
        }
    }

    public func setDefaultAddress(customerId: String, addressId: String) async throws {
        // Attempt specialized default-shipping endpoint, fallback to PUT
        do {
            let res: AddressDto = try await apiClient.post(
                endpoint: "/api/v1/customers/\(customerId)/addresses/\(addressId)/default-shipping",
                body: [String: String]()
            )
            await MainActor.run {
                self.addresses = self.addresses.map { $0.updatingDefault($0.id == addressId) }
                self.selectAddress(res)
            }
        } catch {
            struct PutBody: Codable {
                let isDefault: Bool
            }
            let _: AddressDto = try await apiClient.request(
                endpoint: "/api/v1/customers/\(customerId)/addresses/\(addressId)",
                method: "PUT",
                body: try? JSONEncoder().encode(PutBody(isDefault: true))
            )
            await loadAddresses(customerId: customerId)
        }
    }

    /**
     * 20-Meter GPS Matching Algorithm matching Android AddressViewModel.kt:
     * - Requests device GPS
     * - Reverse geocodes place
     * - Matches saved addresses within 20 meters
     * - If matched: selects existing saved address
     * - Else: assigns ephemeral temp_gps address
     */
    public func useCurrentLocation() async {
        do {
            let (loc, placemark) = try await locationService.requestCurrentLocation()
            let userLat = loc.coordinate.latitude
            let userLng = loc.coordinate.longitude

            await MainActor.run {
                // Check if any saved address is within a 20-meter radius of the current GPS pinpoint
                let matchingSaved = self.addresses.compactMap { addr -> (AddressDto, Double)? in
                    guard addr.latitude != 0.0, addr.longitude != 0.0 else { return nil }
                    let distMeters = RealTimeEtaEngine.calculateDistanceKm(
                        lat1: userLat, lon1: userLng,
                        lat2: addr.latitude, lon2: addr.longitude
                    ) * 1000.0
                    return distMeters <= 20.0 ? (addr, distMeters) : nil
                }.min(by: { $0.1 < $1.1 })?.0

                if let matched = matchingSaved {
                    self.selectAddress(matched)
                } else {
                    let colony = placemark?.subLocality ?? placemark?.locality ?? placemark?.name ?? "Current Location"
                    let city = placemark?.locality ?? placemark?.administrativeArea ?? ""
                    let line = [colony, city].filter { !$0.isEmpty }.joined(separator: ", ")

                    let gpsAddress = AddressDto(
                        id: "temp_gps_\(Int(Date().timeIntervalSince1970))",
                        tag: "Current Location",
                        addressType: "OTHER",
                        addressLine: line.isEmpty ? "Current Location" : line,
                        city: city,
                        postalCode: placemark?.postalCode ?? "",
                        latitude: userLat,
                        longitude: userLng,
                        isDefault: false
                    )
                    self.selectAddress(gpsAddress)
                }
            }
        } catch {
            await MainActor.run {
                self.errorMessage = "Could not acquire GPS location: \(error.localizedDescription)"
            }
        }
    }

    public func useCurrentLocationFallback() async {
        guard selectedAddress == nil else { return }
        await useCurrentLocation()
    }

    private func restoreCachedSelection() {
        self.selectedAddress = nil
    }

    private func recalculateEta() {
        guard let addr = selectedAddress, addr.latitude != 0.0, addr.longitude != 0.0 else {
            self.calculatedEtaMinutes = 11
            return
        }
        self.calculatedEtaMinutes = RealTimeEtaEngine.calculateEtaMinutes(userLat: addr.latitude, userLng: addr.longitude)
    }
}

// Minimal helper for generic decoding
public struct AnyCodableValue: Codable {}
