import Foundation
import Combine

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
    
    public init(session: URLSession = .shared) {
        self.session = session
        // Load persisted token from Keychain
        if let token = UserDefaults.standard.string(forKey: "rider_auth_token"),
           let riderId = UserDefaults.standard.string(forKey: "rider_id") {
            self.authToken = token
            self.currentRiderId = riderId
        }
    }
    
    public var isAuthenticated: Bool {
        return authToken != nil && !authToken!.isEmpty
    }
    
    public func setAuth(token: String, riderId: String) {
        self.authToken = token
        self.currentRiderId = riderId
        UserDefaults.standard.set(token, forKey: "rider_auth_token")
        UserDefaults.standard.set(riderId, forKey: "rider_id")
    }
    
    public func clearAuth() {
        self.authToken = nil
        self.currentRiderId = nil
        UserDefaults.standard.removeObject(forKey: "rider_auth_token")
        UserDefaults.standard.removeObject(forKey: "rider_id")
    }
    
    public func request<T: Decodable>(
        endpoint: RiderEndpoint,
        body: Data? = nil
    ) async throws -> T {
        let baseURL = RiderEnvironment.baseURL
        let url = baseURL.appendingPathComponent(endpoint.path)
        
        var req = URLRequest(url: url)
        req.httpMethod = endpoint.method
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
