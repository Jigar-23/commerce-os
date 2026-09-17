import SwiftUI
import CoreLocation

public struct ActiveDeliveryScreen: View {
    @EnvironmentObject private var container: RiderContainer
    @EnvironmentObject private var sessionManager: RiderSessionManager
    @EnvironmentObject private var locationManager: RiderBackgroundLocationManager
    
    @StateObject private var geofence = RiderGeofenceDetector.shared
    @StateObject private var streamer = RiderTelemetryStreamer.shared
    
    @State private var showingOtpDialog: Bool = false
    @State private var enteredOtp: String = ""
    @State private var otpErrorMessage: String? = nil
    @State private var isSubmittingOtp: Bool = false
    @State private var isDelivered: Bool = false
    @State private var copiedOrderId: Bool = false
    @State private var completedOrderId: String? = nil
    @State private var completedCustomerName: String = ""
    @State private var completedIsCod: Bool = false
    @State private var completedCodAmount: Double? = nil
    @State private var showCelebrationModal: Bool = false
    @State private var isSimulatingRoute: Bool = false
    @State private var simulationTask: Task<Void, Never>? = nil
    
    public init() {}
    
    public var body: some View {
        ZStack(alignment: .bottom) {
            // Live Turn-by-Turn Map
            RiderLiveNavigationView(
                merchantCoordinate: sessionManager.activeSession.map { CLLocationCoordinate2D(latitude: $0.merchantLat, longitude: $0.merchantLng) },
                customerCoordinate: sessionManager.activeSession.map { CLLocationCoordinate2D(latitude: $0.customerLat, longitude: $0.customerLng) },
                riderCoordinate: locationManager.lastLocation?.coordinate ?? currentWaypointCoordinate,
                destinationCoordinate: currentWaypointCoordinate,
                riderBearing: locationManager.currentBearing,
                speedKmh: locationManager.currentSpeed
            )
            .edgesIgnoringSafeArea(.all)
            
            // Top Turn-by-Turn Maneuver Header with Geofence Distance
            VStack {
                HStack(spacing: 14) {
                    Image(systemName: geofence.isInsideArrivalZone ? "checkmark.circle.fill" : "arrow.turn.up.right")
                        .font(.system(size: 28, weight: .black))
                        .foregroundColor(geofence.isInsideArrivalZone ? RiderTheme.Colors.safetyGreen : RiderTheme.Colors.speedAccent)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(currentTurnInstruction)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                        
                        HStack(spacing: 8) {
                            if let dist = geofence.distanceToTargetMeters {
                                Text("\(Int(dist))m to target")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(dist <= 50 ? RiderTheme.Colors.safetyGreen : RiderTheme.Colors.safetyYellow)
                            } else {
                                Text("Speed: \(Int(locationManager.currentSpeed)) km/h")
                                    .font(.system(size: 12))
                                    .foregroundColor(.gray)
                            }
                            
                            if geofence.isInsideArrivalZone {
                                Text("50M GEOFENCE TRIGGERED")
                                    .font(.system(size: 9, weight: .black))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(RiderTheme.Colors.safetyGreen)
                                    .foregroundColor(.black)
                                    .cornerRadius(4)
                            }
                        }
                    }
                    Spacer()
                }
                .padding(16)
                .background(Color.black.opacity(0.92))
                .cornerRadius(12)
                .padding(.horizontal)
                .padding(.top, 44)
                
                Spacer()
            }
            
            // Bottom Action Card
            if let session = sessionManager.activeSession {
                VStack(spacing: 12) {
                    // Pull Handle
                    RoundedRectangle(cornerRadius: 2.5)
                        .fill(Color.gray.opacity(0.5))
                        .frame(width: 36, height: 5)
                        .padding(.top, 6)
                    
                    // Status Badge & Order Number
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text("ORDER \(session.orderId)")
                                    .font(.system(size: 13, weight: .black))
                                Button(action: {
                                    UIPasteboard.general.string = session.orderId
                                    copiedOrderId = true
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                        copiedOrderId = false
                                    }
                                }) {
                                    HStack(spacing: 3) {
                                        Image(systemName: copiedOrderId ? "checkmark" : "doc.on.doc")
                                        Text(copiedOrderId ? "COPIED" : "COPY")
                                    }
                                    .font(.system(size: 8, weight: .black))
                                    .padding(.horizontal, 5)
                                    .padding(.vertical, 2)
                                    .background(copiedOrderId ? RiderTheme.Colors.safetyGreen.opacity(0.3) : Color.gray.opacity(0.3))
                                    .foregroundColor(copiedOrderId ? RiderTheme.Colors.safetyGreen : .white)
                                    .cornerRadius(4)
                                }
                            }
                            Text(stageTitle)
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(RiderTheme.Colors.safetyYellow)
                        }
                        Spacer()
                        Button(action: {
                            toggleRouteSimulation()
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: isSimulatingRoute ? "pause.fill" : "play.fill")
                                Text(isSimulatingRoute ? "Simulating..." : "Simulate Drive")
                            }
                            .font(.system(size: 10, weight: .bold))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(isSimulatingRoute ? Color.orange.opacity(0.3) : Color.blue.opacity(0.25))
                            .foregroundColor(isSimulatingRoute ? .orange : Color(red: 56/255, green: 189/255, blue: 248/255))
                            .cornerRadius(6)
                        }
                        Text("10-Min SLA")
                            .font(.system(size: 11, weight: .black))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(RiderTheme.Colors.safetyGreen.opacity(0.2))
                            .foregroundColor(RiderTheme.Colors.safetyGreen)
                            .cornerRadius(6)
                    }
                    .padding(.horizontal)
                    
                    Divider()
                    
                    // Current Destination Address Info
                    HStack(spacing: 12) {
                        Image(systemName: isEnRouteToStore ? "cross.case.fill" : "house.fill")
                            .font(.system(size: 24))
                            .foregroundColor(isEnRouteToStore ? RiderTheme.Colors.safetyGreen : RiderTheme.Colors.speedAccent)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(isEnRouteToStore ? session.merchantName : session.customerName)
                                .font(.system(size: 14, weight: .bold))
                            Text(isEnRouteToStore ? session.merchantAddress : session.customerAddress)
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                        Spacer()
                        
                        Button(action: {}) {
                            Image(systemName: "phone.circle.fill")
                                .font(.system(size: 32))
                                .foregroundColor(.green)
                        }
                    }
                    .padding(.horizontal)
                    
                    if !isEnRouteToStore && (session.isCod || (session.codAmountToCollect ?? 0) > 0) {
                        HStack(spacing: 8) {
                            Image(systemName: "banknote.fill")
                                .font(.system(size: 20))
                                .foregroundColor(RiderTheme.Colors.safetyYellow)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("CASH TO COLLECT (COD)")
                                    .font(.system(size: 11, weight: .black))
                                    .foregroundColor(RiderTheme.Colors.safetyYellow)
                                Text("₹\(String(format: "%.2f", session.codAmountToCollect ?? 0))")
                                    .font(.system(size: 18, weight: .black))
                                    .foregroundColor(.white)
                            }
                            Spacer()
                            Text("COLLECT CASH")
                                .font(.system(size: 9, weight: .black))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 3)
                                .background(RiderTheme.Colors.safetyYellow.opacity(0.2))
                                .foregroundColor(RiderTheme.Colors.safetyYellow)
                                .cornerRadius(4)
                        }
                        .padding(12)
                        .background(Color(hex: "1E293B"))
                        .cornerRadius(10)
                        .padding(.horizontal)
                    }
                    
                    // Action Trigger Button based on stage
                    Button(action: handleStageAction) {
                        Text(actionButtonTitle)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(actionButtonColor)
                            .cornerRadius(10)
                    }
                    .padding(.horizontal)
                    .padding(.bottom, 16)
                }
                .background(Color(.systemBackground))
                .cornerRadius(18)
                .shadow(color: Color.black.opacity(0.3), radius: 10, x: 0, y: -4)
            }
            
            // Celebration Modal Overlay
            if showCelebrationModal, let orderId = completedOrderId {
                DeliveryCompletionCelebrationView(
                    orderId: orderId,
                    customerName: completedCustomerName,
                    payoutFormatted: "₹65.00",
                    isCod: completedIsCod,
                    codAmount: completedCodAmount,
                    onDismiss: {
                        showCelebrationModal = false
                        sessionManager.activeSession = nil
                        container.locationManager.activeDeliveryId = nil
                    }
                )
                .zIndex(200)
                .transition(.opacity.combined(with: .scale))
            }
        }
        .sheet(isPresented: $showingOtpDialog) {
            if let session = sessionManager.activeSession {
                NavigationView {
                    ScrollView {
                        CustomerHandoffView(
                            session: session,
                            onVerifyOtp: { otp, cash in
                                return await verifyDeliveryOtp(otp, cashCollected: cash)
                            },
                            onComplete: {
                                showingOtpDialog = false
                            }
                        )
                        .padding()
                    }
                    .background(Color(hex: "0D0F14").ignoresSafeArea())
                    .navigationTitle("Customer Handoff")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarTrailing) {
                            Button("Close") { showingOtpDialog = false }
                                .foregroundColor(Color(hex: "38BDF8"))
                        }
                    }
                }
                .navigationViewStyle(.stack)
            }
        }
        .onAppear {
            locationManager.startBackgroundTracking()
            if let s = sessionManager.activeSession {
                locationManager.activeDeliveryId = s.deliveryId
                let mCoord = CLLocationCoordinate2D(latitude: s.merchantLat, longitude: s.merchantLng)
                let cCoord = CLLocationCoordinate2D(latitude: s.customerLat, longitude: s.customerLng)
                geofence.startMonitoring(merchantCoord: mCoord, customerCoord: cCoord, initialStage: s.status)
                streamer.startStreaming(deliveryId: s.deliveryId)
            }
            
            geofence.onMerchantArrival = {
                if sessionManager.activeSession?.status == "ASSIGNED" {
                    handleStageAction()
                }
            }
            geofence.onCustomerArrival = {
                if sessionManager.activeSession?.status == "PICKED_UP" {
                    handleStageAction()
                }
            }
        }
        .onChange(of: locationManager.lastLocation) { newLoc in
            if let loc = newLoc {
                geofence.updateLocation(loc)
                streamer.processLocationUpdate(loc)
            }
        }
        .onDisappear {
            isSimulatingRoute = false
            simulationTask?.cancel()
            simulationTask = nil
            geofence.stopMonitoring()
            streamer.stopStreaming()
        }
    }
    
    private var isEnRouteToStore: Bool {
        sessionManager.activeSession?.status == "ASSIGNED" || sessionManager.activeSession?.status == "ARRIVED_MERCHANT"
    }
    
    private var currentWaypointCoordinate: CLLocationCoordinate2D? {
        guard let s = sessionManager.activeSession else { return nil }
        if isEnRouteToStore {
            return CLLocationCoordinate2D(latitude: s.merchantLat, longitude: s.merchantLng)
        } else {
            return CLLocationCoordinate2D(latitude: s.customerLat, longitude: s.customerLng)
        }
    }
    
    private var currentTurnInstruction: String {
        isEnRouteToStore ? "Head to \(sessionManager.activeSession?.merchantName ?? "Merchant")" : "Deliver to \(sessionManager.activeSession?.customerName ?? "Customer")"
    }
    
    private var stageTitle: String {
        switch sessionManager.activeSession?.status {
        case "ASSIGNED": return "STEP 1/4: Heading to Dark Store"
        case "ARRIVED_MERCHANT": return "STEP 2/4: At Dark Store (Collect Package)"
        case "PICKED_UP": return "STEP 3/4: Out for Delivery to Customer"
        case "ARRIVED_CUSTOMER": return "STEP 4/4: At Customer Door (Enter OTP)"
        default: return "Delivery Active"
        }
    }
    
    private var actionButtonTitle: String {
        switch sessionManager.activeSession?.status {
        case "ASSIGNED": return "ARRIVED AT DARK STORE"
        case "ARRIVED_MERCHANT": return "CONFIRM PACKAGE PICKUP"
        case "PICKED_UP": return "ARRIVED AT CUSTOMER DOOR"
        case "ARRIVED_CUSTOMER": return "COMPLETE DELIVERY (ENTER OTP)"
        default: return "PROCEED"
        }
    }
    
    private var actionButtonColor: Color {
        sessionManager.activeSession?.status == "ARRIVED_CUSTOMER" ? RiderTheme.Colors.safetyYellow : RiderTheme.Colors.safetyGreen
    }
    
    private func handleStageAction() {
        guard let session = sessionManager.activeSession else { return }
        
        switch session.status {
        case "ASSIGNED":
            Task {
                let _: [String: String]? = try? await container.apiClient.post(
                    endpoint: .arriveMerchant(deliveryId: session.deliveryId),
                    body: ["status": "ARRIVED_MERCHANT"]
                )
                await updateSessionStatus("ARRIVED_MERCHANT")
            }
        case "ARRIVED_MERCHANT":
            Task {
                let _: [String: String]? = try? await container.apiClient.post(
                    endpoint: .confirmPickup(deliveryId: session.deliveryId),
                    body: ["status": "PICKED_UP"]
                )
                await updateSessionStatus("PICKED_UP")
            }
        case "PICKED_UP":
            Task {
                let _: [String: String]? = try? await container.apiClient.post(
                    endpoint: .arriveCustomer(deliveryId: session.deliveryId),
                    body: ["status": "ARRIVED_CUSTOMER"]
                )
                await updateSessionStatus("ARRIVED_CUSTOMER")
            }
        case "ARRIVED_CUSTOMER":
            showingOtpDialog = true
        default:
            break
        }
    }
    
    private func updateSessionStatus(_ newStatus: String) async {
        guard let s = sessionManager.activeSession else { return }
        let updated = ActiveDeliverySessionDto(
            deliveryId: s.deliveryId,
            orderId: s.orderId,
            status: newStatus,
            merchantName: s.merchantName,
            merchantAddress: s.merchantAddress,
            merchantLat: s.merchantLat,
            merchantLng: s.merchantLng,
            customerName: s.customerName,
            customerPhone: s.customerPhone,
            customerAddress: s.customerAddress,
            customerLat: s.customerLat,
            customerLng: s.customerLng,
            items: s.items,
            isCod: s.isCod,
            codAmountToCollect: s.codAmountToCollect,
            routePolyline: s.routePolyline
        )
        await MainActor.run {
            sessionManager.activeSession = updated
        }
    }
    
    private func verifyDeliveryOtp(_ otp: String, cashCollected: Double? = nil) async -> Bool {
        guard let session = sessionManager.activeSession else { return false }
        do {
            let req = DeliverWithOtpRequest(otp: otp, cashCollected: cashCollected ?? session.codAmountToCollect)
            let res: DeliverWithOtpResponse = try await container.apiClient.post(
                endpoint: .deliverWithOtp(deliveryId: session.deliveryId),
                body: req
            )
            
            if res.success {
                await MainActor.run {
                    completedOrderId = session.orderId
                    completedCustomerName = session.customerName
                    completedIsCod = session.isCod
                    completedCodAmount = cashCollected ?? session.codAmountToCollect
                    showingOtpDialog = false
                    showCelebrationModal = true
                }
                return true
            }
            return false
        } catch {
            return false
        }
    }
    
    private func toggleRouteSimulation() {
        if isSimulatingRoute {
            isSimulatingRoute = false
            simulationTask?.cancel()
            simulationTask = nil
        } else {
            guard let session = sessionManager.activeSession else { return }
            isSimulatingRoute = true
            simulationTask?.cancel()
            simulationTask = Task { @MainActor in
                let isStore = isEnRouteToStore
                let startLat = locationManager.lastLocation?.coordinate.latitude ?? (isStore ? (session.merchantLat + 0.005) : session.merchantLat)
                let startLng = locationManager.lastLocation?.coordinate.longitude ?? (isStore ? (session.merchantLng + 0.005) : session.merchantLng)
                let targetLat = isStore ? session.merchantLat : session.customerLat
                let targetLng = isStore ? session.merchantLng : session.customerLng

                var step = 0
                let totalSteps = 30
                while !Task.isCancelled && isSimulatingRoute && step <= totalSteps {
                    let progress = Double(step) / Double(totalSteps)
                    let currentLat = startLat + (targetLat - startLat) * progress
                    let currentLng = startLng + (targetLng - startLng) * progress

                    let dLat = targetLat - currentLat
                    let dLng = targetLng - currentLng
                    var bearing = atan2(dLng, dLat) * 180.0 / .pi
                    if bearing < 0 { bearing += 360.0 }

                    let simLoc = CLLocation(
                        coordinate: CLLocationCoordinate2D(latitude: currentLat, longitude: currentLng),
                        altitude: 10.0,
                        horizontalAccuracy: 10.0,
                        verticalAccuracy: 5.0,
                        course: bearing,
                        speed: 6.8,
                        timestamp: Date()
                    )

                    locationManager.lastLocation = simLoc
                    locationManager.currentSpeed = 24.5
                    locationManager.currentBearing = bearing

                    geofence.updateLocation(simLoc)
                    streamer.processLocationUpdate(simLoc)

                    step += 1
                    try? await Task.sleep(nanoseconds: 1_200_000_000)
                }
                isSimulatingRoute = false
            }
        }
    }
}

public struct DeliveryOtpEntryDialog: View {
    @Environment(\.presentationMode) private var presentationMode
    let orderId: String
    let onVerify: (String) async -> Bool
    
    @State private var otp: String = ""
    @State private var isVerifying: Bool = false
    @State private var errorMessage: String? = nil
    
    public var body: some View {
        NavigationView {
            VStack(spacing: 24) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 56))
                    .foregroundColor(RiderTheme.Colors.safetyYellow)
                    .padding(.top, 30)
                
                VStack(spacing: 6) {
                    Text("Customer Delivery Verification")
                        .font(.system(size: 20, weight: .bold))
                    Text("Ask customer for the 4-digit PIN displayed on their live tracking screen.")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal)
                }
                
                // 4-Digit Input Field
                TextField("Enter 4-Digit PIN", text: $otp)
                    .font(.system(size: 32, weight: .black, design: .monospaced))
                    .multilineTextAlignment(.center)
                    .keyboardType(.numberPad)
                    .padding()
                    .background(Color(.systemGray6))
                    .cornerRadius(12)
                    .padding(.horizontal, 48)
                
                if let err = errorMessage {
                    Text(err)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(RiderTheme.Colors.urgentRed)
                }
                
                Spacer()
                
                Button(action: submitOtp) {
                    if isVerifying {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Text("VERIFY & COMPLETE DELIVERY")
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(.white)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(otp.count >= 4 ? RiderTheme.Colors.safetyGreen : Color.gray)
                .cornerRadius(10)
                .padding(.horizontal)
                .disabled(otp.count < 4 || isVerifying)
                .padding(.bottom, 20)
            }
            .navigationBarTitle("Verify OTP", displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { presentationMode.wrappedValue.dismiss() }
                }
            }
        }
    }
    
    private func submitOtp() {
        isVerifying = true
        errorMessage = nil
        Task {
            let success = await onVerify(otp)
            if !success {
                await MainActor.run {
                    errorMessage = "Invalid delivery PIN. Please ask customer to re-check."
                    isVerifying = false
                }
            }
        }
    }
}
