import Foundation
import Combine

@MainActor
public final class RiderContainer: ObservableObject {
    public static let shared = RiderContainer()
    
    public let apiClient: RiderAPIClient
    public let sessionManager: RiderSessionManager
    public let locationManager: RiderBackgroundLocationManager
    public let pushManager: RiderPushNotificationManager
    public let offerPipeline: RiderOfferEventPipeline
    public let alertNotifier: RiderAlertNotifier
    public let telemetryBuffer: RiderTelemetryBuffer
    public let voiceNotifier: TurnByTurnVoiceNotifier
    public let geofenceDetector: RiderGeofenceDetector
    public let telemetryStreamer: RiderTelemetryStreamer
    
    public init(
        apiClient: RiderAPIClient = RiderAPIClient.shared,
        sessionManager: RiderSessionManager = RiderSessionManager.shared,
        locationManager: RiderBackgroundLocationManager = RiderBackgroundLocationManager.shared,
        pushManager: RiderPushNotificationManager = RiderPushNotificationManager.shared,
        offerPipeline: RiderOfferEventPipeline = RiderOfferEventPipeline.shared,
        alertNotifier: RiderAlertNotifier = RiderAlertNotifier.shared,
        telemetryBuffer: RiderTelemetryBuffer = RiderTelemetryBuffer.shared,
        voiceNotifier: TurnByTurnVoiceNotifier = TurnByTurnVoiceNotifier.shared,
        geofenceDetector: RiderGeofenceDetector = RiderGeofenceDetector.shared,
        telemetryStreamer: RiderTelemetryStreamer = RiderTelemetryStreamer.shared
    ) {
        self.apiClient = apiClient
        self.sessionManager = sessionManager
        self.locationManager = locationManager
        self.pushManager = pushManager
        self.offerPipeline = offerPipeline
        self.alertNotifier = alertNotifier
        self.telemetryBuffer = telemetryBuffer
        self.voiceNotifier = voiceNotifier
        self.geofenceDetector = geofenceDetector
        self.telemetryStreamer = telemetryStreamer
    }
}
