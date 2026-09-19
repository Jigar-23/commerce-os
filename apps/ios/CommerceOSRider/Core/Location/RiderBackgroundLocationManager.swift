import Foundation
import CoreLocation
import Combine

public final class RiderBackgroundLocationManager: NSObject, ObservableObject, CLLocationManagerDelegate {
    public static let shared = RiderBackgroundLocationManager()

    @Published public var lastLocation: CLLocation? = nil
    @Published public var currentBearing: Double = 0.0
    @Published public var currentSpeed: Double = 0.0
    @Published public var isTracking: Bool = false

    public var activeDeliveryId: String? = nil

    public var currentLocation: CLLocation? {
        return lastLocation ?? locationManager.location
    }

    private let locationManager = CLLocationManager()

    public override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        locationManager.distanceFilter = 5.0
        locationManager.pausesLocationUpdatesAutomatically = false
        locationManager.activityType = .automotiveNavigation
    }

    public func startBackgroundTracking() {
        locationManager.requestWhenInUseAuthorization()
        locationManager.requestAlwaysAuthorization()
        #if !targetEnvironment(simulator)
        locationManager.allowsBackgroundLocationUpdates = true
        #endif
        locationManager.showsBackgroundLocationIndicator = true
        locationManager.startUpdatingLocation()
        locationManager.startUpdatingHeading()
        isTracking = true
        if let current = locationManager.location {
            self.lastLocation = current
            RiderTelemetryStreamer.shared.processLocationUpdate(current)
            RiderGeofenceDetector.shared.updateLocation(current)
        }
    }

    public func stopBackgroundTracking() {
        guard isTracking else { return }
        locationManager.stopUpdatingLocation()
        locationManager.stopUpdatingHeading()
        #if !targetEnvironment(simulator)
        locationManager.allowsBackgroundLocationUpdates = false
        #endif
        isTracking = false
    }

    // MARK: - CLLocationManagerDelegate
    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        self.lastLocation = location

        if location.course >= 0 {
            self.currentBearing = location.course
        }

        if location.speed >= 0 {
            self.currentSpeed = location.speed * 3.6 // m/s to km/h
        } else {
            self.currentSpeed = 0.0
        }

        // 1. Immediately forward GPS fix to active telemetry streamer
        RiderTelemetryStreamer.shared.processLocationUpdate(location)

        // 2. Immediately forward GPS fix to geofence detector
        RiderGeofenceDetector.shared.updateLocation(location)

        // 3. Trigger dispatch poll check
        Task { @MainActor in
            await RiderOfferEventPipeline.shared.pollActiveOffersOnce()
        }
    }

    public func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        if newHeading.headingAccuracy >= 0 {
            self.currentBearing = newHeading.trueHeading > 0 ? newHeading.trueHeading : newHeading.magneticHeading
        }
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("[RiderLocation] Manager failed with error: \(error.localizedDescription)")
    }
}
