import Foundation
import CoreLocation
import Combine

public struct LiveRiderTelemetryPayload: Codable {
    public let latitude: Double?
    public let longitude: Double?
    public let speedKmh: Double?
    public let heading: Double?
    public let sequenceNumber: Int?

    enum CodingKeys: String, CodingKey {
        case latitude
        case longitude
        case speedKmh = "speed_kmh"
        case speedKmhCamel = "speedKmh"
        case speed
        case heading
        case bearing
        case sequenceNumber = "sequence_number"
        case sequenceNumberCamel = "sequenceNumber"
    }

    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        self.latitude = try? c.decode(Double.self, forKey: .latitude)
        self.longitude = try? c.decode(Double.self, forKey: .longitude)
        self.speedKmh = (try? c.decode(Double.self, forKey: .speedKmh))
            ?? (try? c.decode(Double.self, forKey: .speedKmhCamel))
            ?? (try? c.decode(Double.self, forKey: .speed))
        self.heading = (try? c.decode(Double.self, forKey: .heading))
            ?? (try? c.decode(Double.self, forKey: .bearing))
        self.sequenceNumber = (try? c.decode(Int.self, forKey: .sequenceNumber))
            ?? (try? c.decode(Int.self, forKey: .sequenceNumberCamel))
    }

    public func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(latitude, forKey: .latitude)
        try c.encodeIfPresent(longitude, forKey: .longitude)
        try c.encodeIfPresent(speedKmh, forKey: .speedKmh)
        try c.encodeIfPresent(heading, forKey: .heading)
        try c.encodeIfPresent(sequenceNumber, forKey: .sequenceNumber)
    }
}

public struct LiveTrackingPayload: Codable {
    public let orderId: String
    public let status: String
    public let etaMinutes: Int?
    public let riderName: String?
    public let riderPhone: String?
    public let riderLat: Double?
    public let riderLng: Double?
    public let riderBearing: Double?
    public let speedKmh: Double?
    public let merchantLat: Double?
    public let merchantLng: Double?
    public let customerLat: Double?
    public let customerLng: Double?
    public let deliveryOtp: String?
    public let isCod: Bool?
    public let totalAmount: Double?
    public let isLiveTelemetryAvailable: Bool?
    public let routePolyline: String?

    enum CodingKeys: String, CodingKey {
        case orderId = "order_id"
        case orderIdCamel = "orderId"
        case status
        case state
        case etaMinutes = "eta_minutes"
        case etaMinutesCamel = "etaMinutes"
        case estimatedArrivalMins
        case riderName = "rider_name"
        case riderNameCamel = "riderName"
        case riderPhone = "rider_phone"
        case riderPhoneCamel = "riderPhone"
        case riderLat = "rider_lat"
        case riderLatCamel = "riderLat"
        case riderLng = "rider_lng"
        case riderLngCamel = "riderLng"
        case riderBearing = "rider_bearing"
        case riderBearingCamel = "riderBearing"
        case riderHeading = "rider_heading"
        case riderHeadingCamel = "riderHeading"
        case speedKmh = "speed_kmh"
        case speedKmhCamel = "speedKmh"
        case speed
        case merchantLat = "merchant_lat"
        case merchantLatCamel = "merchantLat"
        case merchantLng = "merchant_lng"
        case merchantLngCamel = "merchantLng"
        case customerLat = "customer_lat"
        case customerLatCamel = "customerLat"
        case customerLng = "customer_lng"
        case customerLngCamel = "customerLng"
        case deliveryOtp = "delivery_otp"
        case deliveryOtpCamel = "deliveryOtp"
        case isCod = "is_cod"
        case isCodCamel = "isCod"
        case totalAmount = "total_amount"
        case totalAmountCamel = "totalAmount"
        case isLiveTelemetryAvailable = "is_live_telemetry_available"
        case isLiveTelemetryAvailableCamel = "isLiveTelemetryAvailable"
        case routePolyline = "route_polyline"
        case routePolylineCamel = "routePolyline"
        case liveRiderTelemetry = "live_rider_telemetry"
        case liveRiderTelemetryCamel = "liveRiderTelemetry"
        case active
    }

    public init(
        orderId: String,
        status: String = "PLACED",
        etaMinutes: Int? = nil,
        riderName: String? = nil,
        riderPhone: String? = nil,
        riderLat: Double? = nil,
        riderLng: Double? = nil,
        riderBearing: Double? = nil,
        speedKmh: Double? = nil,
        merchantLat: Double? = nil,
        merchantLng: Double? = nil,
        customerLat: Double? = nil,
        customerLng: Double? = nil,
        deliveryOtp: String? = nil,
        isCod: Bool? = nil,
        totalAmount: Double? = nil,
        isLiveTelemetryAvailable: Bool? = nil,
        routePolyline: String? = nil
    ) {
        self.orderId = orderId
        self.status = status
        self.etaMinutes = etaMinutes
        self.riderName = riderName
        self.riderPhone = riderPhone
        self.riderLat = riderLat
        self.riderLng = riderLng
        self.riderBearing = riderBearing
        self.speedKmh = speedKmh
        self.merchantLat = merchantLat
        self.merchantLng = merchantLng
        self.customerLat = customerLat
        self.customerLng = customerLng
        self.deliveryOtp = deliveryOtp
        self.isCod = isCod
        self.totalAmount = totalAmount
        self.isLiveTelemetryAvailable = isLiveTelemetryAvailable
        self.routePolyline = routePolyline
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let telem = (try? container.decode(LiveRiderTelemetryPayload.self, forKey: .liveRiderTelemetry))
            ?? (try? container.decode(LiveRiderTelemetryPayload.self, forKey: .liveRiderTelemetryCamel))

        self.orderId = (try? container.decode(String.self, forKey: .orderId))
            ?? (try? container.decode(String.self, forKey: .orderIdCamel))
            ?? ""
        self.status = (try? container.decode(String.self, forKey: .status))
            ?? (try? container.decode(String.self, forKey: .state))
            ?? "PLACED"
        self.etaMinutes = (try? container.decode(Int.self, forKey: .etaMinutes))
            ?? (try? container.decode(Int.self, forKey: .etaMinutesCamel))
            ?? (try? container.decode(Int.self, forKey: .estimatedArrivalMins))
        self.riderName = (try? container.decode(String.self, forKey: .riderName))
            ?? (try? container.decode(String.self, forKey: .riderNameCamel))
        self.riderPhone = (try? container.decode(String.self, forKey: .riderPhone))
            ?? (try? container.decode(String.self, forKey: .riderPhoneCamel))
        self.riderLat = (try? container.decode(Double.self, forKey: .riderLat))
            ?? (try? container.decode(Double.self, forKey: .riderLatCamel))
            ?? telem?.latitude
        self.riderLng = (try? container.decode(Double.self, forKey: .riderLng))
            ?? (try? container.decode(Double.self, forKey: .riderLngCamel))
            ?? telem?.longitude
        self.riderBearing = (try? container.decode(Double.self, forKey: .riderBearing))
            ?? (try? container.decode(Double.self, forKey: .riderBearingCamel))
            ?? (try? container.decode(Double.self, forKey: .riderHeading))
            ?? (try? container.decode(Double.self, forKey: .riderHeadingCamel))
            ?? telem?.heading
        self.speedKmh = (try? container.decode(Double.self, forKey: .speedKmh))
            ?? (try? container.decode(Double.self, forKey: .speedKmhCamel))
            ?? (try? container.decode(Double.self, forKey: .speed))
            ?? telem?.speedKmh
        self.merchantLat = (try? container.decode(Double.self, forKey: .merchantLat))
            ?? (try? container.decode(Double.self, forKey: .merchantLatCamel))
        self.merchantLng = (try? container.decode(Double.self, forKey: .merchantLng))
            ?? (try? container.decode(Double.self, forKey: .merchantLngCamel))
        self.customerLat = (try? container.decode(Double.self, forKey: .customerLat))
            ?? (try? container.decode(Double.self, forKey: .customerLatCamel))
        self.customerLng = (try? container.decode(Double.self, forKey: .customerLng))
            ?? (try? container.decode(Double.self, forKey: .customerLngCamel))
        self.deliveryOtp = (try? container.decode(String.self, forKey: .deliveryOtp))
            ?? (try? container.decode(String.self, forKey: .deliveryOtpCamel))
        self.isCod = (try? container.decode(Bool.self, forKey: .isCod))
            ?? (try? container.decode(Bool.self, forKey: .isCodCamel))
        self.totalAmount = (try? container.decode(Double.self, forKey: .totalAmount))
            ?? (try? container.decode(Double.self, forKey: .totalAmountCamel))
        self.isLiveTelemetryAvailable = (try? container.decode(Bool.self, forKey: .isLiveTelemetryAvailable))
            ?? (try? container.decode(Bool.self, forKey: .isLiveTelemetryAvailableCamel))
        self.routePolyline = (try? container.decode(String.self, forKey: .routePolyline))
            ?? (try? container.decode(String.self, forKey: .routePolylineCamel))
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(orderId, forKey: .orderId)
        try container.encode(status, forKey: .status)
        try container.encodeIfPresent(etaMinutes, forKey: .etaMinutes)
        try container.encodeIfPresent(riderName, forKey: .riderName)
        try container.encodeIfPresent(riderPhone, forKey: .riderPhone)
        try container.encodeIfPresent(riderLat, forKey: .riderLat)
        try container.encodeIfPresent(riderLng, forKey: .riderLng)
        try container.encodeIfPresent(riderBearing, forKey: .riderBearing)
        try container.encodeIfPresent(speedKmh, forKey: .speedKmh)
        try container.encodeIfPresent(merchantLat, forKey: .merchantLat)
        try container.encodeIfPresent(merchantLng, forKey: .merchantLng)
        try container.encodeIfPresent(customerLat, forKey: .customerLat)
        try container.encodeIfPresent(customerLng, forKey: .customerLng)
        try container.encodeIfPresent(deliveryOtp, forKey: .deliveryOtp)
        try container.encodeIfPresent(isCod, forKey: .isCod)
        try container.encodeIfPresent(totalAmount, forKey: .totalAmount)
        try container.encodeIfPresent(isLiveTelemetryAvailable, forKey: .isLiveTelemetryAvailable)
        try container.encodeIfPresent(routePolyline, forKey: .routePolyline)
    }
}

public class TrackingRepository: ObservableObject {
    public static let shared = TrackingRepository()
    private let apiClient: APIClient

    @Published public var activeTracking: LiveTrackingPayload? = nil
    @Published public var isLiveStreaming: Bool = false
    @Published public var streamError: String? = nil
    @Published public var isStreamReconnecting: Bool = false

    private var streamTask: Task<Void, Never>? = nil
    private var reconciliationTimer: Timer? = nil

    public init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    public func fetchActiveDelivery(orderId: String? = nil) async {
        do {
            let endpoint: String
            if let orderId = orderId, !orderId.isEmpty {
                endpoint = "/api/v1/delivery/order/\(orderId)"
            } else {
                endpoint = "/api/v1/orders/active-delivery"
            }
            let tracking: LiveTrackingPayload = try await apiClient.get(endpoint: endpoint)
            await MainActor.run {
                if tracking.orderId.isEmpty || tracking.status == "NO_ACTIVE_ORDER" {
                    self.activeTracking = nil
                } else {
                    self.activeTracking = tracking
                }
                self.streamError = nil
            }
        } catch {
            // Fallback to order detail if delivery session not yet spawned
            if let orderId = orderId, !orderId.isEmpty {
                do {
                    let order: ServerOrderResponse = try await apiClient.get(endpoint: "/api/v1/orders/\(orderId)")
                    await MainActor.run {
                        self.activeTracking = LiveTrackingPayload(
                            orderId: order.id,
                            status: order.status,
                            etaMinutes: 12,
                            riderName: "Express Fleet",
                            riderPhone: nil,
                            riderLat: nil,
                            riderLng: nil,
                            merchantLat: 28.202224,
                            merchantLng: 76.615418,
                            customerLat: 28.191828,
                            customerLng: 76.608148,
                            deliveryOtp: order.deliveryOtp,
                            isCod: order.paymentMethod == "COD",
                            totalAmount: order.totalAmount,
                            isLiveTelemetryAvailable: false,
                            routePolyline: nil
                        )
                        self.streamError = nil
                    }
                    return
                } catch {
                    print("[TrackingRepository] Fallback to order detail failed:", error.localizedDescription)
                }
            }
            await MainActor.run {
                if self.activeTracking == nil {
                    self.streamError = error.localizedDescription
                }
            }
        }
    }

    public func startLiveTracking(orderId: String? = nil) {
        stopLiveTracking()
        isLiveStreaming = true

        // 1. Initial snapshot fetch
        Task {
            await fetchActiveDelivery(orderId: orderId)
        }

        // 2. Primary Realtime SSE Stream Task with Exponential Backoff & Jitter
        streamTask = Task { [weak self] in
            guard let self = self else { return }
            var retryAttempt = 0
            while !Task.isCancelled {
                do {
                    guard let token = self.apiClient.authToken else {
                        try await Task.sleep(nanoseconds: 2_000_000_000)
                        continue
                    }
                    let streamPath: String
                    if let orderId = orderId, !orderId.isEmpty {
                        streamPath = "/api/v1/delivery/order/\(orderId)/stream"
                    } else {
                        streamPath = "/api/v1/orders/active-delivery/stream"
                    }
                    guard let url = URL(string: "\(self.apiClient.baseURLString)\(streamPath)") else {
                        break
                    }
                    var req = URLRequest(url: url)
                    req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                    req.setValue("text/event-stream", forHTTPHeaderField: "Accept")
                    req.timeoutInterval = 300

                    let (bytes, response) = try await URLSession.shared.bytes(for: req)
                    guard let httpRes = response as? HTTPURLResponse, httpRes.statusCode == 200 else {
                        let delay = self.calculateBackoffDelay(attempt: retryAttempt)
                        retryAttempt += 1
                        await MainActor.run {
                            self.isStreamReconnecting = true
                        }
                        try await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                        continue
                    }

                    // Reset retry attempt on successful 200 stream connection
                    await MainActor.run {
                        self.isStreamReconnecting = false
                    }
                    retryAttempt = 0

                    for try await line in bytes.lines {
                        if Task.isCancelled { break }
                        if line.hasPrefix("data:") {
                            let jsonString = String(line.dropFirst(5)).trimmingCharacters(in: .whitespacesAndNewlines)
                            if let data = jsonString.data(using: .utf8) {
                                let decoder = JSONDecoder()
                                decoder.keyDecodingStrategy = .convertFromSnakeCase
                                if let update = try? decoder.decode(LiveTrackingPayload.self, from: data) {
                                    await MainActor.run {
                                        self.activeTracking = update
                                        self.streamError = nil
                                        self.isStreamReconnecting = false
                                    }
                                }
                            }
                        }
                    }
                } catch {
                    if !Task.isCancelled {
                        let delay = self.calculateBackoffDelay(attempt: retryAttempt)
                        retryAttempt += 1
                        await MainActor.run {
                            self.isStreamReconnecting = true
                            self.streamError = "Reconnecting in \(String(format: "%.1f", delay))s..."
                        }
                        try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
                    }
                }
            }
        }

        // 3. Periodic reconciliation fallback loop (15 seconds)
        reconciliationTimer = Timer.scheduledTimer(withTimeInterval: 15.0, repeats: true) { [weak self] _ in
            Task {
                await self?.fetchActiveDelivery(orderId: orderId)
            }
        }
    }
    
    private func calculateBackoffDelay(attempt: Int) -> Double {
        let baseDelay = 1.0
        let maxDelay = 30.0
        let exponential = baseDelay * pow(2.0, Double(min(attempt, 5)))
        let jitter = Double.random(in: 0.1...0.9)
        return min(maxDelay, exponential + jitter)
    }

    public func stopLiveTracking() {
        reconciliationTimer?.invalidate()
        reconciliationTimer = nil
        streamTask?.cancel()
        streamTask = nil
        isLiveStreaming = false
        isStreamReconnecting = false
    }
}
