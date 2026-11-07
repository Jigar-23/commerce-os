import Foundation
import DeviceCheck
import CryptoKit

public final class AppAttestManager {
    public static let shared = AppAttestManager()

    private let keyIdKey = "rider_app_attest_key_id"
    private let service = DCAppAttestService.shared

    public var isSupported: Bool {
        return service.isSupported
    }

    public func getOrCreateKeyId() async throws -> String {
        if let existing = UserDefaults.standard.string(forKey: keyIdKey), !existing.isEmpty {
            return existing
        }

        return try await withCheckedThrowingContinuation { continuation in
            service.generateKey { [weak self] keyId, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let keyId = keyId, !keyId.isEmpty else {
                    continuation.resume(throwing: NSError(domain: "AppAttestManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "No keyId generated"]))
                    return
                }
                UserDefaults.standard.set(keyId, forKey: self?.keyIdKey ?? "rider_app_attest_key_id")
                continuation.resume(returning: keyId)
            }
        }
    }

    public func generateAssertion(clientDataHash: Data) async throws -> (keyId: String, assertionBase64: String) {
        let keyId = try await getOrCreateKeyId()
        let assertionData = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            service.generateAssertion(keyId, clientDataHash: clientDataHash) { assertion, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let assertion = assertion else {
                    continuation.resume(throwing: NSError(domain: "AppAttestManager", code: -2, userInfo: [NSLocalizedDescriptionKey: "No assertion generated"]))
                    return
                }
                continuation.resume(returning: assertion)
            }
        }
        return (keyId: keyId, assertionBase64: assertionData.base64EncodedString())
    }
}
