import SwiftUI
import MapKit

public struct ZomatoDarkMapView: UIViewRepresentable {
    public var riderCoordinate: CLLocationCoordinate2D?
    public var merchantCoordinate: CLLocationCoordinate2D?
    public var customerCoordinate: CLLocationCoordinate2D?
    public var routeCoordinates: [CLLocationCoordinate2D] = []
    public var riderBearing: Double = 0.0
    public var merchantTitle: String = "Fulfillment Hub"
    
    public init(
        riderCoordinate: CLLocationCoordinate2D? = nil,
        merchantCoordinate: CLLocationCoordinate2D? = nil,
        customerCoordinate: CLLocationCoordinate2D? = nil,
        routeCoordinates: [CLLocationCoordinate2D] = [],
        riderBearing: Double = 0.0,
        merchantTitle: String = "Fulfillment Hub"
    ) {
        self.riderCoordinate = riderCoordinate
        self.merchantCoordinate = merchantCoordinate
        self.customerCoordinate = customerCoordinate
        self.routeCoordinates = routeCoordinates
        self.riderBearing = riderBearing
        self.merchantTitle = merchantTitle
    }
    
    public func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.overrideUserInterfaceStyle = .dark
        mapView.mapType = .standard
        mapView.showsCompass = true
        mapView.showsScale = false
        mapView.isRotateEnabled = true
        
        let center = customerCoordinate ?? merchantCoordinate ?? riderCoordinate ?? CLLocationCoordinate2D(latitude: 0, longitude: 0)
        let region = MKCoordinateRegion(center: center, latitudinalMeters: 1800, longitudinalMeters: 1800)
        mapView.setRegion(region, animated: false)
        
        return mapView
    }
    
    public func updateUIView(_ uiView: MKMapView, context: Context) {
        // Update Overlays
        uiView.removeOverlays(uiView.overlays)
        if !routeCoordinates.isEmpty {
            let polyline = MKPolyline(coordinates: routeCoordinates, count: routeCoordinates.count)
            uiView.addOverlay(polyline)
        }
        
        // Update Annotations
        uiView.removeAnnotations(uiView.annotations)
        
        if let merchant = merchantCoordinate {
            let ann = CustomMapAnnotation(coordinate: merchant, title: merchantTitle, type: .merchant)
            uiView.addAnnotation(ann)
        }
        
        if let customer = customerCoordinate {
            let ann = CustomMapAnnotation(coordinate: customer, title: "Delivery Location", type: .customer)
            uiView.addAnnotation(ann)
        }
        
        if let rider = riderCoordinate {
            let ann = CustomMapAnnotation(coordinate: rider, title: "Delivery Partner", type: .rider, bearing: riderBearing)
            uiView.addAnnotation(ann)
        }
    }
    
    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    public class Coordinator: NSObject, MKMapViewDelegate {
        var parent: ZomatoDarkMapView
        
        init(_ parent: ZomatoDarkMapView) {
            self.parent = parent
        }
        
        public func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
            if let polyline = overlay as? MKPolyline {
                let renderer = MKPolylineRenderer(polyline: polyline)
                renderer.strokeColor = UIColor.systemOrange
                renderer.lineWidth = 4.5
                renderer.lineJoin = .round
                renderer.lineCap = .round
                return renderer
            }
            return MKOverlayRenderer(overlay: overlay)
        }
        
        public func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
            guard let customAnn = annotation as? CustomMapAnnotation else { return nil }
            
            let identifier = "CustomMarker_\(customAnn.type)"
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? MKMarkerAnnotationView
            
            if view == nil {
                view = MKMarkerAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                view?.canShowCallout = true
            } else {
                view?.annotation = annotation
            }
            
            switch customAnn.type {
            case .merchant:
                view?.markerTintColor = .systemGreen
                view?.glyphImage = UIImage(systemName: "cross.fill")
            case .customer:
                view?.markerTintColor = .systemBlue
                view?.glyphImage = UIImage(systemName: "house.fill")
            case .rider:
                view?.markerTintColor = .systemOrange
                view?.glyphImage = UIImage(systemName: "bicycle")
            }
            
            return view
        }
    }
}

public enum MapAnnotationType {
    case merchant
    case customer
    case rider
}

public class CustomMapAnnotation: NSObject, MKAnnotation {
    public let coordinate: CLLocationCoordinate2D
    public let title: String?
    public let type: MapAnnotationType
    public let bearing: Double
    
    public init(coordinate: CLLocationCoordinate2D, title: String?, type: MapAnnotationType, bearing: Double = 0.0) {
        self.coordinate = coordinate
        self.title = title
        self.type = type
        self.bearing = bearing
    }
}
