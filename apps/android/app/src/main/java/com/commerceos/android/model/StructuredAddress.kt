package com.commerceos.android.model

import com.commerceos.android.location.GeoPoint
import com.commerceos.android.location.GeocodedPlace
import com.commerceos.android.location.cleanAddressSegments

enum class EntranceType(val label: String) {
    MAIN_GATE("Main Gate"),
    GATE_2("Gate 2"),
    TOWER_ENTRANCE("Tower Entrance"),
    SECURITY_GATE("Security Gate"),
    OTHER("Other")
}

enum class RecipientType {
    ME,
    SOMEONE_ELSE
}

/**
 * Field-level validation results for address creation and editing.
 */
data class AddressValidationResult(
    val isValid: Boolean,
    val houseNumberError: String? = null,
    val streetError: String? = null,
    val cityError: String? = null,
    val postalCodeError: String? = null,
    val customEntranceDetailsError: String? = null,
    val contactNameError: String? = null,
    val contactPhoneError: String? = null,
    val conflictWarning: AddressConflictWarning? = null
)

/**
 * Explicit Checkout Eligibility model replacing raw serviceability state checks.
 */
data class CheckoutEligibility(
    val isEligible: Boolean,
    val reason: String? = null
)

/**
 * Canonical Domain Model for Commerce OS Addresses.
 * Stores structured geographic and physical property components independently,
 * preventing string dump corruption during CRUD operations.
 */
data class StructuredAddress(
    val id: String = "",
    val tag: String = "Home",
    val houseNumber: String = "",
    val building: String = "",
    val floor: String = "",
    val street: String = "",
    val subLocality: String = "",
    val locality: String = "",
    val landmark: String = "",
    val city: String = "",
    val district: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "India",
    val deliveryInstructions: String = "",
    val entrance: EntranceType = EntranceType.MAIN_GATE,
    val customEntranceDetails: String = "",
    val recipientType: RecipientType = RecipientType.ME,
    val contactName: String = "",
    val contactPhone: String = "",
    val isDefault: Boolean = false,
    val geoLocation: GeoPoint? = null,
    val placeId: String? = null
) {
    val formattedAddress: String
        get() {
            val line1 = listOf(houseNumber.trim(), building.trim(), floor.trim()).filter { it.isNotBlank() }.joinToString(", ")
            val landmarkStr = if (landmark.isNotBlank()) "Near ${landmark.trim()}" else ""
            val raw = cleanAddressSegments(line1, street, subLocality, locality, landmarkStr, city)
            return raw.ifBlank { city }
        }

    val displayLocationHeader: String
        get() {
            val shortTag = if (tag.isNotBlank()) tag else "Address"
            val area = cleanAddressSegments(street, subLocality).ifBlank { city }
            return "$shortTag • $area"
        }

    fun validate(): AddressValidationResult {
        var houseErr: String? = null
        var pinErr: String? = null
        var entranceErr: String? = null
        var nameErr: String? = null
        var phoneErr: String? = null

        if (houseNumber.trim().isBlank() && building.trim().isBlank() && street.trim().isBlank()) {
            houseErr = "Address details are required"
        }

        // India PIN Code Validation (6 digits, first digit 1-9 if provided)
        val cleanPin = postalCode.trim()
        if (cleanPin.isNotBlank() && !cleanPin.matches(Regex("^[1-9][0-9]{5}$"))) {
            pinErr = "Enter a valid 6-digit PIN code (e.g. 122001)"
        }

        if (entrance == EntranceType.OTHER && customEntranceDetails.trim().isBlank()) {
            entranceErr = "Specify entrance details when Other is selected"
        }

        // Phone Number Validation: strictly required if for someone else, or validated if filled
        val cleanPhone = contactPhone.trim().replace(Regex("[^0-9]"), "").takeLast(10)
        if (recipientType == RecipientType.SOMEONE_ELSE) {
            if (cleanPhone.length != 10 || !cleanPhone.matches(Regex("^[6-9][0-9]{9}$"))) {
                phoneErr = "Enter valid 10-digit mobile number for receiver"
            }
            if (contactName.trim().isBlank()) {
                nameErr = "Receiver's name is required"
            }
        } else if (cleanPhone.isNotBlank() && (cleanPhone.length != 10 || !cleanPhone.matches(Regex("^[6-9][0-9]{9}$")))) {
            phoneErr = "Enter valid Indian mobile number starting with 6, 7, 8 or 9"
        }

        val isValid = houseErr == null &&
                pinErr == null && entranceErr == null && nameErr == null && phoneErr == null

        return AddressValidationResult(
            isValid = isValid,
            houseNumberError = houseErr,
            streetError = null,
            cityError = null,
            postalCodeError = pinErr,
            customEntranceDetailsError = entranceErr,
            contactNameError = nameErr,
            contactPhoneError = phoneErr
        )
    }

    fun toAddAddressRequest(): AddAddressRequest {
        val userDetails = listOfNotNull(
            houseNumber.trim().takeIf { it.isNotBlank() },
            building.trim().takeIf { it.isNotBlank() },
            floor.trim().takeIf { it.isNotBlank() }
        ).joinToString(", ")

        // Strictly keep the user's entered address details separate from the pinpoint:
        // Pinpoint coordinates (latitude/longitude) and geographic areas are stored in their own fields.
        // We do NOT append the map pinpoint onto the user's address details.
        val finalAddressLine = if (userDetails.isNotBlank()) {
            userDetails
        } else {
            cleanAddressSegments(street.trim(), subLocality.trim()).ifBlank { "Selected Delivery Address" }
        }

        val boundLat = geoLocation?.latitude ?: 0.0
        val boundLng = geoLocation?.longitude ?: 0.0
        val boundAccuracy = geoLocation?.accuracyMeters ?: 10.0
        val boundCity = city.trim().ifBlank {
            subLocality.trim().ifBlank {
                locality.trim().ifBlank {
                    district.trim().ifBlank { "Local Area" }
                }
            }
        }

        val cleanPhone = contactPhone.trim().replace(Regex("[^0-9]"), "").takeLast(10)
        val formattedPhone = if (cleanPhone.length == 10) "+91$cleanPhone" else contactPhone.trim()

        return AddAddressRequest(
            tag = tag.ifBlank { "Home" },
            addressLine = finalAddressLine.ifBlank { "Selected Delivery Address" },
            city = boundCity,
            state = state.trim(),
            postalCode = postalCode.trim(),
            country = country.ifBlank { "India" },
            landmark = landmark.trim(),
            contactName = if (recipientType == RecipientType.SOMEONE_ELSE) contactName.trim() else "Customer",
            contactPhone = formattedPhone,
            isDefault = isDefault,
            latitude = boundLat,
            longitude = boundLng,
            deliveryInstructions = deliveryInstructions.trim(),
            placeId = placeId?.ifBlank { "geo_${boundLat}_${boundLng}" } ?: "geo_${boundLat}_${boundLng}",
            accuracyMeters = boundAccuracy.toFloat()
        )
    }

    companion object {
        fun fromApiAddress(api: ApiAddress): StructuredAddress {
            val recType = if (!api.contactName.isNullOrBlank() || !api.contactPhone.isNullOrBlank()) {
                RecipientType.SOMEONE_ELSE
            } else {
                RecipientType.ME
            }

            val geo = if (api.latitude != null && api.longitude != null && api.latitude != 0.0 && api.longitude != 0.0) {
                GeoPoint(
                    latitude = api.latitude,
                    longitude = api.longitude,
                    accuracyMeters = api.accuracyMeters,
                    provider = "backend"
                )
            } else null

            return StructuredAddress(
                id = api.id,
                tag = api.tag.ifBlank { "Home" },
                houseNumber = api.addressLine.trim(),
                building = "",
                floor = "",
                street = "",
                subLocality = "",
                locality = api.city,
                landmark = api.landmark,
                city = api.city,
                district = "",
                state = api.state,
                postalCode = api.postalCode,
                country = api.country.ifBlank { "India" },
                deliveryInstructions = api.deliveryInstructions ?: "",
                entrance = EntranceType.MAIN_GATE,
                recipientType = recType,
                contactName = api.contactName ?: "",
                contactPhone = api.contactPhone ?: "",
                isDefault = api.isDefault,
                geoLocation = geo,
                placeId = api.placeId
            )
        }
    }
}

/**
 * Address validation & location conflict detection model.
 */
data class AddressConflictWarning(
    val hasConflict: Boolean,
    val message: String? = null,
    val suggestedCity: String? = null,
    val suggestedPostalCode: String? = null
)

fun StructuredAddress.detectAddressConflict(mapPlace: GeocodedPlace?): AddressConflictWarning {
    if (mapPlace == null) return AddressConflictWarning(hasConflict = false)

    val cityMismatch = city.isNotBlank() && mapPlace.city.isNotBlank() &&
            !city.equals(mapPlace.city, ignoreCase = true)

    val pinMismatch = postalCode.isNotBlank() && mapPlace.postalCode.isNotBlank() &&
            postalCode.trim() != mapPlace.postalCode.trim()

    if (cityMismatch || pinMismatch) {
        val details = when {
            cityMismatch && pinMismatch -> "City and PIN code ($city, $postalCode) mismatch map location (${mapPlace.city}, ${mapPlace.postalCode})."
            cityMismatch -> "City ($city) does not match map location (${mapPlace.city})."
            else -> "PIN code ($postalCode) does not match map area (${mapPlace.postalCode})."
        }
        return AddressConflictWarning(
            hasConflict = true,
            message = details,
            suggestedCity = mapPlace.city,
            suggestedPostalCode = mapPlace.postalCode
        )
    }

    return AddressConflictWarning(hasConflict = false)
}
