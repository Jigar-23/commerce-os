import SwiftUI
import MapKit
import CoreLocation

/**
 * Zomato-Grade Native Apple MapKit Viewport for iOS.
 * Replaces legacy WKWebView with a 120Hz native MKMapView.
 * Provides high-precision camera state tracking (moving vs settled)
 * and seamless programmatic pan-to animations.
 */
public struct LocationMapViewport: UIViewRepresentable {
    public var centerCoordinate: CLLocationCoordinate2D?
    public var onCameraMoveStateChanged: (Bool) -> Void
    public var onCameraSettled: (CLLocationCoordinate2D) -> Void

    public init(
        centerCoordinate: CLLocationCoordinate2D? = nil,
        onCameraMoveStateChanged: @escaping (Bool) -> Void = { _ in },
        onCameraSettled: @escaping (CLLocationCoordinate2D) -> Void = { _ in }
    ) {
        self.centerCoordinate = centerCoordinate
        self.onCameraMoveStateChanged = onCameraMoveStateChanged
        self.onCameraSettled = onCameraSettled
    }

    public func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    public func makeUIView(context: Context) -> MKMapView {
        let mapView = MKMapView(frame: .zero)
        mapView.delegate = context.coordinator
        mapView.showsUserLocation = false
        mapView.showsCompass = false
        mapView.showsScale = false
        mapView.isPitchEnabled = false
        mapView.isRotateEnabled = true
        mapView.pointOfInterestFilter = .excludingAll

        // Configure light clean cartography
        if #available(iOS 17.0, *) {
            let config = MKStandardMapConfiguration(elevationStyle: .realistic, emphasisStyle: .muted)
            mapView.preferredConfiguration = config
        }

        context.coordinator.mapView = mapView

        if let initialCoord = centerCoordinate {
            let span = MKCoordinateSpan(latitudeDelta: 0.0035, longitudeDelta: 0.0035)
            let region = MKCoordinateRegion(center: initialCoord, span: span)
            mapView.setRegion(region, animated: false)
        }

        return mapView
    }

    public func updateUIView(_ uiView: MKMapView, context: Context) {
        guard let target = centerCoordinate else { return }
        let current = uiView.centerCoordinate
        let dLat = abs(current.latitude - target.latitude)
        let dLng = abs(current.longitude - target.longitude)

        // Only animate camera if target coordinate significantly diverged (e.g. from GPS tap or place search)
        if (dLat > 0.0003 || dLng > 0.0003) && !context.coordinator.isUserInteracting {
            let span = MKCoordinateSpan(latitudeDelta: 0.0035, longitudeDelta: 0.0035)
            let region = MKCoordinateRegion(center: target, span: span)
            uiView.setRegion(region, animated: true)
        }
    }

    public class Coordinator: NSObject, MKMapViewDelegate, UIGestureRecognizerDelegate {
        var parent: LocationMapViewport
        weak var mapView: MKMapView?
        var isUserInteracting: Bool = false
        private var debounceTimer: Timer?

        init(_ parent: LocationMapViewport) {
            self.parent = parent
        }

        public func mapView(_ mapView: MKMapView, regionWillChangeAnimated animated: Bool) {
            // Detect user-driven gesture vs programmatic pan
            let view = mapView.subviews.first
            if let gestureRecognizers = view?.gestureRecognizers {
                for recognizer in gestureRecognizers {
                    if recognizer.state == .began || recognizer.state == .changed {
                        isUserInteracting = true
                        break
                    }
                }
            }

            debounceTimer?.invalidate()
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                self.parent.onCameraMoveStateChanged(true)
            }
        }

        public func mapView(_ mapView: MKMapView, regionDidChangeAnimated animated: Bool) {
            debounceTimer?.invalidate()
            debounceTimer = Timer.scheduledTimer(withTimeInterval: 0.22, repeats: false) { [weak self] _ in
                guard let self = self, let mv = self.mapView else { return }
                self.isUserInteracting = false
                let settledCoord = mv.centerCoordinate

                DispatchQueue.main.async {
                    self.parent.onCameraMoveStateChanged(false)
                    self.parent.onCameraSettled(settledCoord)
                }
            }
        }
    }
}

// MARK: - Zomato-Grade Grounded Delivery Pin Component
public struct ZomatoDeliveryPinView: View {
    public let isMoving: Bool

    public init(isMoving: Bool) {
        self.isMoving = isMoving
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            // 1. Physical Ground Anchor Disk (Tip of pin rests on this shadow)
            Ellipse()
                .fill(Color(hex: "0F172A").opacity(isMoving ? 0.20 : 0.42))
                .frame(width: isMoving ? 8 : 15, height: isMoving ? 3 : 5)
                .animation(.spring(response: 0.35, dampingFraction: 0.65), value: isMoving)

            // 2. Elevating Teardrop Pin with Spring Levitation & Landing Bounce
            ZStack(alignment: .center) {
                // Teardrop pin container
                VStack(spacing: 0) {
                    ZStack {
                        Circle()
                            .fill(Color(hex: "0E675E"))
                            .frame(width: 38, height: 38)
                            .shadow(color: Color.black.opacity(0.18), radius: 4, x: 0, y: 2)

                        Image(systemName: "mappin")
                            .font(.system(size: 19, weight: .black))
                            .foregroundColor(.white)
                    }

                    // Triangular pin point
                    PinTipShape()
                        .fill(Color(hex: "0E675E"))
                        .frame(width: 12, height: 10)
                        .offset(y: -2)
                }
            }
            .offset(y: isMoving ? -20 : 0)
            .animation(
                isMoving
                    ? .easeOut(duration: 0.16)
                    : .spring(response: 0.38, dampingFraction: 0.62, blendDuration: 0),
                value: isMoving
            )
        }
        .frame(width: 44, height: 58, alignment: .bottom)
        .onChange(of: isMoving) { moving in
            if !moving {
                // Subtle tactile haptic click on pin drop
                HapticFeedbackEngine.shared.triggerImpact(.light)
            }
        }
    }
}

// MARK: - Pin Tip Triangle
private struct PinTipShape: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
