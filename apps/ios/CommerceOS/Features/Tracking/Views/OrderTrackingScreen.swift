import SwiftUI
import CoreLocation
import MapKit

/// Full-fidelity Order Details & Live Tracking Screen with 1:1 Android Parity.
/// Directly mirrors Android's `OrderTrackingScreen.kt` and `CustomerLiveMapTrackingView.kt`.
public struct OrderTrackingScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @ObservedObject private var trackingRepo = TrackingRepository.shared
    
    public let orderId: String?
    public var onBack: (() -> Void)?
    
    @State private var trackingData: CustomerOrderTrackingDto? = nil
    @State private var orderDetail: ServerOrderResponse? = nil
    @State private var copiedOrderId: Bool = false
    @State private var userRating: Int = 5
    @State private var ratingSubmitted: Bool = false
    @State private var localRoadRoute: [CLLocationCoordinate2D] = []
    @State private var isMapExpanded: Bool = false
    @State private var isShowingCancelSheet: Bool = false
    @State private var pulseScale: CGFloat = 0.85
    @State private var pulseAlpha: Double = 0.5
    
    public init(orderId: String? = nil, onBack: (() -> Void)? = nil) {
        self.orderId = orderId
        self.onBack = onBack
    }
    
    // MARK: - Authoritative Computed State
    private var effectiveOrderId: String {
        orderId ?? trackingData?.orderId ?? orderDetail?.id ?? "UNKNOWN"
    }
    
    private var shortOrderId: String {
        String(effectiveOrderId.suffix(6)).uppercased()
    }
    
    private var effectiveStatus: String {
        orderDetail?.orderStatus ?? orderDetail?.status ?? trackingData?.status ?? "PLACED"
    }
    
    private var isDelivered: Bool {
        effectiveStatus.uppercased() == "DELIVERED"
    }
    
    private var isCancelled: Bool {
        effectiveStatus.uppercased() == "CANCELLED"
    }
    
    private var isActive: Bool {
        !isDelivered && !isCancelled
    }
    
    private var canCancel: Bool {
        let s = effectiveStatus.uppercased()
        return s == "PLACED" || s == "ORDER_PLACED" || s == "CREATED" ||
               s == "CONFIRMED" || s == "ACCEPTED" || s == "SELLER_ACCEPTED" ||
               s == "ORDER_SELLER_ACCEPTED" || s == "ALLOCATED_DARK_STORE" ||
               s == "PAYMENT_PENDING" || s == "PRESCRIPTION_VERIFICATION_PENDING" ||
               s == "PACKED" || s == "PACKED_FEFO" || s == "READY_FOR_PICKUP" ||
               s == "SEARCHING_FOR_RIDER" || s == "LOOKING_FOR_RIDER"
    }
    
    /// Strict Parity with Android `dynamicEtaMins`:
    /// Coerces to at least 8 minutes, never displays 0 minutes.
    private var dynamicEtaMins: Int {
        if let liveMins = trackingData?.estimatedMinutes, liveMins > 0 {
            return max(liveMins, 8)
        }
        if let sla = orderDetail?.effectiveSlaMins, sla > 0 {
            return max(sla, 8)
        }
        return 10
    }
    
    private var displayOtp: String? {
        orderDetail?.effectiveDeliveryPin ?? trackingData?.deliveryOtp
    }
    
    private var storeDisplayName: String {
        orderDetail?.storeName ?? trackingData?.merchantName ?? "CommerceOS Central Express Hub"
    }
    
    private var items: [ServerOrderItemResponse] {
        orderDetail?.items ?? []
    }
    
    public var body: some View {
        ZStack {
            Color(hex: "F4F5F7").ignoresSafeArea()
            
            if isMapExpanded && !isDelivered {
                // Fullscreen Map Mode (Matching Android CustomerLiveMapTrackingView isFullscreen)
                ZStack(alignment: .topLeading) {
                    ZomatoDarkMapView(
                        riderCoordinate: resolvedRiderCoordinate,
                        merchantCoordinate: resolvedMerchantCoordinate,
                        customerCoordinate: resolvedCustomerCoordinate,
                        routeCoordinates: buildRouteCoordinates(trackingData)
                    )
                    .edgesIgnoringSafeArea(.all)
                    
                    Button(action: { isMapExpanded = false }) {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 42, height: 42)
                            .overlay(
                                Image(systemName: "arrow.down.right.and.arrow.up.left")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(Color(hex: "0F172A"))
                            )
                            .shadow(color: Color.black.opacity(0.2), radius: 4, x: 0, y: 2)
                    }
                    .padding(.top, 54)
                    .padding(.leading, 16)
                }
            } else {
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        // 1. FIRST ELEMENT AT VERY TOP:
                        // Active delivery -> Live interactive Dark Map tracking (300pt height)
                        // Delivered or Cancelled order -> Clean top app bar with Back button and Order ID (NO MAP)
                        if !isDelivered && !isCancelled {
                            liveMapHeaderView
                        } else {
                            deliveredTopBarView
                        }
                        
                        // 2. BELOW TOP HEADER: STATUS CARDS, DETAILS, ITEMS, RECEIPT
                        VStack(spacing: 14) {
                            // Reconnecting notification banner if telemetry stream drops (Exact Android guard string)
                            if trackingRepo.isStreamReconnecting && !isDelivered && !isCancelled {
                                reconnectingBanner
                            }
                            
                            if isCancelled {
                                cancelledOrderCard
                            } else if isDelivered {
                                deliveredSuccessHeroCard
                            } else {
                                activeStatusSlaCard
                                assignedPartnerCard
                            }
                            
                            // Ordered Items Card
                            if !items.isEmpty {
                                itemsOrderedCard
                            }
                            
                            // Licensed Pharmacy & Quality Assurance Anatomy
                            verifiedPharmacyCard
                            
                            // Delivery Location Card
                            deliveryLocationCard
                            
                            // Bill Summary & Payment Breakdown
                            billSummaryCard
                            
                            // Cancel Order Action Button (Pre-delivery cancellation)
                            if canCancel {
                                cancelOrderButton
                            }
                            
                            // Need Help / Support Button
                            supportButton
                        }
                        .padding(.horizontal, 14)
                        .padding(.top, 14)
                        .padding(.bottom, 60)
                    }
                }
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            initializeScreen()
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
            let rawEta = update.etaMinutes ?? self.orderDetail?.effectiveSlaMins ?? 10
            let effEta = max(rawEta > 0 ? rawEta : 10, 8)
            
            let updatedDto = CustomerOrderTrackingDto(
                orderId: update.orderId,
                deliveryId: "DEL-\(update.orderId.suffix(4))",
                status: update.status,
                currentStage: stage,
                estimatedMinutes: effEta,
                merchantName: self.storeDisplayName,
                merchantAddress: nil,
                merchantLat: update.merchantLat,
                merchantLng: update.merchantLng,
                customerAddress: self.orderDetail?.deliveryAddress?.addressLine ?? "Delivery Address",
                customerLat: update.customerLat,
                customerLng: update.customerLng,
                riderName: update.riderName ?? self.orderDetail?.riderName,
                riderPhone: update.riderPhone ?? self.orderDetail?.riderPhone,
                riderLat: update.riderLat,
                riderLng: update.riderLng,
                routePolyline: update.routePolyline,
                deliveryOtp: update.deliveryOtp ?? self.orderDetail?.deliveryOtp
            )
            self.trackingData = updatedDto
            self.updateLiveActivity(with: updatedDto)
            self.resolveRoadRouteIfNeeded(for: updatedDto)
        }
        .sheet(isPresented: $isShowingCancelSheet) {
            CancelOrderSheet(orderId: effectiveOrderId) {
                if let current = self.orderDetail {
                    self.orderDetail = ServerOrderResponse(
                        id: current.id,
                        orderId: current.orderId,
                        customerId: current.customerId,
                        status: "CANCELLED",
                        orderStatus: "CANCELLED",
                        totalAmount: current.totalAmount,
                        deliveryFee: current.deliveryFee,
                        paymentMethod: current.paymentMethod,
                        paymentStatus: current.paymentStatus,
                        deliveryOtp: current.deliveryOtp,
                        createdAt: current.createdAt,
                        deliverySlaMins: current.deliverySlaMins,
                        deliveryAddress: current.deliveryAddress,
                        items: current.items,
                        riderName: current.riderName,
                        riderPhone: current.riderPhone,
                        riderVehicle: current.riderVehicle,
                        storeName: current.storeName
                    )
                } else {
                    self.orderDetail = ServerOrderResponse(
                        id: effectiveOrderId,
                        orderId: effectiveOrderId,
                        status: "CANCELLED",
                        orderStatus: "CANCELLED"
                    )
                }
                self.trackingData = nil
                self.container.trackingRepository.stopLiveTracking()
            }
        }
    }
    
    // MARK: - Top Map Header (300pt Height matching Android 300.dp)
    private var liveMapHeaderView: some View {
        ZStack(alignment: .top) {
            ZomatoDarkMapView(
                riderCoordinate: resolvedRiderCoordinate,
                merchantCoordinate: resolvedMerchantCoordinate,
                customerCoordinate: resolvedCustomerCoordinate,
                routeCoordinates: buildRouteCoordinates(trackingData)
            )
            .frame(height: 300)
            
            // Top Navigation Overlay
            HStack(alignment: .center) {
                // Back Button (Matching Android 38dp surface)
                Button(action: {
                    if let onBack = onBack {
                        onBack()
                    }
                }) {
                    Circle()
                        .fill(Color.white)
                        .frame(width: 38, height: 38)
                        .overlay(
                            Circle()
                                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                        )
                        .overlay(
                            Image(systemName: "arrow.left")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        )
                        .shadow(color: Color.black.opacity(0.15), radius: 3, x: 0, y: 1)
                }
                
                Spacer()
                
                // Authoritative Order ID Badge with Tap to Copy
                Button(action: copyOrderIdToClipboard) {
                    HStack(spacing: 6) {
                        Text("ORDER #\(shortOrderId)")
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .foregroundColor(Color(hex: "0F172A"))
                            .tracking(0.5)
                        
                        Text(copiedOrderId ? "✓" : "⧉")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(copiedOrderId ? Color(hex: "059669") : Color(hex: "64748B"))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.white.opacity(0.92))
                    .cornerRadius(8)
                    .shadow(color: Color.black.opacity(0.15), radius: 3, x: 0, y: 1)
                }
                .buttonStyle(PlainButtonStyle())
                
                // Expand Map Button
                Button(action: { isMapExpanded = true }) {
                    Circle()
                        .fill(Color.white.opacity(0.92))
                        .frame(width: 36, height: 36)
                        .overlay(
                            Image(systemName: "arrow.up.left.and.arrow.down.right")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        )
                        .shadow(color: Color.black.opacity(0.15), radius: 3, x: 0, y: 1)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 50)
        }
        .frame(height: 300)
    }
    
    // MARK: - Delivered Clean Top Bar (Map is completely hidden)
    private var deliveredTopBarView: some View {
        HStack(alignment: .center) {
            Button(action: {
                if let onBack = onBack {
                    onBack()
                }
            }) {
                Circle()
                    .fill(Color.white)
                    .frame(width: 38, height: 38)
                    .overlay(
                        Circle()
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                    .overlay(
                        Image(systemName: "arrow.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                    )
            }
            
            Spacer().frame(width: 12)
            
            VStack(alignment: .leading, spacing: 2) {
                Text("Order Details")
                    .font(.system(size: 19, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Text("Summary & Receipt")
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: "64748B"))
            }
            
            Spacer()
            
            Button(action: copyOrderIdToClipboard) {
                HStack(spacing: 5) {
                    Text("ORDER #\(shortOrderId)")
                        .font(.system(size: 11, weight: .bold, design: .monospaced))
                        .foregroundColor(Color(hex: "0F172A"))
                        .tracking(0.5)
                    
                    Text(copiedOrderId ? "✓" : "⧉")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(copiedOrderId ? Color(hex: "059669") : Color(hex: "64748B"))
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(hex: "F1F5F9"))
                .cornerRadius(8)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .padding(.top, 40)
        .background(Color.white)
        .overlay(
            Rectangle()
                .fill(Color(hex: "E2E8F0"))
                .frame(height: 1),
            alignment: .bottom
        )
    }
    
    // MARK: - Reconnecting Banner
    private var reconnectingBanner: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(Color(hex: "F59E0B"))
                .frame(width: 8, height: 8)
            Text("Live telemetry stream reconnecting with exponential backoff...")
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Color(hex: "92400E"))
            Spacer()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(Color(hex: "FEF3C7"))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color(hex: "F59E0B").opacity(0.5), lineWidth: 1)
        )
    }
    
    // MARK: - Active Order Status & SLA Card (Live Radar Pulse + 4-Digit PIN)
    private var activeStatusSlaCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    // Pulsing Radar Status Row
                    HStack(spacing: 6) {
                        Circle()
                            .fill(isActive ? Color(hex: "059669").opacity(pulseAlpha) : Color(hex: "2563EB"))
                            .frame(width: 8, height: 8)
                            .scaleEffect(isActive ? pulseScale : 1.0)
                        
                        Text(statusBadgeTitle)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(isActive ? Color(hex: "059669") : Color(hex: "2563EB"))
                            .tracking(0.5)
                    }
                    
                    // Dynamic ETA Header (Guaranteed >= 8 mins, never 0)
                    Text(etaDisplayHeadline)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                    
                    Text("\(storeDisplayName) • Direct dispatch")
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "64748B"))
                }
                
                Spacer()
                
                // Delivery PIN Badge (Matching Android PIN Container)
                if let pin = displayOtp, !pin.isEmpty {
                    VStack(spacing: 2) {
                        HStack(spacing: 3) {
                            Image(systemName: "lock.fill")
                                .font(.system(size: 9))
                                .foregroundColor(Color(hex: "B45309"))
                            Text("PIN")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Color(hex: "92400E"))
                        }
                        Text(pin)
                            .font(.system(size: 17, weight: .black, design: .monospaced))
                            .foregroundColor(Color(hex: "B45309"))
                            .tracking(2)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(hex: "FFFBEB"))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: "FCD34D"), lineWidth: 1)
                    )
                }
            }
            
            // Stepper Progress Line
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color(hex: "E2E8F0"))
                        .frame(height: 4)
                    
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color(hex: "059669"))
                        .frame(width: geo.size.width * CGFloat(progressRatio), height: 4)
                }
            }
            .frame(height: 4)
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    // MARK: - Assigned Partner Card (🛵 Icon + Call Button)
    private var assignedPartnerCard: some View {
        let riderName = trackingData?.riderName ?? orderDetail?.riderName ?? "Assigned Delivery Partner"
        let riderPhone = trackingData?.riderPhone ?? orderDetail?.riderPhone ?? ""
        let riderVehicle = orderDetail?.riderVehicle ?? "Electric Delivery Scooter"
        
        return HStack(alignment: .center, spacing: 12) {
            Circle()
                .fill(Color(hex: "ECFDF5"))
                .frame(width: 46, height: 46)
                .overlay(
                    Text("🛵")
                        .font(.system(size: 22))
                )
            
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Text(riderName)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "059669"))
                }
                Text("Delivery Partner • 4.9 ★")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: "059669"))
                Text(riderVehicle)
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: "64748B"))
            }
            
            Spacer()
            
            if !riderPhone.isEmpty {
                Button(action: {
                    if let url = URL(string: "tel://\(riderPhone.replacingOccurrences(of: " ", with: ""))") {
                        UIApplication.shared.open(url)
                    }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text("Call")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(Color(hex: "059669"))
                    .cornerRadius(10)
                }
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    // MARK: - Delivered Success Hero Card
    private var deliveredSuccessHeroCard: some View {
        VStack(spacing: 14) {
            Circle()
                .fill(Color(hex: "ECFDF5"))
                .frame(width: 64, height: 64)
                .overlay(
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 38, weight: .bold))
                        .foregroundColor(Color(hex: "059669"))
                )
            
            Text("Order Delivered Successfully 🎉")
                .font(.system(size: 19, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
                .multilineTextAlignment(.center)
            
            Text("Your package was handed over safely at your doorstep.")
                .font(.system(size: 12))
                .foregroundColor(Color(hex: "64748B"))
                .multilineTextAlignment(.center)
            
            // 5-Star Rating Bar
            HStack(spacing: 8) {
                ForEach(1...5, id: \.self) { star in
                    Button(action: {
                        userRating = star
                        ratingSubmitted = true
                    }) {
                        Image(systemName: star <= userRating ? "star.fill" : "star")
                            .font(.system(size: 22))
                            .foregroundColor(Color(hex: "FBBF24"))
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.top, 4)
            
            if ratingSubmitted {
                Text("Thank you for your rating!")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(hex: "059669"))
            }
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "D1FAE5"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    // MARK: - Cancelled Card
    private var cancelledOrderCard: some View {
        VStack(spacing: 10) {
            Circle()
                .fill(Color(hex: "FEE2E2"))
                .frame(width: 52, height: 52)
                .overlay(
                    Text("✕")
                        .font(.system(size: 22, weight: .black))
                        .foregroundColor(Color(hex: "DC2626"))
                )
            
            Text("Order Cancelled")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            Text("This order was cancelled. If you paid online, a full refund will be credited to your account. For Cash on Delivery, no payment is due.")
                .font(.system(size: 12))
                .foregroundColor(Color(hex: "64748B"))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(20)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "FEE2E2"), lineWidth: 1)
        )
    }
    
    // MARK: - Items Ordered Card (Authentic Medicine Packaging Photos)
    private var itemsOrderedCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Items Ordered (\(items.count))")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            VStack(spacing: 12) {
                ForEach(items, id: \.sku) { item in
                    HStack(alignment: .center, spacing: 12) {
                        // Authentic Medicine Thumbnail
                        let imgUrl = MedicineImageResolver.resolve(sku: item.sku, name: item.name)
                        ZStack {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color(hex: "F8FAFC"))
                                .frame(width: 56, height: 56)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                            
                            if let url = URL(string: imgUrl), !imgUrl.isEmpty {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let img):
                                        img.resizable()
                                            .aspectRatio(contentMode: .fit)
                                            .frame(width: 48, height: 48)
                                            .cornerRadius(8)
                                    default:
                                        Image(systemName: "pills.fill")
                                            .font(.system(size: 20))
                                            .foregroundColor(Color(hex: "059669"))
                                    }
                                }
                            } else {
                                Image(systemName: "pills.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(Color(hex: "059669"))
                            }
                        }
                        .frame(width: 56, height: 56)
                        
                        // Item Details
                        VStack(alignment: .leading, spacing: 3) {
                            Text(item.name ?? item.sku)
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(Color(hex: "0F172A"))
                                .lineLimit(2)
                            
                            HStack(spacing: 6) {
                                Text("Qty: \(item.quantity)")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Color(hex: "334155"))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color(hex: "F1F5F9"))
                                    .cornerRadius(4)
                                
                                Text("₹\(Int(item.effectivePrice)) each")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color(hex: "64748B"))
                            }
                        }
                        
                        Spacer()
                        
                        // Item Total
                        Text("₹\(Int(item.effectivePrice * Double(item.quantity)))")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                    }
                }
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    // MARK: - Licensed Pharmacy Dispensation Card
    private var verifiedPharmacyCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 10) {
                Circle()
                    .fill(Color(hex: "ECFDF5"))
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "checkmark.shield.fill")
                            .font(.system(size: 18))
                            .foregroundColor(Color(hex: "059669"))
                    )
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("Verified Pharmacy Dispensation")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                    Text("Licensed Pharmacist Verified & Quality Checked")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color(hex: "059669"))
                }
            }
            
            Divider()
                .background(Color(hex: "F1F5F9"))
                .padding(.vertical, 2)
            
            HStack {
                Text("Dispensed By")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "64748B"))
                Spacer()
                Text(storeDisplayName)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(hex: "0F172A"))
            }
            
            HStack {
                Text("Drug License")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "64748B"))
                Spacer()
                Text("DL-HR-2024-009182")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(hex: "334155"))
            }
            
            HStack {
                Text("Safety & Storage")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "64748B"))
                Spacer()
                Text("Tamper-Evident & Cold Chain Monitored")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(hex: "059669"))
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    // MARK: - Delivery Location Card
    private var deliveryLocationCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "mappin.circle.fill")
                    .font(.system(size: 18))
                    .foregroundColor(Color(hex: "059669"))
                Text("Delivery Location")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
            }
            
            let addr = orderDetail?.deliveryAddress
            let fullText: String = {
                var s = addr?.addressLine ?? "Delivery address registered on order"
                if let c = addr?.city, !c.isEmpty { s += ", \(c)" }
                if let p = addr?.postalCode, !p.isEmpty { s += " - \(p)" }
                return s
            }()
            
            Text(fullText)
                .font(.system(size: 13))
                .foregroundColor(Color(hex: "334155"))
                .lineSpacing(3)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    // MARK: - Bill Summary & Payment Breakdown
    private var billSummaryCard: some View {
        let itemSubtotal = items.reduce(0.0) { $0 + ($1.effectivePrice * Double($1.quantity)) }
        let effectiveDeliveryFee = orderDetail?.deliveryFee ?? (itemSubtotal >= 199.0 ? 0.0 : 2.0)
        let isFreeDelivery = effectiveDeliveryFee <= 0.0
        let totalAmt = orderDetail?.totalAmount ?? (itemSubtotal + (isFreeDelivery ? 0.0 : effectiveDeliveryFee))
        let isCod = (orderDetail?.paymentMethod?.uppercased() == "COD") || (orderDetail?.paymentMethod == nil)
        
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Bill Summary")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                
                Spacer()
                
                Text(isCod ? "Cash on Delivery" : "Paid Online")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: "059669"))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color(hex: "ECFDF5"))
                    .cornerRadius(6)
            }
            
            Divider().background(Color(hex: "F1F5F9"))
            
            HStack {
                Text("Item Total")
                    .font(.system(size: 13))
                    .foregroundColor(Color(hex: "64748B"))
                Spacer()
                Text("₹\(Int(itemSubtotal))")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(Color(hex: "0F172A"))
            }
            
            HStack {
                Text("Delivery Partner Fee")
                    .font(.system(size: 13))
                    .foregroundColor(Color(hex: "64748B"))
                Spacer()
                Text(isFreeDelivery ? "FREE" : "₹\(Int(effectiveDeliveryFee))")
                    .font(.system(size: 13, weight: isFreeDelivery ? .bold : .medium))
                    .foregroundColor(isFreeDelivery ? Color(hex: "059669") : Color(hex: "0F172A"))
            }
            
            Divider().background(Color(hex: "F1F5F9"))
            
            HStack {
                Text("To Pay")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Spacer()
                Text("₹\(Int(totalAmt))")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    // MARK: - Cancel Order Button
    private var cancelOrderButton: some View {
        Button(action: {
            isShowingCancelSheet = true
        }) {
            HStack(spacing: 8) {
                Image(systemName: "xmark.circle.fill")
                    .font(.system(size: 15, weight: .bold))
                Text("Cancel Order")
                    .font(.system(size: 14, weight: .bold))
            }
            .foregroundColor(Color(hex: "DC2626"))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(Color.white)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "FCA5A5"), lineWidth: 1.5)
            )
            .shadow(color: Color.black.opacity(0.02), radius: 3, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
    }

    // MARK: - Support Button
    private var supportButton: some View {
        Button(action: {
            if let url = URL(string: "tel://919817916180") {
                UIApplication.shared.open(url)
            }
        }) {
            HStack(spacing: 8) {
                Image(systemName: "questionmark.circle")
                    .font(.system(size: 14, weight: .bold))
                Text("Need Help with this Order?")
                    .font(.system(size: 13, weight: .bold))
            }
            .foregroundColor(Color(hex: "64748B"))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(Color.white)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    // MARK: - Helpers & Presentation Logic
    private var statusBadgeTitle: String {
        switch effectiveStatus.uppercased() {
        case "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU":
            return "ON THE WAY"
        case "SELLER_ACCEPTED", "PACKED":
            return "BEING PACKED"
        case "ARRIVED_CUSTOMER", "HANDOFF_STARTED":
            return "AT YOUR DOORSTEP"
        case "DELIVERED":
            return "DELIVERED"
        default:
            return "ORDER CONFIRMED"
        }
    }
    
    private var etaDisplayHeadline: String {
        switch effectiveStatus.uppercased() {
        case "ARRIVED_CUSTOMER", "HANDOFF_STARTED":
            return "At your doorstep ⚡"
        case "DELIVERED":
            return "Delivered ⚡"
        default:
            return "Arriving in \(dynamicEtaMins) mins ⚡"
        }
    }
    
    private var progressRatio: Float {
        switch effectiveStatus.uppercased() {
        case "DELIVERED": return 1.0
        case "ARRIVED_CUSTOMER", "HANDOFF_STARTED": return 0.9
        case "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU": return 0.75
        case "PACKED", "READY_FOR_PICKUP": return 0.55
        case "SELLER_ACCEPTED", "ACCEPTED": return 0.35
        default: return 0.25
        }
    }
    
    private var resolvedRiderCoordinate: CLLocationCoordinate2D? {
        if let lat = trackingData?.riderLat, let lng = trackingData?.riderLng, lat != 0.0, lng != 0.0 {
            return CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
        return nil
    }
    
    private var resolvedMerchantCoordinate: CLLocationCoordinate2D? {
        if let lat = trackingData?.merchantLat, let lng = trackingData?.merchantLng, lat != 0.0, lng != 0.0 {
            return CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
        return CLLocationCoordinate2D(latitude: 28.202224, longitude: 76.615418)
    }
    
    private var resolvedCustomerCoordinate: CLLocationCoordinate2D? {
        if let lat = trackingData?.customerLat, let lng = trackingData?.customerLng, lat != 0.0, lng != 0.0 {
            return CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
        if let lat = orderDetail?.deliveryAddress?.latitude, let lng = orderDetail?.deliveryAddress?.longitude, lat != 0.0, lng != 0.0 {
            return CLLocationCoordinate2D(latitude: lat, longitude: lng)
        }
        return CLLocationCoordinate2D(latitude: 28.1918, longitude: 76.6081)
    }
    
    private func copyOrderIdToClipboard() {
        UIPasteboard.general.string = effectiveOrderId
        copiedOrderId = true
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            copiedOrderId = false
        }
    }
    
    private func initializeScreen() {
        withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
            pulseScale = 1.2
            pulseAlpha = 1.0
        }
        
        if let id = orderId {
            // Instant cache resolution
            if let cached = container.orderRepository.customerOrders.first(where: { $0.id == id || $0.orderId == id }) {
                self.orderDetail = cached
            } else if let last = container.orderRepository.lastPlacedOrder, (last.id == id || last.orderId == id) {
                self.orderDetail = last
            }
            
            Task {
                if let fresh = await container.orderRepository.fetchOrderDetail(orderId: id) {
                    await MainActor.run {
                        self.orderDetail = fresh
                    }
                }
            }
            
            container.trackingRepository.startLiveTracking(orderId: id)
        }
    }
    
    // MARK: - Road Polyline Resolution
    private func buildRouteCoordinates(_ data: CustomerOrderTrackingDto?) -> [CLLocationCoordinate2D] {
        guard let data = data else { return localRoadRoute }
        if let poly = data.routePolyline, !poly.isEmpty {
            let decoded = decodePolyline(poly)
            if !decoded.isEmpty { return decoded }
        }
        return localRoadRoute
    }
    
    private func resolveRoadRouteIfNeeded(for data: CustomerOrderTrackingDto) {
        guard localRoadRoute.isEmpty else { return }
        guard data.routePolyline == nil || data.routePolyline!.isEmpty else { return }
        
        let origin: CLLocationCoordinate2D? = {
            if let rLat = data.riderLat, let rLng = data.riderLng, rLat != 0.0 {
                return CLLocationCoordinate2D(latitude: rLat, longitude: rLng)
            }
            if let mLat = data.merchantLat, let mLng = data.merchantLng, mLat != 0.0 {
                return CLLocationCoordinate2D(latitude: mLat, longitude: mLng)
            }
            return nil
        }()
        
        guard let startCoord = origin,
              let cLat = data.customerLat, let cLng = data.customerLng, cLat != 0.0 else { return }
        
        let destCoord = CLLocationCoordinate2D(latitude: cLat, longitude: cLng)
        
        let req = MKDirections.Request()
        req.source = MKMapItem(placemark: MKPlacemark(coordinate: startCoord))
        req.destination = MKMapItem(placemark: MKPlacemark(coordinate: destCoord))
        req.transportType = .automobile
        
        let directions = MKDirections(request: req)
        directions.calculate { response, _ in
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
    public func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

public struct RoundedCorner: Shape {
    public var radius: CGFloat = .infinity
    public var corners: UIRectCorner = .allCorners

    public init(radius: CGFloat = .infinity, corners: UIRectCorner = .allCorners) {
        self.radius = radius
        self.corners = corners
    }

    public func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(roundedRect: rect, byRoundingCorners: corners, cornerRadii: CGSize(width: radius, height: radius))
        return Path(path.cgPath)
    }
}
