import Foundation

/**
 * 1:1 Parity with Android EntranceType in StructuredAddress.kt
 */
public enum EntranceType: String, CaseIterable, Codable, Identifiable {
    case mainGate = "MAIN_GATE"
    case gate2 = "GATE_2"
    case towerEntrance = "TOWER_ENTRANCE"
    case securityGate = "SECURITY_GATE"
    case other = "OTHER"

    public var id: String { rawValue }

    public var label: String {
        switch self {
        case .mainGate: return "Main Gate"
        case .gate2: return "Gate 2"
        case .towerEntrance: return "Tower Entrance"
        case .securityGate: return "Security Gate"
        case .other: return "Other"
        }
    }
}

/**
 * 1:1 Parity with Android RecipientType in StructuredAddress.kt
 */
public enum RecipientType: String, CaseIterable, Codable, Identifiable {
    case me = "ME"
    case someoneElse = "SOMEONE_ELSE"

    public var id: String { rawValue }
}

/**
 * Field-level validation results for address creation and editing.
 * 1:1 Parity with Android AddressValidationResult in StructuredAddress.kt
 */
public struct AddressValidationResult: Equatable {
    public let isValid: Bool
    public let houseNumberError: String?
    public let streetError: String?
    public let cityError: String?
    public let postalCodeError: String?
    public let customEntranceDetailsError: String?
    public let contactNameError: String?
    public let contactPhoneError: String?
    public let conflictWarning: AddressConflictWarning?

    public init(
        isValid: Bool,
        houseNumberError: String? = nil,
        streetError: String? = nil,
        cityError: String? = nil,
        postalCodeError: String? = nil,
        customEntranceDetailsError: String? = nil,
        contactNameError: String? = nil,
        contactPhoneError: String? = nil,
        conflictWarning: AddressConflictWarning? = nil
    ) {
        self.isValid = isValid
        self.houseNumberError = houseNumberError
        self.streetError = streetError
        self.cityError = cityError
        self.postalCodeError = postalCodeError
        self.customEntranceDetailsError = customEntranceDetailsError
        self.contactNameError = contactNameError
        self.contactPhoneError = contactPhoneError
        self.conflictWarning = conflictWarning
    }
}

/**
 * Explicit Checkout Eligibility model replacing raw serviceability state checks.
 * 1:1 Parity with Android CheckoutEligibility in StructuredAddress.kt
 */
public struct CheckoutEligibility: Codable, Equatable {
    public let isEligible: Bool
    public let reason: String?

    public init(isEligible: Bool, reason: String? = nil) {
        self.isEligible = isEligible
        self.reason = reason
    }
}

/**
 * Address validation & location conflict detection model.
 * 1:1 Parity with Android AddressConflictWarning in StructuredAddress.kt
 */
public struct AddressConflictWarning: Equatable {
    public let hasConflict: Bool
    public let message: String?
    public let suggestedCity: String?
    public let suggestedPostalCode: String?

    public init(
        hasConflict: Bool = false,
        message: String? = nil,
        suggestedCity: String? = nil,
        suggestedPostalCode: String? = nil
    ) {
        self.hasConflict = hasConflict
        self.message = message
        self.suggestedCity = suggestedCity
        self.suggestedPostalCode = suggestedPostalCode
    }
}

/**
 * Canonical Domain Model for Commerce OS Addresses.
 * Stores structured geographic and physical property components independently,
 * preventing string dump corruption during CRUD operations.
 * 1:1 Parity with Android StructuredAddress in StructuredAddress.kt
 */
public struct StructuredAddress: Codable, Identifiable, Equatable, Hashable {
    public var id: String
    public var tag: String
    public var houseNumber: String
    public var building: String
    public var floor: String
    public var street: String
    public var subLocality: String
    public var locality: String
    public var landmark: String
    public var city: String
    public var district: String
    public var state: String
    public var postalCode: String
    public var country: String
    public var deliveryInstructions: String
    public var entrance: EntranceType
    public var customEntranceDetails: String
    public var recipientType: RecipientType
    public var contactName: String
    public var contactPhone: String
    public var isDefault: Bool
    public var geoLocation: GeoPoint?
    public var placeId: String?

    public init(
        id: String = "",
        tag: String = "Home",
        houseNumber: String = "",
        building: String = "",
        floor: String = "",
        street: String = "",
        subLocality: String = "",
        locality: String = "",
        landmark: String = "",
        city: String = "",
        district: String = "",
        state: String = "",
        postalCode: String = "",
        country: String = "India",
        deliveryInstructions: String = "",
        entrance: EntranceType = .mainGate,
        customEntranceDetails: String = "",
        recipientType: RecipientType = .me,
        contactName: String = "",
        contactPhone: String = "",
        isDefault: Bool = false,
        geoLocation: GeoPoint? = nil,
        placeId: String? = nil
    ) {
        self.id = id
        self.tag = tag
        self.houseNumber = houseNumber
        self.building = building
        self.floor = floor
        self.street = street
        self.subLocality = subLocality
        self.locality = locality
        self.landmark = landmark
        self.city = city
        self.district = district
        self.state = state
        self.postalCode = postalCode
        self.country = country
        self.deliveryInstructions = deliveryInstructions
        self.entrance = entrance
        self.customEntranceDetails = customEntranceDetails
        self.recipientType = recipientType
        self.contactName = contactName
        self.contactPhone = contactPhone
        self.isDefault = isDefault
        self.geoLocation = geoLocation
        self.placeId = placeId
    }

    public var formattedAddress: String {
        var parts: [String] = []
        let line1Parts = [houseNumber.trimmingCharacters(in: .whitespaces),
                          building.trimmingCharacters(in: .whitespaces),
                          floor.trimmingCharacters(in: .whitespaces)].filter { !$0.isEmpty }
        if !line1Parts.isEmpty {
            parts.append(line1Parts.joined(separator: ", "))
        }
        let cleanStreet = street.trimmingCharacters(in: .whitespaces)
        if !cleanStreet.isEmpty {
            parts.append(cleanStreet)
        }
        let cleanSubLocality = subLocality.trimmingCharacters(in: .whitespaces)
        if !cleanSubLocality.isEmpty && cleanSubLocality != cleanStreet {
            parts.append(cleanSubLocality)
        }
        let cleanLocality = locality.trimmingCharacters(in: .whitespaces)
        if !cleanLocality.isEmpty && cleanLocality != city {
            parts.append(cleanLocality)
        }
        let cleanLandmark = landmark.trimmingCharacters(in: .whitespaces)
        if !cleanLandmark.isEmpty {
            parts.append("Near \(cleanLandmark)")
        }
        let combined = parts.joined(separator: ", ")
        return combined.isEmpty ? city : combined
    }

    public var displayLocationHeader: String {
        let shortTag = tag.trimmingCharacters(in: .whitespaces).isEmpty ? "Address" : tag
        let area: String
        if !street.trimmingCharacters(in: .whitespaces).isEmpty {
            area = street.trimmingCharacters(in: .whitespaces)
        } else if !subLocality.trimmingCharacters(in: .whitespaces).isEmpty {
            area = subLocality.trimmingCharacters(in: .whitespaces)
        } else {
            area = city
        }
        return "\(shortTag) • \(area)"
    }

    /**
     * Android Address validation rules:
     * - Flat/House/Apartment or street required
     * - India PIN code: 6 digits starting with 1-9
     * - Entrance details required if entrance == OTHER
     * - Compulsory 10-digit phone number for delivery contact
     * - Recipient name required if recipientType == SOMEONE_ELSE
     */
    public func validate() -> AddressValidationResult {
        var houseErr: String? = nil
        var pinErr: String? = nil
        var entranceErr: String? = nil
        var nameErr: String? = nil
        var phoneErr: String? = nil

        let cleanHouse = houseNumber.trimmingCharacters(in: .whitespaces)
        let cleanBuilding = building.trimmingCharacters(in: .whitespaces)
        let cleanStreet = street.trimmingCharacters(in: .whitespaces)

        if cleanHouse.isEmpty && cleanBuilding.isEmpty && cleanStreet.isEmpty {
            houseErr = "Address details are required"
        }

        // India PIN Code Validation (6 digits, first digit 1-9 if provided)
        let cleanPin = postalCode.trimmingCharacters(in: .whitespaces)
        if !cleanPin.isEmpty {
            let pinRegex = try? NSRegularExpression(pattern: "^[1-9][0-9]{5}$")
            let matches = pinRegex?.matches(in: cleanPin, range: NSRange(location: 0, length: cleanPin.utf16.count))
            if matches == nil || matches?.isEmpty == true {
                pinErr = "Enter a valid 6-digit PIN code (e.g. 122001)"
            }
        }

        if entrance == .other && customEntranceDetails.trimmingCharacters(in: .whitespaces).isEmpty {
            entranceErr = "Specify entrance details when Other is selected"
        }

        // Compulsory Phone Number Validation for ALL addresses (Delivery contact / Rider calls)
        let cleanPhone = String(contactPhone.filter { $0.isNumber }.suffix(10))
        if cleanPhone.count != 10 {
            phoneErr = "Phone number is required (10 digits for delivery updates)"
        }

        // Recipient Name validation if someone else
        if recipientType == .someoneElse && contactName.trimmingCharacters(in: .whitespaces).isEmpty {
            nameErr = "Recipient name is required"
        }

        let isValid = houseErr == nil && pinErr == nil && entranceErr == nil && nameErr == nil && phoneErr == nil

        return AddressValidationResult(
            isValid: isValid,
            houseNumberError: houseErr,
            streetError: nil,
            cityError: nil,
            postalCodeError: pinErr,
            customEntranceDetailsError: entranceErr,
            contactNameError: nameErr,
            contactPhoneError: phoneErr
        )
    }

    /**
     * Address validation & location conflict detection model.
     * 1:1 Parity with Android detectAddressConflict in StructuredAddress.kt
     */
    public func detectAddressConflict(mapPlace: GeocodedPlace?) -> AddressConflictWarning {
        guard let mapPlace = mapPlace else {
            return AddressConflictWarning(hasConflict: false)
        }

        let cleanCity = city.trimmingCharacters(in: .whitespaces)
        let mapCity = mapPlace.city.trimmingCharacters(in: .whitespaces)
        let cityMismatch = !cleanCity.isEmpty && !mapCity.isEmpty &&
            cleanCity.caseInsensitiveCompare(mapCity) != .orderedSame

        let cleanPin = postalCode.trimmingCharacters(in: .whitespaces)
        let mapPin = mapPlace.postalCode.trimmingCharacters(in: .whitespaces)
        let pinMismatch = !cleanPin.isEmpty && !mapPin.isEmpty &&
            cleanPin != mapPin

        if cityMismatch || pinMismatch {
            let details: String
            if cityMismatch && pinMismatch {
                details = "City and PIN code (\(cleanCity), \(cleanPin)) mismatch map location (\(mapCity), \(mapPin))."
            } else if cityMismatch {
                details = "City (\(cleanCity)) does not match map location (\(mapCity))."
            } else {
                details = "PIN code (\(cleanPin)) does not match map area (\(mapPin))."
            }
            return AddressConflictWarning(
                hasConflict: true,
                message: details,
                suggestedCity: mapPlace.city,
                suggestedPostalCode: mapPlace.postalCode
            )
        }

        return AddressConflictWarning(hasConflict: false)
    }

    public func toAddressDto() -> AddressDto {
        let compositeLineParts = [
            houseNumber.trimmingCharacters(in: .whitespaces),
            building.trimmingCharacters(in: .whitespaces),
            floor.trimmingCharacters(in: .whitespaces),
            street.trimmingCharacters(in: .whitespaces),
            subLocality.trimmingCharacters(in: .whitespaces)
        ].filter { !$0.isEmpty }

        var uniqueParts: [String] = []
        for part in compositeLineParts where !uniqueParts.contains(part) {
            uniqueParts.append(part)
        }

        let finalAddressLine = uniqueParts.joined(separator: ", ").isEmpty ?
            "\(street.trimmingCharacters(in: .whitespaces)), \(city.trimmingCharacters(in: .whitespaces))".trimmingCharacters(in: CharacterSet(charactersIn: ", ")) :
            uniqueParts.joined(separator: ", ")

        let boundLat = geoLocation?.latitude ?? 0.0
        let boundLng = geoLocation?.longitude ?? 0.0
        let boundCity: String
        if !city.trimmingCharacters(in: .whitespaces).isEmpty {
            boundCity = city.trimmingCharacters(in: .whitespaces)
        } else if !subLocality.trimmingCharacters(in: .whitespaces).isEmpty {
            boundCity = subLocality.trimmingCharacters(in: .whitespaces)
        } else if !locality.trimmingCharacters(in: .whitespaces).isEmpty {
            boundCity = locality.trimmingCharacters(in: .whitespaces)
        } else if !district.trimmingCharacters(in: .whitespaces).isEmpty {
            boundCity = district.trimmingCharacters(in: .whitespaces)
        } else {
            boundCity = "Local Area"
        }

        let cleanPhone = String(contactPhone.filter { $0.isNumber }.suffix(10))
        let formattedPhone = cleanPhone.count == 10 ? "+91\(cleanPhone)" : contactPhone.trimmingCharacters(in: .whitespaces)

        return AddressDto(
            id: id,
            customerId: nil,
            label: tag.isEmpty ? "Home" : tag,
            tag: tag.isEmpty ? "Home" : tag,
            addressType: tag.uppercased(),
            addressLine: finalAddressLine.isEmpty ? "Selected Delivery Address" : finalAddressLine,
            city: boundCity,
            state: state.trimmingCharacters(in: .whitespaces),
            postalCode: postalCode.trimmingCharacters(in: .whitespaces),
            flatNumber: houseNumber.isEmpty ? nil : houseNumber,
            landmark: landmark.isEmpty ? nil : landmark,
            latitude: boundLat,
            longitude: boundLng,
            recipientName: recipientType == .someoneElse ? contactName.trimmingCharacters(in: .whitespaces) : "Customer",
            recipientPhone: formattedPhone,
            contactPhone: formattedPhone,
            isDefault: isDefault
        )
    }

    public static func fromAddressDto(_ dto: AddressDto) -> StructuredAddress {
        let recType: RecipientType
        if let name = dto.recipientName, !name.trimmingCharacters(in: .whitespaces).isEmpty, name != "Customer" {
            recType = .someoneElse
        } else {
            recType = .me
        }

        let geo: GeoPoint?
        if dto.latitude != 0.0 && dto.longitude != 0.0 {
            geo = GeoPoint(
                latitude: dto.latitude,
                longitude: dto.longitude,
                accuracyMeters: 10.0,
                provider: "backend"
            )
        } else {
            geo = nil
        }

        let parts = dto.addressLine.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        let house = dto.flatNumber ?? parts.first ?? dto.addressLine
        let streetPart = parts.count > 1 ? parts.dropFirst().joined(separator: ", ") : ""

        return StructuredAddress(
            id: dto.id,
            tag: dto.displayTag,
            houseNumber: house,
            building: "",
            floor: "",
            street: streetPart,
            subLocality: "",
            locality: dto.city ?? "",
            landmark: dto.landmark ?? "",
            city: dto.city ?? "",
            district: "",
            state: dto.state ?? "",
            postalCode: dto.postalCode ?? "",
            country: "India",
            deliveryInstructions: "",
            entrance: .mainGate,
            customEntranceDetails: "",
            recipientType: recType,
            contactName: dto.recipientName ?? "",
            contactPhone: dto.contactPhone ?? dto.recipientPhone ?? "",
            isDefault: dto.isDefault,
            geoLocation: geo,
            placeId: nil
        )
    }
}
