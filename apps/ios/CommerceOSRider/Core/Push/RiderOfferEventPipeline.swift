import Foundation
import Combine

@MainActor
public final class RiderOfferEventPipeline: ObservableObject {
    public static let shared = RiderOfferEventPipeline()

    @Published public var activeOffer: DispatchOfferDto? = nil
    @Published public var offerTimeRemainingSeconds: Int = 30
    @Published public var isListening: Bool = false

    private var countdownTimer: Timer?
    private var pollingTimer: Timer?
    private var sseStreamTask: Task<Void, Never>?
    private var lastAlertedOfferId: String = ""

    public init() {}

    public func startListening() {
        guard !isListening else { return }
        isListening = true

        // 1. Initial immediate check
        Task {
            await self.pollActiveOffersOnce()
        }

        // 2. Periodic reconciliation polling (every 4 seconds)
        pollingTimer?.invalidate()
        pollingTimer = Timer.scheduledTimer(withTimeInterval: 4.0, repeats: true) { [weak self] _ in
            Task { @MainActor in
                await self?.pollActiveOffersOnce()
            }
        }

        // 3. Persistent Realtime SSE Stream
        startSseStream()
    }

    public func stopListening() {
        isListening = false
        pollingTimer?.invalidate()
        pollingTimer = nil
        sseStreamTask?.cancel()
        sseStreamTask = nil
    }

    private func pollActiveOffersOnce() async {
        guard isListening else { return }
        guard RiderAPIClient.shared.isAuthenticated else { return }
        guard RiderSessionManager.shared.isShiftOnline else { return }

        do {
            let response: ActiveOffersResponseDto = try await RiderAPIClient.shared.request(endpoint: .getActiveOffers)
            if let offers = response.offers, let first = offers.first {
                if self.activeOffer?.offerId != first.offerId {
                    self.receiveOffer(offer: first)
                }
            } else if self.activeOffer != nil && (response.offers == nil || response.offers!.isEmpty) {
                self.dismissActiveOffer(reason: "RECONCILED_EMPTY")
            }
        } catch {
            // Silently ignore transient network glitches during polling
        }
    }

    private func startSseStream() {
        sseStreamTask?.cancel()
        sseStreamTask = Task { [weak self] in
            guard let self = self else { return }
            while !Task.isCancelled {
                guard RiderAPIClient.shared.isAuthenticated && RiderSessionManager.shared.isShiftOnline else {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    continue
                }

                let baseURL = RiderEnvironment.baseURL
                let url = baseURL.appendingPathComponent("api/v1/delivery/rider/stream")
                var req = URLRequest(url: url)
                req.httpMethod = "GET"
                req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                if let token = RiderAPIClient.shared.authToken, !token.isEmpty {
                    req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                }
                req.timeoutInterval = 300

                do {
                    let (bytes, response) = try await URLSession.shared.bytes(for: req)
                    guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
                        try? await Task.sleep(nanoseconds: 5_000_000_000)
                        continue
                    }

                    for try await line in bytes.lines {
                        if Task.isCancelled { break }
                        if line.hasPrefix("data:") {
                            let jsonString = String(line.dropFirst(5)).trimmingCharacters(in: .whitespacesAndNewlines)
                            if let data = jsonString.data(using: .utf8),
                               let jsonObject = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                                let payload: [String: Any] = (jsonObject["offer"] as? [String: Any])
                                    ?? (jsonObject["data"] as? [String: Any])
                                    ?? jsonObject
                                if let validated = OfferPayloadValidator.validate(payload: payload) {
                                    await MainActor.run {
                                        self.receiveOffer(offer: validated)
                                    }
                                }
                            }
                        }
                    }
                } catch {
                    // Reconnect backoff on stream disconnect
                    try? await Task.sleep(nanoseconds: 4_000_000_000)
                }
            }
        }
    }

    public func receiveOffer(offer: DispatchOfferDto) {
        self.activeOffer = offer
        self.offerTimeRemainingSeconds = 30
        self.startCountdown()

        if lastAlertedOfferId != offer.offerId {
            lastAlertedOfferId = offer.offerId
            RiderAlertNotifier.shared.playIncomingOfferAlert()
            RiderPushNotificationManager.shared.postOfferNotification(offer: offer)
        }
    }

    public func dismissActiveOffer(reason: String) {
        countdownTimer?.invalidate()
        countdownTimer = nil
        self.activeOffer = nil
        self.offerTimeRemainingSeconds = 30
        if reason == "EXPIRED" || reason == "ACCEPTED" || reason == "DECLINED" {
            lastAlertedOfferId = ""
        }
    }

    private func startCountdown() {
        countdownTimer?.invalidate()
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            Task { @MainActor in
                if self.offerTimeRemainingSeconds > 1 {
                    self.offerTimeRemainingSeconds -= 1
                } else {
                    self.dismissActiveOffer(reason: "EXPIRED")
                }
            }
        }
    }

    deinit {
        countdownTimer?.invalidate()
        pollingTimer?.invalidate()
        sseStreamTask?.cancel()
    }
}
