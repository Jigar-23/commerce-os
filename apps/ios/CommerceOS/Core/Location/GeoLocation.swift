import Foundation
import CoreLocation

/**
 * Domain representation of a geographic coordinate.
 * Accuracy is nullable and NEVER synthetic/hardcoded.
 * 1:1 parity with Android GeoLocation.kt
 */
public struct GeoPoint: Codable, Equatable, Hashable {
    public let latitude: Double
    public let longitude: Double
    public let accuracyMeters: Float?
    public let timestamp: Int64?
    public let provider: String?

    public init(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float? = nil,
        timestamp: Int64? = Int64(Date().timeIntervalSince1970 * 1000),
        provider: String? = nil
    ) {
        self.latitude = latitude
        self.longitude = longitude
        self.accuracyMeters = accuracyMeters
        self.timestamp = timestamp
        self.provider = provider
    }

    public var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
    }
}

/**
 * Geocode confidence levels to assess data quality for delivery routing.
 */
public enum GeocodeConfidence: String, Codable, Equatable {
    case high = "HIGH"
    case medium = "MEDIUM"
    case low = "LOW"
}

/**
 * Structured geocoded place metadata returned by reverse/forward geocoders.
 * 1:1 parity with Android GeocodedPlace in GeoLocation.kt
 */
public struct GeocodedPlace: Codable, Equatable, Hashable {
    public let placeId: String?
    public let formattedAddress: String
    public let houseNumber: String?
    public let street: String?
    public let subLocality: String?
    public let locality: String?
    public let city: String
    public let district: String?
    public let state: String
    public let postalCode: String
    public let country: String
    public let confidence: GeocodeConfidence
    public let geoPoint: GeoPoint

    public init(
        placeId: String? = nil,
        formattedAddress: String,
        houseNumber: String? = nil,
        street: String? = nil,
        subLocality: String? = nil,
        locality: String? = nil,
        city: String,
        district: String? = nil,
        state: String,
        postalCode: String,
        country: String = "India",
        confidence: GeocodeConfidence = .medium,
        geoPoint: GeoPoint
    ) {
        self.placeId = placeId
        self.formattedAddress = formattedAddress
        self.houseNumber = houseNumber
        self.street = street
        self.subLocality = subLocality
        self.locality = locality
        self.city = city
        self.district = district
        self.state = state
        self.postalCode = postalCode
        self.country = country
        self.confidence = confidence
        self.geoPoint = geoPoint
    }
}

/**
 * Autocomplete location search result item.
 * 1:1 parity with Android PlaceSearchResult in GeoLocation.kt
 */
public struct PlaceSearchResult: Identifiable, Hashable, Equatable {
    public var id: String { placeId }
    public let placeId: String
    public let primaryText: String
    public let secondaryText: String
    public let fullAddress: String
    public let geoPoint: GeoPoint
    public let distance: String?

    public init(
        placeId: String,
        primaryText: String,
        secondaryText: String,
        fullAddress: String,
        geoPoint: GeoPoint,
        distance: String? = nil
    ) {
        self.placeId = placeId
        self.primaryText = primaryText
        self.secondaryText = secondaryText
        self.fullAddress = fullAddress
        self.geoPoint = geoPoint
        self.distance = distance
    }
}

/**
 * Hardware GPS & Runtime Permission state for Location Platform.
 */
public enum LocationPermissionState: Equatable {
    case idle
    case requesting
    case granted
    case denied(canRetry: Bool)
    case permanentlyDenied
    case locationDisabled
    case searching
    case success(GeoPoint)
    case error(String)
}

/**
 * Status wrapper for location acquisition tasks.
 */
public enum LocationResult: Equatable {
    case success(GeoPoint)
    case failure(reason: String, isPermissionError: Bool)
}

/**
 * RealTimeEtaEngine
 * 1:1 parity with Android RealTimeEtaEngine.kt
 */
public struct RealTimeEtaEngine {
    // Master Dark Store coordinates (Rewari / NCR Quick Commerce Hub)
    public static let defaultStoreLat: Double = 28.1970
    public static let defaultStoreLng: Double = 76.6190

    /**
     * Calculates the great-circle Haversine distance in kilometers.
     */
    public static func calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let r = 6371.0 // Earth radius in km
        let dLat = (lat2 - lat1) * .pi / 180.0
        let dLon = (lon2 - lon1) * .pi / 180.0
        let a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(lat1 * .pi / 180.0) * cos(lat2 * .pi / 180.0) *
                sin(dLon / 2.0) * sin(dLon / 2.0)
        let c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return r * c
    }

    /**
     * Real-time road trip ETA in minutes from store to delivery coordinate.
     * Takes into account:
     * 1. 1.30x urban road routing winding factor
     * 2. 4 minutes warehouse picking & packing SLA
     * 3. 20 km/h 2-wheeler average transit speed (3.0 mins/km)
     */
    public static func calculateEtaMinutes(
        userLat: Double?,
        userLng: Double?,
        storeLat: Double = defaultStoreLat,
        storeLng: Double = defaultStoreLng
    ) -> Int {
        guard let uLat = userLat, let uLng = userLng, uLat != 0.0, uLng != 0.0 else {
            return 11
        }
        let straightLineKm = calculateDistanceKm(lat1: storeLat, lon1: storeLng, lat2: uLat, lon2: uLng)
        let roadDistanceKm = straightLineKm * 1.30
        let prepTimeMinutes = 4.0
        let travelMinutes = roadDistanceKm * 3.0
        let totalMinutes = Int((prepTimeMinutes + travelMinutes).rounded())
        return min(max(totalMinutes, 8), 45)
    }

    public static func formatEtaLabel(minutes: Int) -> String {
        return "\(minutes) mins"
    }
}
