import SwiftUI
import CoreLocation
import MapKit

/**
 * 1:1 Parity with Android LocationMapStep.kt
 * Interactive pin-drop map screen with reverse geocoded delivery address card.
 */
public struct LocationMapStepView: View {
    public let formAddress: StructuredAddress
    public let isGeocoding: Bool
    public let onMapCameraSettled: (Double, Double) -> Void
    public let onRecenterGps: () -> Void
    public let onConfirmLocation: () -> Void

    @State private var isMapMoving: Bool = false

    public init(
        formAddress: StructuredAddress,
        isGeocoding: Bool,
        onMapCameraSettled: @escaping (Double, Double) -> Void,
        onRecenterGps: @escaping () -> Void,
        onConfirmLocation: @escaping () -> Void
    ) {
        self.formAddress = formAddress
        self.isGeocoding = isGeocoding
        self.onMapCameraSettled = onMapCameraSettled
        self.onRecenterGps = onRecenterGps
        self.onConfirmLocation = onConfirmLocation
    }

    private var isReadyToConfirm: Bool {
        !isGeocoding && formAddress.geoLocation != nil
    }

    private var centerCoord: CLLocationCoordinate2D? {
        guard let geo = formAddress.geoLocation else { return nil }
        return CLLocationCoordinate2D(latitude: geo.latitude, longitude: geo.longitude)
    }

    public var body: some View {
        VStack(spacing: 12) {
            Text("Position map pin precisely over your delivery entrance")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(Color(hex: "64748B"))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)

            // Map Viewport with Central Floating Spring Pin
            ZStack {
                LocationMapViewport(
                    centerCoordinate: centerCoord,
                    onCameraMoveStateChanged: { moving in
                        withAnimation(.spring(response: 0.35, dampingFraction: 0.6)) {
                            isMapMoving = moving
                        }
                    },
                    onCameraSettled: { coord in
                        onMapCameraSettled(coord.latitude, coord.longitude)
                    }
                )
                .frame(maxHeight: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )

                // Central Zomato-grade Map Pin
                VStack(spacing: 0) {
                    ZStack {
                        Circle()
                            .fill(Color(hex: "0E675E"))
                            .frame(width: 38, height: 38)
                            .shadow(color: Color.black.opacity(0.25), radius: 6, y: 4)

                        Image(systemName: "house.fill")
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                    }
                    .offset(y: isMapMoving ? -14 : 0)

                    // Pin needle tip
                    Triangle()
                        .fill(Color(hex: "0E675E"))
                        .frame(width: 12, height: 8)
                        .offset(y: isMapMoving ? -14 : 0)

                    // Ground shadow dot
                    Ellipse()
                        .fill(Color.black.opacity(isMapMoving ? 0.15 : 0.35))
                        .frame(width: isMapMoving ? 8 : 12, height: isMapMoving ? 3 : 5)
                        .scaleEffect(isMapMoving ? 0.8 : 1.0)
                }

                // Floating Re-Center GPS Button
                VStack {
                    Spacer()
                    HStack {
                        Spacer()
                        Button(action: onRecenterGps) {
                            ZStack {
                                Circle()
                                    .fill(Color.white)
                                    .frame(width: 44, height: 44)
                                    .shadow(color: Color.black.opacity(0.18), radius: 6, y: 3)

                                Image(systemName: "location.fill")
                                    .font(.system(size: 18, weight: .bold))
                                    .foregroundColor(Color(hex: "0E675E"))
                            }
                        }
                        .padding(16)
                    }
                }
            }
            .padding(.horizontal, 16)

            // Location Verification Card
            VStack(alignment: .leading, spacing: 4) {
                Text("Confirm delivery location")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: "64748B"))

                if isGeocoding {
                    HStack(spacing: 8) {
                        ProgressView()
                            .scaleEffect(0.8)
                        Text("Updating address location...")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                    }
                    .padding(.vertical, 2)
                } else {
                    Text(formAddress.formattedAddress.isEmpty ? "Selected geographic pin" : formAddress.formattedAddress)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                        .lineLimit(2)

                    if !formAddress.city.isEmpty {
                        Text("\(formAddress.city), \(formAddress.state) \(formAddress.postalCode)")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color(hex: "F8FAFC"))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
            )
            .padding(.horizontal, 16)

            // Confirm Location Action Button
            Button(action: onConfirmLocation) {
                HStack(spacing: 8) {
                    if isGeocoding {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            .scaleEffect(0.8)
                    }
                    Text(isGeocoding ? "Updating address location..." : "Confirm location & enter house details")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(isReadyToConfirm ? .white : Color(hex: "94A3B8"))
                }
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(isReadyToConfirm ? Color(hex: "0E675E") : Color(hex: "E2E8F0"))
                .cornerRadius(12)
            }
            .disabled(!isReadyToConfirm)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
    }
}

// Inverted Triangle for Map Needle
fileprivate struct Triangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
