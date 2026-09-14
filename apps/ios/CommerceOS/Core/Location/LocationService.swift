import Foundation
import CoreLocation
import Combine

public final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    public static let shared = LocationService()

    private let locationManager = CLLocationManager()
    private let geocoder = CLGeocoder()

    @Published public var currentLocation: CLLocation? = nil
    @Published public var currentPlacemark: CLPlacemark? = nil
    @Published public var authorizationStatus: CLAuthorizationStatus = .notDetermined
    @Published public var isLocating: Bool = false
    @Published public var locationError: String? = nil

    private var locationContinuation: CheckedContinuation<(location: CLLocation, placemark: CLPlacemark?), Error>?

    private override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBest
        self.authorizationStatus = locationManager.authorizationStatus
    }

    public func requestAuthorization() {
        locationManager.requestWhenInUseAuthorization()
    }

    public func requestCurrentLocation() async throws -> (location: CLLocation, placemark: CLPlacemark?) {
        let auth = locationManager.authorizationStatus
        if auth == .notDetermined {
            locationManager.requestWhenInUseAuthorization()
        }

        return try await withCheckedThrowingContinuation { continuation in
            self.locationContinuation = continuation
            self.isLocating = true
            self.locationManager.requestLocation()
        }
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        DispatchQueue.main.async {
            self.authorizationStatus = manager.authorizationStatus
        }
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        DispatchQueue.main.async {
            self.currentLocation = location
            self.isLocating = false
        }

        // Reverse geocode
        geocoder.reverseGeocodeLocation(location) { [weak self] placemarks, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if let p = placemarks?.first {
                    self.currentPlacemark = p
                }
                if let continuation = self.locationContinuation {
                    self.locationContinuation = nil
                    continuation.resume(returning: (location: location, placemark: self.currentPlacemark))
                }
            }
        }
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        DispatchQueue.main.async {
            self.isLocating = false
            self.locationError = error.localizedDescription
            if let continuation = self.locationContinuation {
                self.locationContinuation = nil
                // Fallback to last known location or throw
                if let loc = self.currentLocation {
                    continuation.resume(returning: (location: loc, placemark: self.currentPlacemark))
                } else {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    public func reverseGeocode(coordinate: CLLocationCoordinate2D) async -> CLPlacemark? {
        let location = CLLocation(latitude: coordinate.latitude, longitude: coordinate.longitude)
        do {
            let placemarks = try await geocoder.reverseGeocodeLocation(location)
            return placemarks.first
        } catch {
            return nil
        }
    }
}
