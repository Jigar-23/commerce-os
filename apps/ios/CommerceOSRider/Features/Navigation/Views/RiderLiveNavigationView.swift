import SwiftUI
import MapKit
import CoreLocation

public struct RiderLiveNavigationView: UIViewRepresentable {
    public let riderCoordinate: CLLocationCoordinate2D?
    public let destinationCoordinate: CLLocationCoordinate2D?
    public let riderBearing: Double

    public init(
        riderCoordinate: CLLocationCoordinate2D?,
        destinationCoordinate: CLLocationCoordinate2D?,
        riderBearing: Double = 0.0
    ) {
        self.riderCoordinate = riderCoordinate
        self.destinationCoordinate = destinationCoordinate
        self.riderBearing = riderBearing
    }

    public func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView(frame: .zero)
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = false
        mapView.isPitchEnabled = true
        mapView.isRotateEnabled = true
        mapView.overrideUserInterfaceStyle = .dark
        return mapView
    }

    public func updateUIView(_ uiView: MKMapView, context: Context) {
        context.coordinator.update(
            mapView: uiView,
            riderCoordinate: riderCoordinate,
            destinationCoordinate: destinationCoordinate,
            bearing: riderBearing
        )
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator()
    }

    public class Coordinator: NSObject, MKMapViewDelegate {
        private var routeOverlay: MKPolyline?
        private var riderAnnotation: MKPointAnnotation?
        private var destAnnotation: MKPointAnnotation?
        private var lastOrigin: CLLocationCoordinate2D?
        private var lastDestination: CLLocationCoordinate2D?

        func update(
            mapView: MKMapView,
            riderCoordinate: CLLocationCoordinate2D?,
            destinationCoordinate: CLLocationCoordinate2D?,
            bearing: Double
        ) {
            // Update rider marker
            if let rCoord = riderCoordinate {
                if let existing = riderAnnotation {
                    existing.coordinate = rCoord
                } else {
                    let annot = MKPointAnnotation()
                    annot.coordinate = rCoord
                    annot.title = "Rider"
                    mapView.addAnnotation(annot)
                    self.riderAnnotation = annot
                }

                // Update Camera tracking rider
                let camera = MKMapCamera(
                    lookingAtCenter: rCoord,
                    fromDistance: 800,
                    pitch: 45,
                    heading: bearing
                )
                mapView.setCamera(camera, animated: true)
            }

            // Update destination marker
            if let dCoord = destinationCoordinate {
                if let existing = destAnnotation {
                    existing.coordinate = dCoord
                } else {
                    let annot = MKPointAnnotation()
                    annot.coordinate = dCoord
                    annot.title = "Destination"
                    mapView.addAnnotation(annot)
                    self.destAnnotation = annot
                }
            }

            // Calculate road route overlay via MKDirections if coordinates changed
            if let rCoord = riderCoordinate, let dCoord = destinationCoordinate {
                let originChanged = lastOrigin == nil || abs(lastOrigin!.latitude - rCoord.latitude) > 0.001 || abs(lastOrigin!.longitude - rCoord.longitude) > 0.001
                let destChanged = lastDestination == nil || abs(lastDestination!.latitude - dCoord.latitude) > 0.0001 || abs(lastDestination!.longitude - dCoord.longitude) > 0.0001

                if originChanged || destChanged {
                    self.lastOrigin = rCoord
                    self.lastDestination = dCoord
                    requestRoadDirections(from: rCoord, to: dCoord, on: mapView)
                }
            }
        }

        private func requestRoadDirections(from origin: CLLocationCoordinate2D, to dest: CLLocationCoordinate2D, on mapView: MKMapView) {
            let req = MKDirections.Request()
            req.source = MKMapItem(placemark: MKPlacemark(coordinate: origin))
            req.destination = MKMapItem(placemark: MKPlacemark(coordinate: dest))
            req.transportType = .automobile

            let directions = MKDirections(request: req)
            directions.calculate { [weak self, weak mapView] response, error in
                guard let self = self, let mapView = mapView, let route = response?.routes.first else { return }
                if let old = self.routeOverlay {
                    mapView.removeOverlay(old)
                }
                self.routeOverlay = route.polyline
                mapView.addOverlay(route.polyline, level: .aboveRoads)
            }
        }

        public func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = UIColor(red: 16/255, green: 185/255, blue: 129/255, alpha: 0.95) // safetyGreen
                renderer.lineWidth = 6.0
                renderer.lineCap = .round
                renderer.lineJoin = .round
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }

        public func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            guard !(annotation is MKUserLocation) else { return nil }

            let identifier = "RiderMapPin"
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView
            if view == nil {
                view = MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                view?.canShowCallout = true
            } else {
                view?.annotation = annotation
            }

            if annotation.title == "Rider" {
                view?.glyphImage = UIImage(systemName: "bicycle")
                view?.markerTintColor = UIColor(red: 16/255, green: 185/255, blue: 129/255, alpha: 1.0)
            } else {
                view?.glyphImage = UIImage(systemName: "mappin.circle.fill")
                view?.markerTintColor = UIColor(red: 245/255, green: 158/255, blue: 11/255, alpha: 1.0)
            }

            return view
        }
    }
}
