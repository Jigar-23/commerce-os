import XCTest
@testable import CommerceOS
import CoreLocation

final class AddressPlatformTests: XCTestCase {

    func testAddressValidationValid() {
        let address = StructuredAddress(
            tag: "Home",
            houseNumber: "A-504, 5th Floor",
            street: "DLF Cyber City",
            city: "Gurugram",
            state: "Haryana",
            postalCode: "122001",
            entrance: .mainGate,
            recipientType: .me,
            contactPhone: "9876501234"
        )

        let result = address.validate()
        XCTAssertTrue(result.isValid, "Valid address should pass validation")
        XCTAssertNil(result.houseNumberError)
        XCTAssertNil(result.postalCodeError)
        XCTAssertNil(result.contactPhoneError)
        XCTAssertNil(result.customEntranceDetailsError)
        XCTAssertNil(result.contactNameError)
    }

    func testAddressValidationEmptyDetails() {
        let address = StructuredAddress(
            houseNumber: "",
            building: "",
            street: "",
            postalCode: "122001",
            contactPhone: "9876501234"
        )

        let result = address.validate()
        XCTAssertFalse(result.isValid)
        XCTAssertEqual(result.houseNumberError, "Address details are required")
    }

    func testAddressValidationInvalidPinCode() {
        // Invalid PIN: starts with 0
        var address = StructuredAddress(
            houseNumber: "Flat 101",
            postalCode: "012345",
            contactPhone: "9876501234"
        )
        var result = address.validate()
        XCTAssertFalse(result.isValid)
        XCTAssertEqual(result.postalCodeError, "Enter a valid 6-digit PIN code (e.g. 122001)")

        // Invalid PIN: less than 6 digits
        address.postalCode = "12200"
        result = address.validate()
        XCTAssertFalse(result.isValid)
        XCTAssertEqual(result.postalCodeError, "Enter a valid 6-digit PIN code (e.g. 122001)")

        // Valid India PIN
        address.postalCode = "122001"
        result = address.validate()
        XCTAssertTrue(result.isValid)
        XCTAssertNil(result.postalCodeError)
    }

    func testAddressValidationEntranceOther() {
        var address = StructuredAddress(
            houseNumber: "Tower 3, Flat 202",
            postalCode: "122001",
            entrance: .other,
            customEntranceDetails: "",
            contactPhone: "9876501234"
        )

        var result = address.validate()
        XCTAssertFalse(result.isValid)
        XCTAssertEqual(result.customEntranceDetailsError, "Specify entrance details when Other is selected")

        address.customEntranceDetails = "Back entrance near club house"
        result = address.validate()
        XCTAssertTrue(result.isValid)
        XCTAssertNil(result.customEntranceDetailsError)
    }

    func testAddressValidationPhoneRequired() {
        var address = StructuredAddress(
            houseNumber: "Villa 12",
            postalCode: "122001",
            contactPhone: "12345"
        )

        var result = address.validate()
        XCTAssertFalse(result.isValid)
        XCTAssertEqual(result.contactPhoneError, "Phone number is required (10 digits for delivery updates)")

        // Valid with +91 and spaces
        address.contactPhone = "+91 98765 01234"
        result = address.validate()
        XCTAssertTrue(result.isValid)
        XCTAssertNil(result.contactPhoneError)
    }

    func testAddressValidationRecipientSomeoneElse() {
        var address = StructuredAddress(
            houseNumber: "House 45",
            postalCode: "122001",
            recipientType: .someoneElse,
            contactName: "",
            contactPhone: "9876501234"
        )

        var result = address.validate()
        XCTAssertFalse(result.isValid)
        XCTAssertEqual(result.contactNameError, "Recipient name is required")

        address.contactName = "Aarav Sharma"
        result = address.validate()
        XCTAssertTrue(result.isValid)
        XCTAssertNil(result.contactNameError)
    }

    func testAddressConflictDetection() {
        let address = StructuredAddress(
            city: "Gurugram",
            postalCode: "122001"
        )

        // Matching map place -> No conflict
        let matchingPlace = GeocodedPlace(
            formattedAddress: "DLF Cyber City, Gurugram",
            city: "Gurugram",
            state: "Haryana",
            postalCode: "122001",
            geoPoint: GeoPoint(latitude: 28.4900, longitude: 77.0900)
        )
        let noConflict = address.detectAddressConflict(mapPlace: matchingPlace)
        XCTAssertFalse(noConflict.hasConflict)
        XCTAssertNil(noConflict.message)

        // Mismatched city and PIN -> Conflict detected
        let conflictingPlace = GeocodedPlace(
            formattedAddress: "Connaught Place, New Delhi",
            city: "New Delhi",
            state: "Delhi",
            postalCode: "110001",
            geoPoint: GeoPoint(latitude: 28.6300, longitude: 77.2200)
        )
        let conflict = address.detectAddressConflict(mapPlace: conflictingPlace)
        XCTAssertTrue(conflict.hasConflict)
        XCTAssertEqual(conflict.suggestedCity, "New Delhi")
        XCTAssertEqual(conflict.suggestedPostalCode, "110001")
        XCTAssertTrue(conflict.message?.contains("mismatch") == true)
    }

    func testRealTimeEtaEngineDistanceAndEta() {
        // Test Store coordinates (Rewari / NCR Hub)
        let storeLat = 28.1970
        let storeLng = 76.6190

        // Zero distance should clamp to minimum rapid delivery SLA (8 mins)
        let zeroEta = RealTimeEtaEngine.calculateEtaMinutes(userLat: storeLat, userLng: storeLng)
        XCTAssertEqual(zeroEta, 8)

        // ~5 km straight line distance
        // Road distance = 5 * 1.3 = 6.5 km
        // Travel time = 6.5 * 3 = 19.5 min + 4 prep = ~24 min
        let dist5KmLat = storeLat + (5.0 / 111.0)
        let eta = RealTimeEtaEngine.calculateEtaMinutes(userLat: dist5KmLat, userLng: storeLng)
        XCTAssertGreaterThanOrEqual(eta, 20)
        XCTAssertLessThanOrEqual(eta, 28)

        // Far away distance clamps to max 45 mins
        let farEta = RealTimeEtaEngine.calculateEtaMinutes(userLat: 12.9716, userLng: 77.5946)
        XCTAssertEqual(farEta, 45)
    }

    func testTwentyMeterGpsMatchingAlgorithm() {
        let pinLat = 28.4595
        let pinLng = 77.0266

        // Address 1: 10 meters away (lat shift ~0.00009 deg)
        let closeLat = pinLat + (10.0 / 111000.0)
        let closeDist = RealTimeEtaEngine.calculateDistanceKm(lat1: pinLat, lon1: pinLng, lat2: closeLat, lon2: pinLng) * 1000.0
        XCTAssertLessThanOrEqual(closeDist, 20.0, "Should be within 20 meters")

        // Address 2: 50 meters away (lat shift ~0.00045 deg)
        let farLat = pinLat + (50.0 / 111000.0)
        let farDist = RealTimeEtaEngine.calculateDistanceKm(lat1: pinLat, lon1: pinLng, lat2: farLat, lon2: pinLng) * 1000.0
        XCTAssertGreaterThan(farDist, 20.0, "Should be beyond 20 meters")
    }
}
