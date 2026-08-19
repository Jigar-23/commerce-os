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
        case .networkError(let err): return "Network connection failure: \(err.localizedDescription)"
        }
    }
}

public struct CustomerAuthResponse: Codable {
    public let userId: String
    public let phone: String
    public let roles: [String]
    public let accessToken: String
    public let refreshToken: String?
}

public class APIClient: ObservableObject {
    public static let shared = APIClient()

    @Published public var baseURLString: String = "http://localhost:8080"
    @Published public var authToken: String? = nil
    @Published public var currentCustomerId: String? = nil

    private let session: URLSession

    public init(session: URLSession = .shared) {
        self.session = session
        // Secure Keychain Loading
        if let token = KeychainHelper.shared.get(key: "auth_token"),
           let customerId = KeychainHelper.shared.get(key: "customer_id") {
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
    }

    public func clearAuth() {
        self.authToken = nil
        self.currentCustomerId = nil
        KeychainHelper.shared.delete(key: "auth_token")
        KeychainHelper.shared.delete(key: "customer_id")
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
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("application/json", forHTTPHeaderField: "Accept")

        if let token = authToken, !token.isEmpty {
            req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        headers.forEach { req.setValue($1, forHTTPHeaderField: $0) }
        req.httpBody = body

        do {
            let (data, response) = try await session.data(for: req)
            guard let httpRes = response as? HTTPURLResponse else {
                throw APIError.serverError(0, "Invalid HTTP response")
            }

            if httpRes.statusCode == 401 {
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
            decoder.keyDecodingStrategy = .convertFromSnakeCase
            return try decoder.decode(T.self, from: data)
        } catch let err as APIError {
            throw err
        } catch let decErr as DecodingError {
            throw APIError.decodingError(decErr)
        } catch {
            throw APIError.networkError(error)
        }
    }

    public func get<T: Decodable>(endpoint: String) async throws -> T {
        try await request(endpoint: endpoint, method: "GET")
    }

    public func post<T: Decodable, B: Encodable>(endpoint: String, body: B) async throws -> T {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        let data = try encoder.encode(body)
        return try await request(endpoint: endpoint, method: "POST", body: data)
    }
}
