import Foundation
import LocalAuthentication

public final class KeychainPasskeyManager {
    public static let shared = KeychainPasskeyManager()
    
    private init() {}

    public func authenticateWithFaceID(completion: @escaping (Bool, String?) -> Void) {
        let context = LAContext()
        var error: NSError?

        if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
            let reason = "Authenticate to access Commerce OS Health Account"
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, authenticationError in
                DispatchQueue.main.async {
                    if success {
                        completion(true, nil)
                    } else {
                        completion(false, authenticationError?.localizedDescription ?? "Face ID Failed")
                    }
                }
            }
        } else {
            completion(false, "Biometrics Not Supported")
        }
    }
}
