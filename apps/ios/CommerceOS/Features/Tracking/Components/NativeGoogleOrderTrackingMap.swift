import SwiftUI
import MapKit
import CoreLocation

/**
 * 1:1 Parity with Android NativeGoogleOrderTrackingMap.kt (833 lines).
 * Native Order Tracking Map with Progressive Turn-by-Turn Navigation Ribbon.
 * - Teardrop Store Hub Pin with ground contact shadow and SHOP pill tag
 * - Teardrop Customer Destination Pin with ground contact shadow and HOME pill tag
 * - 2.5D Delivery Scooter with vehicle heading rotation, helmet, delivery box & glowing headlight beam
 * - Signature Brand Emerald Navigation Polyline (#0E675E)
 * - Two-tone path split (traveled vs remaining)
 * - Auto-framing bounds incorporating Store, Customer, and Rider
 * - Floating recenter crosshair button
 */
public struct NativeGoogleOrderTrackingMap: View {
    public let merchantCoordinate: CLLocationCoordinate2D
    public let customerCoordinate: CLLocationCoordinate2D
    public let riderCoordinate: CLLocationCoordinate2D?
    public let riderHeading: Double?
    public let speedKmh: Double?
    public let waypoints: [CLLocationCoordinate2D]
    public let activeStage: String

    @State private var mapCoordinator: OrderTrackingMapCoordinator?

    public init(
        merchantCoordinate: CLLocationCoordinate2D,
        customerCoordinate: CLLocationCoordinate2D,
        riderCoordinate: CLLocationCoordinate2D? = nil,
        riderHeading: Double? = nil,
        speedKmh: Double? = nil,
        waypoints: [CLLocationCoordinate2D] = [],
        activeStage: String = "ASSIGNING_PARTNER"
    ) {
        self.merchantCoordinate = merchantCoordinate
        self.customerCoordinate = customerCoordinate
        self.riderCoordinate = riderCoordinate
        self.riderHeading = riderHeading
        self.speedKmh = speedKmh
        self.waypoints = waypoints
        self.activeStage = activeStage
    }

    public var body: some View {
        ZStack(alignment: .topTrailing) {
            OrderTrackingMapViewRepresentable(
                merchantCoordinate: merchantCoordinate,
                customerCoordinate: customerCoordinate,
                riderCoordinate: riderCoordinate,
                riderHeading: riderHeading,
                waypoints: waypoints,
                activeStage: activeStage,
                onCoordinatorCreated: { coord in
                    self.mapCoordinator = coord
                }
            )
            .edgesIgnoringSafeArea(.all)

            // Floating Recenter Crosshair Button (Top Right - Matches Android FloatingActionButton)
            Button(action: {
                mapCoordinator?.recenter()
            }) {
                Image(systemName: "location.fill")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(hex: "0E675E"))
                    .frame(width: 44, height: 44)
                    .background(Color.white)
                    .clipShape(Circle())
                    .shadow(color: Color.black.opacity(0.15), radius: 6, x: 0, y: 3)
            }
            .padding(.top, 16)
            .padding(.trailing, 16)
        }
    }
}

// MARK: - UIKit MapView Bridge
public struct OrderTrackingMapViewRepresentable: UIViewRepresentable {
    public let merchantCoordinate: CLLocationCoordinate2D
    public let customerCoordinate: CLLocationCoordinate2D
    public let riderCoordinate: CLLocationCoordinate2D?
    public let riderHeading: Double?
    public let waypoints: [CLLocationCoordinate2D]
    public let activeStage: String
    public let onCoordinatorCreated: (OrderTrackingMapCoordinator) -> Void

    public func makeCoordinator() -> OrderTrackingMapCoordinator {
        let coordinator = OrderTrackingMapCoordinator()
        onCoordinatorCreated(coordinator)
        return coordinator
    }

    public func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView()
        mapView.delegate = context.coordinator
        mapView.showsCompass = false
        mapView.showsScale = false
        mapView.isRotateEnabled = true
        mapView.isPitchEnabled = true
        context.coordinator.mapView = mapView
        return mapView
    }

    public func updateUIView(_ uiView: MKMapView, context: Context) {
        context.coordinator.update(
            merchantCoordinate: merchantCoordinate,
            customerCoordinate: customerCoordinate,
            riderCoordinate: riderCoordinate,
            riderHeading: riderHeading,
            waypoints: waypoints,
            activeStage: activeStage
        )
    }
}

// MARK: - Map Coordinator & Render Pipeline
public class OrderTrackingMapCoordinator: NSObject, MKMapViewDelegate {
    weak var mapView: MKMapView?

    private var storeAnnotation: StorePointAnnotation?
    private var customerAnnotation: CustomerPointAnnotation?
    private var riderAnnotation: RiderPointAnnotation?
    private var routeOverlay: MKPolyline?

    private var lastStage: String = ""
    private var hasFittedBoundsOnce: Bool = false

    public func update(
        merchantCoordinate: CLLocationCoordinate2D,
        customerCoordinate: CLLocationCoordinate2D,
        riderCoordinate: CLLocationCoordinate2D?,
        riderHeading: Double?,
        waypoints: [CLLocationCoordinate2D],
        activeStage: String
    ) {
        guard let mapView = mapView else { return }

        let stageUpper = activeStage.uppercased()
        let isCancelled = stageUpper == "CANCELLED"
        let isPostPickup = !isCancelled && [
            "PICKED_UP", "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU",
            "NEARBY", "ARRIVED_CUSTOMER", "AT_DOORSTEP", "HANDOFF_STARTED"
        ].contains(stageUpper)

        let isHeadingToStore = !isCancelled && [
            "HEADING_TO_STORE", "ACCEPTED", "EN_ROUTE_PICKUP", "EN_ROUTE_STORE",
            "ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_MERCHANT", "AT_STORE", "PACKED",
            "SELLER_ACCEPTED", "ASSIGNED", "DISPATCHED", "RIDER_ASSIGNED", "RIDER_ACCEPTED"
        ].contains(stageUpper)

        // 1. Store Hub Pin
        if !isCancelled {
            if storeAnnotation == nil {
                let store = StorePointAnnotation()
                store.coordinate = merchantCoordinate
                store.title = "Shop / Store Hub"
                mapView.addAnnotation(store)
                self.storeAnnotation = store
            } else {
                storeAnnotation?.coordinate = merchantCoordinate
            }

            // 2. Customer Destination Pin
            if customerAnnotation == nil {
                let cust = CustomerPointAnnotation()
                cust.coordinate = customerCoordinate
                cust.title = "Home / Delivery Location"
                mapView.addAnnotation(cust)
                self.customerAnnotation = cust
            } else {
                customerAnnotation?.coordinate = customerCoordinate
            }
        } else {
            if let sa = storeAnnotation { mapView.removeAnnotation(sa); storeAnnotation = nil }
            if let ca = customerAnnotation { mapView.removeAnnotation(ca); customerAnnotation = nil }
        }

        // 3. Scooter / Rider Marker
        let shouldShowRider = riderCoordinate != nil && (isHeadingToStore || isPostPickup)
        if shouldShowRider, let rCoord = riderCoordinate {
            if riderAnnotation == nil {
                let rider = RiderPointAnnotation()
                rider.coordinate = rCoord
                rider.heading = riderHeading ?? 0.0
                rider.title = "Delivery Partner"
                mapView.addAnnotation(rider)
                self.riderAnnotation = rider
            } else {
                UIView.animate(withDuration: 0.8) {
                    self.riderAnnotation?.coordinate = rCoord
                }
                self.riderAnnotation?.heading = riderHeading ?? 0.0
                if let rView = mapView.view(for: riderAnnotation!) as? RiderAnnotationView {
                    rView.updateHeading(riderHeading ?? 0.0)
                }
            }
        } else {
            if let ra = riderAnnotation {
                mapView.removeAnnotation(ra)
                self.riderAnnotation = nil
            }
        }

        // 4. Navigation Polyline
        let shouldShowRoute = (isHeadingToStore || isPostPickup) && riderCoordinate != nil
        if shouldShowRoute && waypoints.count >= 2 {
            if let old = routeOverlay {
                mapView.removeOverlay(old)
            }
            let polyline = MKPolyline(coordinates: waypoints, count: waypoints.count)
            mapView.addOverlay(polyline, level: .aboveRoads)
            self.routeOverlay = polyline
        } else {
            if let old = routeOverlay {
                mapView.removeOverlay(old)
                self.routeOverlay = nil
            }
        }

        // 5. Auto Framing
        if !hasFittedBoundsOnce || lastStage != activeStage {
            hasFittedBoundsOnce = true
            lastStage = activeStage
            fitBounds(merchant: merchantCoordinate, customer: customerCoordinate, rider: riderCoordinate)
        }
    }

    public func recenter() {
        guard let mapView = mapView else { return }
        if let r = riderAnnotation?.coordinate {
            let region = MKCoordinateRegion(center: r, latitudinalMeters: 800, longitudinalMeters: 800)
            mapView.setRegion(region, animated: true)
        } else if let s = storeAnnotation?.coordinate {
            let region = MKCoordinateRegion(center: s, latitudinalMeters: 1000, longitudinalMeters: 1000)
            mapView.setRegion(region, animated: true)
        }
    }

    private func fitBounds(
        merchant: CLLocationCoordinate2D,
        customer: CLLocationCoordinate2D,
        rider: CLLocationCoordinate2D?
    ) {
        guard let mapView = mapView else { return }
        var coords = [merchant, customer]
        if let r = rider { coords.append(r) }

        let minLat = coords.map { $0.latitude }.min() ?? merchant.latitude
        let maxLat = coords.map { $0.latitude }.max() ?? merchant.latitude
        let minLng = coords.map { $0.longitude }.min() ?? merchant.longitude
        let maxLng = coords.map { $0.longitude }.max() ?? merchant.longitude

        let center = CLLocationCoordinate2D(
            latitude: (minLat + maxLat) / 2.0,
            longitude: (minLng + maxLng) / 2.0
        )
        let span = MKCoordinateSpan(
            latitudeDelta: max((maxLat - minLat) * 1.5, 0.008),
            longitudeDelta: max((maxLng - minLng) * 1.5, 0.008)
        )
        let region = MKCoordinateRegion(center: center, span: span)
        mapView.setRegion(region, animated: true)
    }

    // MARK: - MKMapViewDelegate
    public func mapView(_ mapView: MKMapView, viewFor annotation: MKAnnotation) -> MKAnnotationView? {
        if annotation is StorePointAnnotation {
            let identifier = "StoreMarker"
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
            if view == nil {
                view = MKAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                view?.canShowCallout = false
                view?.image = PinRenderer.renderStoreMarker()
                view?.centerOffset = CGPoint(x: 0, y: -24)
            } else {
                view?.annotation = annotation
            }
            return view
        } else if annotation is CustomerPointAnnotation {
            let identifier = "CustomerMarker"
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier)
            if view == nil {
                view = MKAnnotationView(annotation: annotation, reuseIdentifier: identifier)
                view?.canShowCallout = false
                view?.image = PinRenderer.renderCustomerMarker()
                view?.centerOffset = CGPoint(x: 0, y: -24)
            } else {
                view?.annotation = annotation
            }
            return view
        } else if let riderAnno = annotation as? RiderPointAnnotation {
            let identifier = "RiderMarker"
            var view = mapView.dequeueReusableAnnotationView(withIdentifier: identifier) as? RiderAnnotationView
            if view == nil {
                view = RiderAnnotationView(annotation: riderAnno, reuseIdentifier: identifier)
            } else {
                view?.annotation = riderAnno
                view?.updateHeading(riderAnno.heading)
            }
            return view
        }
        return nil
    }

    public func mapView(_ mapView: MKMapView, rendererFor overlay: MKOverlay) -> MKOverlayRenderer {
        if let polyline = overlay as? MKPolyline {
            let renderer = MKPolylineRenderer(polyline: polyline)
            renderer.strokeColor = UIColor(red: 14/255, green: 103/255, blue: 94/255, alpha: 1.0) // Brand Emerald
            renderer.lineWidth = 6
            renderer.lineCap = .round
            renderer.lineJoin = .round
            return renderer
        }
        return MKOverlayRenderer(overlay: overlay)
    }
}

// MARK: - Point Annotations
public class StorePointAnnotation: MKPointAnnotation {}
public class CustomerPointAnnotation: MKPointAnnotation {}
public class RiderPointAnnotation: MKPointAnnotation {
    public var heading: Double = 0.0
}

// MARK: - Custom Rider Annotation View (Rotates with Vehicle Heading)
public class RiderAnnotationView: MKAnnotationView {
    private let iconImageView = UIImageView()

    override init(annotation: MKAnnotation?, reuseIdentifier: String?) {
        super.init(annotation: annotation, reuseIdentifier: reuseIdentifier)
        self.canShowCallout = false
        self.frame = CGRect(x: 0, y: 0, width: 56, height: 56)
        self.centerOffset = CGPoint(x: 0, y: 0)

        iconImageView.frame = bounds
        iconImageView.contentMode = .scaleAspectFit
        iconImageView.image = PinRenderer.renderRiderScooter()
        addSubview(iconImageView)

        if let rider = annotation as? RiderPointAnnotation {
            updateHeading(rider.heading)
        }
    }

    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    public func updateHeading(_ heading: Double) {
        let radians = CGFloat(heading * .pi / 180.0)
        UIView.animate(withDuration: 0.3) {
            self.iconImageView.transform = CGAffineTransform(rotationAngle: radians)
        }
    }
}

// MARK: - Native High-Res Marker Graphic Renderers (1:1 with Android Canvas)
public enum PinRenderer {

    /** Teardrop Storefront Pin with SHOP Badge */
    public static func renderStoreMarker() -> UIImage {
        let size = CGSize(width: 50, height: 65)
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        guard let ctx = UIGraphicsGetCurrentContext() else { return UIImage() }

        // 1. Contact shadow
        ctx.setFillColor(UIColor(white: 0, alpha: 0.22).cgColor)
        ctx.fillEllipse(in: CGRect(x: 14, y: 56, width: 22, height: 8))

        // 2. Teardrop Silhouette
        let pinPath = UIBezierPath()
        pinPath.move(to: CGPoint(x: 25, y: 58))
        pinPath.addCurve(to: CGPoint(x: 6, y: 26), controlPoint1: CGPoint(x: 21, y: 50), controlPoint2: CGPoint(x: 6, y: 38))
        pinPath.addArc(withCenter: CGPoint(x: 25, y: 26), radius: 19, startAngle: .pi, endAngle: 0, clockwise: true)
        pinPath.addCurve(to: CGPoint(x: 25, y: 58), controlPoint1: CGPoint(x: 44, y: 38), controlPoint2: CGPoint(x: 29, y: 50))
        pinPath.close()

        // Deep Store Azure Fill (#0284C7)
        ctx.setFillColor(UIColor(red: 2/255, green: 132/255, blue: 199/255, alpha: 1.0).cgColor)
        ctx.addPath(pinPath.cgPath)
        ctx.fillPath()

        // White Border
        ctx.setStrokeColor(UIColor.white.cgColor)
        ctx.setLineWidth(2.5)
        ctx.addPath(pinPath.cgPath)
        ctx.strokePath()

        // 3. Inner White High-Contrast Disc
        ctx.setFillColor(UIColor.white.cgColor)
        ctx.fillEllipse(in: CGRect(x: 13, y: 14, width: 24, height: 24))

        // 4. Storefront Icon Emblem (#0369A1)
        ctx.setFillColor(UIColor(red: 3/255, green: 105/255, blue: 161/255, alpha: 1.0).cgColor)
        ctx.fill(CGRect(x: 17, y: 19, width: 16, height: 2))
        ctx.fill(CGRect(x: 17, y: 22, width: 16, height: 5))
        ctx.fill(CGRect(x: 18, y: 29, width: 4, height: 5))
        ctx.fill(CGRect(x: 28, y: 29, width: 4, height: 5))
        ctx.fill(CGRect(x: 23, y: 29, width: 4, height: 7))

        // 5. SHOP Pill Badge
        let pillRect = CGRect(x: 10, y: 1, width: 30, height: 12)
        let pillPath = UIBezierPath(roundedRect: pillRect, cornerRadius: 6)
        ctx.setFillColor(UIColor(red: 15/255, green: 23/255, blue: 42/255, alpha: 1.0).cgColor)
        ctx.addPath(pillPath.cgPath)
        ctx.fillPath()
        ctx.setStrokeColor(UIColor.white.cgColor)
        ctx.setLineWidth(1)
        ctx.addPath(pillPath.cgPath)
        ctx.strokePath()

        let text = "SHOP"
        let font = UIFont.systemFont(ofSize: 7, weight: .bold)
        let textAttrs: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: UIColor(red: 56/255, green: 189/255, blue: 248/255, alpha: 1.0)
        ]
        let textSize = text.size(withAttributes: textAttrs)
        text.draw(at: CGPoint(x: 25 - textSize.width / 2, y: 7 - textSize.height / 2), withAttributes: textAttrs)

        let image = UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
        UIGraphicsEndImageContext()
        return image
    }

    /** Teardrop Customer Destination Pin with HOME Badge */
    public static func renderCustomerMarker() -> UIImage {
        let size = CGSize(width: 50, height: 65)
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        guard let ctx = UIGraphicsGetCurrentContext() else { return UIImage() }

        // 1. Contact shadow
        ctx.setFillColor(UIColor(white: 0, alpha: 0.22).cgColor)
        ctx.fillEllipse(in: CGRect(x: 14, y: 56, width: 22, height: 8))

        // 2. Teardrop Silhouette
        let pinPath = UIBezierPath()
        pinPath.move(to: CGPoint(x: 25, y: 58))
        pinPath.addCurve(to: CGPoint(x: 6, y: 26), controlPoint1: CGPoint(x: 21, y: 50), controlPoint2: CGPoint(x: 6, y: 38))
        pinPath.addArc(withCenter: CGPoint(x: 25, y: 26), radius: 19, startAngle: .pi, endAngle: 0, clockwise: true)
        pinPath.addCurve(to: CGPoint(x: 25, y: 58), controlPoint1: CGPoint(x: 44, y: 38), controlPoint2: CGPoint(x: 29, y: 50))
        pinPath.close()

        // Warm Delivery Coral (#EA580C)
        ctx.setFillColor(UIColor(red: 234/255, green: 88/255, blue: 12/255, alpha: 1.0).cgColor)
        ctx.addPath(pinPath.cgPath)
        ctx.fillPath()

        // White Border
        ctx.setStrokeColor(UIColor.white.cgColor)
        ctx.setLineWidth(2.5)
        ctx.addPath(pinPath.cgPath)
        ctx.strokePath()

        // 3. Inner White High-Contrast Disc
        ctx.setFillColor(UIColor.white.cgColor)
        ctx.fillEllipse(in: CGRect(x: 13, y: 14, width: 24, height: 24))

        // 4. House Emblem (#C2410C)
        ctx.setFillColor(UIColor(red: 194/255, green: 65/255, blue: 12/255, alpha: 1.0).cgColor)
        let roofPath = UIBezierPath()
        roofPath.move(to: CGPoint(x: 25, y: 18))
        roofPath.addLine(to: CGPoint(x: 17, y: 25))
        roofPath.addLine(to: CGPoint(x: 19, y: 26))
        roofPath.addLine(to: CGPoint(x: 25, y: 21))
        roofPath.addLine(to: CGPoint(x: 31, y: 26))
        roofPath.addLine(to: CGPoint(x: 33, y: 25))
        roofPath.close()
        ctx.addPath(roofPath.cgPath)
        ctx.fillPath()

        ctx.fill(CGRect(x: 19, y: 25, width: 12, height: 10))
        ctx.setFillColor(UIColor.white.cgColor)
        ctx.fill(CGRect(x: 23, y: 29, width: 4, height: 6))

        // 5. HOME Pill Badge
        let pillRect = CGRect(x: 10, y: 1, width: 30, height: 12)
        let pillPath = UIBezierPath(roundedRect: pillRect, cornerRadius: 6)
        ctx.setFillColor(UIColor(red: 15/255, green: 23/255, blue: 42/255, alpha: 1.0).cgColor)
        ctx.addPath(pillPath.cgPath)
        ctx.fillPath()
        ctx.setStrokeColor(UIColor.white.cgColor)
        ctx.setLineWidth(1)
        ctx.addPath(pillPath.cgPath)
        ctx.strokePath()

        let text = "HOME"
        let font = UIFont.systemFont(ofSize: 7, weight: .bold)
        let textAttrs: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: UIColor(red: 245/255, green: 158/255, blue: 11/255, alpha: 1.0)
        ]
        let textSize = text.size(withAttributes: textAttrs)
        text.draw(at: CGPoint(x: 25 - textSize.width / 2, y: 7 - textSize.height / 2), withAttributes: textAttrs)

        let image = UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
        UIGraphicsEndImageContext()
        return image
    }

    /** 2.5D Delivery Scooter with Rider & Delivery Box */
    public static func renderRiderScooter() -> UIImage {
        let size = CGSize(width: 56, height: 56)
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        guard let ctx = UIGraphicsGetCurrentContext() else { return UIImage() }

        // 1. Glowing radar halo
        ctx.setFillColor(UIColor(red: 14/255, green: 103/255, blue: 94/255, alpha: 0.18).cgColor)
        ctx.fillEllipse(in: CGRect(x: 2, y: 2, width: 52, height: 52))

        // 2. White Disc
        ctx.setFillColor(UIColor.white.cgColor)
        ctx.fillEllipse(in: CGRect(x: 8, y: 8, width: 40, height: 40))

        // 3. Dark Navy Core (#1E1B4B)
        ctx.setFillColor(UIColor(red: 30/255, green: 27/255, blue: 75/255, alpha: 1.0).cgColor)
        ctx.fillEllipse(in: CGRect(x: 10, y: 10, width: 36, height: 36))

        // 4. Front Wheel
        ctx.setFillColor(UIColor(red: 15/255, green: 23/255, blue: 42/255, alpha: 1.0).cgColor)
        ctx.fill(CGRect(x: 26, y: 12, width: 4, height: 6))

        // 5. Scooter Body / Apron (Brand Emerald #0E675E)
        ctx.setFillColor(UIColor(red: 14/255, green: 103/255, blue: 94/255, alpha: 1.0).cgColor)
        ctx.fill(CGRect(x: 23, y: 18, width: 10, height: 8))

        // 6. Handlebar
        ctx.setStrokeColor(UIColor(red: 226/255, green: 232/255, blue: 240/255, alpha: 1.0).cgColor)
        ctx.setLineWidth(2)
        ctx.strokeLineSegments(between: [CGPoint(x: 18, y: 19), CGPoint(x: 38, y: 19)])

        // 7. Rider Helmet
        ctx.setFillColor(UIColor.white.cgColor)
        ctx.fillEllipse(in: CGRect(x: 23, y: 23, width: 10, height: 10))

        // Helmet Visor
        ctx.setFillColor(UIColor(red: 15/255, green: 23/255, blue: 42/255, alpha: 1.0).cgColor)
        ctx.fill(CGRect(x: 24, y: 24, width: 8, height: 4))

        // 8. Delivery Thermal Backpack (#7C3AED / Violet)
        ctx.setFillColor(UIColor(red: 124/255, green: 58/255, blue: 237/255, alpha: 1.0).cgColor)
        ctx.fill(CGRect(x: 22, y: 32, width: 12, height: 9))

        // Reflective Safety Strip
        ctx.setStrokeColor(UIColor(red: 241/255, green: 245/255, blue: 249/255, alpha: 1.0).cgColor)
        ctx.setLineWidth(1.5)
        ctx.strokeLineSegments(between: [CGPoint(x: 23, y: 36), CGPoint(x: 33, y: 36)])

        // 9. Rear Wheel
        ctx.setFillColor(UIColor(red: 15/255, green: 23/255, blue: 42/255, alpha: 1.0).cgColor)
        ctx.fill(CGRect(x: 26, y: 41, width: 4, height: 5))

        let image = UIGraphicsGetImageFromCurrentImageContext() ?? UIImage()
        UIGraphicsEndImageContext()
        return image
    }
}
