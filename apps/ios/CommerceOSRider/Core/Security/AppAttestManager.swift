import Foundation
import DeviceCheck
import CryptoKit

public struct AttestationChallengeResponse: Codable {
    public let ok: Bool?
    public let challenge: String
    public let riderId: String?
}

public struct AttestationRegisterPayload: Encodable {
    public let keyId: String
    public let attestation: String
    public let challenge: String
    public let platform: String

    public init(keyId: String, attestation: String, challenge: String, platform: String = "ios_app_attest") {
        self.keyId = keyId
        self.attestation = attestation
        self.challenge = challenge
        self.platform = platform
    }
}

public final class AppAttestManager {
    public static let shared = AppAttestManager()

    private let keyIdKey = "rider_app_attest_key_id"
    private let isRegisteredKey = "rider_app_attest_is_registered"
    private let service = DCAppAttestService.shared
    private let apiClient: RiderAPIClient

    public init(apiClient: RiderAPIClient = .shared) {
        self.apiClient = apiClient
    }

    public var isSupported: Bool {
        return service.isSupported
    }

    public func getOrRegisterKey() async throws -> String {
        let isRegistered = UserDefaults.standard.bool(forKey: isRegisteredKey)
        if let existing = UserDefaults.standard.string(forKey: keyIdKey), !existing.isEmpty, isRegistered {
            return existing
        }

        // 1. Generate hardware key identifier on secure enclave
        let keyId = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            service.generateKey { keyId, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let keyId = keyId, !keyId.isEmpty else {
                    continuation.resume(throwing: NSError(domain: "AppAttestManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "No keyId generated"]))
                    return
                }
                continuation.resume(returning: keyId)
            }
        }

        // 2. Request a one-time cryptographic enrollment challenge from backend
        let challengeRes: AttestationChallengeResponse = try await apiClient.request(
            endpoint: .getAttestationChallenge,
            body: nil
        )
        let challenge = challengeRes.challenge
        let challengeData = Data(challenge.utf8)
        let clientDataHash = SHA256.hash(data: challengeData)

        // 3. Obtain Apple platform attestation certification for the new key
        let attestationData = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            service.attestKey(keyId, clientDataHash: Data(clientDataHash)) { attestation, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let attestation = attestation else {
                    continuation.resume(throwing: NSError(domain: "AppAttestManager", code: -2, userInfo: [NSLocalizedDescriptionKey: "No attestation returned by Apple"]))
                    return
                }
                continuation.resume(returning: attestation)
            }
        }

        // 4. Enroll verified public key on backend with the single-use enrollment challenge
        let payload = AttestationRegisterPayload(
            keyId: keyId,
            attestation: attestationData.base64EncodedString(),
            challenge: challenge,
            platform: "ios_app_attest"
        )
        try await apiClient.postVoid(
            endpoint: .registerAttestationKey,
            body: payload
        )

        UserDefaults.standard.set(keyId, forKey: keyIdKey)
        UserDefaults.standard.set(true, forKey: isRegisteredKey)
        return keyId
    }

    public func generateAssertion(clientDataHash: Data) async throws -> (keyId: String, assertionBase64: String) {
        let keyId = try await getOrRegisterKey()
        let assertionData = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            service.generateAssertion(keyId, clientDataHash: clientDataHash) { assertion, error in
                if let error = error {
                    continuation.resume(throwing: error)
                    return
                }
                guard let assertion = assertion else {
                    continuation.resume(throwing: NSError(domain: "AppAttestManager", code: -3, userInfo: [NSLocalizedDescriptionKey: "No assertion generated"]))
                    return
                }
                continuation.resume(returning: assertion)
            }
        }
        return (keyId: keyId, assertionBase64: assertionData.base64EncodedString())
    }
}
