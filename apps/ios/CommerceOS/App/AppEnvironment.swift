import Foundation

public enum AppEnvironment {
    public static var baseURL: URL {
        ServerEnvironmentConfig.shared.activeBaseURL
    }
    public static let clientVersion: String = "1.0.0"
    public static let platform: String = "iOS-Customer"
}
