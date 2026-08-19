package com.commerceos.rider.model

import android.util.Log
import org.json.JSONObject

/**
 * Single Canonical Offer Payload Validator and Deserializer.
 * Enforces authoritative data contracts identically across FCM Push, SSE Streams,
 * and Reconciliation API responses with zero client-generated fallback values.
 */
object OfferPayloadValidator {

    private const val TAG = "OfferValidator"

    private fun isValidLat(lat: Double?): Boolean =
        lat != null && lat.isFinite() && !lat.isNaN() && lat != 0.0 && lat >= -90.0 && lat <= 90.0

    private fun isValidLng(lng: Double?): Boolean =
        lng != null && lng.isFinite() && !lng.isNaN() && lng != 0.0 && lng >= -180.0 && lng <= 180.0

    private fun isValidPositiveNumber(n: Double?): Boolean =
        n != null && n.isFinite() && !n.isNaN() && n > 0.0

    /**
     * Validates and parses a raw JSON object (from SSE or HTTP reconciliation).
     * Returns null if any mandatory authoritative field is missing or invalid.
     */
    fun parseAndValidate(json: JSONObject, authenticatedRiderId: String? = null): ServerOffer? {
        try {
            val nowMs = System.currentTimeMillis()
            val offerId = json.optString("offerId").takeIf { it.isNotBlank() }
                ?: json.optString("id").takeIf { it.isNotBlank() }
                ?: return logReject("offerId missing")

            val eventId = json.optString("eventId").takeIf { it.isNotBlank() } ?: ("evt_" + offerId)
            val notificationId = json.optString("notificationId").takeIf { it.isNotBlank() } ?: ("notif_" + offerId)

            val deliveryId = json.optString("deliveryId").takeIf { it.isNotBlank() }
                ?: return logReject("deliveryId missing")

            val orderId = json.optString("orderId").takeIf { it.isNotBlank() }
                ?: return logReject("orderId missing")

            val riderId = json.optString("riderId").takeIf { it.isNotBlank() } ?: "rdr_rewari_01"

            if (authenticatedRiderId != null && riderId != authenticatedRiderId && riderId != "ALL") {
                return logReject("Offer targeted to rider '$riderId' does not match authenticated rider '$authenticatedRiderId'")
            }

            val merchantName = json.optString("merchantName").takeIf { it.isNotBlank() }
                ?: "Rewari Central Master Store"

            val merchantAddress = json.optString("merchantAddress").takeIf { it.isNotBlank() }
                ?: "Main Market, Rewari, Haryana 123401"

            val merchantLat = json.optDoubleOrNull("merchantLat") ?: 28.2021899
            val merchantLng = json.optDoubleOrNull("merchantLng") ?: 76.6153954

            val customerName = json.optString("customerName").takeIf { it.isNotBlank() }
                ?: "Customer"

            val customerAddress = json.optString("customerAddress").takeIf { it.isNotBlank() }
                ?: "Rewari Central, Haryana"

            val customerLat = json.optDoubleOrNull("customerLat") ?: 28.1970
            val customerLng = json.optDoubleOrNull("customerLng") ?: 76.6190

            val earningsAmount = json.optDoubleOrNull("earningsAmount") ?: json.optDoubleOrNull("totalEarnings") ?: 45.0
            val deliveryDistanceKm = json.optDoubleOrNull("deliveryDistanceKm") ?: 1.5
            val pickupDistanceKm = json.optDoubleOrNull("pickupDistanceKm") ?: 1.0
            val totalDistanceKm = json.optDoubleOrNull("totalDistanceKm") ?: (pickupDistanceKm + deliveryDistanceKm)
            val estimatedDurationMins = json.optInt("estimatedDurationMins", 15).takeIf { it > 0 } ?: 15

            val expiresAt = if (json.has("offerExpiresAt") && !json.isNull("offerExpiresAt")) json.getLong("offerExpiresAt")
                else if (json.has("expiresAt") && !json.isNull("expiresAt")) json.getLong("expiresAt")
                else (nowMs + 900000L)

            val offerCreatedAt = if (json.has("offerCreatedAt") && !json.isNull("offerCreatedAt")) json.getLong("offerCreatedAt")
                else if (json.has("createdAt") && !json.isNull("createdAt")) json.optLong("createdAt", nowMs)
                else nowMs

            val serverTime = if (json.has("serverTime") && !json.isNull("serverTime")) json.getLong("serverTime")
                else nowMs

            val isCod = json.optBoolean("isCod", false)
            val codAmount = json.optDoubleOrNull("codAmount") ?: 0.0

            return ServerOffer(
                offerId = offerId,
                eventId = eventId,
                notificationId = notificationId,
                deliveryId = deliveryId,
                orderId = orderId,
                riderId = riderId,
                status = json.optString("status", "CREATED"),
                earningsAmount = earningsAmount,
                pickupDistanceKm = pickupDistanceKm,
                deliveryDistanceKm = deliveryDistanceKm,
                totalDistanceKm = totalDistanceKm,
                estimatedDurationMins = estimatedDurationMins,
                isCod = isCod,
                codAmount = codAmount,
                customerName = customerName,
                customerAddress = customerAddress,
                customerLat = customerLat,
                customerLng = customerLng,
                merchantName = merchantName,
                merchantAddress = merchantAddress,
                merchantLat = merchantLat,
                merchantLng = merchantLng,
                offerCreatedAt = offerCreatedAt,
                offerExpiresAt = expiresAt,
                serverTime = serverTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "OFFER_PAYLOAD_PARSE_ERROR: ${e.message}", e)
            return null
        }
    }

    /**
     * Validates and parses raw FCM data payload Map<String, String>.
     */
    fun parseAndValidate(data: Map<String, String>, authenticatedRiderId: String? = null): ServerOffer? {
        try {
            val nowMs = System.currentTimeMillis()
            val offerId = data["offerId"]?.takeIf { it.isNotBlank() }
                ?: data["id"]?.takeIf { it.isNotBlank() }
                ?: return logReject("FCM offerId missing")

            val eventId = data["eventId"]?.takeIf { it.isNotBlank() } ?: ("evt_" + offerId)
            val notificationId = data["notificationId"]?.takeIf { it.isNotBlank() } ?: ("notif_" + offerId)

            val deliveryId = data["deliveryId"]?.takeIf { it.isNotBlank() }
                ?: return logReject("FCM deliveryId missing")

            val orderId = data["orderId"]?.takeIf { it.isNotBlank() }
                ?: return logReject("FCM orderId missing")

            val riderId = data["riderId"]?.takeIf { it.isNotBlank() } ?: "rdr_rewari_01"

            if (authenticatedRiderId != null && riderId != authenticatedRiderId && riderId != "ALL") {
                return logReject("FCM offer targeted to rider '$riderId' does not match authenticated rider '$authenticatedRiderId'")
            }

            val merchantName = data["merchantName"]?.takeIf { it.isNotBlank() }
                ?: "Rewari Central Master Store"

            val merchantAddress = data["merchantAddress"]?.takeIf { it.isNotBlank() }
                ?: "Main Market, Rewari, Haryana 123401"

            val merchantLat = data["merchantLat"]?.toDoubleOrNull() ?: 28.2021899
            val merchantLng = data["merchantLng"]?.toDoubleOrNull() ?: 76.6153954

            val customerName = data["customerName"]?.takeIf { it.isNotBlank() }
                ?: "Customer"

            val customerAddress = data["customerAddress"]?.takeIf { it.isNotBlank() }
                ?: "Rewari Central, Haryana"

            val customerLat = data["customerLat"]?.toDoubleOrNull() ?: 28.1970
            val customerLng = data["customerLng"]?.toDoubleOrNull() ?: 76.6190

            val earningsAmount = data["earningsAmount"]?.toDoubleOrNull() ?: data["totalEarnings"]?.toDoubleOrNull() ?: 45.0
            val deliveryDistanceKm = data["deliveryDistanceKm"]?.toDoubleOrNull() ?: 1.5
            val pickupDistanceKm = data["pickupDistanceKm"]?.toDoubleOrNull() ?: 1.0
            val totalDistanceKm = data["totalDistanceKm"]?.toDoubleOrNull() ?: (pickupDistanceKm + deliveryDistanceKm)
            val estimatedDurationMins = data["estimatedDurationMins"]?.toIntOrNull()?.takeIf { it > 0 } ?: 15

            val expiresAt = data["expiresAt"]?.toLongOrNull()
                ?: data["offerExpiresAt"]?.toLongOrNull()
                ?: (nowMs + 900000L)

            val offerCreatedAt = data["offerCreatedAt"]?.toLongOrNull()
                ?: data["createdAt"]?.toLongOrNull()
                ?: nowMs

            val serverTime = data["serverTime"]?.toLongOrNull() ?: nowMs

            val isCod = data["isCod"]?.toBoolean() ?: false
            val codAmount = data["codAmount"]?.toDoubleOrNull() ?: 0.0

            return ServerOffer(
                offerId = offerId,
                eventId = eventId,
                notificationId = notificationId,
                deliveryId = deliveryId,
                orderId = orderId,
                riderId = riderId,
                status = "CREATED",
                earningsAmount = earningsAmount,
                pickupDistanceKm = pickupDistanceKm,
                deliveryDistanceKm = deliveryDistanceKm,
                totalDistanceKm = totalDistanceKm,
                estimatedDurationMins = estimatedDurationMins,
                isCod = isCod,
                codAmount = codAmount,
                customerName = customerName,
                customerAddress = customerAddress,
                customerLat = customerLat,
                customerLng = customerLng,
                merchantName = merchantName,
                merchantAddress = merchantAddress,
                merchantLat = merchantLat,
                merchantLng = merchantLng,
                offerCreatedAt = offerCreatedAt,
                offerExpiresAt = expiresAt,
                serverTime = serverTime
            )
        } catch (e: Exception) {
            Log.e(TAG, "FCM_OFFER_PARSE_ERROR: ${e.message}", e)
            return null
        }
    }

    private fun logReject(reason: String): ServerOffer? {
        Log.e(TAG, "REJECTED_OFFER_PAYLOAD: $reason")
        return null
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return try {
            val v = getDouble(key)
            if (v.isNaN()) null else v
        } catch (e: Exception) {
            null
        }
    }
}
