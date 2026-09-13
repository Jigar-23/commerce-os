import Foundation
import LocalAuthentication
import Combine

public enum BiometricType: String {
    case none = "None"
    case touchID = "Touch ID"
    case faceID = "Face ID"
    case opticID = "Optic ID"
}

public enum BiometricAuthError: LocalizedError {
    case biometricsNotAvailable(String)
    case authenticationFailed(String)
    case userCancelled
    case passcodeNotSet
    case unknown
    
    public var errorDescription: String? {
        switch self {
        case .biometricsNotAvailable(let msg):
            return "Biometrics not available: \(msg)"
        case .authenticationFailed(let msg):
            return "Authentication failed: \(msg)"
        case .userCancelled:
            return "User cancelled biometric challenge."
        case .passcodeNotSet:
            return "Device passcode is not set."
        case .unknown:
            return "Unknown biometric authentication error."
        }
    }
}

@MainActor
public final class BiometricPasskeyService: ObservableObject {
    public static let shared = BiometricPasskeyService()
    
    @Published public private(set) var biometricType: BiometricType = .none
    @Published public private(set) var isBiometricsAvailable: Bool = false
    @Published public private(set) var isUnlocked: Bool = false
    @Published public private(set) var lastError: String? = nil
    
    private init() {
        evaluateBiometricCapabilities()
    }
    
    /// Evaluates hardware sensor support (FaceID vs TouchID vs OpticID)
    public func evaluateBiometricCapabilities() {
        let context = LAContext()
        var error: NSError?
        
        let canEvaluate = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
        isBiometricsAvailable = canEvaluate
        
        if canEvaluate {
            switch context.biometryType {
            case .faceID:
                self.biometricType = .faceID
            case .touchID:
                self.biometricType = .touchID
            case .opticID:
                self.biometricType = .opticID
            case .none:
                self.biometricType = .none
            @unknown default:
                self.biometricType = .none
            }
        } else {
            self.biometricType = .none
            self.lastError = error?.localizedDescription
        }
    }
    
    /// Requests native biometric authentication via Apple LocalAuthentication
    public func authenticate(reason: String = "Authenticate to access encrypted prescription vault and payment tokens") async -> Result<Bool, BiometricAuthError> {
        let context = LAContext()
        context.localizedCancelTitle = "Cancel"
        context.localizedFallbackTitle = "Use Device Passcode"
        
        var authError: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &authError) else {
            // Check if fallback to passcode is possible
            if context.canEvaluatePolicy(.deviceOwnerAuthentication, error: nil) {
                return await evaluatePolicy(context: context, policy: .deviceOwnerAuthentication, reason: reason)
            }
            let desc = authError?.localizedDescription ?? "Hardware unsupported"
            self.lastError = desc
            return .failure(.biometricsNotAvailable(desc))
        }
        
        return await evaluatePolicy(context: context, policy: .deviceOwnerAuthenticationWithBiometrics, reason: reason)
    }
    
    private func evaluatePolicy(context: LAContext, policy: LAPolicy, reason: String) async -> Result<Bool, BiometricAuthError> {
        do {
            let success = try await context.evaluatePolicy(policy, localizedReason: reason)
            if success {
                self.isUnlocked = true
                self.lastError = nil
                return .success(true)
            } else {
                self.isUnlocked = false
                return .failure(.authenticationFailed("Authentication denied"))
            }
        } catch let laError as LAError {
            self.isUnlocked = false
            switch laError.code {
            case .userCancel:
                return .failure(.userCancelled)
            case .passcodeNotSet:
                return .failure(.passcodeNotSet)
            case .biometryNotAvailable:
                return .failure(.biometricsNotAvailable(laError.localizedDescription))
            default:
                self.lastError = laError.localizedDescription
                return .failure(.authenticationFailed(laError.localizedDescription))
            }
        } catch {
            self.isUnlocked = false
            self.lastError = error.localizedDescription
            return .failure(.unknown)
        }
    }
    
    /// Relocks the secure enclave session
    public func lockSession() {
        self.isUnlocked = false
    }
}
