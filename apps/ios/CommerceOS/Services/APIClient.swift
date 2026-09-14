import Foundation
import Combine

public enum APIError: LocalizedError {
    case invalidURL
    case unauthenticated
    case forbidden(String)
    case serverError(Int, String)
    case decodingError(Error)
    case networkError(Error)

    public var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid server endpoint URL."
        case .unauthenticated: return "Authentication required. Please sign in."
        case .forbidden(let msg): return "Access forbidden: \(msg)"
        case .serverError(let code, let msg): return "Server error (\(code)): \(msg)"
        case .decodingError(let err): return "Failed to process server response: \(err.localizedDescription)"
        case .networkError(let err):
            if let urlErr = err as? URLError {
                switch urlErr.code {
                case .timedOut:
                    return "Network timeout: The server took too long to respond. Please try again."
                case .notConnectedToInternet:
                    return "No internet connection. Please check your network and try again."
                case .networkConnectionLost:
                    return "Network connection interrupted: The server closed the connection. Retrying may resolve this."
                case .cannotConnectToHost:
                    return "Unable to reach server. Please check your network connection."
                default:
                    return "Network connection failure: \(urlErr.localizedDescription)"
                }
            }
            return "Network connection failure: \(err.localizedDescription)"
        }
    }
}

public struct CustomerAuthResponse: Codable {
    public let userId: String
    public let phone: String
    public let name: String?
    public let roles: [String]
    public let accessToken: String
    public let refreshToken: String?

    public init(userId: String, phone: String, name: String? = nil, roles: [String] = ["ROLE_CUSTOMER"], accessToken: String, refreshToken: String? = nil) {
        self.userId = userId
        self.phone = phone
        self.name = name
        self.roles = roles
        self.accessToken = accessToken
        self.refreshToken = refreshToken
    }
}

public class APIClient: ObservableObject {
    public static let shared = APIClient()
    public static let sessionExpiredNotification = Notification.Name("CommerceOSUserSessionExpired")

    public var baseURLString: String {
        get { ServerEnvironmentConfig.shared.activeURLString }
        set { ServerEnvironmentConfig.shared.setCustomURL(newValue) }
    }
    @Published public var authToken: String? = nil
    @Published public var currentCustomerId: String? = nil

    private let session: URLSession

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

    public init(session: URLSession? = nil) {
        if let customSession = session {
            self.session = customSession
        } else {
            let config = URLSessionConfiguration.default
            config.timeoutIntervalForRequest = 45.0
            config.timeoutIntervalForResource = 90.0
            config.waitsForConnectivity = true
            config.requestCachePolicy = .reloadIgnoringLocalCacheData
            self.session = URLSession(configuration: config)
        }
        // Dual-layer Keychain + UserDefaults Loading
        let token = KeychainHelper.shared.get(key: "auth_token") ?? UserDefaults.standard.string(forKey: "auth_token")
        var customerId = KeychainHelper.shared.get(key: "customer_id") ?? UserDefaults.standard.string(forKey: "customer_id")
        if let token = token, !token.isEmpty {
            if customerId == nil || customerId?.isEmpty == true {
                customerId = APIClient.extractSubFromJwt(token)
            }
            self.authToken = token
            self.currentCustomerId = customerId
        }
    }

    public var isAuthenticated: Bool {
        return authToken != nil && !authToken!.isEmpty
    }

    public func setAuth(token: String, customerId: String) {
        self.authToken = token
        self.currentCustomerId = customerId
        KeychainHelper.shared.save(key: "auth_token", data: token)
        KeychainHelper.shared.save(key: "customer_id", data: customerId)
        UserDefaults.standard.set(token, forKey: "auth_token")
        UserDefaults.standard.set(customerId, forKey: "customer_id")
    }

    public func clearAuth() {
        self.authToken = nil
        self.currentCustomerId = nil
        KeychainHelper.shared.delete(key: "auth_token")
        KeychainHelper.shared.delete(key: "customer_id")
        UserDefaults.standard.removeObject(forKey: "auth_token")
        UserDefaults.standard.removeObject(forKey: "customer_id")
    }

    public struct OtpChallengeResponse: Codable {
        public let ok: Bool?
        public let challengeId: String?
        public let phone: String?
        public let message: String?
        public let expiresInSeconds: Int?
        public let debugOtp: String?
        public let masterOtp: String?
    }

    public struct OtpSendResult {
        public let challengeId: String
        public let debugOtp: String?
    }

    public struct CustomerDetailsResponse: Codable {
        public let id: String?
        public let phone: String?
        public let name: String?
        public let email: String?
    }

    public struct CustomerVerifyResponse: Codable {
        public let ok: Bool?
        public let accessToken: String
        public let userId: String?
        public let phone: String?
        public let customer: CustomerDetailsResponse?
    }

    public func sendOtp(phone: String) async throws -> OtpSendResult {
        let digitsOnly = phone.filter { $0.isNumber }
        let clean10 = String(digitsOnly.suffix(10))
        let formatted = "+91\(clean10)"

        let payload: [String: Any] = [
            "phone": formatted,
            "mobile": formatted,
            "phoneNumber": formatted,
            "phone_number": formatted
        ]
        let data = try JSONSerialization.data(withJSONObject: payload)
        let res: OtpChallengeResponse = try await request(endpoint: "/api/v1/auth/otp/send", method: "POST", body: data)
        guard let ch = res.challengeId, !ch.isEmpty else {
            throw APIError.serverError(500, res.message ?? "Failed to request OTP.")
        }
        return OtpSendResult(challengeId: ch, debugOtp: res.debugOtp ?? res.masterOtp)
    }

    public func verifyOtp(challengeId: String, phone: String, code: String, name: String? = nil) async throws -> CustomerAuthResponse {
        let digitsOnly = phone.filter { $0.isNumber }
        let clean10 = String(digitsOnly.suffix(10))
        let formatted = "+91\(clean10)"

        let payload: [String: Any] = [
            "challengeId": challengeId,
            "challenge_id": challengeId,
            "sessionId": challengeId,
            "phone": formatted,
            "otp": code,
            "otpCode": code,
            "otp_code": code,
            "fullName": name ?? "",
            "full_name": name ?? ""
        ]
        let data = try JSONSerialization.data(withJSONObject: payload)
        let res: CustomerVerifyResponse = try await request(endpoint: "/api/v1/auth/otp/verify", method: "POST", body: data)
        let userId = res.userId ?? res.customer?.id ?? "cust_\(clean10)"
        let customerName = res.customer?.name ?? name
        let authRes = CustomerAuthResponse(
            userId: userId,
            phone: res.phone ?? formatted,
            name: customerName,
            roles: ["ROLE_CUSTOMER"],
            accessToken: res.accessToken,
            refreshToken: nil
        )
        await MainActor.run {
            self.setAuth(token: authRes.accessToken, customerId: authRes.userId)
        }
        return authRes
    }

    public func loginWithPhone(phone: String, otp: String) async throws -> CustomerAuthResponse {
        struct LoginBody: Codable {
            let phone: String
            let otp: String
        }
        let response: CustomerAuthResponse = try await post(endpoint: "/api/v1/auth/customer/verify-otp", body: LoginBody(phone: phone, otp: otp))
        await MainActor.run {
            self.setAuth(token: response.accessToken, customerId: response.userId)
        }
        return response
    }

    public func request<T: Decodable>(
        endpoint: String,
        method: String = "GET",
        body: Data? = nil,
        headers: [String: String] = [:]
    ) async throws -> T {
        let cleanEndpoint = endpoint.hasPrefix("/") ? String(endpoint.dropFirst()) : endpoint
        guard let url = URL(string: "\(baseURLString)/\(cleanEndpoint)") else {
            throw APIError.invalidURL
        }

        var req = URLRequest(url: url)
        req.httpMethod = method
        req.timeoutInterval = 45.0
        req.cachePolicy = .reloadIgnoringLocalCacheData
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")

        if let token = authToken, !token.isEmpty, !endpoint.contains("/auth/") {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        headers.forEach { req.setValue($1, forHTTPHeaderField: $0) }
        req.httpBody = body

        var attempts = 0
        let maxAttempts = 2

        while true {
            attempts += 1
            do {
                let hasAuth = req.value(forHTTPHeaderField: "Authorization") != nil
                print("[APIClient] \(method) \(url.path) (auth: \(hasAuth), attempt: \(attempts))")
                let (data, response) = try await session.data(for: req)
                guard let httpRes = response as? HTTPURLResponse else {
                    throw APIError.serverError(0, "Invalid HTTP response")
                }

                print("[APIClient] \(method) \(url.path) -> HTTP \(httpRes.statusCode)")

                if httpRes.statusCode == 401 {
                    if let authHdr = req.value(forHTTPHeaderField: "Authorization"), !authHdr.isEmpty {
                        await MainActor.run {
                            self.clearAuth()
                            NotificationCenter.default.post(name: APIClient.sessionExpiredNotification, object: nil)
                        }
                    }
                    throw APIError.unauthenticated
                }

                if httpRes.statusCode >= 400 {
                    let errorObj = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
                    let message = errorObj?["message"] as? String ?? errorObj?["error"] as? String ?? "HTTP \(httpRes.statusCode)"
                    if httpRes.statusCode == 403 {
                        throw APIError.forbidden(message)
                    }
                    throw APIError.serverError(httpRes.statusCode, message)
                }

                let decoder = JSONDecoder()
                if let decoded = try? decoder.decode(T.self, from: data) {
                    return decoded
                }
                decoder.keyDecodingStrategy = .convertFromSnakeCase
                return try decoder.decode(T.self, from: data)
            } catch let err as APIError {
                print("[APIClient] APIError for \(method) \(url.path): \(err.localizedDescription)")
                throw err
            } catch let decErr as DecodingError {
                print("[APIClient] DecodingError for \(method) \(url.path): \(decErr)")
                throw APIError.decodingError(decErr)
            } catch let urlErr as URLError where attempts < maxAttempts && (urlErr.code == .networkConnectionLost || urlErr.code == .timedOut) {
                print("[APIClient] Network warning (\(urlErr.code.rawValue): \(urlErr.localizedDescription)). Auto-retrying request with fresh connection (attempt \(attempts + 1)/\(maxAttempts))...")
                try? await Task.sleep(nanoseconds: 500_000_000)
                continue
            } catch {
                print("[APIClient] Underlying network error for \(method) \(url.path): \(error) (\((error as NSError).domain) code: \((error as NSError).code))")
                throw APIError.networkError(error)
            }
        }
    }

    public func get<T: Decodable>(endpoint: String) async throws -> T {
        try await request(endpoint: endpoint, method: "GET")
    }

    public func get<T: Decodable>(endpoint: APIEndpoint) async throws -> T {
        try await get(endpoint: endpoint.path)
    }

    public func post<T: Decodable, B: Encodable>(endpoint: String, body: B) async throws -> T {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        let data = try encoder.encode(body)
        return try await request(endpoint: endpoint, method: "POST", body: data)
    }

    public func post<T: Decodable, B: Encodable>(endpoint: APIEndpoint, body: B) async throws -> T {
        try await post(endpoint: endpoint.path, body: body)
    }
}
