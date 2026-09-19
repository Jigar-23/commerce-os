import Foundation
import AVFoundation
import Combine

public enum SpeechPriority {
    case low
    case normal
    case urgent
}

/// Hands-free audio navigation and turn-by-turn voice assistant for delivery riders
public final class TurnByTurnVoiceNotifier: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    public static let shared = TurnByTurnVoiceNotifier()
    
    @Published public var isVoiceMuted: Bool = false
    @Published public private(set) var isSpeaking: Bool = false
    @Published public private(set) var lastUtteranceText: String? = nil
    
    private let synthesizer = AVSpeechSynthesizer()
    private let audioSession = AVAudioSession.sharedInstance()
    private var voiceRate: Float = AVSpeechUtteranceDefaultSpeechRate
    private var voicePitch: Float = 1.05
    
    public override init() {
        super.init()
        synthesizer.delegate = self
        configureAudioSession()
    }
    
    /// Configures audio session for background navigation speech with audio ducking
    private func configureAudioSession() {
        do {
            try audioSession.setCategory(
                .playback,
                mode: .voicePrompt,
                options: [.duckOthers, .interruptSpokenAudioAndMixWithOthers]
            )
        } catch {
            print("[TurnByTurnVoiceNotifier Error] AudioSession configuration failed: \(error.localizedDescription)")
        }
    }
    
    /// Speaks any text with specified priority and automatic audio ducking
    public func speak(text: String, priority: SpeechPriority = .normal) {
        guard !isVoiceMuted else { return }
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        
        if priority == .urgent && synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        
        do {
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            print("[TurnByTurnVoiceNotifier Error] Activating audio session failed: \(error.localizedDescription)")
        }
        
        let utterance = AVSpeechUtterance(string: text)
        utterance.voice = AVSpeechSynthesisVoice(language: "en-US") ?? AVSpeechSynthesisVoice(language: Locale.current.languageCode ?? "en")
        utterance.rate = voiceRate
        utterance.pitchMultiplier = voicePitch
        utterance.volume = 1.0
        
        self.lastUtteranceText = text
        synthesizer.speak(utterance)
    }
    
    // MARK: - Navigation Announcements
    
    public func announceOfferReceived(orderId: String, distanceKm: Double, payout: Double) {
        let text = "New delivery offer. Distance: \(String(format: "%.1f", distanceKm)) kilometers. Guaranteed pay: \(Int(payout)) rupees. Tap to accept."
        speak(text: text, priority: .urgent)
    }
    
    public func announceManeuver(instruction: String, distanceMeters: Int) {
        let text = "In \(distanceMeters) meters, \(instruction)"
        speak(text: text, priority: .normal)
    }
    
    public func announceApproachingPickup(merchantName: String) {
        let text = "Approaching merchant: \(merchantName). Park safely and proceed to dispatch counter."
        speak(text: text, priority: .urgent)
    }
    
    public func announceOrderPickedUp(orderId: String) {
        let text = "Items verified and picked up. Route to customer updated. Ride safely."
        speak(text: text, priority: .normal)
    }
    
    public func announceApproachingCustomer(customerAddress: String) {
        let text = "Arriving at delivery address: \(customerAddress)."
        speak(text: text, priority: .urgent)
    }
    
    public func announceArrivalPromptOtp() {
        let text = "You have arrived at the doorstep. Please ask the customer for the 4-digit verification code."
        speak(text: text, priority: .urgent)
    }
    
    public func announceDeliveryCompleted(earnings: Double = 0) {
        let text = "Delivery successfully confirmed. Ready for next ride."
        speak(text: text, priority: .normal)
    }
    
    public func stopSpeaking() {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
    }
    
    public func toggleMute() {
        self.isVoiceMuted.toggle()
        if isVoiceMuted {
            stopSpeaking()
        }
    }
    
    // MARK: - AVSpeechSynthesizerDelegate
    
    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didStart utterance: AVSpeechUtterance) {
        DispatchQueue.main.async {
            self.isSpeaking = true
        }
    }
    
    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async {
            self.isSpeaking = false
            // Yield audio session back to normal music volume
            try? self.audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        }
    }
    
    public func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        DispatchQueue.main.async {
            self.isSpeaking = false
            try? self.audioSession.setActive(false, options: .notifyOthersOnDeactivation)
        }
    }
}
