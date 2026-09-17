import Foundation
import CoreLocation

public struct BufferedTelemetryRecord: Codable {
    public let latitude: Double
    public let longitude: Double
    public let speedKmh: Double
    public let bearing: Double
    public let accuracyMeters: Double
    public let batteryPct: Int
    public let deliveryId: String
    public let sequenceNumber: Int
    public let timestamp: Date
}

public final class RiderTelemetryBuffer {
    public static let shared = RiderTelemetryBuffer()

    private var buffer: [BufferedTelemetryRecord] = []
    private let queue = DispatchQueue(label: "com.commerceos.rider.telemetrybuffer", qos: .utility)
    private var flushTimer: Timer?
    private let apiClient: RiderAPIClient
    private var sequenceCounter: Int = 0

    public init(apiClient: RiderAPIClient = .shared) {
        self.apiClient = apiClient
        setupFlushTimer()
    }

    private func setupFlushTimer() {
        DispatchQueue.main.async {
            self.flushTimer = Timer.scheduledTimer(withTimeInterval: RiderEnvironment.telemetryFlushIntervalSeconds, repeats: true) { [weak self] _ in
                self?.flush()
            }
        }
    }

    public func record(
        location: CLLocation,
        deliveryId: String,
        speedKmh: Double,
        bearing: Double,
        batteryPct: Int
    ) {
        queue.async {
            self.sequenceCounter += 1
            let record = BufferedTelemetryRecord(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude,
                speedKmh: speedKmh,
                bearing: bearing,
                accuracyMeters: location.horizontalAccuracy,
                batteryPct: batteryPct,
                deliveryId: deliveryId,
                sequenceNumber: self.sequenceCounter,
                timestamp: Date()
            )
            self.buffer.append(record)
            self.flushSynchronously()
        }
    }

    public func flush() {
        queue.async {
            self.flushSynchronously()
        }
    }

    private func flushSynchronously() {
        guard !buffer.isEmpty else { return }
        let batch = buffer
        buffer.removeAll()

        Task {
            // Stream each telemetry packet with monotonic sequenceNumber to POST /api/v1/delivery/:deliveryId/telemetry
            for record in batch {
                let payload: [String: AnyEncodable] = [
                    "latitude": AnyEncodable(record.latitude),
                    "longitude": AnyEncodable(record.longitude),
                    "speedKmh": AnyEncodable(record.speedKmh),
                    "speed": AnyEncodable(record.speedKmh),
                    "heading": AnyEncodable(record.bearing),
                    "bearing": AnyEncodable(record.bearing),
                    "accuracyMeters": AnyEncodable(record.accuracyMeters),
                    "sequenceNumber": AnyEncodable(record.sequenceNumber)
                ]
                let _: [String: String]? = try? await apiClient.post(
                    endpoint: .streamDeliveryTelemetry(deliveryId: record.deliveryId),
                    body: payload
                )
            }

            // Also keep rider presence updated on server for seller live visibility
            if let latest = batch.last {
                let presencePayload: [String: AnyEncodable] = [
                    "latitude": AnyEncodable(latest.latitude),
                    "longitude": AnyEncodable(latest.longitude),
                    "speedKmh": AnyEncodable(latest.speedKmh),
                    "heading": AnyEncodable(latest.bearing),
                    "accuracyMeters": AnyEncodable(latest.accuracyMeters),
                    "isOnline": AnyEncodable(true)
                ]
                let _: [String: String]? = try? await apiClient.post(
                    endpoint: .updatePresence,
                    body: presencePayload
                )
            }
        }
    }

    deinit {
        flushTimer?.invalidate()
    }
}

public struct AnyEncodable: Encodable {
    private let encodeFunc: (Encoder) throws -> Void

    public init<T: Encodable>(_ encodable: T) {
        self.encodeFunc = encodable.encode
    }

    public func encode(to encoder: Encoder) throws {
        try encodeFunc(encoder)
    }
}
