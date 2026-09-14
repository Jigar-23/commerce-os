import Foundation
import Combine

@MainActor
public final class RiderSessionManager: ObservableObject {
    public static let shared = RiderSessionManager()

    @Published public var isShiftOnline: Bool = false
    @Published public var activeSession: ActiveDeliverySessionDto? = nil
    @Published public var profile: RiderProfileDto? = nil

    private let apiClient: RiderAPIClient

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

        if self.isShiftOnline {
            RiderOfferEventPipeline.shared.startListening()
        }
    }

    public func toggleShift() async throws {
        let newStatus = !isShiftOnline
        isShiftOnline = newStatus
        UserDefaults.standard.set(newStatus, forKey: "rider_shift_online")

        let body = ["online": newStatus]
        let _: [String: String]? = try? await apiClient.post(
            endpoint: .toggleShift(online: newStatus),
            body: body
        )

        if newStatus {
            await refreshActiveSession()
            RiderOfferEventPipeline.shared.startListening()
        } else {
            activeSession = nil
            RiderOfferEventPipeline.shared.stopListening()
        }
    }

    public func refreshActiveSession() async {
        do {
            let session: ActiveDeliverySessionDto = try await apiClient.request(endpoint: .activeSession)
            self.activeSession = session
        } catch {
            // No active session or network error
        }

        do {
            let prof: RiderProfileDto = try await apiClient.request(endpoint: .getProfile)
            self.profile = prof
        } catch {
            // Keep existing fallback profile
        }
    }
}
