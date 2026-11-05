import Foundation
import CoreLocation

public struct RouteResult: Equatable {
    public let waypoints: [CLLocationCoordinate2D]
    public let distanceText: String
    public let distanceMeters: Int
    public let durationText: String
    public let durationSeconds: Int
    public let encodedPolyline: String

    public init(
        waypoints: [CLLocationCoordinate2D],
        distanceText: String,
        distanceMeters: Int,
        durationText: String,
        durationSeconds: Int,
        encodedPolyline: String = ""
    ) {
        self.waypoints = waypoints
        self.distanceText = distanceText
        self.distanceMeters = distanceMeters
        self.durationText = durationText
        self.durationSeconds = durationSeconds
        self.encodedPolyline = encodedPolyline
    }

    public static func == (lhs: RouteResult, rhs: RouteResult) -> Bool {
        return lhs.distanceMeters == rhs.distanceMeters &&
               lhs.durationSeconds == rhs.durationSeconds &&
               lhs.waypoints.count == rhs.waypoints.count
    }
}

/**
 * 1:1 Parity with Android GoogleRoutesProvider.kt.
 * Fetches turn-by-turn road polyline and transit metrics exclusively from authoritative backend routing gateway.
 */
public final class GoogleRoutesProvider {
    public static let shared = GoogleRoutesProvider()

    private var routeCache: [String: RouteResult] = [:]
    private let lock = NSLock()

    public init() {}

    public func getDrivingRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ) async -> RouteResult? {
        let cacheKey = String(format: "%.4f,%.4f->%.4f,%.4f", originLat, originLng, destLat, destLng)

        lock.lock()
        if let cached = routeCache[cacheKey] {
            lock.unlock()
            return cached
        }
        lock.unlock()

        // Authoritative Backend Routing Gateway
        let backendUrlStr = "\(APIClient.shared.baseURLString)/api/v1/delivery/route?originLat=\(originLat)&originLng=\(originLng)&destLat=\(destLat)&destLng=\(destLng)"
        guard let backendUrl = URL(string: backendUrlStr) else { return nil }

        do {
            var request = URLRequest(url: backendUrl)
            request.timeoutInterval = 3.5
            let (data, response) = try await URLSession.shared.data(for: request)
            if let http = response as? HTTPURLResponse, http.statusCode == 200,
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let ok = json["ok"] as? Bool, ok,
               let wpArr = json["waypoints"] as? [[String: Any]] {
                let pts = wpArr.compactMap { dict -> CLLocationCoordinate2D? in
                    guard let lat = dict["lat"] as? Double, let lng = dict["lng"] as? Double else { return nil }
                    return CLLocationCoordinate2D(latitude: lat, longitude: lng)
                }

                if pts.count >= 2 {
                    let distKm = json["distanceKm"] as? Double ?? 1.2
                    let durMins = json["durationMins"] as? Int ?? 5
                    let result = RouteResult(
                        waypoints: pts,
                        distanceText: "\(distKm) km",
                        distanceMeters: Int(distKm * 1000),
                        durationText: "\(durMins) mins",
                        durationSeconds: durMins * 60
                    )
                    lock.lock()
                    routeCache[cacheKey] = result
                    lock.unlock()
                    return result
                }
            }
        } catch {}

        return nil
    }
}
