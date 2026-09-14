import SwiftUI
import MapKit

/**
 * 1:1 Parity with Android RealLocationMapViewport in MapProvider.kt
 * Displays interactive map with centered target delivery pin and reports camera settles.
 */
public struct LocationMapViewport: UIViewRepresentable {
    public var centerCoordinate: CLLocationCoordinate2D?
    public var onCameraSettled: (CLLocationCoordinate2D) -> Void

    public init(
        centerCoordinate: CLLocationCoordinate2D? = nil,
        onCameraSettled: @escaping (CLLocationCoordinate2D) -> Void = { _ in }
    ) {
        self.centerCoordinate = centerCoordinate
        self.onCameraSettled = onCameraSettled
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = true
        mapView.showsCompass = true
        mapView.isRotateEnabled = true
        mapView.mapType = .standard

        let initialCenter = centerCoordinate ?? CLLocationCoordinate2D(latitude: 28.1970, longitude: 76.6190)
        let region = MKCoordinateRegion(center: initialCenter, latitudinalMeters: 600, longitudinalMeters: 600)
        mapView.setRegion(region, animated: false)

        return mapView
    }

    public func updateUIView(_ uiView: MKMapView, context: Context) {
        if let target = centerCoordinate {
            let current = uiView.centerCoordinate
            let dLat = abs(current.latitude - target.latitude)
            let dLng = abs(current.longitude - target.longitude)
            // Only programmatic recenter if significantly shifted (> 10 meters)
            if dLat > 0.0001 || dLng > 0.0001 {
                let region = MKCoordinateRegion(center: target, latitudinalMeters: 600, longitudinalMeters: 600)
                uiView.setRegion(region, animated: true)
            }
        }
    }

    public class Coordinator: NSObject, MKMapViewDelegate {
        var parent: LocationMapViewport
        private var debounceTimer: Timer?

        init(_ parent: LocationMapViewport) {
            self.parent = parent
        }

        public func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            debounceTimer?.invalidate()
            debounceTimer = Timer.scheduledTimer(withTimeInterval: 0.35, repeats: false) { [weak self] _ in
                guard let self = self else { return }
                self.parent.onCameraSettled(mapView.centerCoordinate)
            }
        }
    }
}
