import Foundation
import CoreLocation

/**
 * 1:1 Parity with Android GeocodingProvider.kt (200 lines).
 * Provider-independent protocol for reverse and forward geocoding.
 */
public protocol GeocodingProvider {
    func reverseGeocode(latitude: Double, longitude: Double) async -> GeocodedPlace?
    func forwardGeocode(query: String) async -> [GeocodedPlace]
}

/**
 * Native iOS implementation of GeocodingProvider leveraging CLGeocoder.
 * Parses subLocality, thoroughfare, postal code, locality, and administrative area.
 */
public final class DefaultGeocodingProvider: GeocodingProvider {
    public static let shared = DefaultGeocodingProvider()

    private let geocoder = CLGeocoder()

    public init() {}

    public func reverseGeocode(latitude: Double, longitude: Double) async -> GeocodedPlace? {
        let location = CLLocation(latitude: latitude, longitude: longitude)
        do {
            let placemarks = try await geocoder.reverseGeocodeLocation(location)
            guard let pm = placemarks.first else { return nil }
            return parsePlacemarkToGeocodedPlace(pm, lat: latitude, lng: longitude)
        } catch {
            return nil
        }
    }

    public func forwardGeocode(query: String) async -> [GeocodedPlace] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        do {
            let placemarks = try await geocoder.geocodeAddressString(trimmed)
            return placemarks.compactMap { pm in
                guard let loc = pm.location else { return nil }
                return parsePlacemarkToGeocodedPlace(pm, lat: loc.coordinate.latitude, lng: loc.coordinate.longitude)
            }
        } catch {
            return []
        }
    }

    private func parsePlacemarkToGeocodedPlace(_ pm: CLPlacemark, lat: Double, lng: Double) -> GeocodedPlace {
        let name = pm.name
        let street = pm.thoroughfare
        let subLocality = pm.subLocality
        let locality = pm.locality ?? pm.subAdministrativeArea ?? "Delhi"
        let state = pm.administrativeArea ?? "Delhi"
        let postalCode = pm.postalCode ?? "110001"
        let country = pm.country ?? "India"

        var fullParts: [String] = []
        if let n = name { fullParts.append(n) }
        if let s = street, s != name { fullParts.append(s) }
        if let sl = subLocality { fullParts.append(sl) }
        fullParts.append(locality)
        fullParts.append(state)
        fullParts.append(postalCode)

        let formatted = fullParts.joined(separator: ", ")

        return GeocodedPlace(
            placeId: "geo_\(lat)_\(lng)",
            formattedAddress: formatted,
            houseNumber: pm.subThoroughfare,
            street: street,
            subLocality: subLocality,
            locality: locality,
            city: locality,
            district: pm.subAdministrativeArea,
            state: state,
            postalCode: postalCode,
            country: country,
            confidence: .high,
            geoPoint: GeoPoint(latitude: lat, longitude: lng)
        )
    }
}
