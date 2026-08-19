import Foundation
import CoreLocation
import Combine

public struct LiveTrackingPayload: Codable {
    public let orderId: String
    public let status: String
    public let etaMinutes: Int?
    public let riderName: String?
    public let riderPhone: String?
    public let riderLat: Double?
    public let riderLng: Double?
    public let merchantLat: Double?
    public let merchantLng: Double?
    public let customerLat: Double?
    public let customerLng: Double?
    public let deliveryOtp: String?
    public let isCod: Bool?
    public let totalAmount: Double?
    public let isLiveTelemetryAvailable: Bool?
}

public class TrackingRepository: ObservableObject {
    public static let shared = TrackingRepository()
    private let apiClient: APIClient

    @Published public var activeTracking: LiveTrackingPayload? = nil
    @Published public var isLiveStreaming: Bool = false
    @Published public var streamError: String? = nil

    private var streamTask: Task<Void, Never>? = nil
    private var reconciliationTimer: Timer? = nil

    public init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    public func fetchActiveDelivery() async {
        do {
            let tracking: LiveTrackingPayload = try await apiClient.get(endpoint: "/api/v1/orders/active-delivery")
            await MainActor.run {
                self.activeTracking = tracking
                self.streamError = nil
            }
        } catch {
            await MainActor.run {
                if self.activeTracking == nil {
                    self.streamError = error.localizedDescription
                }
            }
        }
    }

    public func startLiveTracking() {
        stopLiveTracking()
        isLiveStreaming = true

        // 1. Initial snapshot fetch
        Task {
            await fetchActiveDelivery()
        }

        // 2. Primary Realtime SSE Stream Task
        streamTask = Task { [weak self] in
            guard let self = self else { return }
            while !Task.isCancelled {
                do {
                    guard let token = self.apiClient.authToken else {
                        try await Task.sleep(nanoseconds: 2_000_000_000)
                        continue
                    }
                    guard let url = URL(string: "\(self.apiClient.baseURLString)/api/v1/orders/active-delivery/stream") else {
                        break
                    }
                    var req = URLRequest(url: url)
                    req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                    req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                    req.timeoutInterval = 300

                    let (bytes, response) = try await URLSession.shared.bytes(for: req)
                    guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
                        try await Task.sleep(nanoseconds: 3_000_000_000)
                        continue
                    }

                    for try await line in bytes.lines {
                        if Task.isCancelled { break }
                        if line.hasPrefix("data:") {
                            let jsonString = String(line.dropFirst(5)).trimmingCharacters(in: .whitespacesAndNewlines)
                            if let data = jsonString.data(using: .utf8) {
                                let decoder = JSONDecoder()
                                decoder.keyDecodingStrategy = .convertFromSnakeCase
                                if let update = try? decoder.decode(LiveTrackingPayload.self, from: data) {
                                    await MainActor.run {
                                        self.activeTracking = update
                                        self.streamError = nil
                                    }
                                }
                            }
                        }
                    }
                } catch {
                    if !Task.isCancelled {
                        try? await Task.sleep(nanoseconds: 3_000_000_000)
                    }
                }
            }
        }

        // 3. Periodic reconciliation fallback loop (15 seconds)
        reconciliationTimer = Timer.scheduledTimer(withTimeInterval: 15.0, repeats: true) { [weak self] _ in
            Task {
                await self?.fetchActiveDelivery()
            }
        }
    }

    public func stopLiveTracking() {
        reconciliationTimer?.invalidate()
        reconciliationTimer = nil
        streamTask?.cancel()
        streamTask = nil
        isLiveStreaming = false
    }
}
