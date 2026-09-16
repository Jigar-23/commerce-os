package com.commerceos.rider.repository

import com.commerceos.rider.model.ActiveOfferResult
import com.commerceos.rider.model.DeliveryRouteResult
import com.commerceos.rider.model.RiderNotificationItem
import com.commerceos.rider.model.RiderProfile
import com.commerceos.rider.model.RoutePoint
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.model.ServerOffer
import com.commerceos.rider.model.TelemetryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RiderDeliveryRepository(
    private val baseUrlProvider: () -> String,
    private val authTokenProvider: () -> String
) {

    companion object {
        @Volatile
        var activeLocalSession: ServerDeliverySession? = null
    }

    suspend fun sendRiderOtp(phone: String): Result<String> = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        if (baseUrl.isBlank()) return@withContext Result.failure(Exception("Base URL empty"))

        val cleanDigits = phone.filter { it.isDigit() }.takeLast(10)
        val formattedPhone = if (phone.startsWith("+91")) phone else "+91$cleanDigits"

        // Stage 1: Try dedicated Rider OTP endpoint
        try {
            val url = URL("$baseUrl/api/v1/auth/rider/send-otp")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }
            val body = JSONObject().apply { put("phone", phone) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val challengeId = JSONObject(jsonStr).optString("challengeId", "")
                if (challengeId.isNotBlank()) {
                    return@withContext Result.success(challengeId)
                }
            }
        } catch (_: Exception) {
            // Fall through to unified gateway endpoint
        }

        // Stage 2: Try unified auth gateway endpoint (auto-onboards & sends SMS OTP)
        try {
            val url = URL("$baseUrl/api/v1/auth/send-otp")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
            }
            val body = JSONObject().apply {
                put("phone", formattedPhone)
                put("role", "ROLE_RIDER")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val challengeId = obj.optString("challengeId", "")
                if (challengeId.isNotBlank()) {
                    return@withContext Result.success(challengeId)
                }
            }
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            val errMsg = try { JSONObject(errStr).optString("message", errStr) } catch (_: Exception) { errStr }
            return@withContext Result.failure(Exception(errMsg))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun verifyRiderOtp(
        challengeId: String,
        phone: String,
        otp: String,
        name: String = "",
        vehicle: String = ""
    ): Result<Pair<String, RiderProfile>> = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlProvider().trimEnd('/')
        if (baseUrl.isBlank()) return@withContext Result.failure(Exception("Base URL empty"))

        val cleanDigits = phone.filter { it.isDigit() }.takeLast(10)
        val formattedPhone = if (phone.startsWith("+91")) phone else "+91$cleanDigits"

        // Stage 1: Try dedicated Rider verify endpoint
        try {
            val url = URL("$baseUrl/api/v1/auth/rider/verify-otp")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
            }
            val body = JSONObject().apply {
                put("challengeId", challengeId)
                put("phone", phone)
                put("otp", otp)
                put("name", name)
                put("vehicle", vehicle)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val pair = parseRiderAuthResponse(jsonStr, phone, name, vehicle)
                if (pair != null) return@withContext Result.success(pair)
            }
        } catch (_: Exception) {
            // Fall through to unified gateway verify
        }

        // Stage 2: Try unified auth gateway verify endpoint with ROLE_RIDER
        try {
            val url = URL("$baseUrl/api/v1/auth/verify-otp")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 6000
                readTimeout = 6000
            }
            val body = JSONObject().apply {
                put("challengeId", challengeId)
                put("phone", formattedPhone)
                put("otp", otp)
                put("role", "ROLE_RIDER")
                put("name", name.ifBlank { "Delivery Partner" })
                put("vehicle", vehicle.ifBlank { "Electric Scooter" })
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val pair = parseRiderAuthResponse(jsonStr, phone, name, vehicle)
                if (pair != null) return@withContext Result.success(pair)
            }
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            val errMsg = try { JSONObject(errStr).optString("message", errStr) } catch (_: Exception) { errStr }
            return@withContext Result.failure(Exception(errMsg))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun parseRiderAuthResponse(
        jsonStr: String,
        phone: String,
        fallbackName: String,
        fallbackVehicle: String
    ): Pair<String, RiderProfile>? {
        return try {
            val obj = JSONObject(jsonStr)
            val token = obj.optString("accessToken").ifBlank { obj.optString("token") }
            val riderObj = obj.optJSONObject("rider") ?: obj.optJSONObject("user")
            val cleanDigits = phone.filter { it.isDigit() }.takeLast(10)
            val riderId = riderObj?.optString("id")?.takeIf { it.isNotBlank() }
                ?: riderObj?.optString("riderId")?.takeIf { it.isNotBlank() }
                ?: obj.optString("riderId").takeIf { it.isNotBlank() }
                ?: "rdr_$cleanDigits"
            val riderName = riderObj?.optString("name")?.takeIf { it.isNotBlank() }
                ?: riderObj?.optString("full_name")?.takeIf { it.isNotBlank() }
                ?: fallbackName.ifBlank { "Delivery Partner" }
            val riderPhone = riderObj?.optString("phone")?.takeIf { it.isNotBlank() } ?: phone
            val riderVehicle = riderObj?.optString("vehicle")?.takeIf { it.isNotBlank() }
                ?: riderObj?.optString("vehicle_number")?.takeIf { it.isNotBlank() }
                ?: fallbackVehicle.ifBlank { "Electric Scooter" }
            val rating = if (riderObj != null && riderObj.has("rating") && !riderObj.isNull("rating")) riderObj.getDouble("rating") else 4.9
            val profile = RiderProfile(
                riderId = riderId,
                name = riderName,
                phone = riderPhone,
                vehicleNumber = riderVehicle,
                rating = rating,
                completedToday = riderObj?.optInt("completedToday", 0) ?: 0,
                earningsTodayFormatted = riderObj?.optString("earningsTodayFormatted", "₹0") ?: "₹0",
                shiftStatus = riderObj?.optString("shiftStatus", "ONLINE_AVAILABLE") ?: "ONLINE_AVAILABLE",
                assignedHub = riderObj?.optString("assignedHub", "Rewari Central Hub") ?: "Rewari Central Hub"
            )
            Pair(token, profile)
        } catch (_: Exception) {
            null
        }
    }

    private fun getCandidateUrls(): List<String> {
        val primary = baseUrlProvider().trimEnd('/')
        return if (primary.isNotBlank()) listOf(primary) else listOf("https://commerce-os-api.onrender.com")
    }

    private fun openCandidateConnection(base: String, path: String, method: String): HttpURLConnection {
        val cleanBase = base.trimEnd('/')
        val url = URL("$cleanBase$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        val token = authTokenProvider()
        if (token.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        return conn
    }

    private fun createConnection(path: String, method: String): HttpURLConnection {
        val baseUrl = baseUrlProvider().trimEnd('/')
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        val token = authTokenProvider()
        if (token.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        return conn
    }

    suspend fun fetchRiderProfile(): Result<RiderProfile> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/profile", "GET")
            val code = conn.responseCode
            if (code == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val rawObj = JSONObject(jsonStr)
                val json = rawObj.optJSONObject("rider") ?: rawObj.optJSONObject("profile") ?: rawObj
                val riderId = json.optString("riderId").takeIf { it.isNotBlank() }
                    ?: json.optString("id").takeIf { it.isNotBlank() }
                    ?: "rdr_active"
                val name = json.optString("name").takeIf { it.isNotBlank() }
                    ?: json.optString("full_name").takeIf { it.isNotBlank() }
                    ?: "Delivery Partner"
                val phone = json.optString("phone", "")
                val vehicle = json.optString("vehicleNumber", json.optString("vehicle", json.optString("vehicle_number", "Electric Scooter")))
                val rating = if (json.has("rating") && !json.isNull("rating")) json.getDouble("rating") else 4.9
                val completedToday = if (json.has("completedToday") && !json.isNull("completedToday")) json.getInt("completedToday") else json.optInt("completed_today", 0)
                val earningsTodayFormatted = json.optString("earningsTodayFormatted", json.optString("earnings_today", "₹0"))
                val shiftStatus = json.optString("shiftStatus", json.optString("status", "ONLINE_AVAILABLE"))
                val assignedHub = json.optString("assignedHub", json.optString("assigned_hub", "Central Hub - Koramangala"))
                val profile = RiderProfile(
                    riderId = riderId,
                    name = name,
                    phone = phone,
                    vehicleNumber = vehicle,
                    rating = rating,
                    completedToday = completedToday,
                    earningsTodayFormatted = if (earningsTodayFormatted.startsWith("₹")) earningsTodayFormatted else "₹$earningsTodayFormatted",
                    shiftStatus = shiftStatus,
                    assignedHub = assignedHub
                )
                return@withContext Result.success(profile)
            }
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            return@withContext Result.failure(Exception("Failed to fetch profile (HTTP $code): $errStr"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun updateShiftStatus(isOnline: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/shift-status", "POST")
            conn.doOutput = true
            val body = JSONObject().apply {
                put("status", if (isOnline) "ONLINE_AVAILABLE" else "OFFLINE")
                put("isOnline", isOnline)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                return@withContext Result.success(isOnline)
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            return@withContext Result.failure(Exception("Failed to update shift status: $err"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun registerDeviceToken(fcmToken: String, deviceId: String = "android_rider_app"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/device-token", "POST")
            conn.doOutput = true
            val body = JSONObject().apply {
                put("fcmToken", fcmToken)
                put("deviceId", deviceId)
                put("platform", "ANDROID")
                put("appVersion", "2.0.0")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                return@withContext Result.success(true)
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            return@withContext Result.failure(Exception("Failed to register device token: $err"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun logoutDeviceToken(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/device-token/logout", "POST")
            conn.doOutput = true
            if (conn.responseCode in 200..299) {
                return@withContext Result.success(true)
            }
            return@withContext Result.failure(Exception("Failed to logout device token"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun fetchActiveOffer(): ActiveOfferResult = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/offers/active", "GET")
            val code = conn.responseCode
            if (code == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val offersArr = obj.optJSONArray("offers")
                if (offersArr != null) {
                    if (offersArr.length() == 0) {
                        return@withContext ActiveOfferResult.None
                    }
                    val firstOffer = offersArr.getJSONObject(0)
                    val orderStatus = firstOffer.optString("orderStatus", firstOffer.optString("order_status", ""))
                    if (orderStatus == "PLACED" || orderStatus == "PENDING" || orderStatus == "PRESCRIPTION_VERIFICATION_PENDING") {
                        return@withContext ActiveOfferResult.None
                    }
                    val offer = parseOfferJson(firstOffer) ?: return@withContext ActiveOfferResult.None
                    return@withContext ActiveOfferResult.Success(offer)
                }
                if (obj.has("offerId") || obj.has("id")) {
                    val orderStatus = obj.optString("orderStatus", obj.optString("order_status", ""))
                    if (orderStatus == "PLACED" || orderStatus == "PENDING" || orderStatus == "PRESCRIPTION_VERIFICATION_PENDING") {
                        return@withContext ActiveOfferResult.None
                    }
                    val offer = parseOfferJson(obj) ?: return@withContext ActiveOfferResult.None
                    return@withContext ActiveOfferResult.Success(offer)
                }
                return@withContext ActiveOfferResult.None
            } else if (code == 404) {
                return@withContext ActiveOfferResult.None
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                return@withContext ActiveOfferResult.Error("Server error $err")
            }
        } catch (e: Exception) {
            return@withContext ActiveOfferResult.Error(e.message ?: "Network error fetching active offer", e)
        }
    }

    suspend fun fetchOfferById(offerId: String): ActiveOfferResult = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/offers/$offerId", "GET")
            val code = conn.responseCode
            if (code == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val offer = parseOfferJson(obj)
                return@withContext ActiveOfferResult.Success(offer)
            } else if (code == 404 || code == 409) {
                return@withContext ActiveOfferResult.None
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                return@withContext ActiveOfferResult.Error("Server error $err")
            }
        } catch (e: Exception) {
            return@withContext ActiveOfferResult.Error(e.message ?: "Network error fetching offer", e)
        }
    }

    suspend fun ackOffer(offerId: String, status: String = "DISPLAYED"): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/offers/$offerId/ack", "POST")
            conn.doOutput = true
            val body = JSONObject().apply { put("status", status) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) return@withContext Result.success(true)
            return@withContext Result.failure(Exception("Ack failed"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun acceptOffer(offerId: String, fallbackOffer: ServerOffer? = null): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val urlsToTry = mutableListOf<String>()
            val primary = baseUrlProvider().trimEnd('/')
            if (primary.isNotBlank() && !primary.contains("127.0.0.1") && !primary.contains("localhost")) {
                urlsToTry.add(primary)
            }
            if (!urlsToTry.contains("https://commerce-os-api.onrender.com")) {
                urlsToTry.add("https://commerce-os-api.onrender.com")
            }

            var lastCode = 0
            var lastErrStr = ""

            for (base in urlsToTry) {
                try {
                    val conn = (URL("$base/api/v1/delivery/offers/$offerId/accept").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        val token = authTokenProvider()
                        if (token.isNotBlank()) {
                            setRequestProperty("Authorization", "Bearer $token")
                        }
                        connectTimeout = 3000
                        readTimeout = 3000
                        doOutput = true
                    }
                    conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    lastCode = code
                    val responseStr = if (code in 200..299) {
                        conn.inputStream.bufferedReader().use { it.readText() }
                    } else {
                        conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    lastErrStr = responseStr

                    if (code in 200..299) {
                        val json = JSONObject(responseStr)
                        val sessionJson = json.optJSONObject("session") ?: json
                        var session = parseSessionJson(sessionJson)

                        if (fallbackOffer != null && (session.customerAddress.isBlank() || session.merchantAddress.isBlank() || session.customerLat == null)) {
                            session = session.copy(
                                customerId = session.customerId.ifBlank { "cust_" + fallbackOffer.orderId },
                                customerName = session.customerName.ifBlank { fallbackOffer.customerName },
                                customerAddress = session.customerAddress.ifBlank { fallbackOffer.customerAddress },
                                customerLat = session.customerLat ?: fallbackOffer.customerLat,
                                customerLng = session.customerLng ?: fallbackOffer.customerLng,
                                merchantName = session.merchantName.ifBlank { fallbackOffer.merchantName },
                                merchantAddress = session.merchantAddress.ifBlank { fallbackOffer.merchantAddress },
                                merchantLat = session.merchantLat ?: fallbackOffer.merchantLat,
                                merchantLng = session.merchantLng ?: fallbackOffer.merchantLng,
                                payoutFormatted = session.payoutFormatted ?: "₹${fallbackOffer.earningsAmount.toInt()}",
                                distanceKm = session.distanceKm ?: fallbackOffer.totalDistanceKm,
                                estimatedTimeMins = session.estimatedTimeMins ?: fallbackOffer.estimatedDurationMins,
                                isCod = session.isCod || fallbackOffer.isCod,
                                codAmount = session.codAmount ?: (if (fallbackOffer.isCod) fallbackOffer.codAmount else null),
                                orderTotal = session.orderTotal ?: fallbackOffer.orderTotal,
                                items = if (session.items.isNotEmpty()) session.items else fallbackOffer.items
                            )
                        }
                        activeLocalSession = session
                        return@withContext Result.success(session)
                    }

                    if (code == 409) {
                        break
                    }
                } catch (e: Exception) {
                    lastErrStr = e.message ?: "Connection error"
                }
            }

            val errObj = try { JSONObject(lastErrStr) } catch (e: Exception) { null }
            val errMsg = errObj?.optString("message", "Offer expired or claimed by another rider") ?: "Offer acceptance failed"
            return@withContext Result.failure(Exception(errMsg))
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }

    suspend fun declineOffer(offerId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (activeLocalSession?.deliveryId == offerId || activeLocalSession?.orderId == offerId) {
                activeLocalSession = null
            }
            val conn = createConnection("/api/v1/delivery/offers/$offerId/decline", "POST")
            conn.doOutput = true
            if (conn.responseCode in 200..299 || conn.responseCode == 404 || conn.responseCode == 409) {
                return@withContext Result.success(true)
            }
            return@withContext Result.failure(Exception("Decline failed"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun fetchNotifications(category: String = "ALL"): List<RiderNotificationItem> = withContext(Dispatchers.IO) {
        try {
            val urlStr = if (category != "ALL") "/api/v1/delivery/rider/notifications?category=$category" else "/api/v1/delivery/rider/notifications"
            val conn = createConnection(urlStr, "GET")
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val array = obj.optJSONArray("notifications") ?: org.json.JSONArray()
                val list = mutableListOf<RiderNotificationItem>()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val rId = item.optString("riderId").ifBlank { item.optString("rider_id") }.ifBlank { "rider_self" }
                    val notifId = item.optString("notificationId").ifBlank { item.optString("id", UUID.randomUUID().toString()) }
                    val offStatus = item.optString("offerStatus").takeIf { it.isNotBlank() } ?: item.optString("offer_status").takeIf { it.isNotBlank() }
                    val ordStatus = item.optString("orderStatus").takeIf { it.isNotBlank() } ?: item.optString("order_status").takeIf { it.isNotBlank() }
                    val ordRiderId = item.optString("orderRiderId").takeIf { it.isNotBlank() } ?: item.optString("order_rider_id").takeIf { it.isNotBlank() }
                    val categoryVal = item.optString("category", "ORDERS")
                    val typeVal = item.optString("type", "ORDER_OFFER")
                    val offerIdVal = item.optString("offerId").takeIf { it.isNotBlank() }

                    val isOrderOffer = categoryVal.equals("ORDERS", ignoreCase = true) ||
                                       typeVal.contains("OFFER", ignoreCase = true) ||
                                       !offerIdVal.isNullOrBlank()

                    val isTaken = offStatus in listOf("ACCEPTED", "CLAIMED_BY_OTHER", "EXPIRED", "CANCELLED", "DECLINED") ||
                                  ordStatus in listOf("RIDER_ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED") ||
                                  (!ordRiderId.isNullOrBlank() && ordRiderId != rId)

                    val isAvailableVal = if (isOrderOffer) !isTaken else null

                    list.add(
                        RiderNotificationItem(
                            notificationId = notifId,
                            eventId = item.optString("eventId").ifBlank { notifId },
                            type = typeVal,
                            category = categoryVal,
                            priority = item.optString("priority", "HIGH"),
                            riderId = rId,
                            orderId = item.optString("orderId").takeIf { it.isNotBlank() },
                            deliveryId = item.optString("deliveryId").takeIf { it.isNotBlank() },
                            offerId = offerIdVal,
                            title = item.optString("title", "New Job Alert"),
                            body = item.optString("body", "New order offer received"),
                            deepLink = item.optString("deepLink").takeIf { it.isNotBlank() },
                            createdAt = item.optString("createdAt", ""),
                            expiresAt = if (item.has("expiresAt") && !item.isNull("expiresAt")) item.getLong("expiresAt") else null,
                            readAt = if (item.has("readAt") && !item.isNull("readAt")) item.getString("readAt") else null,
                            offerStatus = offStatus,
                            orderStatus = ordStatus,
                            orderRiderId = ordRiderId,
                            isAvailable = isAvailableVal
                        )
                    )
                }
                return@withContext list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }

    suspend fun markNotificationRead(notifId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/notifications/$notifId/read", "POST")
            conn.doOutput = true
            if (conn.responseCode in 200..299) return@withContext Result.success(true)
            return@withContext Result.failure(Exception("Failed to mark notification read"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun markAllNotificationsRead(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/notifications/read-all", "POST")
            conn.doOutput = true
            if (conn.responseCode in 200..299) return@withContext Result.success(true)
            return@withContext Result.failure(Exception("Failed to mark all read"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun fetchActiveSession(): ServerDeliverySession? = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/active-session", "GET")
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val sessionObj = obj.optJSONObject("session")
                if (sessionObj != null) {
                    val s = parseSessionJson(sessionObj)
                    if (s != null && s.state !in listOf("CANCELLED", "DECLINED", "DELIVERED")) {
                        activeLocalSession = s
                        return@withContext s
                    }
                }
                if (obj.optBoolean("active", true) && obj.has("deliveryId")) {
                    val s = parseSessionJson(obj)
                    if (s != null && s.state !in listOf("CANCELLED", "DECLINED", "DELIVERED")) {
                        activeLocalSession = s
                        return@withContext s
                    }
                }
                activeLocalSession = null
                return@withContext null
            } else if (conn.responseCode == 404) {
                activeLocalSession = null
                return@withContext null
            }
        } catch (e: Exception) {
            // Network failure: fall back to local session if still active
            val local = activeLocalSession
            if (local != null && local.state !in listOf("CANCELLED", "DECLINED", "DELIVERED")) {
                return@withContext local
            }
        }
        return@withContext null
    }

    suspend fun fetchSession(orderId: String): ServerDeliverySession? = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/order/$orderId", "GET")
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext parseSessionJson(JSONObject(jsonStr))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun fetchTrips(): List<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/trips", "GET")
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val array = org.json.JSONArray(jsonStr)
                val list = mutableListOf<ServerDeliverySession>()
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parseSessionJson(obj)?.let { list.add(it) }
                }
                return@withContext list
            }
        } catch (e: Exception) {
            // Trips fetch fallback
        }
        return@withContext emptyList()
    }



    suspend fun verifyOtp(deliveryId: String, otp: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanOtp = otp.trim()
        val urls = getCandidateUrls()
        var lastErr = "Incorrect OTP PIN"

        for (base in urls) {
            try {
                val conn = openCandidateConnection(base, "/api/v1/delivery/$deliveryId/verify-otp", "POST")
                conn.doOutput = true
                val body = JSONObject().apply { put("otp", cleanOtp) }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val responseStr = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                val json = try { JSONObject(responseStr) } catch (_: Exception) { JSONObject() }
                if (code in 200..299 && json.optBoolean("verified", true)) {
                    activeLocalSession = null
                    return@withContext Result.success(true)
                }
                if (code == 400) {
                    val msg = json.optString("message", "Incorrect OTP PIN")
                    return@withContext Result.failure(Exception(msg))
                }
                lastErr = json.optString("message", "Incorrect OTP PIN")
            } catch (e: Exception) {
                lastErr = e.message ?: "Network error"
            }
        }

        return@withContext Result.failure(Exception(lastErr))
    }

    suspend fun reconcileCod(deliveryId: String, collectedAmount: Double): Result<Boolean> = withContext(Dispatchers.IO) {
        val urls = getCandidateUrls()
        var lastErr = "COD reconciliation failed"

        for (base in urls) {
            try {
                val conn = openCandidateConnection(base, "/api/v1/delivery/$deliveryId/complete-cod", "POST")
                conn.doOutput = true
                val body = JSONObject().apply { put("collectedAmount", collectedAmount) }
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val responseStr = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                val json = try { JSONObject(responseStr) } catch (_: Exception) { JSONObject() }
                if (code in 200..299 && json.optBoolean("reconciled", true)) {
                    activeLocalSession = activeLocalSession?.copy(codReconciled = true, codCollectedAmount = collectedAmount)
                    return@withContext Result.success(true)
                }
                lastErr = json.optString("message", "COD reconciliation failed")
            } catch (e: Exception) {
                lastErr = e.message ?: "Network error"
            }
        }

        if (activeLocalSession != null) {
            activeLocalSession = activeLocalSession!!.copy(codReconciled = true, codCollectedAmount = collectedAmount)
            return@withContext Result.success(true)
        }
        return@withContext Result.failure(Exception(lastErr))
    }

    suspend fun resendOtp(deliveryId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/$deliveryId/resend-otp", "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }

            val responseStr = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream.bufferedReader().use { it.readText() }
            }

            val json = JSONObject(responseStr)
            if (conn.responseCode == 200 && json.optBoolean("ok")) {
                return@withContext Result.success(true)
            } else {
                val msg = json.optString("message", "Resend OTP failed")
                return@withContext Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun reportIssue(deliveryId: String, issueType: String, note: String = ""): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/$deliveryId/report-issue", "POST")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("issueType", issueType)
                put("note", note)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseStr = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream.bufferedReader().use { it.readText() }
            }

            val json = JSONObject(responseStr)
            if (conn.responseCode == 200 && json.has("session")) {
                val session = parseSessionJson(json.getJSONObject("session"))
                return@withContext Result.success(session)
            } else {
                val msg = json.optString("message", "Reporting issue failed")
                return@withContext Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun cancelDelivery(deliveryId: String, reason: String, note: String = ""): Result<Boolean> = withContext(Dispatchers.IO) {
        activeLocalSession = null
        try {
            val conn = createConnection("/api/v1/delivery/$deliveryId/cancel", "POST")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("reason", reason)
                put("note", note)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseStr = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            activeLocalSession = null
            if (conn.responseCode in 200..299 || conn.responseCode == 404 || conn.responseCode == 429) {
                return@withContext Result.success(true)
            } else {
                val json = try { JSONObject(responseStr) } catch (e: Exception) { null }
                val msg = json?.optString("message", "Cancelling delivery failed") ?: "Cancellation failed"
                return@withContext Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            activeLocalSession = null
            return@withContext Result.success(true)
        }
    }

    suspend fun completeDelivery(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        val urls = getCandidateUrls()
        var lastErr = "Completion failed"

        for (base in urls) {
            try {
                val conn = openCandidateConnection(base, "/api/v1/delivery/$deliveryId/complete", "POST")
                conn.doOutput = true
                conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val responseStr = if (code in 200..299) {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                val json = try { JSONObject(responseStr) } catch (_: Exception) { JSONObject() }
                if (code in 200..299 && json.has("session")) {
                    val session = parseSessionJson(json.getJSONObject("session"))
                    activeLocalSession = null
                    return@withContext Result.success(session)
                }
                lastErr = json.optString("message", "Completion failed")
            } catch (e: Exception) {
                lastErr = e.message ?: "Network error"
            }
        }

        return@withContext Result.failure(Exception("Completion failed: $lastErr"))
    }

    suspend fun arriveMerchant(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        val urls = getCandidateUrls()
        var lastErr = "Arrive store failed"

        for (base in urls) {
            try {
                val conn = openCandidateConnection(base, "/api/v1/delivery/session/$deliveryId/arrive-merchant", "POST")
                conn.doOutput = true
                conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val session = parseSessionJson(JSONObject(jsonStr))
                    activeLocalSession = session
                    return@withContext Result.success(session)
                }
                val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                lastErr = errStr
            } catch (e: Exception) {
                lastErr = e.message ?: "Network error"
            }
        }

        return@withContext Result.failure(Exception("Arrive store failed: $lastErr"))
    }

    suspend fun pickupFromMerchant(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        val urls = getCandidateUrls()
        var lastErr = "Pickup confirmation failed"

        for (base in urls) {
            try {
                val conn = openCandidateConnection(base, "/api/v1/delivery/session/$deliveryId/pickup", "POST")
                conn.doOutput = true
                conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val session = parseSessionJson(JSONObject(jsonStr))
                    activeLocalSession = session
                    return@withContext Result.success(session)
                }
                val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                lastErr = errStr
            } catch (e: Exception) {
                lastErr = e.message ?: "Network error"
            }
        }

        return@withContext Result.failure(Exception("Pickup confirmation failed: $lastErr"))
    }

    suspend fun arriveCustomer(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        val urls = getCandidateUrls()
        var lastErr = "Arrive customer failed"

        for (base in urls) {
            try {
                val conn = openCandidateConnection(base, "/api/v1/delivery/session/$deliveryId/arrive-customer", "POST")
                conn.doOutput = true
                conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val session = parseSessionJson(JSONObject(jsonStr))
                    activeLocalSession = session
                    return@withContext Result.success(session)
                }
                val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                lastErr = errStr
            } catch (e: Exception) {
                lastErr = e.message ?: "Network error"
            }
        }

        return@withContext Result.failure(Exception("Arrive customer failed: $lastErr"))
    }

    suspend fun fetchRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<DeliveryRouteResult> = withContext(Dispatchers.IO) {
        // 1. Try via Commerce OS Server
        try {
            val path = "/api/v1/delivery/route?originLat=$originLat&originLng=$originLng&destLat=$destLat&destLng=$destLng"
            val conn = createConnection(path, "GET")
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val distKm = json.getDouble("distanceKm")
                val durMins = json.getInt("durationMins")
                val provider = json.optString("provider", "OSRM_OPENSTREETMAP")
                val waypointsArray = json.getJSONArray("waypoints")
                val waypoints = mutableListOf<RoutePoint>()
                for (i in 0 until waypointsArray.length()) {
                    val pt = waypointsArray.getJSONObject(i)
                    waypoints.add(RoutePoint(lat = pt.getDouble("lat"), lng = pt.getDouble("lng")))
                }
                if (waypoints.size >= 2) {
                    return@withContext Result.success(
                        DeliveryRouteResult(
                            distanceKm = distKm,
                            durationMins = durMins,
                            waypoints = waypoints,
                            provider = provider
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Direct HTTPS fallback to OSRM OpenStreetMap routing service
        try {
            val osrmUrl = java.net.URL("https://router.project-osrm.org/route/v1/driving/$originLng,$originLat;$destLng,$destLat?overview=full&geometries=geojson")
            val conn = (osrmUrl.openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "CommerceOS-Rider/2.0")
            }
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val routes = json.getJSONArray("routes")
                if (routes.length() > 0) {
                    val routeObj = routes.getJSONObject(0)
                    val distMeters = routeObj.getDouble("distance")
                    val durSeconds = routeObj.getDouble("duration")
                    val geom = routeObj.getJSONObject("geometry")
                    val coords = geom.getJSONArray("coordinates")
                    val waypoints = mutableListOf<RoutePoint>()
                    for (i in 0 until coords.length()) {
                        val c = coords.getJSONArray(i)
                        waypoints.add(RoutePoint(lat = c.getDouble(1), lng = c.getDouble(0)))
                    }
                    if (waypoints.size >= 2) {
                        return@withContext Result.success(
                            DeliveryRouteResult(
                                distanceKm = Math.round((distMeters / 1000.0) * 10.0) / 10.0,
                                durationMins = Math.max(1, (durSeconds / 60.0).toInt()),
                                waypoints = waypoints,
                                provider = "OSRM_DIRECT"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        return@withContext Result.failure(Exception("Road route geometry calculation unavailable"))
    }

    private fun parseSessionJson(json: JSONObject): ServerDeliverySession {
        val stateStr = json.optStringFirst("state", "deliveryStatus", "delivery_status", "status").ifEmpty { "ASSIGNED" }

        val telemObj = json.optJSONObject("telemetry")
        val telemetry = if (telemObj != null && telemObj.has("latitude") && telemObj.has("longitude")) {
            TelemetryState(
                latitude = telemObj.getDouble("latitude"),
                longitude = telemObj.getDouble("longitude"),
                speedKmh = telemObj.optDouble("speedKmh", 0.0).toFloat(),
                heading = if (telemObj.has("heading") && !telemObj.isNull("heading")) telemObj.getDouble("heading").toFloat() else null,
                accuracyMeters = telemObj.optDouble("accuracyMeters", 0.0).toFloat(),
                sequenceNumber = telemObj.optLong("sequenceNumber", 0L),
                serverTimestamp = telemObj.optLong("serverTimestamp", 0L),
                clientTimestamp = telemObj.optLong("clientTimestamp", 0L),
                isStale = telemObj.optBoolean("isStale", false)
            )
        } else {
            null
        }

        val distKm = json.optDoubleOrNull("distanceKm", "distance_km", "total_distance_km")
        val estMins = json.optIntOrNull("estimatedTimeMins", "estimated_time_mins", "estimated_duration_mins", "remaining_duration_mins")

        val deliveryId = json.optStringFirst("deliveryId", "delivery_id", "id").ifEmpty { "del_active" }
        val orderId = json.optStringFirst("orderId", "order_id").ifEmpty { "ord_active" }

        val cLat = json.optDoubleOrNull("customerLat", "customer_lat", "destLat", "dest_lat")?.takeIf { !it.isNaN() && it != 0.0 }
        val cLng = json.optDoubleOrNull("customerLng", "customer_lng", "destLng", "dest_lng")?.takeIf { !it.isNaN() && it != 0.0 }
        val mLat = json.optDoubleOrNull("merchantLat", "merchant_lat", "storeLat", "store_lat")?.takeIf { !it.isNaN() && it != 0.0 }
        val mLng = json.optDoubleOrNull("merchantLng", "merchant_lng", "storeLng", "store_lng")?.takeIf { !it.isNaN() && it != 0.0 }
        val isCod = json.optBoolean("isCod", json.optBoolean("is_cod", false))

        return ServerDeliverySession(
            deliveryId = deliveryId,
            orderId = orderId,
            riderId = json.optStringFirst("riderId", "rider_id"),
            riderName = json.optStringFirst("riderName", "rider_name"),
            riderPhone = json.optStringFirst("riderPhone", "rider_phone"),
            riderVehicle = json.optStringFirst("riderVehicle", "rider_vehicle", "vehicleNumber", "vehicle_number"),
            customerId = json.optStringFirst("customerId", "customer_id"),
            customerName = json.optStringFirst("customerName", "customer_name"),
            customerPhone = json.optStringFirst("customerPhone", "customer_phone"),
            customerAddress = json.optStringFirst("customerAddress", "customer_address", "deliveryAddress", "delivery_address"),
            customerLat = cLat,
            customerLng = cLng,
            merchantName = json.optStringFirst("merchantName", "merchant_name", "storeName", "store_name", "merchant"),
            merchantAddress = json.optStringFirst("merchantAddress", "merchant_address", "storeAddress", "store_address"),
            merchantLat = mLat,
            merchantLng = mLng,
            merchantPhone = json.optStringFirst("merchantPhone", "merchant_phone", "storePhone", "store_phone"),
            payoutFormatted = json.optStringFirst("payoutFormatted", "payout_formatted", "payout", "earnings").takeIf { it.isNotBlank() },
            distanceKm = distKm,
            estimatedTimeMins = estMins,
            state = stateStr,
            otpAttemptsLeft = json.optIntOrNull("otpAttemptsLeft", "otp_attempts_left") ?: 3,
            otpVerified = json.optBoolean("otpVerified", json.optBoolean("otp_verified", false)),
            isCod = isCod,
            codAmount = if (isCod) json.optDoubleOrNull("codAmount", "cod_amount") else null,
            codCollectedAmount = if (isCod) json.optDoubleOrNull("codCollectedAmount", "cod_collected_amount") else null,
            codReconciled = json.optBoolean("codReconciled", json.optBoolean("cod_reconciled", false)),
            orderTotal = json.optDoubleOrNull("orderTotal", "order_total", "totalAmount", "total_amount", "cartValue") ?: (if (isCod) json.optDoubleOrNull("codAmount", "cod_amount") else null),
            items = mutableListOf<com.commerceos.rider.model.RiderOrderItem>().apply {
                val arr = json.optJSONArray("items")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val itObj = arr.optJSONObject(i)
                        if (itObj != null) {
                            val name = itObj.optStringFirst("name", "productName", "product_name").ifEmpty { "Item" }
                            val qty = itObj.optIntOrNull("quantity", "qty") ?: 1
                            val price = itObj.optDoubleOrNull("price", "unitPrice", "unit_price") ?: 0.0
                            val sku = itObj.optStringFirst("sku", "productId", "product_id")
                            add(com.commerceos.rider.model.RiderOrderItem(name = name, quantity = qty, price = price, sku = sku))
                        }
                    }
                }
            },
            telemetry = telemetry,
            history = emptyList()
        )
    }

    private fun parseOfferJson(json: JSONObject): ServerOffer {
        return com.commerceos.rider.model.OfferPayloadValidator.parseAndValidate(json)
            ?: throw IllegalArgumentException("INVALID_OFFER_PAYLOAD: Mandatory authoritative offer fields missing or invalid.")
    }

    suspend fun listenRiderSseStream(onOfferJsonReceived: (JSONObject) -> Unit) = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/stream", "GET")
            conn.setRequestProperty("Accept", "text/event-stream")
            if (conn.responseCode == 200) {
                val reader = conn.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line?.startsWith("data:") == true) {
                        val jsonStr = line!!.substring(5).trim()
                        if (jsonStr.isNotBlank()) {
                            try {
                                val obj = JSONObject(jsonStr)
                                val offerObj = obj.optJSONObject("data") ?: obj.optJSONObject("offer") ?: if (obj.has("offerId")) obj else null
                                if (offerObj != null && (offerObj.has("offerId") || offerObj.has("id"))) {
                                    onOfferJsonReceived(offerObj)
                                }
                            } catch (e: Exception) {
                                // Skip unparseable or non-offer events
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Stream disconnected
        }
    }

    private fun JSONObject.optDoubleOrNull(vararg keys: String): Double? {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                try {
                    val v = getDouble(k)
                    if (!v.isNaN()) return v
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun JSONObject.optIntOrNull(vararg keys: String): Int? {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                try {
                    return getInt(k)
                } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun JSONObject.optStringFirst(vararg keys: String): String {
        for (k in keys) {
            if (has(k) && !isNull(k)) {
                val s = optString(k, "").trim()
                if (s.isNotEmpty()) return s
            }
        }
        return ""
    }
}
