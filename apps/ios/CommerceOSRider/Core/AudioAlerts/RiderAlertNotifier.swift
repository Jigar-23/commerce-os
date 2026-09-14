import Foundation
import AudioToolbox
import AVFoundation

public final class RiderAlertNotifier: ObservableObject {
    public static let shared = RiderAlertNotifier()

    private var audioPlayer: AVAudioPlayer?

    public init() {}

    public func playIncomingOfferAlert() {
        RiderHapticEngine.shared.playIncomingOfferBurst()
        AudioServicesPlaySystemSound(1005) // System chime
        TurnByTurnVoiceNotifier.shared.speak(text: "New dispatch offer available. Tap to accept.", priority: .urgent)
    }

    public func playArrivalChime() {
        RiderHapticEngine.shared.playGeofenceArrivalRumble()
        AudioServicesPlaySystemSound(1025)
        TurnByTurnVoiceNotifier.shared.speak(text: "You have arrived within 50 meters of your destination.", priority: .normal)
    }

    public func playDeliverySuccessAlert() {
        RiderHapticEngine.shared.playDeliveryConfirmedSequence()
        AudioServicesPlaySystemSound(1001)
        TurnByTurnVoiceNotifier.shared.speak(text: "Delivery completed successfully.", priority: .normal)
    }
}
