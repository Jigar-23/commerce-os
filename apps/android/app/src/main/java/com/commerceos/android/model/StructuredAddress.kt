package com.commerceos.android.model

import com.commerceos.android.location.GeoPoint
import com.commerceos.android.location.GeocodedPlace

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
            val parts = mutableListOf<String>()
            val line1Parts = listOf(houseNumber.trim(), building.trim(), floor.trim()).filter { it.isNotBlank() }
            if (line1Parts.isNotEmpty()) parts.add(line1Parts.joinToString(", "))
            if (street.isNotBlank()) parts.add(street.trim())
            if (subLocality.isNotBlank() && subLocality != street) parts.add(subLocality.trim())
            if (locality.isNotBlank() && locality != city) parts.add(locality.trim())
            if (landmark.isNotBlank()) parts.add("Near ${landmark.trim()}")
            return parts.joinToString(", ").ifBlank { city }
        }

    val displayLocationHeader: String
        get() {
            val shortTag = if (tag.isNotBlank()) tag else "Address"
            val area = street.ifBlank { subLocality.ifBlank { city } }
            return "$shortTag • $area"
        }

    fun validate(): AddressValidationResult {
        var houseErr: String? = null
        var streetErr: String? = null
        var cityErr: String? = null
        var pinErr: String? = null
        var entranceErr: String? = null
        var nameErr: String? = null
        var phoneErr: String? = null

        if (houseNumber.isBlank() && building.isBlank()) {
            houseErr = "Flat / House No. or Building Name is required"
        }
        if (street.isBlank()) {
            streetErr = "Street, Area, or Sector is required"
        }
        if (city.isBlank()) {
            cityErr = "City is required"
        }

        // Strict India PIN Code Validation (6 digits, first digit 1-9)
        val cleanPin = postalCode.trim()
        if (cleanPin.isBlank()) {
            pinErr = "PIN code is required"
        } else if (!cleanPin.matches(Regex("^[1-9][0-9]{5}$"))) {
            pinErr = "Enter a valid 6-digit PIN code (e.g. 122001)"
        }

        if (entrance == EntranceType.OTHER && customEntranceDetails.trim().isBlank()) {
            entranceErr = "Specify entrance details when Other is selected"
        }

        // Recipient validation if someone else
        if (recipientType == RecipientType.SOMEONE_ELSE) {
            if (contactName.trim().isBlank()) {
                nameErr = "Recipient name is required"
            }
            val cleanPhone = contactPhone.trim().replace(Regex("[^0-9]"), "")
            if (cleanPhone.length != 10) {
                phoneErr = "Enter a valid 10-digit mobile number"
            }
        }

        val isValid = houseErr == null && streetErr == null && cityErr == null &&
                pinErr == null && entranceErr == null && nameErr == null && phoneErr == null

        return AddressValidationResult(
            isValid = isValid,
            houseNumberError = houseErr,
            streetError = streetErr,
            cityError = cityErr,
            postalCodeError = pinErr,
            customEntranceDetailsError = entranceErr,
            contactNameError = nameErr,
            contactPhoneError = phoneErr
        )
    }

    fun toAddAddressRequest(): AddAddressRequest {
        val compositeLineParts = listOf(
            houseNumber.trim(),
            building.trim(),
            floor.trim(),
            street.trim(),
            subLocality.trim()
        ).filter { it.isNotBlank() }

        val finalAddressLine = compositeLineParts.distinct().joinToString(", ").ifBlank {
            "${street.trim()}, ${city.trim()}".trim().trim(',')
        }

        val boundLat = geoLocation?.latitude ?: 28.1970
        val boundLng = geoLocation?.longitude ?: 76.6190
        val boundAccuracy = geoLocation?.accuracyMeters ?: 10.0

        return AddAddressRequest(
            tag = tag.ifBlank { "Home" },
            addressLine = finalAddressLine.ifBlank { "Selected Delivery Address" },
            city = city.trim().ifBlank { "NCR" },
            state = state.trim().ifBlank { "Haryana" },
            postalCode = postalCode.trim().ifBlank { "122002" },
            country = country.ifBlank { "India" },
            landmark = landmark.trim(),
            contactName = if (recipientType == RecipientType.SOMEONE_ELSE) contactName.trim() else "",
            contactPhone = if (recipientType == RecipientType.SOMEONE_ELSE) contactPhone.trim() else "",
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
            val parts = api.addressLine.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val house = parts.getOrNull(0) ?: ""
            val bldg = parts.getOrNull(1) ?: ""
            val remainingStreet = if (parts.size > 2) parts.drop(2).joinToString(", ") else ""

            val recType = if (!api.contactName.isNullOrBlank() || !api.contactPhone.isNullOrBlank()) {
                RecipientType.SOMEONE_ELSE
            } else {
                RecipientType.ME
            }

            val geo = if (api.latitude != null && api.longitude != null) {
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
                houseNumber = house,
                building = bldg,
                floor = "",
                street = remainingStreet.ifBlank { api.addressLine },
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
                contactName = api.contactName,
                contactPhone = api.contactPhone,
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
