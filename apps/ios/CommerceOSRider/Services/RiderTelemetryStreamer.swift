import Foundation
import CoreLocation
import UIKit
import Combine

public struct StreamedTelemetryPoint: Codable {
    public let latitude: Double
    public let longitude: Double
    public let speedKmh: Double
    public let bearing: Double
    public let accuracyMeters: Double
    public let batteryLevel: Float
    public let timestamp: Date
    public let isDeadReckoned: Bool
}

/// Battery-efficient continuous GPS streaming service with dead-reckoning and velocity adaptation
public final class RiderTelemetryStreamer: ObservableObject {
    public static let shared = RiderTelemetryStreamer()
    
    @Published public private(set) var isStreaming: Bool = false
    @Published public private(set) var streamCount: Int = 0
    @Published public private(set) var lastStreamedCoordinate: CLLocationCoordinate2D? = nil
    @Published public private(set) var lastSpeedKmh: Double = 0.0
    @Published public private(set) var isLowBatteryThrottled: Bool = false
    
    private var activeDeliveryId: String? = nil
    private var lastRecordedLocation: CLLocation? = nil
    private var lastTransmissionTime: Date = Date.distantPast
    
    private let minStationaryJitterDistanceMeters: CLLocationDistance = 3.0
    private let stationarySpeedThresholdMs: CLLocationSpeed = 1.0 // 3.6 km/h
    private let highSpeedThresholdMs: CLLocationSpeed = 7.0       // 25.2 km/h
    
    public init() {
        UIDevice.current.isBatteryMonitoringEnabled = true
    }
    
    /// Starts live GPS telemetry streaming for an active delivery assignment
    public func startStreaming(deliveryId: String) {
        self.activeDeliveryId = deliveryId
        self.isStreaming = true
        self.streamCount = 0
        self.lastRecordedLocation = nil
        self.lastTransmissionTime = Date.distantPast
    }
    
    /// Evaluates and streams incoming GPS fixes using adaptive thresholds & dead-reckoning
    public func processLocationUpdate(_ location: CLLocation) {
        guard isStreaming, let deliveryId = activeDeliveryId else { return }
        
        // 1. Accuracy Filter (discard fixes worse than 35m)
        guard location.horizontalAccuracy > 0 && location.horizontalAccuracy <= 35.0 else {
            // Apply dead-reckoning fallback if moving fast and have prior trajectory
            if let prior = lastRecordedLocation, prior.speed > stationarySpeedThresholdMs {
                let predicted = deadReckonLocation(from: prior, elapsedSeconds: Date().timeIntervalSince(prior.timestamp))
                transmitTelemetry(predicted, deliveryId: deliveryId, isDeadReckoned: true)
            }
            return
        }
        
        let now = Date()
        let timeSinceLastTransmission = now.timeIntervalSince(lastTransmissionTime)
        
        // 2. Battery & Velocity Adaptive Interval Calculation
        let battery = UIDevice.current.batteryLevel
        let isLowBattery = battery >= 0 && battery < 0.15 && UIDevice.current.batteryState != .charging
        self.isLowBatteryThrottled = isLowBattery
        
        let requiredIntervalSeconds: TimeInterval
        if isLowBattery {
            requiredIntervalSeconds = 10.0 // Conservative power saving
        } else if location.speed > highSpeedThresholdMs {
            requiredIntervalSeconds = 2.5  // High precision on highway / fast transit
        } else if location.speed > stationarySpeedThresholdMs {
            requiredIntervalSeconds = 4.5  // City street transit
        } else {
            requiredIntervalSeconds = 15.0 // Stationary at merchant or customer door
        }
        
        // 3. Stationary Jitter Suppression
        if let prior = lastRecordedLocation {
            let distance = location.distance(from: prior)
            if location.speed < stationarySpeedThresholdMs && distance < minStationaryJitterDistanceMeters {
                // If stationary, only transmit heartbeat every 15s
                if timeSinceLastTransmission < 15.0 {
                    return
                }
            }
        }
        
        // 4. Rate-Limit Transmission
        guard timeSinceLastTransmission >= requiredIntervalSeconds else { return }
        
        transmitTelemetry(location, deliveryId: deliveryId, isDeadReckoned: false)
    }
    
    private func transmitTelemetry(_ location: CLLocation, deliveryId: String, isDeadReckoned: Bool) {
        let speedKmh = max(0, location.speed * 3.6)
        let bearing = location.course >= 0 ? location.course : 0.0
        let battery = UIDevice.current.batteryLevel
        
        let point = StreamedTelemetryPoint(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            speedKmh: speedKmh,
            bearing: bearing,
            accuracyMeters: location.horizontalAccuracy,
            batteryLevel: battery,
            timestamp: Date(),
            isDeadReckoned: isDeadReckoned
        )
        
        // Push into telemetry buffer for resilient batch delivery
        RiderTelemetryBuffer.shared.record(
            location: location,
            deliveryId: deliveryId,
            speedKmh: speedKmh,
            bearing: bearing,
            batteryPct: Int(max(0, battery * 100))
        )
        
        DispatchQueue.main.async {
            self.streamCount += 1
            self.lastStreamedCoordinate = location.coordinate
            self.lastSpeedKmh = speedKmh
            self.lastTransmissionTime = Date()
            self.lastRecordedLocation = location
        }
    }
    
    /// Computes predicted coordinate using heading and speed vector during transient GPS dropouts
    private func deadReckonLocation(from origin: CLLocation, elapsedSeconds: TimeInterval) -> CLLocation {
        guard origin.course >= 0 && origin.speed > 0 else { return origin }
        
        let distanceMeters = origin.speed * elapsedSeconds
        let angularDistance = distanceMeters / 6371000.0 // Earth radius in meters
        let bearingRad = origin.course * .pi / 180.0
        
        let lat1 = origin.coordinate.latitude * .pi / 180.0
        let lon1 = origin.coordinate.longitude * .pi / 180.0
        
        let lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(bearingRad))
        let lon2 = lon1 + atan2(sin(bearingRad) * sin(angularDistance) * cos(lat1), cos(angularDistance) - sin(lat1) * sin(lat2))
        
        let newCoordinate = CLLocationCoordinate2D(
            latitude: lat2 * 180.0 / .pi,
            longitude: lon2 * 180.0 / .pi
        )
        
        return CLLocation(
            coordinate: newCoordinate,
            altitude: origin.altitude,
            horizontalAccuracy: 15.0,
            verticalAccuracy: origin.verticalAccuracy,
            course: origin.course,
            speed: origin.speed,
            timestamp: Date()
        )
    }
    
    public func stopStreaming() {
        self.isStreaming = false
        self.activeDeliveryId = nil
        self.lastRecordedLocation = nil
    }
}
