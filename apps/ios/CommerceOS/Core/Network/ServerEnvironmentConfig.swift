import Foundation
import Combine

public enum ServerEnvironmentPreset: String, CaseIterable, Identifiable {
    case lanGateway = "Local LAN Gateway (192.168.1.52:3000)"
    case cloudRender = "Cloud Production (Render)"
    case localSimulator = "Localhost Simulator (3000)"
    case custom = "Custom Server Endpoint"

    public var id: String { rawValue }

    public var defaultURLString: String {
        switch self {
        case .lanGateway:
            return "http://192.168.1.52:3000"
        case .cloudRender:
            return "https://commerce-os-api.onrender.com"
        case .localSimulator:
            return "http://localhost:3000"
        case .custom:
            return ""
        }
    }
}

public final class ServerEnvironmentConfig: ObservableObject {
    public static let shared = ServerEnvironmentConfig()

    private let userDefaultsKey = "commerceos_active_server_url"
    private let presetKey = "commerceos_active_server_preset"

    @Published public var activePreset: ServerEnvironmentPreset {
        didSet {
            UserDefaults.standard.set(activePreset.rawValue, forKey: presetKey)
            if activePreset != .custom {
                activeURLString = activePreset.defaultURLString
            }
        }
    }

    @Published public var activeURLString: String {
        didSet {
            UserDefaults.standard.set(activeURLString, forKey: userDefaultsKey)
            checkHealth()
        }
    }

    @Published public var isServerConnected: Bool = false
    @Published public var isCheckingHealth: Bool = false
    @Published public var lastHealthStatus: String = "Unknown"
    @Published public var latencyMs: Int? = nil

    private var healthCheckCancellable: AnyCancellable?

    private init() {
        let initialPreset: ServerEnvironmentPreset = .cloudRender
        self.activePreset = initialPreset

        var urlToUse = ServerEnvironmentPreset.cloudRender.defaultURLString
        if let savedURL = UserDefaults.standard.string(forKey: userDefaultsKey), !savedURL.isEmpty {
            if savedURL.contains("localhost") || savedURL.contains("127.0.0.1") || savedURL.contains("192.168.") {
                // Heal any stale local/LAN URL to the live Render cloud server
                urlToUse = ServerEnvironmentPreset.cloudRender.defaultURLString
                UserDefaults.standard.set(urlToUse, forKey: userDefaultsKey)
                UserDefaults.standard.set(ServerEnvironmentPreset.cloudRender.rawValue, forKey: presetKey)
            } else {
                urlToUse = savedURL
            }
        } else {
            UserDefaults.standard.set(urlToUse, forKey: userDefaultsKey)
            UserDefaults.standard.set(ServerEnvironmentPreset.cloudRender.rawValue, forKey: presetKey)
        }
        self.activeURLString = urlToUse

        // Run initial connectivity probe
        checkHealth()
    }

    public var activeBaseURL: URL {
        let trimmed = activeURLString.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.contains("localhost") || trimmed.contains("127.0.0.1") || trimmed.contains("192.168.") {
            return URL(string: ServerEnvironmentPreset.cloudRender.defaultURLString)!
        }
        return URL(string: trimmed) ?? URL(string: ServerEnvironmentPreset.cloudRender.defaultURLString)!
    }

    public func setCustomURL(_ urlString: String) {
        let clean = urlString.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        self.activePreset = .custom
        self.activeURLString = clean
    }

    public func selectPreset(_ preset: ServerEnvironmentPreset) {
        self.activePreset = preset
        if preset != .custom {
            self.activeURLString = preset.defaultURLString
        }
    }

    public func checkHealth() {
        guard let healthURL = URL(string: "\(activeBaseURL.absoluteString)/health") else {
            self.isServerConnected = false
            self.lastHealthStatus = "Invalid URL"
            return
        }

        isCheckingHealth = true
        let start = CFAbsoluteTimeGetCurrent()

        var req = URLRequest(url: healthURL)
        req.timeoutInterval = 4.0
        req.cachePolicy = .reloadIgnoringLocalCacheData

        URLSession.shared.dataTask(with: req) { [weak self] data, response, error in
            let elapsedMs = Int((CFAbsoluteTimeGetCurrent() - start) * 1000)
            DispatchQueue.main.async {
                guard let self = self else { return }
                self.isCheckingHealth = false
                if let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 {
                    self.isServerConnected = true
                    self.latencyMs = elapsedMs
                    if let data = data, let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                       let service = json["service"] as? String {
                        self.lastHealthStatus = "\(service) (\(elapsedMs)ms)"
                    } else {
                        self.lastHealthStatus = "Online (\(elapsedMs)ms)"
                    }
                } else {
                    self.isServerConnected = false
                    self.latencyMs = nil
                    self.lastHealthStatus = error?.localizedDescription ?? "Offline"
                }
            }
        }.resume()
    }
}
