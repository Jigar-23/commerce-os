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

    private val COD_PAYMENT_ALLOWLIST = setOf("COD", "CASH", "PAY_ON_DELIVERY", "CASH_ON_DELIVERY")

    fun extractCoarseLocality(rawAddress: String?, localityCandidate: String? = null): String {
        if (!localityCandidate.isNullOrBlank()) {
            val candidate = localityCandidate.trim()
            val isStreetOrHouse = candidate.matches(Regex("(?i).*\\b(street|road|st\\b|rd\\b|lane|ave|avenue|dr\\b|drive|gali|marg|sector|house|h\\.no|flat|apt|apartment|unit|tower|villa|plot|floor|block|room|bldg|b-?\\d+)\\b.*")) ||
                candidate.matches(Regex("^#?\\d+.*")) ||
                candidate.matches(Regex("^[A-Z]?-\\d+.*", RegexOption.IGNORE_CASE))

            if (!candidate.contains(",") && candidate.length in 3..40 && !candidate.matches(Regex(".*\\d{3,}.*")) && !isStreetOrHouse) {
                val isPersonName = candidate.matches(Regex("^[A-Z][a-z]+\\s+[A-Z][a-z]+$"))
                if (!isPersonName) return candidate
            }
            val parts = candidate.split(",").map { it.trim() }.filter { it.isNotBlank() }
            val filtered = parts.filterNot { part ->
                part.matches(Regex("(?i)^(flat|apt|apartment|unit|tower|house|h\\.no|villa|plot|floor|block|room|bldg|b-?\\d+)[\\s\\d\\-#].*")) ||
                part.matches(Regex("^[A-Z]?-\\d+.*", RegexOption.IGNORE_CASE)) ||
                part.matches(Regex("^#?\\d+.*")) ||
                part.matches(Regex(".*\\b(street|road|st|rd|lane|ave|avenue|dr|drive|gali|marg|sector)\\b.*", RegexOption.IGNORE_CASE)) ||
                part.matches(Regex("^[A-Z][a-z]+\\s+[A-Z][a-z]+$"))
            }
            if (filtered.isNotEmpty()) {
                val chosen = filtered.last()
                val isChosenStreetOrHouse = chosen.matches(Regex("(?i).*\\b(street|road|st\\b|rd\\b|lane|ave|avenue|dr\\b|drive|gali|marg|sector|house|h\\.no|flat|apt|apartment|unit|tower|villa|plot|floor|block|room|bldg|b-?\\d+)\\b.*")) ||
                    chosen.matches(Regex("^#?\\d+.*")) ||
                    chosen.matches(Regex("^[A-Z]?-\\d+.*", RegexOption.IGNORE_CASE))
                if (!isChosenStreetOrHouse) {
                    return chosen
                }
            }
        }
        return "Customer Delivery Area"
    }

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

            val eventNonce = json.optString("broadcastNonce").takeIf { it.isNotBlank() }
                ?: ""

            val eventId = json.optString("eventId").takeIf { it.isNotBlank() }
                ?: if (eventNonce.isNotBlank()) "evt_${offerId}_${eventNonce}" else "evt_${offerId}"
            val notificationId = json.optString("notificationId").takeIf { it.isNotBlank() }
                ?: if (eventNonce.isNotBlank()) "notif_${offerId}_${eventNonce}" else "notif_${offerId}"

            val deliveryId = json.optString("deliveryId").takeIf { it.isNotBlank() }
                ?: return logReject("deliveryId missing")

            val orderId = json.optString("orderId").takeIf { it.isNotBlank() }
                ?: return logReject("orderId missing")

            val riderId = json.optString("riderId").takeIf { it.isNotBlank() }
                ?: return logReject("riderId missing")

            val isPoolOffer = riderId.equals("ALL", ignoreCase = true) || riderId.equals("broadcast", ignoreCase = true) || riderId.equals("pool", ignoreCase = true)
            if (authenticatedRiderId != null && riderId != authenticatedRiderId && !isPoolOffer) {
                return logReject("Target rider does not match authenticated rider")
            }

            val merchantName = json.optString("merchantName").takeIf { it.isNotBlank() }
                ?: json.optString("merchant_name").takeIf { it.isNotBlank() }
                ?: json.optString("storeName").takeIf { it.isNotBlank() }
                ?: json.optString("store_name").takeIf { it.isNotBlank() }
                ?: json.optString("merchant").takeIf { it.isNotBlank() }
                ?: "Merchant Partner"

            val merchantAddress = json.optString("merchantAddress").takeIf { it.isNotBlank() }
                ?: json.optString("merchant_address").takeIf { it.isNotBlank() }
                ?: json.optString("storeAddress").takeIf { it.isNotBlank() }
                ?: json.optString("store_address").takeIf { it.isNotBlank() }
                ?: "Merchant Hub"

            val merchantLat = json.optDoubleOrNull("merchantLat") ?: json.optDoubleOrNull("merchant_lat") ?: json.optDoubleOrNull("storeLat") ?: 0.0
            val merchantLng = json.optDoubleOrNull("merchantLng") ?: json.optDoubleOrNull("merchant_lng") ?: json.optDoubleOrNull("storeLng") ?: 0.0

            val offerStatus = json.optString("status", "CREATED")
            val isAccepted = offerStatus.equals("ACCEPTED", ignoreCase = true) || offerStatus.equals("ASSIGNED", ignoreCase = true)

            val rawCustomerName = json.optString("customerName").takeIf { it.isNotBlank() }
                ?: json.optString("customer_name").takeIf { it.isNotBlank() }
                ?: "Customer"

            val rawCustomerAddress = json.optString("customerAddress").takeIf { it.isNotBlank() }
                ?: json.optString("customer_address").takeIf { it.isNotBlank() }
                ?: json.optString("deliveryAddress").takeIf { it.isNotBlank() }
                ?: json.optString("delivery_address").takeIf { it.isNotBlank() }
                ?: "Delivery Address"

            val rawCustomerLat = json.optDoubleOrNull("customerLat") ?: json.optDoubleOrNull("customer_lat") ?: json.optDoubleOrNull("destLat") ?: 0.0
            val rawCustomerLng = json.optDoubleOrNull("customerLng") ?: json.optDoubleOrNull("destLng") ?: 0.0

            val localityHint = json.optString("locality").takeIf { it.isNotBlank() }
                ?: json.optString("dropLocality").takeIf { it.isNotBlank() }
                ?: json.optString("customerLocality").takeIf { it.isNotBlank() }

            val customerName = if (isAccepted) rawCustomerName else "Customer"
            val customerAddress = if (isAccepted) rawCustomerAddress else extractCoarseLocality(null, localityHint)
            val customerLat = if (isAccepted) rawCustomerLat else 0.0
            val customerLng = if (isAccepted) rawCustomerLng else 0.0

            val isCodRaw = if (json.has("isCod")) json.optBoolean("isCod") else if (json.has("is_cod")) json.optBoolean("is_cod") else null
            if (isCodRaw != true) {
                Log.w(TAG, "Rejecting offer $offerId: deployment strictly enforces explicit true COD / Pay on Delivery.")
                return null
            }
            val isCod = true

            val paymentMethod = json.optString("paymentMethod")
                .ifBlank { json.optString("payment_method") }
                .ifBlank { json.optString("paymentMode") }
                .ifBlank { json.optString("payment_mode") }
                .trim()
                .uppercase()
            if (paymentMethod.isNotBlank() && paymentMethod !in COD_PAYMENT_ALLOWLIST) {
                Log.w(TAG, "Rejecting offer $offerId: payment method $paymentMethod contradicts COD-only deployment.")
                return null
            }

            val parsedCodAmount = json.optDoubleOrNull("codAmount") ?: json.optDoubleOrNull("cod_amount")
            val parsedOrderTotal = json.optDoubleOrNull("orderTotal")
                ?: json.optDoubleOrNull("order_total")
                ?: json.optDoubleOrNull("totalAmount")
                ?: json.optDoubleOrNull("total_amount")
                ?: json.optDoubleOrNull("cartValue")

            val codAmount = (parsedCodAmount?.takeIf { it > 0.0 })
                ?: (parsedOrderTotal?.takeIf { it > 0.0 })
            if (codAmount == null || codAmount <= 0.0) {
                Log.w(TAG, "Rejecting offer $offerId: positive COD amount is required in COD deployment.")
                return null
            }

            val orderTotal = parsedOrderTotal ?: codAmount

            val earningsAmount = (json.optDoubleOrNull("earningsAmount")
                ?: json.optDoubleOrNull("earnings_amount")
                ?: json.optDoubleOrNull("totalEarnings")
                ?: json.optDoubleOrNull("payout")
                ?: json.optDoubleOrNull("earnings")
                ?: json.optDoubleOrNull("estimatedEarnings")
                ?: json.optDoubleOrNull("estimated_earnings")
                ?: 0.0)

            val itemsList = mutableListOf<RiderOrderItem>()
            val packageCount = (json.optInt("packageCount", 0).takeIf { it in 1..100 }
                ?: json.optInt("itemCount", 0).takeIf { it in 1..100 }
                ?: json.optJSONArray("items")?.length()?.takeIf { it in 1..100 }
                ?: 1)
            if (isAccepted) {
                val itemsArray = json.optJSONArray("items")
                if (itemsArray != null) {
                    for (i in 0 until itemsArray.length()) {
                        val itObj = itemsArray.optJSONObject(i)
                        if (itObj != null) {
                            val name = itObj.optString("name").takeIf { it.isNotBlank() } ?: itObj.optString("productName").takeIf { it.isNotBlank() } ?: "Item"
                            val qty = itObj.optInt("quantity", 1)
                            val price = itObj.optDouble("price", itObj.optDouble("unitPrice", 0.0))
                            val sku = itObj.optString("sku", "")
                            itemsList.add(RiderOrderItem(name = name, quantity = qty, price = price, sku = sku))
                        }
                    }
                }
            }

            val rawDelivery = json.optDoubleOrNull("deliveryDistanceKm") ?: json.optDoubleOrNull("delivery_distance_km")
            val rawPickup = json.optDoubleOrNull("pickupDistanceKm") ?: json.optDoubleOrNull("pickup_distance_km")
            val rawTotalDist = json.optDoubleOrNull("totalDistanceKm")
            val totalDistanceKm = rawTotalDist ?: (if (rawDelivery != null && rawPickup != null) rawDelivery + rawPickup else rawDelivery ?: 0.0)
            val deliveryDistanceKm = rawDelivery ?: totalDistanceKm
            val pickupDistanceKm = rawPickup
            val estimatedDurationMins = json.optInt("estimatedDurationMins", 0).takeIf { it > 0 }
                ?: json.optInt("estimated_duration_mins", 0).takeIf { it > 0 }
                ?: 0

            val expiresAt = if (json.has("offerExpiresAt") && !json.isNull("offerExpiresAt")) json.getLong("offerExpiresAt")
                else if (json.has("expiresAt") && !json.isNull("expiresAt")) json.getLong("expiresAt")
                else (nowMs + 900000L)

            val offerCreatedAt = if (json.has("offerCreatedAt") && !json.isNull("offerCreatedAt")) json.getLong("offerCreatedAt")
                else if (json.has("createdAt") && !json.isNull("createdAt")) json.optLong("createdAt", nowMs)
                else nowMs

            val serverTime = if (json.has("serverTime") && !json.isNull("serverTime")) json.getLong("serverTime")
                else nowMs

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
                orderTotal = orderTotal,
                items = itemsList,
                packageCount = packageCount,
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

            val eventNonce = data["broadcastNonce"]?.takeIf { it.isNotBlank() }
                ?: ""

            val eventId = data["eventId"]?.takeIf { it.isNotBlank() }
                ?: if (eventNonce.isNotBlank()) "evt_${offerId}_${eventNonce}" else "evt_${offerId}"
            val notificationId = data["notificationId"]?.takeIf { it.isNotBlank() }
                ?: if (eventNonce.isNotBlank()) "notif_${offerId}_${eventNonce}" else "notif_${offerId}"

            val deliveryId = data["deliveryId"]?.takeIf { it.isNotBlank() }
                ?: return logReject("FCM deliveryId missing")

            val orderId = data["orderId"]?.takeIf { it.isNotBlank() }
                ?: return logReject("FCM orderId missing")

            val riderId = data["riderId"]?.takeIf { it.isNotBlank() }
                ?: return logReject("FCM riderId missing")

            val isPoolOffer = riderId.equals("ALL", ignoreCase = true) || riderId.equals("broadcast", ignoreCase = true) || riderId.equals("pool", ignoreCase = true)
            if (authenticatedRiderId != null && riderId != authenticatedRiderId && !isPoolOffer) {
                return logReject("FCM offer targeted to rider '$riderId' does not match authenticated rider '$authenticatedRiderId'")
            }

            val merchantName = data["merchantName"]?.takeIf { it.isNotBlank() }
                ?: data["merchant_name"]?.takeIf { it.isNotBlank() }
                ?: data["storeName"]?.takeIf { it.isNotBlank() }
                ?: "Merchant Partner"

            val merchantAddress = data["merchantAddress"]?.takeIf { it.isNotBlank() }
                ?: data["merchant_address"]?.takeIf { it.isNotBlank() }
                ?: "Merchant Hub"

            val merchantLat = data["merchantLat"]?.toDoubleOrNull() ?: 0.0
            val merchantLng = data["merchantLng"]?.toDoubleOrNull() ?: 0.0

            val offerStatus = data["status"] ?: "CREATED"
            val isAccepted = offerStatus.equals("ACCEPTED", ignoreCase = true) || offerStatus.equals("ASSIGNED", ignoreCase = true)

            val rawCustomerName = data["customerName"]?.takeIf { it.isNotBlank() } ?: "Customer"

            val rawCustomerAddress = data["customerAddress"]?.takeIf { it.isNotBlank() }
                ?: data["customer_address"]?.takeIf { it.isNotBlank() }
                ?: data["deliveryAddress"]?.takeIf { it.isNotBlank() }
                ?: "Delivery Address"

            val rawCustomerLat = data["customerLat"]?.toDoubleOrNull() ?: 0.0
            val rawCustomerLng = data["customerLng"]?.toDoubleOrNull() ?: 0.0

            val localityHint = data["locality"]?.takeIf { it.isNotBlank() }
                ?: data["dropLocality"]?.takeIf { it.isNotBlank() }
                ?: data["customerLocality"]?.takeIf { it.isNotBlank() }

            val customerName = if (isAccepted) rawCustomerName else "Customer"
            val customerAddress = if (isAccepted) rawCustomerAddress else extractCoarseLocality(null, localityHint)
            val customerLat = if (isAccepted) rawCustomerLat else 0.0
            val customerLng = if (isAccepted) rawCustomerLng else 0.0

            val isCodRaw = (data["isCod"] ?: data["is_cod"])?.trim()
            if (isCodRaw == null || !isCodRaw.equals("true", ignoreCase = true)) {
                Log.w(TAG, "Rejecting offer $offerId: deployment strictly enforces explicit true COD / Pay on Delivery.")
                return null
            }
            val isCod = true

            val paymentMethod = (data["paymentMethod"] ?: data["payment_method"] ?: data["paymentMode"] ?: data["payment_mode"] ?: "").trim().uppercase()
            if (paymentMethod.isNotBlank() && paymentMethod !in COD_PAYMENT_ALLOWLIST) {
                Log.w(TAG, "Rejecting offer $offerId: payment method $paymentMethod contradicts COD-only deployment.")
                return null
            }

            val parsedCodAmount = data["codAmount"]?.toDoubleOrNull() ?: data["cod_amount"]?.toDoubleOrNull()
            val parsedOrderTotal = data["orderTotal"]?.toDoubleOrNull()
                ?: data["order_total"]?.toDoubleOrNull()
                ?: data["totalAmount"]?.toDoubleOrNull()
                ?: data["total_amount"]?.toDoubleOrNull()
                ?: data["cartValue"]?.toDoubleOrNull()
            val codAmount = (parsedCodAmount?.takeIf { it > 0.0 })
                ?: (parsedOrderTotal?.takeIf { it > 0.0 })
            if (codAmount == null || codAmount <= 0.0) {
                Log.w(TAG, "Rejecting offer $offerId: positive COD amount is required in COD deployment.")
                return null
            }

            val orderTotal = parsedOrderTotal ?: codAmount

            val earningsAmount = data["earningsAmount"]?.toDoubleOrNull()
                ?: data["totalEarnings"]?.toDoubleOrNull()
                ?: data["payout"]?.toDoubleOrNull()
                ?: data["estimatedEarnings"]?.toDoubleOrNull()
                ?: 0.0

            val rawTotalDist = data["totalDistanceKm"]?.toDoubleOrNull() ?: data["total_distance_km"]?.toDoubleOrNull()
            val rawDuration = data["estimatedDurationMins"]?.toIntOrNull() ?: data["estimated_duration_mins"]?.toIntOrNull()
            val rawPickup = data["pickupDistanceKm"]?.toDoubleOrNull()
            val rawDelivery = data["deliveryDistanceKm"]?.toDoubleOrNull()
            val totalDistanceKm = rawTotalDist ?: (if (rawDelivery != null && rawPickup != null) rawDelivery + rawPickup else 0.0)
            val estimatedDurationMins = rawDuration ?: 0
            val deliveryDistanceKm = rawDelivery ?: totalDistanceKm
            val pickupDistanceKm = rawPickup

            val expiresAt = data["expiresAt"]?.toLongOrNull()
                ?: data["offerExpiresAt"]?.toLongOrNull()
                ?: (nowMs + 900000L)

            val offerCreatedAt = data["offerCreatedAt"]?.toLongOrNull()
                ?: data["createdAt"]?.toLongOrNull()
                ?: nowMs

            val serverTime = data["serverTime"]?.toLongOrNull() ?: nowMs

            val packageCount = (data["packageCount"]?.toIntOrNull()?.takeIf { it in 1..100 }
                ?: data["itemCount"]?.toIntOrNull()?.takeIf { it in 1..100 }
                ?: 1)

            return ServerOffer(
                offerId = offerId,
                eventId = eventId,
                notificationId = notificationId,
                deliveryId = deliveryId,
                orderId = orderId,
                riderId = riderId,
                status = offerStatus.trim().uppercase(),
                earningsAmount = earningsAmount,
                pickupDistanceKm = pickupDistanceKm,
                deliveryDistanceKm = deliveryDistanceKm,
                totalDistanceKm = totalDistanceKm,
                estimatedDurationMins = estimatedDurationMins,
                isCod = isCod,
                codAmount = codAmount,
                orderTotal = orderTotal,
                items = emptyList(),
                packageCount = packageCount,
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
