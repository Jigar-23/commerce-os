import Foundation
import Combine
import Security

public final class RiderKeychainHelper {
    public static let shared = RiderKeychainHelper()
    private let serviceName = "com.commerceos.rider.tokens"

    public func save(key: String, data: String) {
        guard let dataBlob = data.data(using: .utf8) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
        let attributes: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecValueData as String: dataBlob,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        SecItemAdd(attributes as CFDictionary, nil)
    }

    public func get(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var dataTypeRef: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)
        if status == errSecSuccess, let data = dataTypeRef as? Data {
            return String(data: data, encoding: .utf8)
        }
        return nil
    }

    public func delete(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: serviceName,
            kSecAttrAccount as String: key,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

public enum RiderAPIError: LocalizedError {
    case invalidURL
    case unauthenticated
    case forbidden(String)
    case serverError(Int, String)
    case decodingError(Error)
    case networkError(Error)
    
    public var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid rider gateway URL."
        case .unauthenticated: return "Rider session expired. Please log in again."
        case .forbidden(let msg): return "Rider access denied: \(msg)"
        case .serverError(let code, let msg): return "Rider server error (\(code)): \(msg)"
        case .decodingError(let err): return "Payload parsing error: \(err.localizedDescription)"
        case .networkError(let err): return "Network connection error: \(err.localizedDescription)"
        }
    }
}

public final class RiderAPIClient: ObservableObject {
    public static let shared = RiderAPIClient()
    
    @Published public var authToken: String? = nil
    @Published public var currentRiderId: String? = nil
    
    private let session: URLSession

    // Production Authoritative Seed Credentials for Jigar's Device
    public static let defaultRiderId = "rdr_9817916180"
    public static let defaultRiderPhone = "+919817916180"
    public static let defaultRiderToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJyZHJfOTgxNzkxNjE4MCIsInJvbGUiOiJST0xFX1JJREVSIiwicm9sZXMiOlsiUk9MRV9SSURFUiJdLCJpc3MiOiJodHRwczovL2F1dGguY29tbWVyY2Vvcy5pbyIsImF1ZCI6Imh0dHBzOi8vYXBpLmNvbW1lcmNlb3MuaW8iLCJpYXQiOjE3ODk0MTg0MjksImV4cCI6MTc4OTUwNDgyOSwicGhvbmUiOiIrOTE5ODE3OTE2MTgwIn0.seUG1eHS00q6-mwPp5k62HlflQTP22bn0U3WCutMhqY"

    public static func extractSubFromJwt(_ token: String) -> String? {
        let parts = token.components(separatedBy: ".")
        guard parts.count >= 2 else { return nil }
        var base64 = parts[1]
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        while base64.count % 4 != 0 {
            base64.append("=")
        }
        guard let data = Data(base64Encoded: base64),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let sub = (json["sub"] as? String) ?? (json["subject"] as? String) else {
            return nil
        }
        return sub
    }
    
    public init(session: URLSession = .shared) {
        self.session = session
        // Dual-layer Keychain + UserDefaults Loading
        var token = RiderKeychainHelper.shared.get(key: "rider_auth_token") ?? UserDefaults.standard.string(forKey: "rider_auth_token")
        var riderId = RiderKeychainHelper.shared.get(key: "rider_id") ?? UserDefaults.standard.string(forKey: "rider_id")
        
        // Auto-seed Jigar's authoritative rider partner session on first launch
        if token == nil || token?.isEmpty == true {
            token = RiderAPIClient.defaultRiderToken
            riderId = RiderAPIClient.defaultRiderId
            RiderKeychainHelper.shared.save(key: "rider_auth_token", data: token!)
            RiderKeychainHelper.shared.save(key: "rider_id", data: riderId!)
            UserDefaults.standard.set(token, forKey: "rider_auth_token")
            UserDefaults.standard.set(riderId, forKey: "rider_id")
            UserDefaults.standard.set(RiderAPIClient.defaultRiderPhone, forKey: "rider_phone")
            UserDefaults.standard.set("Jigar (Partner)", forKey: "rider_name")
            if UserDefaults.standard.object(forKey: "rider_shift_online") == nil {
                UserDefaults.standard.set(true, forKey: "rider_shift_online")
            }
        }

        if let token = token, !token.isEmpty {
            if riderId == nil || riderId?.isEmpty == true {
                riderId = RiderAPIClient.extractSubFromJwt(token)
            }
            self.authToken = token
            self.currentRiderId = riderId
        }
    }
    
    public var isAuthenticated: Bool {
        return authToken != nil && !authToken!.isEmpty
    }
    
    public func setAuth(token: String, riderId: String, phone: String? = nil, name: String? = nil) {
        self.authToken = token
        self.currentRiderId = riderId
        RiderKeychainHelper.shared.save(key: "rider_auth_token", data: token)
        RiderKeychainHelper.shared.save(key: "rider_id", data: riderId)
        UserDefaults.standard.set(token, forKey: "rider_auth_token")
        UserDefaults.standard.set(riderId, forKey: "rider_id")
        if let p = phone { UserDefaults.standard.set(p, forKey: "rider_phone") }
        if let n = name { UserDefaults.standard.set(n, forKey: "rider_name") }
    }
    
    public func clearAuth() {
        self.authToken = nil
        self.currentRiderId = nil
        RiderKeychainHelper.shared.delete(key: "rider_auth_token")
        RiderKeychainHelper.shared.delete(key: "rider_id")
        UserDefaults.standard.removeObject(forKey: "rider_auth_token")
        UserDefaults.standard.removeObject(forKey: "rider_id")
    }

    public func sendRiderOtp(phone: String) async throws -> String {
        let cleanDigits = phone.filter { $0.isNumber }
        let phone10 = String(cleanDigits.suffix(10))
        let formatted = "+91\(phone10)"

        let payload: [String: Any] = [
            "phone": formatted,
            "mobile": formatted,
            "role": "ROLE_RIDER"
        ]
        let data = try JSONSerialization.data(withJSONObject: payload)

        struct OtpSendRes: Codable {
            let ok: Bool?
            let challengeId: String?
            let challenge_id: String?
            let message: String?
        }
        let res: OtpSendRes = try await request(endpoint: .sendOtp(phone: formatted), body: data)
        guard let chId = res.challengeId ?? res.challenge_id, !chId.isEmpty else {
            throw RiderAPIError.serverError(500, res.message ?? "Could not send OTP")
        }
        return chId
    }

    public func verifyRiderOtp(challengeId: String, phone: String, code: String, name: String? = nil, vehicle: String? = nil) async throws -> (token: String, riderId: String) {
        let cleanDigits = phone.filter { $0.isNumber }
        let phone10 = String(cleanDigits.suffix(10))
        let formatted = "+91\(phone10)"

        let payload: [String: Any] = [
            "challengeId": challengeId,
            "challenge_id": challengeId,
            "phone": formatted,
            "otp": code,
            "otpCode": code,
            "otp_code": code,
            "name": name ?? "Partner \(phone10.suffix(4))",
            "vehicle": vehicle ?? "HR-26-AB-1234",
            "role": "ROLE_RIDER"
        ]
        let data = try JSONSerialization.data(withJSONObject: payload)

        struct VerifyRes: Codable {
            let ok: Bool?
            let accessToken: String?
            let token: String?
            let riderId: String?
            let rider_id: String?
            let userId: String?
            let message: String?
        }
        let res: VerifyRes = try await request(
            endpoint: .verifyOtp(challengeId: challengeId, phone: formatted, otp: code, name: name ?? "", vehicle: vehicle ?? ""),
            body: data
        )

        guard let token = res.accessToken ?? res.token, !token.isEmpty else {
            throw RiderAPIError.serverError(400, res.message ?? "OTP verification failed")
        }
        let riderId = res.riderId ?? res.rider_id ?? res.userId ?? RiderAPIClient.extractSubFromJwt(token) ?? "rdr_\(phone10)"

        await MainActor.run {
            self.setAuth(token: token, riderId: riderId, phone: formatted, name: name)
        }
        return (token: token, riderId: riderId)
    }
    
    public func request<T: Decodable>(
        endpoint: RiderEndpoint,
        body: Data? = nil
    ) async throws -> T {
        let baseURL = RiderEnvironment.baseURL
        let url = baseURL.appendingPathComponent(endpoint.path)
        
        var req = URLRequest(url: url)
        req.httpMethod = endpoint.method
        req.timeoutInterval = 45.0
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue(RiderEnvironment.platform, forHTTPHeaderField: "X-Client-Platform")
        req.setValue(RiderEnvironment.clientVersion, forHTTPHeaderField: "X-Client-Version")
        
        if let token = authToken, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        req.httpBody = body
        
        let (data, response) = try await session.data(for: req)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw RiderAPIError.networkError(NSError(domain: "RiderAPIClient", code: -1, userInfo: nil))
        }
        
        if httpResponse.statusCode == 401 {
            await MainActor.run { self.clearAuth() }
            throw RiderAPIError.unauthenticated
        }
        
        if httpResponse.statusCode >= 400 {
            let errorMsg = String(data: data, encoding: .utf8) ?? "HTTP \(httpResponse.statusCode)"
            throw RiderAPIError.serverError(httpResponse.statusCode, errorMsg)
        }
        
        do {
            let decoder = JSONDecoder()
            if let decoded = try? decoder.decode(T.self, from: data) {
                return decoded
            }
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            return try decoder.decode(T.self, from: data)
        } catch {
            throw RiderAPIError.decodingError(error)
        }
    }
    
    public func post<Req: Encodable, Res: Decodable>(
        endpoint: RiderEndpoint,
        body: Req
    ) async throws -> Res {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        let data = try encoder.encode(body)
        return try await request(endpoint: endpoint, body: data)
    }
}
