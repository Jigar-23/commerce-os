import Foundation
import CoreLocation
import CryptoKit
import DeviceCheck

public struct BufferedTelemetryRecord: Codable {
    public let latitude: Double
    public let longitude: Double
    public let speedKmh: Double
    public let bearing: Double
    public let accuracyMeters: Double
    public let batteryPct: Int
    public let deliveryId: String
    public var sequenceNumber: Int
    public let timestamp: Date
}

private struct TelemetryAckResponse: Codable {
    let ok: Bool?
    let accepted: Bool?
    let ackSequenceNumber: Int?
    let highestSequence: Int?
    let error: String?
}

public final class RiderTelemetryBuffer {
    public static let shared = RiderTelemetryBuffer()

    private let outboxKey = "rider_telemetry_durable_outbox"
    private let queue = DispatchQueue(label: "com.commerceos.rider.telemetrybuffer", qos: .utility)
    private var flushTimer: Timer?
    private let apiClient: RiderAPIClient
    private var sequenceCounter: Int = 0
    private var isFlushing: Bool = false

    public init(apiClient: RiderAPIClient = .shared) {
        self.apiClient = apiClient
        setupFlushTimer()
    }

    private func setupFlushTimer() {
        DispatchQueue.main.async {
            let timer = Timer(timeInterval: RiderEnvironment.telemetryFlushIntervalSeconds, repeats: true) { [weak self] _ in
                self?.flush()
            }
            RunLoop.main.add(timer, forMode: .common)
            self.flushTimer = timer
        }
    }

    private func loadOutbox() -> [BufferedTelemetryRecord] {
        guard let data = UserDefaults.standard.data(forKey: outboxKey),
              let list = try? JSONDecoder().decode([BufferedTelemetryRecord].self, from: data) else {
            return []
        }
        return list
    }

    private func saveOutbox(_ items: [BufferedTelemetryRecord]) {
        if let data = try? JSONEncoder().encode(items) {
            UserDefaults.standard.set(data, forKey: outboxKey)
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
            let hwmKey = "telemetry_hwm_\(deliveryId)"
            let storedSeq = UserDefaults.standard.integer(forKey: hwmKey)
            self.sequenceCounter = max(self.sequenceCounter, storedSeq) + 1
            UserDefaults.standard.set(self.sequenceCounter, forKey: hwmKey)

            let rawAcc = location.horizontalAccuracy
            let normalizedAcc = (rawAcc > 0 && rawAcc <= 65.0) ? rawAcc : 25.0
            let record = BufferedTelemetryRecord(
                latitude: location.coordinate.latitude,
                longitude: location.coordinate.longitude,
                speedKmh: speedKmh,
                bearing: bearing,
                accuracyMeters: normalizedAcc,
                batteryPct: batteryPct,
                deliveryId: deliveryId,
                sequenceNumber: self.sequenceCounter,
                timestamp: Date()
            )

            var outbox = self.loadOutbox()
            if outbox.count > 500 {
                outbox.removeFirst(outbox.count - 500)
            }
            outbox.append(record)
            self.saveOutbox(outbox)

            self.flushSynchronously()
        }
    }

    public func clearDelivery(deliveryId: String) {
        queue.async {
            UserDefaults.standard.removeObject(forKey: "telemetry_hwm_\(deliveryId)")
            var currentOutbox = self.loadOutbox()
            currentOutbox.removeAll { $0.deliveryId == deliveryId }
            self.saveOutbox(currentOutbox)
        }
    }

    public func flush() {
        queue.async {
            self.flushSynchronously()
        }
    }

    private func removeAcknowledgedRecord(deliveryId: String, sequenceNumber: Int, ackSequence: Int?) {
        queue.sync {
            var currentOutbox = self.loadOutbox()
            currentOutbox.removeAll { $0.deliveryId == deliveryId && $0.sequenceNumber == sequenceNumber }
            self.saveOutbox(currentOutbox)
            if let ackSeq = ackSequence {
                UserDefaults.standard.set(ackSeq, forKey: "telemetry_hwm_\(deliveryId)")
            }
        }
    }

    private func resequencePendingOutbox(deliveryId: String, highestSeq: Int) {
        queue.sync {
            var currentOutbox = self.loadOutbox()
            let nextSeq = max(self.sequenceCounter, highestSeq) + 1
            var cur = nextSeq
            for i in 0..<currentOutbox.count {
                if currentOutbox[i].deliveryId == deliveryId {
                    currentOutbox[i].sequenceNumber = cur
                    cur += 1
                }
            }
            self.sequenceCounter = max(self.sequenceCounter, cur)
            self.saveOutbox(currentOutbox)
            UserDefaults.standard.set(cur - 1, forKey: "telemetry_hwm_\(deliveryId)")
        }
    }

    private func flushSynchronously() {
        guard !isFlushing else { return }
        isFlushing = true

        Task {
            defer {
                self.queue.async {
                    self.isFlushing = false
                }
            }

            var lastSuccessful: BufferedTelemetryRecord? = nil

            while true {
                let nextRecord: BufferedTelemetryRecord? = self.queue.sync {
                    let current = self.loadOutbox()
                    return current.first
                }
                guard let record = nextRecord else { break }

                let ts = Int(record.timestamp.timeIntervalSince1970 * 1000)
                let nonce = "\(record.deliveryId):\(record.sequenceNumber):\(ts)"
                let riderId = self.apiClient.currentRiderId ?? "rdr_assigned"

                // Server candidate payload: `${riderId}:${deliveryId}:${lat}:${lng}:${sequenceNumber}:${timestamp}:${nonce}`
                let payloadMsg = "\(riderId):\(record.deliveryId):\(record.latitude):\(record.longitude):\(record.sequenceNumber):\(ts):\(nonce)"
                let hashData = SHA256.hash(data: Data(payloadMsg.utf8))
                let clientDataHashHex = hashData.compactMap { String(format: "%02x", $0) }.joined()

                var payload: [String: AnyEncodable] = [
                    "latitude": AnyEncodable(record.latitude),
                    "longitude": AnyEncodable(record.longitude),
                    "speedKmh": AnyEncodable(record.speedKmh),
                    "speed": AnyEncodable(record.speedKmh),
                    "heading": AnyEncodable(record.bearing),
                    "bearing": AnyEncodable(record.bearing),
                    "accuracyMeters": AnyEncodable(record.accuracyMeters),
                    "sequenceNumber": AnyEncodable(record.sequenceNumber),
                    "timestamp": AnyEncodable(ts),
                    "nonce": AnyEncodable(nonce)
                ]

                // Wire platform hardware attestation proof if available
                var attestationKeyId: String? = nil
                var assertionData: String? = nil

                if AppAttestManager.shared.isSupported {
                    if let (kId, assertionStr) = try? await AppAttestManager.shared.generateAssertion(clientDataHash: Data(hashData)) {
                        attestationKeyId = kId
                        assertionData = assertionStr
                    }
                }

                if let keyId = attestationKeyId, !keyId.isEmpty, let assertionData = assertionData, !assertionData.isEmpty {
                    payload["deviceAttestation"] = AnyEncodable([
                        "platform": AnyEncodable("ios_app_attest"),
                        "keyId": AnyEncodable(keyId),
                        "assertion": AnyEncodable(assertionData),
                        "clientDataHash": AnyEncodable(clientDataHashHex),
                        "nonce": AnyEncodable(nonce),
                        "timestamp": AnyEncodable(String(ts))
                    ])
                }

                do {
                    let encoder = JSONEncoder()
                    encoder.keyEncodingStrategy = .convertToSnakeCase
                    let requestData = try encoder.encode(payload)
                    let ack: TelemetryAckResponse = try await self.apiClient.request(
                        endpoint: .streamDeliveryTelemetry(deliveryId: record.deliveryId),
                        body: requestData
                    )

                    lastSuccessful = record
                    self.removeAcknowledgedRecord(
                        deliveryId: record.deliveryId,
                        sequenceNumber: record.sequenceNumber,
                        ackSequence: ack.ackSequenceNumber ?? ack.highestSequence
                    )
                } catch {
                    // Check for 409 SEQUENCE_CONFLICT recovery
                    if case RiderAPIError.serverError(let code, let msg) = error, code == 409 {
                        var highestSeq = 0
                        if let data = msg.data(using: .utf8),
                           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                           let hSeq = json["highestSequence"] as? Int {
                            highestSeq = hSeq
                        }
                        self.resequencePendingOutbox(deliveryId: record.deliveryId, highestSeq: highestSeq)
                        break
                    } else {
                        print("[RiderTelemetryBuffer] Telemetry post failed for delivery \(record.deliveryId): \(error.localizedDescription); retained in outbox for replay")
                        break
                    }
                }
            }

            // Also keep rider presence updated on server for seller live visibility
            if let latest = lastSuccessful {
                let presencePayload: [String: AnyEncodable] = [
                    "latitude": AnyEncodable(latest.latitude),
                    "longitude": AnyEncodable(latest.longitude),
                    "speedKmh": AnyEncodable(latest.speedKmh),
                    "heading": AnyEncodable(latest.bearing),
                    "accuracyMeters": AnyEncodable(latest.accuracyMeters),
                    "isOnline": AnyEncodable(true)
                ]
                do {
                    try await self.apiClient.postVoid(
                        endpoint: .updatePresence,
                        body: presencePayload
                    )
                } catch {
                    print("[RiderTelemetryBuffer] Presence post failed: \(error.localizedDescription)")
                }
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
