import SwiftUI
import CoreLocation
import MapKit

public struct MapRoutePoint: Codable, Equatable {
    public let lat: Double
    public let lng: Double

    public init(lat: Double, lng: Double) {
        self.lat = lat
        self.lng = lng
    }

    public var coordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: lat, longitude: lng)
    }
}

/**
 * 1:1 Parity with Android CustomerLiveMapTrackingView.kt (562 lines).
 * Live Delivery Map Tracking Container:
 * - Real turn-by-turn road polyline from backend / OSRM
 * - Dynamic stage state machine (CANCELLED, DELIVERED, AT_DOORSTEP, OUT_FOR_DELIVERY, AT_STORE, HEADING_TO_STORE, ASSIGNING_PARTNER)
 * - Strict Invariant: ZERO lines drawn before rider commits
 * - Pulsing live partner beacon
 * - Fullscreen toggle with header controls
 */
public struct CustomerLiveMapTrackingView: View {
    public let order: ServerOrderResponse
    public let liveTracking: CustomerOrderTrackingDto?
    public let isFullscreen: Bool
    public let onExpandClick: (() -> Void)?
    public let onExitFullscreen: (() -> Void)?
    public let onBack: (() -> Void)?

    @State private var dynamicRoadPoints: [CLLocationCoordinate2D] = []
    @State private var cachedRiderLat: Double? = nil
    @State private var cachedRiderLng: Double? = nil
    @State private var lastRoutedOrigin: CLLocationCoordinate2D? = nil
    @State private var lastRoutedStage: String? = nil
    @State private var isBeaconPulsing: Bool = false

    public init(
        order: ServerOrderResponse,
        liveTracking: CustomerOrderTrackingDto?,
        isFullscreen: Bool = false,
        onExpandClick: (() -> Void)? = nil,
        onExitFullscreen: (() -> Void)? = nil,
        onBack: (() -> Void)? = nil
    ) {
        self.order = order
        self.liveTracking = liveTracking
        self.isFullscreen = isFullscreen
        self.onExpandClick = onExpandClick
        self.onExitFullscreen = onExitFullscreen
        self.onBack = onBack
    }

    private var merchantLat: Double {
        liveTracking?.merchantLat ?? 0.0
    }

    private var merchantLng: Double {
        liveTracking?.merchantLng ?? 0.0
    }

    private var customerLat: Double {
        liveTracking?.customerLat ?? order.deliveryAddress?.latitude ?? 0.0
    }

    private var customerLng: Double {
        liveTracking?.customerLng ?? order.deliveryAddress?.longitude ?? 0.0
    }

    private var realRiderLat: Double? {
        (liveTracking?.riderLat ?? order.riderLat)?.takeIf { $0 != 0.0 }
    }

    private var realRiderLng: Double? {
        (liveTracking?.riderLng ?? order.riderLng)?.takeIf { $0 != 0.0 }
    }

    private var heading: Double? {
        liveTracking?.riderBearing ?? order.riderBearing
    }

    private var speedKmh: Double? {
        liveTracking?.speedKmh ?? order.speedKmh
    }

    private var currentStatus: String {
        order.orderStatus ?? order.status
    }

    private var activeStage: String {
        let raw = liveTracking?.status ?? currentStatus
        switch raw.uppercased() {
        case "CANCELLED": return "CANCELLED"
        case "DELIVERED": return "DELIVERED"
        case "ARRIVED_CUSTOMER", "HANDOFF_STARTED": return "AT_DOORSTEP"
        case "PICKED_UP", "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU": return "OUT_FOR_DELIVERY"
        case "ARRIVED_PICKUP", "EN_ROUTE_PICKUP", "AT_STORE": return "AT_STORE"
        case "SELLER_ACCEPTED", "ACCEPTED", "EN_ROUTE_STORE", "ASSIGNED": return "HEADING_TO_STORE"
        default: return "ASSIGNING_PARTNER"
        }
    }

    private var isHeadingToCustomer: Bool {
        [
            "PICKED_UP", "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU",
            "NEARBY", "ARRIVED_CUSTOMER", "AT_DOORSTEP", "HANDOFF_STARTED"
        ].contains(activeStage)
    }

    private var isHeadingToStore: Bool {
        [
            "HEADING_TO_STORE", "ACCEPTED", "EN_ROUTE_PICKUP", "EN_ROUTE_STORE",
            "ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_MERCHANT", "AT_STORE", "PACKED",
            "SELLER_ACCEPTED", "ASSIGNED", "DISPATCHED", "RIDER_ASSIGNED", "RIDER_ACCEPTED"
        ].contains(activeStage)
    }

    private var isOrderPlaced: Bool {
        !isHeadingToCustomer && !isHeadingToStore
    }

    private var effectiveRiderCoord: CLLocationCoordinate2D? {
        if isOrderPlaced { return nil }
        if let rLat = realRiderLat, let rLng = realRiderLng {
            return CLLocationCoordinate2D(latitude: rLat, longitude: rLng)
        }
        if let cLat = cachedRiderLat, let cLng = cachedRiderLng {
            return CLLocationCoordinate2D(latitude: cLat, longitude: cLng)
        }
        if isHeadingToCustomer && merchantLat != 0.0 && merchantLng != 0.0 {
            return CLLocationCoordinate2D(latitude: merchantLat, longitude: merchantLng)
        }
        return nil
    }

    private var routeWaypoints: [CLLocationCoordinate2D] {
        if isOrderPlaced || effectiveRiderCoord == nil {
            return []
        }
        if !dynamicRoadPoints.isEmpty && lastRoutedStage == activeStage {
            return dynamicRoadPoints
        }
        if isHeadingToCustomer && customerLat != 0.0 && effectiveRiderCoord != nil {
            return [effectiveRiderCoord!, CLLocationCoordinate2D(latitude: customerLat, longitude: customerLng)]
        }
        if isHeadingToStore && merchantLat != 0.0 && effectiveRiderCoord != nil {
            return [effectiveRiderCoord!, CLLocationCoordinate2D(latitude: merchantLat, longitude: merchantLng)]
        }
        return []
    }

    public var body: some View {
        ZStack {
            if merchantLat != 0.0 && merchantLng != 0.0 && customerLat != 0.0 && customerLng != 0.0 {
                NativeGoogleOrderTrackingMap(
                    merchantCoordinate: CLLocationCoordinate2D(latitude: merchantLat, longitude: merchantLng),
                    customerCoordinate: CLLocationCoordinate2D(latitude: customerLat, longitude: customerLng),
                    riderCoordinate: effectiveRiderCoord,
                    riderHeading: heading,
                    speedKmh: liveTracking?.speedKmh,
                    waypoints: routeWaypoints,
                    activeStage: activeStage
                )
            } else {
                Color(hex: "F1F5F9")
            }

            // Expand to Fullscreen Floating Button (Inline mode)
            if !isFullscreen && onExpandClick != nil {
                VStack {
                    HStack {
                        Spacer()
                        Button(action: { onExpandClick?() }) {
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                                .padding(8)
                                .background(Color.white.opacity(0.92))
                                .clipShape(Circle())
                                .shadow(color: Color.black.opacity(0.1), radius: 3, x: 0, y: 1)
                        }
                        .padding(10)
                    }
                    Spacer()
                }
            }

            // Fullscreen Top Bar Controls
            if isFullscreen {
                VStack {
                    HStack(spacing: 12) {
                        Button(action: {
                            onExitFullscreen?() ?? onBack?()
                        }) {
                            Image(systemName: "chevron.left")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                                .padding(10)
                                .background(Color.white)
                                .clipShape(Circle())
                                .shadow(color: Color.black.opacity(0.12), radius: 4, x: 0, y: 2)
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(OrderStatusPresentationMapper.present(rawStatus: activeStage).presentedLabel)
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                            Text("Order #\(String(order.id.suffix(6)).uppercased())")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color.white)
                        .cornerRadius(20)
                        .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)

                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 44)

                    Spacer()
                }
            }
        }
        .onAppear {
            if let rLat = realRiderLat, let rLng = realRiderLng {
                cachedRiderLat = rLat
                cachedRiderLng = rLng
            }
            fetchRoadGeometry()
        }
        .onChange(of: realRiderLat) { _ in
            if let rLat = realRiderLat, let rLng = realRiderLng {
                cachedRiderLat = rLat
                cachedRiderLng = rLng
            }
            fetchRoadGeometry()
        }
        .onChange(of: activeStage) { _ in
            dynamicRoadPoints = []
            lastRoutedOrigin = nil
            lastRoutedStage = activeStage
            fetchRoadGeometry()
        }
    }

    private func fetchRoadGeometry() {
        guard !isOrderPlaced, let poly = liveTracking?.routePolyline, !poly.isEmpty else {
            dynamicRoadPoints = []
            return
        }

        let pts = decodePolyline(poly)
        if pts.count >= 2 {
            self.dynamicRoadPoints = pts
        } else {
            self.dynamicRoadPoints = []
        }
    }

    private func decodePolyline(_ encoded: String) -> [CLLocationCoordinate2D] {
        var coordinates: [CLLocationCoordinate2D] = []
        var index = encoded.startIndex
        var lat: Int32 = 0
        var lng: Int32 = 0

        while index < encoded.endIndex {
            var b: Int32 = 0
            var shift: UInt32 = 0
            var result: Int32 = 0
            repeat {
                guard index < encoded.endIndex else { break }
                let scalar = encoded[index].utf8.first ?? 0
                index = encoded.index(after: index)
                b = Int32(scalar) - 63
                result |= (b & 0x1f) << shift
                shift += 5
            } while b >= 0x20
            let dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1))
            lat += dlat

            shift = 0
            result = 0
            repeat {
                guard index < encoded.endIndex else { break }
                let scalar = encoded[index].utf8.first ?? 0
                index = encoded.index(after: index)
                b = Int32(scalar) - 63
                result |= (b & 0x1f) << shift
                shift += 5
            } while b >= 0x20
            let dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1))
            lng += dlng

            let latitude = Double(lat) / 1e5
            let longitude = Double(lng) / 1e5
            coordinates.append(CLLocationCoordinate2D(latitude: latitude, longitude: longitude))
        }
        return coordinates
    }
}

private extension Double {
    func takeIf(_ predicate: (Double) -> Bool) -> Double? {
        predicate(self) ? self : nil
    }
}
