import Foundation
import Combine

@MainActor
public final class RiderSessionManager: ObservableObject {
    public static let shared = RiderSessionManager()

    @Published public var isShiftOnline: Bool = false
    @Published public var activeSession: ActiveDeliverySessionDto? = nil
    @Published public var profile: RiderProfileDto? = nil

    private let apiClient: RiderAPIClient
    private var heartbeatTimer: Timer?

    public init(apiClient: RiderAPIClient = .shared) {
        self.apiClient = apiClient
        self.isShiftOnline = UserDefaults.standard.bool(forKey: "rider_shift_online")
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

        let _: [String: String]? = try? await apiClient.post(
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
            let _: [String: String]? = try? await apiClient.post(endpoint: .registerDeviceToken, body: tokenBody)
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
        let _: [String: String]? = try? await apiClient.post(
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
                let _: [String: String]? = try? await self.apiClient.post(
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

    deinit {
        heartbeatTimer?.invalidate()
    }
}
