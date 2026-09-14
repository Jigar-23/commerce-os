import XCTest
import CoreLocation
import Combine
@testable import CommerceOSRider

final class TelemetryStreamerTests: XCTestCase {
    private var streamer: RiderTelemetryStreamer!
    
    override func setUp() {
        super.setUp()
        streamer = RiderTelemetryStreamer()
        streamer.stopStreaming()
    }
    
    override func tearDown() {
        streamer.stopStreaming()
        streamer = nil
        super.tearDown()
    }
    
    func testInitialStreamerState() {
        XCTAssertFalse(streamer.isStreaming)
        XCTAssertEqual(streamer.streamCount, 0)
        XCTAssertNil(streamer.lastStreamedCoordinate)
        XCTAssertEqual(streamer.lastSpeedKmh, 0.0)
    }
    
    func testStartAndStopStreaming() {
        streamer.startStreaming(deliveryId: "DEL-TEST-881")
        XCTAssertTrue(streamer.isStreaming)
        XCTAssertEqual(streamer.streamCount, 0)
        
        streamer.stopStreaming()
        XCTAssertFalse(streamer.isStreaming)
    }
    
    func testStreamsValidHighSpeedFixes() {
        streamer.startStreaming(deliveryId: "DEL-TEST-992")
        
        let movingLoc = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 28.4715, longitude: 77.0395),
            altitude: 210.0,
            horizontalAccuracy: 8.0,
            verticalAccuracy: 5.0,
            course: 90.0,
            speed: 10.0, // 36 km/h
            timestamp: Date()
        )
        
        streamer.processLocationUpdate(movingLoc)
        
        XCTAssertEqual(streamer.streamCount, 1)
        XCTAssertEqual(streamer.lastStreamedCoordinate?.latitude, 28.4715)
        XCTAssertEqual(streamer.lastStreamedCoordinate?.longitude, 77.0395)
        XCTAssertEqual(streamer.lastSpeedKmh, 36.0, accuracy: 0.1)
    }
    
    func testDiscardsInaccurateGPSFixes() {
        streamer.startStreaming(deliveryId: "DEL-TEST-ACCURACY")
        
        // Inaccurate fix (> 35m accuracy threshold)
        let poorAccuracyLoc = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 28.4715, longitude: 77.0395),
            altitude: 210.0,
            horizontalAccuracy: 65.0,
            verticalAccuracy: 5.0,
            course: 0.0,
            speed: 0.0,
            timestamp: Date()
        )
        
        streamer.processLocationUpdate(poorAccuracyLoc)
        XCTAssertEqual(streamer.streamCount, 0)
        XCTAssertNil(streamer.lastStreamedCoordinate)
    }
    
    func testSuppressesStationaryJitter() {
        streamer.startStreaming(deliveryId: "DEL-TEST-JITTER")
        
        let initialLoc = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 28.4710, longitude: 77.0390),
            altitude: 210.0,
            horizontalAccuracy: 5.0,
            verticalAccuracy: 5.0,
            course: 0.0,
            speed: 0.2, // 0.7 km/h (Stationary)
            timestamp: Date()
        )
        
        streamer.processLocationUpdate(initialLoc)
        XCTAssertEqual(streamer.streamCount, 1)
        
        // Jitter fix 1 meter away immediately after
        let jitterLoc = CLLocation(
            coordinate: CLLocationCoordinate2D(latitude: 28.471005, longitude: 77.039005),
            altitude: 210.0,
            horizontalAccuracy: 5.0,
            verticalAccuracy: 5.0,
            course: 0.0,
            speed: 0.1,
            timestamp: Date()
        )
        
        streamer.processLocationUpdate(jitterLoc)
        // Count should still be 1 because jitter within stationary delta is suppressed
        XCTAssertEqual(streamer.streamCount, 1)
    }
}
