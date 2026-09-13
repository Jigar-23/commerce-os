import XCTest
import CoreLocation
import Combine

final class GeofenceDetectorTests: XCTestCase {
    private var detector: RiderGeofenceDetector!
    
    // Sample Coordinates in Gurugram / Bengaluru
    private let merchantCoord = CLLocationCoordinate2D(latitude: 28.4710, longitude: 77.0390)
    private let customerCoord = CLLocationCoordinate2D(latitude: 28.4795, longitude: 77.0450)
    
    override func setUp() {
        super.setUp()
        detector = RiderGeofenceDetector()
    }
    
    override func tearDown() {
        detector.stopMonitoring()
        detector = nil
        super.tearDown()
    }
    
    func testInitialDetectorState() {
        XCTAssertEqual(detector.currentZone, .enRoute)
        XCTAssertFalse(detector.isInsideArrivalZone)
        XCTAssertFalse(detector.hasTriggeredMerchantArrival)
        XCTAssertFalse(detector.hasTriggeredCustomerArrival)
        XCTAssertNil(detector.distanceToTargetMeters)
    }
    
    func testInside50mMerchantArrival() {
        var arrivalCallbackTriggered = false
        detector.onMerchantArrival = {
            arrivalCallbackTriggered = true
        }
        
        detector.startMonitoring(
            merchantCoord: merchantCoord,
            customerCoord: customerCoord,
            initialStage: "ASSIGNED"
        )
        
        // Location ~15 meters away from merchant
        let nearMerchantLoc = CLLocation(
            latitude: merchantCoord.latitude + 0.0001,
            longitude: merchantCoord.longitude + 0.0001
        )
        
        detector.updateLocation(nearMerchantLoc)
        
        XCTAssertEqual(detector.currentZone, .atMerchant)
        XCTAssertTrue(detector.isInsideArrivalZone)
        XCTAssertTrue(detector.hasTriggeredMerchantArrival)
        XCTAssertTrue(arrivalCallbackTriggered)
        XCTAssertLessThanOrEqual(detector.distanceToTargetMeters ?? 100, 50.0)
    }
    
    func testProximityWarningZone() {
        detector.startMonitoring(
            merchantCoord: merchantCoord,
            customerCoord: customerCoord,
            initialStage: "ASSIGNED"
        )
        
        // Location ~120 meters away from merchant
        let approachLoc = CLLocation(
            latitude: merchantCoord.latitude + 0.001,
            longitude: merchantCoord.longitude
        )
        
        detector.updateLocation(approachLoc)
        
        XCTAssertEqual(detector.currentZone, .nearMerchant)
        XCTAssertFalse(detector.isInsideArrivalZone)
        XCTAssertFalse(detector.hasTriggeredMerchantArrival)
    }
    
    func testInside50mCustomerArrival() {
        var customerArrivalTriggered = false
        detector.onCustomerArrival = {
            customerArrivalTriggered = true
        }
        
        detector.startMonitoring(
            merchantCoord: merchantCoord,
            customerCoord: customerCoord,
            initialStage: "PICKED_UP"
        )
        
        // Location ~20 meters from customer doorstep
        let nearCustomerLoc = CLLocation(
            latitude: customerCoord.latitude + 0.00015,
            longitude: customerCoord.longitude + 0.0001
        )
        
        detector.updateLocation(nearCustomerLoc)
        
        XCTAssertEqual(detector.currentZone, .atCustomer)
        XCTAssertTrue(detector.isInsideArrivalZone)
        XCTAssertTrue(detector.hasTriggeredCustomerArrival)
        XCTAssertTrue(customerArrivalTriggered)
    }
    
    func testStopMonitoringResetsState() {
        detector.startMonitoring(
            merchantCoord: merchantCoord,
            customerCoord: customerCoord,
            initialStage: "ASSIGNED"
        )
        
        let loc = CLLocation(latitude: merchantCoord.latitude, longitude: merchantCoord.longitude)
        detector.updateLocation(loc)
        XCTAssertTrue(detector.isInsideArrivalZone)
        
        detector.stopMonitoring()
        XCTAssertEqual(detector.currentZone, .enRoute)
        XCTAssertFalse(detector.isInsideArrivalZone)
        XCTAssertNil(detector.distanceToTargetMeters)
    }
}
