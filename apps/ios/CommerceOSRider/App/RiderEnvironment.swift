import Foundation

public enum RiderEnvironment {
    public static var baseURL: URL {
        if let customUrl = ProcessInfo.processInfo.environment["COMMERCEOS_RIDER_API_URL"],
           let url = URL(string: customUrl) {
            return url
        }
        if let saved = UserDefaults.standard.string(forKey: "commerceos_rider_server_url"),
           !saved.contains("localhost"), !saved.contains("127.0.0.1"), !saved.contains("192.168."),
           let url = URL(string: saved) {
            return url
        }
        return URL(string: "https://commerce-os-api.onrender.com")!
    }
    
    public static let clientVersion: String = "1.0.0"
    public static let platform: String = "iOS-Rider"
    public static let telemetryFlushIntervalSeconds: TimeInterval = 3.0
    public static let maxTelemetryBatchSize: Int = 20
}
