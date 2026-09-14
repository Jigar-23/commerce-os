import Foundation
import Combine

@MainActor
public final class RiderOfferEventPipeline: ObservableObject {
    public static let shared = RiderOfferEventPipeline()

    @Published public var activeOffer: DispatchOfferDto? = nil
    @Published public var offerTimeRemainingSeconds: Int = 30

    private var countdownTimer: Timer?

    public init() {}

    public func receiveOffer(offer: DispatchOfferDto) {
        self.activeOffer = offer
        self.offerTimeRemainingSeconds = 30
        self.startCountdown()
        RiderAlertNotifier.shared.playIncomingOfferAlert()
    }

    public func dismissActiveOffer(reason: String) {
        countdownTimer?.invalidate()
        countdownTimer = nil
        self.activeOffer = nil
        self.offerTimeRemainingSeconds = 30
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
    }
}
