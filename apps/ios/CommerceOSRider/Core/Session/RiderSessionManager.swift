import Foundation
import Combine

@MainActor
public final class RiderSessionManager: ObservableObject {
    public static let shared = RiderSessionManager()

    @Published public var isShiftOnline: Bool = false
    @Published public var activeSession: ActiveDeliverySessionDto? = nil
    @Published public var profile: RiderProfileDto? = nil
    @Published public var completedOrders: [CompletedOrderItem] = []

    private let apiClient: RiderAPIClient
    private var heartbeatTimer: Timer?

    public init(apiClient: RiderAPIClient = .shared) {
        self.apiClient = apiClient
        self.isShiftOnline = UserDefaults.standard.bool(forKey: "rider_shift_online")
        loadCompletedOrders()
        let savedId = UserDefaults.standard.string(forKey: "rider_id")
        let savedPhone = UserDefaults.standard.string(forKey: "rider_phone")
        let savedName = UserDefaults.standard.string(forKey: "rider_name")
        if let id = savedId, !id.isEmpty {
            self.profile = RiderProfileDto(
                riderId: id,
                name: savedName ?? "Rider Partner",
                phone: savedPhone ?? "",
                vehicleType: "EV 2-Wheeler",
                todayEarnings: 0.0,
                todayCompletedOrders: 0,
                rating: 5.0
            )
        } else {
            self.profile = nil
        }

        if apiClient.isAuthenticated {
            self.isShiftOnline = true
            UserDefaults.standard.set(true, forKey: "rider_shift_online")
            RiderOfferEventPipeline.shared.startListening()
            Task {
                await self.ensureOnlineShift()
            }
        }
    }

    public func ensureOnlineShift() async {
        guard apiClient.isAuthenticated else { return }
        isShiftOnline = true
        UserDefaults.standard.set(true, forKey: "rider_shift_online")

        struct ShiftPayload: Encodable {
            let online: Bool
            let isOnline: Bool
            let shiftStatus: String
            let status: String
            let latitude: Double
            let longitude: Double
        }

        let body = ShiftPayload(
            online: true,
            isOnline: true,
            shiftStatus: "ONLINE_AVAILABLE",
            status: "ONLINE_AVAILABLE",
            latitude: 28.202224,
            longitude: 76.615418
        )

        try? await apiClient.postVoid(
            endpoint: .toggleShift(online: true),
            body: body
        )

        // Register cached APNs device token if available
        if let cachedToken = UserDefaults.standard.string(forKey: "rider_apns_device_token"), !cachedToken.isEmpty {
            struct TokenPayload: Encodable {
                let fcmToken: String
                let deviceToken: String
                let token: String
                let platform: String
            }
            let tokenBody = TokenPayload(fcmToken: cachedToken, deviceToken: cachedToken, token: cachedToken, platform: "IOS")
            try? await apiClient.postVoid(endpoint: .registerDeviceToken, body: tokenBody)
        }

        await refreshActiveSession()
        RiderOfferEventPipeline.shared.startListening()
        startHeartbeat()
        RiderBackgroundLocationManager.shared.startBackgroundTracking()
    }

    public func toggleShift(forcedOnline: Bool? = nil) async throws {
        let newStatus = forcedOnline ?? !isShiftOnline
        isShiftOnline = newStatus
        UserDefaults.standard.set(newStatus, forKey: "rider_shift_online")

        struct ShiftPayload: Encodable {
            let online: Bool
            let isOnline: Bool
            let shiftStatus: String
            let status: String
            let latitude: Double
            let longitude: Double
        }

        let curLoc = RiderBackgroundLocationManager.shared.lastLocation
        let body = ShiftPayload(
            online: newStatus,
            isOnline: newStatus,
            shiftStatus: newStatus ? "ONLINE_AVAILABLE" : "OFFLINE",
            status: newStatus ? "ONLINE_AVAILABLE" : "OFFLINE",
            latitude: curLoc?.coordinate.latitude ?? 28.202224,
            longitude: curLoc?.coordinate.longitude ?? 76.615418
        )
        try? await apiClient.postVoid(
            endpoint: .toggleShift(online: newStatus),
            body: body
        )

        if newStatus {
            RiderBackgroundLocationManager.shared.startBackgroundTracking()
            await refreshActiveSession()
            RiderOfferEventPipeline.shared.startListening()
            startHeartbeat()
        } else {
            activeSession = nil
            RiderOfferEventPipeline.shared.stopListening()
            stopHeartbeat()
            RiderBackgroundLocationManager.shared.stopBackgroundTracking()
        }
    }

    private func startHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 25.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                guard let self = self, self.isShiftOnline, self.apiClient.isAuthenticated else { return }
                struct HeartbeatPayload: Encodable {
                    let online: Bool
                    let isOnline: Bool
                    let shiftStatus: String
                    let status: String
                    let latitude: Double
                    let longitude: Double
                }
                let body = HeartbeatPayload(
                    online: true,
                    isOnline: true,
                    shiftStatus: "ONLINE_AVAILABLE",
                    status: "ONLINE_AVAILABLE",
                    latitude: 28.202224,
                    longitude: 76.615418
                )
                try? await self.apiClient.postVoid(
                    endpoint: .toggleShift(online: true),
                    body: body
                )
            }
        }
    }

    private func stopHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }

    public func refreshActiveSession() async {
        do {
            let session: ActiveDeliverySessionDto = try await apiClient.request(endpoint: .activeSession)
            self.activeSession = session
        } catch {
            self.activeSession = nil
        }

        do {
            let prof: RiderProfileDto = try await apiClient.request(endpoint: .getProfile)
            self.profile = prof
        } catch {
            // Keep existing profile
        }
    }

    public func addCompletedOrder(_ item: CompletedOrderItem) {
        if !completedOrders.contains(where: { $0.orderId == item.orderId }) {
            completedOrders.insert(item, at: 0)
            saveCompletedOrders()
        }
    }

    public func addCompletedOrder(
        deliveryId: String,
        orderId: String,
        customerName: String,
        dropoffAddress: String,
        earnings: Double? = nil,
        completedAt: Date = Date(),
        isCod: Bool = false,
        codAmount: Double? = nil
    ) {
        let payoutVal = (codAmount != nil && codAmount! > 0) ? codAmount! : (earnings ?? 60.0)
        let item = CompletedOrderItem(
            orderId: orderId,
            merchantName: activeSession?.merchantName ?? "Central Store Hub",
            customerName: customerName,
            customerAddress: dropoffAddress,
            itemsCount: activeSession?.items?.count ?? 1,
            distanceKm: 2.2,
            payout: payoutVal,
            deliveredAt: "Just now"
        )
        addCompletedOrder(item)
    }

    public func loadCompletedOrders() {
        if let data = UserDefaults.standard.data(forKey: "rider_completed_orders"),
           let decoded = try? JSONDecoder().decode([CompletedOrderItem].self, from: data) {
            self.completedOrders = decoded
        }
    }

    public func saveCompletedOrders() {
        if let encoded = try? JSONEncoder().encode(completedOrders) {
            UserDefaults.standard.set(encoded, forKey: "rider_completed_orders")
        }
    }

    public func fetchTripsHistory() async {
        do {
            struct TripDto: Codable {
                let id: String?
                let deliveryId: String?
                let orderId: String?
                let merchantName: String?
                let customerName: String?
                let customerAddress: String?
                let itemsCount: Int?
                let distanceKm: Double?
                let payout: Double?
                let payoutFormatted: String?
                let status: String?
                let state: String?
                let deliveredAt: String?
                let createdAt: String?
            }
            let trips: [TripDto] = try await apiClient.request(endpoint: .getTripsHistory)
            var merged: [CompletedOrderItem] = []
            for t in trips {
                guard let oid = t.orderId ?? t.deliveryId, !oid.isEmpty else { continue }
                let payoutVal = t.payout ?? Double(t.payoutFormatted?.replacingOccurrences(of: "₹", with: "") ?? "60.0") ?? 60.0
                let item = CompletedOrderItem(
                    orderId: oid,
                    merchantName: t.merchantName ?? "Rewari Central Hub",
                    customerName: t.customerName ?? "Customer",
                    customerAddress: t.customerAddress ?? "Doorstep Delivery",
                    itemsCount: t.itemsCount ?? 1,
                    distanceKm: t.distanceKm ?? 2.4,
                    payout: payoutVal,
                    deliveredAt: t.deliveredAt ?? "Today"
                )
                merged.append(item)
            }
            for existing in self.completedOrders {
                if !merged.contains(where: { $0.orderId == existing.orderId }) {
                    merged.append(existing)
                }
            }
            self.completedOrders = merged
            self.saveCompletedOrders()
        } catch {
            // Retain locally persisted completed orders
        }
    }

    deinit {
        heartbeatTimer?.invalidate()
    }
}
