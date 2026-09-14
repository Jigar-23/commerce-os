import SwiftUI
import CoreLocation
import MapKit

public struct OrderTrackingScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @ObservedObject private var trackingRepo = TrackingRepository.shared
    
    public let orderId: String?
    @State private var trackingData: CustomerOrderTrackingDto? = nil
    @State private var copiedOrderId: Bool = false
    @State private var selectedRating: Int = 5
    @State private var ratingSubmitted: Bool = false
    @State private var localRoadRoute: [CLLocationCoordinate2D] = []
    
    public init(orderId: String? = nil) {
        self.orderId = orderId
    }
    
    public var body: some View {
        NavigationView {
            Group {
                if let tracking = trackingData {
                    activeTrackingView(tracking)
                } else {
                    VStack(spacing: 16) {
                        ProgressView()
                            .scaleEffect(1.5)
                        Text("Connecting to live delivery tracking...")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.secondary)
                        if let err = trackingRepo.streamError {
                            Text(err)
                                .font(.system(size: 12))
                                .foregroundColor(.red)
                                .padding(.horizontal)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            }
            .navigationBarHidden(true)
            .onAppear {
                container.trackingRepository.startLiveTracking()
                if trackingData != nil {
                    startLiveActivityIfNeeded()
                }
            }
            .onDisappear {
                container.trackingRepository.stopLiveTracking()
                Task {
                    await DeliveryActivityManager.shared.endLiveActivity()
                }
            }
            .onReceive(container.trackingRepository.$activeTracking) { update in
                guard let update = update else { return }
                let stage = stageForStatus(update.status)
                let updatedDto = CustomerOrderTrackingDto(
                    orderId: update.orderId,
                    deliveryId: "DEL-\(update.orderId.suffix(4))",
                    status: update.status,
                    currentStage: stage,
                    estimatedMinutes: update.etaMinutes ?? 0,
                    merchantName: "QuickCommerce Hub",
                    merchantAddress: nil,
                    merchantLat: update.merchantLat,
                    merchantLng: update.merchantLng,
                    customerAddress: "Delivery Address",
                    customerLat: update.customerLat,
                    customerLng: update.customerLng,
                    riderName: update.riderName,
                    riderPhone: update.riderPhone,
                    riderLat: update.riderLat,
                    riderLng: update.riderLng,
                    routePolyline: update.routePolyline,
                    deliveryOtp: update.deliveryOtp
                )
                self.trackingData = updatedDto
                self.updateLiveActivity(with: updatedDto)
                self.resolveRoadRouteIfNeeded(for: updatedDto)
            }
        }
        .navigationViewStyle(.stack)
    }
    
    @ViewBuilder
    private func activeTrackingView(_ trackingData: CustomerOrderTrackingDto) -> some View {
        if trackingData.status.uppercased() == "DELIVERED" {
            // SCENARIO: ORDER DELIVERED - Map is completely hidden, replaced with rich completion summary
            deliveredCompletionView(trackingData)
        } else {
            // SCENARIO: ACTIVE ORDER - Live road polyline tracking on dark mode map
            liveTrackingMapView(trackingData)
        }
    }
    
    @ViewBuilder
    private func liveTrackingMapView(_ trackingData: CustomerOrderTrackingDto) -> some View {
        ZStack(alignment: .bottom) {
            // Background Dark Map with dynamic coordinates
            ZomatoDarkMapView(
                riderCoordinate: (trackingData.riderLat != nil && trackingData.riderLng != nil) ? CLLocationCoordinate2D(latitude: trackingData.riderLat!, longitude: trackingData.riderLng!) : nil,
                merchantCoordinate: (trackingData.merchantLat != nil && trackingData.merchantLng != nil) ? CLLocationCoordinate2D(latitude: trackingData.merchantLat!, longitude: trackingData.merchantLng!) : nil,
                customerCoordinate: (trackingData.customerLat != nil && trackingData.customerLng != nil) ? CLLocationCoordinate2D(latitude: trackingData.customerLat!, longitude: trackingData.customerLng!) : nil,
                routeCoordinates: buildRouteCoordinates(trackingData)
            )
            .edgesIgnoringSafeArea(.all)
            
            // Top Floating Authoritative Order ID Badge with Copy Button
            VStack {
                HStack(spacing: 8) {
                    Text("ORDER #\(trackingData.orderId)")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(.white)
                    
                    Button(action: {
                        UIPasteboard.general.string = trackingData.orderId
                        copiedOrderId = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                            copiedOrderId = false
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: copiedOrderId ? "checkmark" : "doc.on.doc")
                            Text(copiedOrderId ? "COPIED" : "COPY")
                        }
                        .font(.system(size: 9, weight: .black))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(copiedOrderId ? Color(hex: "10B981") : Color.white.opacity(0.2))
                        .foregroundColor(.white)
                        .cornerRadius(6)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.black.opacity(0.85))
                .cornerRadius(20)
                .padding(.top, 54)
                
                Spacer()
            }
            
            // Bottom Tracking Card Modal
            VStack(spacing: 12) {
                // Pull Pill
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(Color.gray.opacity(0.4))
                    .frame(width: 36, height: 5)
                    .padding(.top, 8)
                
                // Live SSE Status Pill
                HStack(spacing: 6) {
                    Circle()
                        .fill(trackingRepo.isLiveStreaming ? CommerceOSTheme.Colors.brandPrimary : CommerceOSTheme.Colors.warning)
                        .frame(width: 7, height: 7)
                    Text(trackingRepo.isLiveStreaming ? "REALTIME SSE STREAM ACTIVE" : "SNAPSHOT TRACKING")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(trackingRepo.isLiveStreaming ? CommerceOSTheme.Colors.brandPrimary : .secondary)
                    Spacer()
                    if let err = trackingRepo.streamError {
                        Text(err)
                            .font(.system(size: 9))
                            .foregroundColor(CommerceOSTheme.Colors.error)
                            .lineLimit(1)
                    }
                }
                .padding(.horizontal)
                
                // ETA Header Bar
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("ARRIVING IN \(trackingData.estimatedMinutes) MINUTES")
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                        Text(stageDescription(for: trackingData.status))
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                    Spacer()
                    Image(systemName: "timer")
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                }
                .padding(.horizontal)
                
                // Authoritative 4-Digit Delivery OTP Card
                if let otp = trackingData.deliveryOtp {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("DELIVERY VERIFICATION PIN")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(.secondary)
                            Text("Share this code with rider upon arrival")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Text(otp)
                            .font(.system(size: 24, weight: .black, design: .monospaced))
                            .padding(.horizontal, 16)
                            .padding(.vertical, 6)
                            .background(CommerceOSTheme.Colors.brandPrimarySoft)
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                            .cornerRadius(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(CommerceOSTheme.Colors.brandPrimaryDark, lineWidth: 1)
                            )
                    }
                    .padding(12)
                    .background(Color(.systemGray6))
                    .cornerRadius(10)
                    .padding(.horizontal)
                }
                
                // Stage Timeline Bar
                HStack(spacing: 4) {
                    ForEach(1...4, id: \.self) { stage in
                        RoundedRectangle(cornerRadius: 3)
                            .fill(stage <= trackingData.currentStage ? CommerceOSTheme.Colors.brandPrimary : Color.gray.opacity(0.3))
                            .frame(height: 6)
                    }
                }
                .padding(.horizontal)
                
                // Rider Profile Card
                HStack(spacing: 12) {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 40))
                        .foregroundColor(CommerceOSTheme.Colors.brandSecondary)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(trackingData.riderName ?? "Assigned Partner")
                            .font(.system(size: 14, weight: .bold))
                        Text("Vaccinated • Electric Scooter")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                    
                    Spacer()
                    
                    Button(action: {
                        if let phone = trackingData.riderPhone, let url = URL(string: "tel://\(phone.replacingOccurrences(of: " ", with: ""))") {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        Image(systemName: "phone.circle.fill")
                            .font(.system(size: 36))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                    }
                }
                .padding(12)
                .background(Color(.systemGray6))
                .cornerRadius(12)
                .padding(.horizontal)
                .padding(.bottom, 16)
            }
            .background(Color(.systemBackground))
            .cornerRadius(20, corners: [.topLeft, .topRight])
            .shadow(color: Color.black.opacity(0.3), radius: 10, x: 0, y: -4)
        }
    }
    
    @ViewBuilder
    private func deliveredCompletionView(_ trackingData: CustomerOrderTrackingDto) -> some View {
        ScrollView {
            VStack(spacing: 20) {
                // 1. Success Hero Card
                VStack(spacing: 12) {
                    ZStack {
                        Circle()
                            .fill(Color(hex: "10B981").opacity(0.15))
                            .frame(width: 88, height: 88)
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 48, weight: .bold))
                            .foregroundColor(Color(hex: "10B981"))
                    }
                    .padding(.top, 28)
                    
                    Text("Order Delivered Successfully 🎉")
                        .font(.system(size: 22, weight: .black))
                        .foregroundColor(.primary)
                    
                    Text("Your package has been safely handed over at your doorstep.")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)
                }
                .frame(maxWidth: .infinity)
                .padding(.bottom, 4)
                
                // 2. Authoritative Order ID Badge with Tap to Copy
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("ORDER NUMBER")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(.secondary)
                        Text("#\(trackingData.orderId)")
                            .font(.system(size: 13, weight: .bold, design: .monospaced))
                            .foregroundColor(.primary)
                    }
                    Spacer()
                    Button(action: {
                        UIPasteboard.general.string = trackingData.orderId
                        copiedOrderId = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                            copiedOrderId = false
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: copiedOrderId ? "checkmark" : "doc.on.doc")
                            Text(copiedOrderId ? "COPIED" : "COPY")
                        }
                        .font(.system(size: 11, weight: .bold))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(copiedOrderId ? Color(hex: "10B981").opacity(0.15) : Color(.systemGray5))
                        .foregroundColor(copiedOrderId ? Color(hex: "10B981") : .primary)
                        .cornerRadius(8)
                    }
                }
                .padding(14)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(12)
                .padding(.horizontal)
                
                // 3. Delivery Partner Rating (Interactive 5 Stars)
                VStack(spacing: 12) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Rate Delivery Experience")
                                .font(.system(size: 14, weight: .bold))
                            Text(trackingData.riderName ?? "Delivery Partner")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        if ratingSubmitted {
                            Text("Rated \(selectedRating) ★")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "10B981"))
                        }
                    }
                    
                    HStack(spacing: 14) {
                        ForEach(1...5, id: \.self) { star in
                            Button(action: {
                                selectedRating = star
                                ratingSubmitted = true
                            }) {
                                Image(systemName: star <= selectedRating ? "star.fill" : "star")
                                    .font(.system(size: 28))
                                    .foregroundColor(star <= selectedRating ? Color(hex: "F59E0B") : Color.gray.opacity(0.35))
                            }
                        }
                    }
                    .padding(.vertical, 4)
                }
                .padding(16)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(12)
                .padding(.horizontal)
                
                // 4. Delivery Handoff Verification Details
                VStack(alignment: .leading, spacing: 12) {
                    Text("Delivery Details")
                        .font(.system(size: 14, weight: .bold))
                    
                    HStack(spacing: 10) {
                        Image(systemName: "checkmark.shield.fill")
                            .foregroundColor(Color(hex: "10B981"))
                        Text("Handed off with PIN verification")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }
                    
                    if let addr = trackingData.customerAddress {
                        HStack(alignment: .top, spacing: 10) {
                            Image(systemName: "house.fill")
                                .foregroundColor(Color(hex: "0284C7"))
                            Text(addr)
                                .font(.system(size: 13))
                                .foregroundColor(.secondary)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(16)
                .background(Color(.secondarySystemBackground))
                .cornerRadius(12)
                .padding(.horizontal)
                
                // 5. Done Button
                Button(action: {
                    container.trackingRepository.activeTracking = nil
                }) {
                    Text("DONE")
                        .font(.system(size: 15, weight: .black))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color(hex: "10B981"))
                        .cornerRadius(12)
                }
                .padding(.horizontal)
                .padding(.top, 8)
                .padding(.bottom, 24)
            }
        }
        .background(Color(.systemBackground).ignoresSafeArea())
    }
    
    // MARK: - Road Polyline Resolution (Zero Straight Lines Across Buildings)
    private func buildRouteCoordinates(_ data: CustomerOrderTrackingDto) -> [CLLocationCoordinate2D] {
        if let poly = data.routePolyline, !poly.isEmpty {
            let decoded = decodePolyline(poly)
            if !decoded.isEmpty { return decoded }
        }
        if !localRoadRoute.isEmpty {
            return localRoadRoute
        }
        // NEVER draw straight-line chords between points.
        // Return empty array until real road directions are resolved.
        return []
    }
    
    private func resolveRoadRouteIfNeeded(for data: CustomerOrderTrackingDto) {
        guard localRoadRoute.isEmpty else { return }
        guard data.routePolyline == nil || data.routePolyline!.isEmpty else { return }
        
        let origin: CLLocationCoordinate2D? = {
            if let rLat = data.riderLat, let rLng = data.riderLng {
                return CLLocationCoordinate2D(latitude: rLat, longitude: rLng)
            }
            if let mLat = data.merchantLat, let mLng = data.merchantLng {
                return CLLocationCoordinate2D(latitude: mLat, longitude: mLng)
            }
            return nil
        }()
        
        guard let startCoord = origin,
              let cLat = data.customerLat, let cLng = data.customerLng else { return }
        
        let destCoord = CLLocationCoordinate2D(latitude: cLat, longitude: cLng)
        
        let req = MKDirections.Request()
        req.source = MKMapItem(placemark: MKPlacemark(coordinate: startCoord))
        req.destination = MKMapItem(placemark: MKPlacemark(coordinate: destCoord))
        req.transportType = .automobile
        
        let directions = MKDirections(request: req)
        directions.calculate { response, error in
            guard let route = response?.routes.first else { return }
            var points = [CLLocationCoordinate2D](repeating: CLLocationCoordinate2D(), count: route.polyline.pointCount)
            route.polyline.getCoordinates(&points, range: NSRange(location: 0, length: route.polyline.pointCount))
            DispatchQueue.main.async {
                self.localRoadRoute = points
            }
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
            let dlat = (result & 1) != 0 ? ~(result >> 1) : (result >> 1)
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
            let dlng = (result & 1) != 0 ? ~(result >> 1) : (result >> 1)
            lng += dlng
            
            let coord = CLLocationCoordinate2D(latitude: Double(lat) / 1e5, longitude: Double(lng) / 1e5)
            coordinates.append(coord)
        }
        return coordinates
    }
    
    private func stageForStatus(_ status: String) -> Int {
        switch status.uppercased() {
        case "PLACED", "ACCEPTED": return 1
        case "PACKED", "READY_FOR_PICKUP": return 2
        case "DISPATCHED", "EN_ROUTE_CUSTOMER", "OUT_FOR_DELIVERY": return 3
        case "ARRIVED", "DELIVERED": return 4
        default: return 3
        }
    }
    
    private func stageDescription(for status: String) -> String {
        switch status.uppercased() {
        case "PLACED", "ACCEPTED": return "Order confirmed with merchant"
        case "PACKED", "READY_FOR_PICKUP": return "Packed and sealed at fulfillment hub"
        case "DISPATCHED", "EN_ROUTE_CUSTOMER", "OUT_FOR_DELIVERY": return "Your delivery partner is on the way"
        case "ARRIVED": return "Delivery partner has arrived at doorstep"
        case "DELIVERED": return "Order delivered successfully"
        default: return "Delivery partner is on the way"
        }
    }
    
    private func startLiveActivityIfNeeded() {
        guard let trackingData = trackingData else { return }
        let state = DeliveryActivityAttributes.ContentState(
            status: trackingData.status,
            stageTitle: "Rider on the way (~ \(trackingData.estimatedMinutes) mins)",
            etaMinutes: trackingData.estimatedMinutes,
            riderName: trackingData.riderName,
            riderPhone: trackingData.riderPhone,
            deliveryOtp: trackingData.deliveryOtp,
            currentStage: trackingData.currentStage,
            progressPercentage: Double(trackingData.currentStage) * 0.25
        )
        
        DeliveryActivityManager.shared.startLiveActivity(
            orderId: trackingData.orderId,
            merchantName: trackingData.merchantName ?? "QuickCommerce Hub",
            customerAddress: trackingData.customerAddress ?? "Your Location",
            itemCount: max(1, container.cartStore.totalItemCount),
            totalAmount: container.trackingRepository.activeTracking?.totalAmount ?? (container.cartStore.subtotal > 0 ? container.cartStore.subtotal : 0.0),
            initialState: state
        )
    }
    
    private func updateLiveActivity(with data: CustomerOrderTrackingDto) {
        let state = DeliveryActivityAttributes.ContentState(
            status: data.status,
            stageTitle: "Rider on the way (~ \(data.estimatedMinutes) mins)",
            etaMinutes: data.estimatedMinutes,
            riderName: data.riderName,
            riderPhone: data.riderPhone,
            deliveryOtp: data.deliveryOtp,
            currentStage: data.currentStage,
            progressPercentage: Double(data.currentStage) * 0.25
        )
        Task {
            await DeliveryActivityManager.shared.updateLiveActivity(state: state)
        }
    }
}

extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}
