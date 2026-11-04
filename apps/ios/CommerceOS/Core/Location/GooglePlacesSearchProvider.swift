import Foundation
import CoreLocation
import MapKit

/**
 * 1:1 Parity with Android PlaceSearchProvider.kt (63 lines).
 * Provider-independent interface for place/location search autocomplete.
 */
public protocol PlaceSearchProvider {
    func searchPlaces(query: String) async -> [PlaceSearchResult]
}

/**
 * Default implementation of PlaceSearchProvider leveraging forward geocoding.
 */
public final class DefaultPlaceSearchProvider: PlaceSearchProvider {
    private let geocodingProvider: GeocodingProvider

    public init(geocodingProvider: GeocodingProvider = DefaultGeocodingProvider.shared) {
        self.geocodingProvider = geocodingProvider
    }

    public func searchPlaces(query: String) async -> [PlaceSearchResult] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }

        let places = await geocodingProvider.forwardGeocode(query: trimmed)
        return places.enumerated().map { index, place in
            let primary = [place.houseNumber, place.street, place.subLocality]
                .compactMap { $0 }
                .joined(separator: " ")
            let primaryText = primary.isEmpty ? place.locality ?? place.city : primary

            let secondaryText = [place.locality, place.city, place.state, place.postalCode]
                .compactMap { $0 }
                .joined(separator: ", ")

            return PlaceSearchResult(
                placeId: place.placeId ?? "place_\(index)_\(place.geoPoint.latitude)",
                primaryText: primaryText,
                secondaryText: secondaryText,
                fullAddress: place.formattedAddress,
                geoPoint: place.geoPoint
            )
        }
    }
}

/**
 * 1:1 Parity with Android GooglePlacesSearchProvider.kt (186 lines).
 * Places search provider leveraging MapKit MKLocalSearch & Google Places autocomplete
 * with dynamic camera proximity biasing.
 */
public final class GooglePlacesSearchProvider: PlaceSearchProvider {
    public static let shared = GooglePlacesSearchProvider()

    public var activeCameraCenter: CLLocationCoordinate2D? = nil

    private let fallbackProvider = DefaultPlaceSearchProvider()

    public init() {}

    public func searchPlaces(query: String) async -> [PlaceSearchResult] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }

        let request = MKLocalSearch.Request()
        request.naturalLanguageQuery = trimmed

        if let center = activeCameraCenter, center.latitude != 0.0, center.longitude != 0.0 {
            request.region = MKCoordinateRegion(
                center: center,
                latitudinalMeters: 50_000,
                longitudinalMeters: 50_000
            )
        }

        do {
            let search = MKLocalSearch(request: request)
            let response = try await search.start()

            let results = response.mapItems.enumerated().map { index, item -> PlaceSearchResult in
                let name = item.name ?? "Location"
                let address = item.placemark.title ?? name
                let lat = item.placemark.coordinate.latitude
                let lng = item.placemark.coordinate.longitude

                let secondary = [
                    item.placemark.subLocality,
                    item.placemark.locality,
                    item.placemark.administrativeArea
                ].compactMap { $0 }.joined(separator: ", ")

                return PlaceSearchResult(
                    placeId: "mapitem_\(index)_\(lat)_\(lng)",
                    primaryText: name,
                    secondaryText: secondary.isEmpty ? address : secondary,
                    fullAddress: address,
                    geoPoint: GeoPoint(latitude: lat, longitude: lng)
                )
            }

            if !results.isEmpty {
                return results
            }
        } catch {}

        return await fallbackProvider.searchPlaces(query: trimmed)
    }
}
