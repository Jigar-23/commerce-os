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

    suspend fun loginAsRider(riderId: String = "rdr_rewari_01", phone: String = "+919876543210"): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = baseUrlProvider().trimEnd('/')
            if (baseUrl.isBlank()) return@withContext Result.failure(Exception("Base URL empty"))
            val url = URL("$baseUrl/api/v1/auth/rider/login")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val body = JSONObject().apply {
                put("riderId", riderId)
                put("phone", phone)
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val token = obj.optString("accessToken", "")
                if (token.isNotBlank()) {
                    return@withContext Result.success(token)
                }
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            return@withContext Result.failure(Exception("Rider login failed: $err"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun createConnection(path: String, method: String): HttpURLConnection {
        val baseUrl = baseUrlProvider().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw IllegalStateException("API base URL is not configured.")
        }
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Content-Type", "application/json")
        val token = authTokenProvider().trim()
        if (token.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        }
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        return conn
    }

    suspend fun fetchRiderProfile(): Result<RiderProfile> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/rider/profile", "GET")
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val rating = if (json.has("rating") && !json.isNull("rating")) json.getDouble("rating") else null
                val completedToday = if (json.has("completedToday") && !json.isNull("completedToday")) json.getInt("completedToday") else null
                val earningsTodayFormatted = json.optString("earningsTodayFormatted", "").takeIf { it.isNotBlank() }
                val shiftStatus = json.optString("shiftStatus", "").takeIf { it.isNotBlank() }
                val assignedHub = json.optString("assignedHub", "").takeIf { it.isNotBlank() }
                val tier = json.optString("tier", "").takeIf { it.isNotBlank() }
                val profile = RiderProfile(
                    riderId = json.getString("riderId"),
                    name = json.getString("name"),
                    phone = json.optString("phone", ""),
                    vehicleNumber = json.optString("vehicleNumber", ""),
                    rating = rating,
                    completedToday = completedToday,
                    earningsTodayFormatted = earningsTodayFormatted,
                    shiftStatus = shiftStatus,
                    assignedHub = assignedHub,
                    tier = tier
                )
                return@withContext Result.success(profile)
            }
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            return@withContext Result.failure(Exception("Failed to fetch profile: $err"))
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
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                val offer = parseOfferJson(obj)
                return@withContext ActiveOfferResult.Success(offer)
            } else if (conn.responseCode == 404) {
                return@withContext ActiveOfferResult.None
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
                return@withContext ActiveOfferResult.Error("Server error $err")
            }
        } catch (e: Exception) {
            return@withContext ActiveOfferResult.Error(e.message ?: "Network error fetching active offer", e)
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

    suspend fun acceptOffer(offerId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/offers/$offerId/accept", "POST")
            conn.doOutput = true
            val responseStr = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (conn.responseCode in 200..299) {
                val json = JSONObject(responseStr)
                val sessionJson = json.optJSONObject("session") ?: json
                val session = parseSessionJson(sessionJson)
                return@withContext Result.success(session)
            }
            val errObj = try { JSONObject(responseStr) } catch (e: Exception) { null }
            val errMsg = errObj?.optString("message", "Offer expired or claimed by another rider") ?: "Offer acceptance failed"
            return@withContext Result.failure(Exception(errMsg))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun declineOffer(offerId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/offers/$offerId/decline", "POST")
            conn.doOutput = true
            if (conn.responseCode in 200..299) return@withContext Result.success(true)
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
                    val notifId = item.optString("notificationId").ifBlank { item.optString("id", UUID.randomUUID().toString()) }
                    list.add(
                        RiderNotificationItem(
                            notificationId = notifId,
                            eventId = item.optString("eventId").ifBlank { notifId },
                            type = item.optString("type", "ORDER_OFFER"),
                            category = item.optString("category", "ORDERS"),
                            priority = item.optString("priority", "HIGH"),
                            riderId = item.optString("riderId", "rdr_rewari_01"),
                            orderId = item.optString("orderId").takeIf { it.isNotBlank() },
                            deliveryId = item.optString("deliveryId").takeIf { it.isNotBlank() },
                            offerId = item.optString("offerId").takeIf { it.isNotBlank() },
                            title = item.optString("title", "New Job Alert"),
                            body = item.optString("body", "New order offer received"),
                            deepLink = item.optString("deepLink").takeIf { it.isNotBlank() },
                            createdAt = item.optString("createdAt", ""),
                            expiresAt = if (item.has("expiresAt") && !item.isNull("expiresAt")) item.getLong("expiresAt") else null,
                            readAt = if (item.has("readAt") && !item.isNull("readAt")) item.getString("readAt") else null
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
                return@withContext parseSessionJson(JSONObject(jsonStr))
            }
        } catch (e: Exception) {
            // No active session
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

    suspend fun transitionState(
        deliveryId: String,
        targetState: String,
        idempotencyKey: String = UUID.randomUUID().toString()
    ): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/$deliveryId/transition", "POST")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("targetState", targetState)
                put("idempotencyKey", idempotencyKey)
            }

            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseStr = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream.bufferedReader().use { it.readText() }
            }

            val json = JSONObject(responseStr)
            if (conn.responseCode == 200) {
                val session = parseSessionJson(json)
                return@withContext Result.success(session)
            } else {
                val errMessage = json.optString("message", "Transition rejected")
                return@withContext Result.failure(Exception(errMessage))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun verifyOtp(deliveryId: String, otp: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/$deliveryId/verify-otp", "POST")
            conn.doOutput = true

            val body = JSONObject().apply { put("otp", otp) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseStr = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream.bufferedReader().use { it.readText() }
            }

            val json = JSONObject(responseStr)
            if (conn.responseCode == 200 && json.optBoolean("verified")) {
                return@withContext Result.success(true)
            } else {
                val msg = json.optString("message", "Incorrect OTP PIN")
                return@withContext Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun reconcileCod(deliveryId: String, collectedAmount: Double): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/$deliveryId/complete-cod", "POST")
            conn.doOutput = true

            val body = JSONObject().apply { put("collectedAmount", collectedAmount) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val responseStr = if (conn.responseCode in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream.bufferedReader().use { it.readText() }
            }

            val json = JSONObject(responseStr)
            if (conn.responseCode == 200 && json.optBoolean("reconciled")) {
                return@withContext Result.success(true)
            } else {
                val msg = json.optString("message", "COD reconciliation failed")
                return@withContext Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
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

            if (conn.responseCode in 200..299) {
                return@withContext Result.success(true)
            } else {
                val json = try { JSONObject(responseStr) } catch (e: Exception) { null }
                val msg = json?.optString("message", "Cancelling delivery failed") ?: "Cancellation failed"
                return@withContext Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun completeDelivery(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/$deliveryId/complete", "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }

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
                val msg = json.optString("message", "Completion failed")
                return@withContext Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun arriveMerchant(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/session/$deliveryId/arrive-merchant", "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext Result.success(parseSessionJson(JSONObject(jsonStr)))
            }
            return@withContext transitionState(deliveryId, "ARRIVED_PICKUP")
        } catch (e: Exception) {
            return@withContext transitionState(deliveryId, "ARRIVED_PICKUP")
        }
    }

    suspend fun pickupFromMerchant(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/session/$deliveryId/pickup", "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext Result.success(parseSessionJson(JSONObject(jsonStr)))
            }
            return@withContext transitionState(deliveryId, "EN_ROUTE_CUSTOMER")
        } catch (e: Exception) {
            return@withContext transitionState(deliveryId, "EN_ROUTE_CUSTOMER")
        }
    }

    suspend fun arriveCustomer(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/session/$deliveryId/arrive-customer", "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext Result.success(parseSessionJson(JSONObject(jsonStr)))
            }
            return@withContext transitionState(deliveryId, "HANDOFF_STARTED")
        } catch (e: Exception) {
            return@withContext transitionState(deliveryId, "HANDOFF_STARTED")
        }
    }

    suspend fun fetchRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): Result<DeliveryRouteResult> = withContext(Dispatchers.IO) {
        try {
            val path = "/api/v1/delivery/route?originLat=$originLat&originLng=$originLng&destLat=$destLat&destLng=$destLng"
            val conn = createConnection(path, "GET")
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
                return@withContext Result.success(
                    DeliveryRouteResult(
                        distanceKm = distKm,
                        durationMins = durMins,
                        waypoints = waypoints,
                        provider = provider
                    )
                )
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
                return@withContext Result.failure(Exception("Route unavailable: $err"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun parseSessionJson(json: JSONObject): ServerDeliverySession {
        val stateStr = json.optString("state", "")
        if (stateStr.isBlank()) {
            throw IllegalArgumentException("INVALID_DATA: Server session state cannot be empty")
        }

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

        val distKm = if (json.has("distanceKm") && !json.isNull("distanceKm")) json.getDouble("distanceKm") else null
        val estMins = if (json.has("estimatedTimeMins") && !json.isNull("estimatedTimeMins")) json.getInt("estimatedTimeMins") else null

        if (!json.has("deliveryId") || !json.has("orderId")) {
            throw IllegalArgumentException("INVALID_DATA: Delivery session missing mandatory deliveryId or orderId")
        }

        val cLat = if (json.has("customerLat") && !json.isNull("customerLat")) json.getDouble("customerLat").takeIf { !it.isNaN() && it != 0.0 } else null
        val cLng = if (json.has("customerLng") && !json.isNull("customerLng")) json.getDouble("customerLng").takeIf { !it.isNaN() && it != 0.0 } else null
        val mLat = if (json.has("merchantLat") && !json.isNull("merchantLat")) json.getDouble("merchantLat").takeIf { !it.isNaN() && it != 0.0 } else null
        val mLng = if (json.has("merchantLng") && !json.isNull("merchantLng")) json.getDouble("merchantLng").takeIf { !it.isNaN() && it != 0.0 } else null
        val isCod = json.optBoolean("isCod", false)

        return ServerDeliverySession(
            deliveryId = json.getString("deliveryId"),
            orderId = json.getString("orderId"),
            riderId = json.optString("riderId", ""),
            riderName = json.optString("riderName", ""),
            riderPhone = json.optString("riderPhone", ""),
            riderVehicle = json.optString("riderVehicle", ""),
            customerId = json.optString("customerId", ""),
            customerName = json.optString("customerName", ""),
            customerPhone = json.optString("customerPhone", ""),
            customerAddress = json.optString("customerAddress", ""),
            customerLat = cLat,
            customerLng = cLng,
            merchantName = json.optString("merchantName", ""),
            merchantAddress = json.optString("merchantAddress", ""),
            merchantLat = mLat,
            merchantLng = mLng,
            payoutFormatted = json.optString("payoutFormatted", "").takeIf { it.isNotBlank() },
            distanceKm = distKm,
            estimatedTimeMins = estMins,
            state = stateStr,
            otpAttemptsLeft = if (json.has("otpAttemptsLeft") && !json.isNull("otpAttemptsLeft")) json.getInt("otpAttemptsLeft") else 3,
            otpVerified = json.optBoolean("otpVerified", false),
            isCod = isCod,
            codAmount = if (isCod && json.has("codAmount") && !json.isNull("codAmount")) json.getDouble("codAmount").takeIf { !it.isNaN() && it > 0.0 } else null,
            codCollectedAmount = if (isCod && json.has("codCollectedAmount") && !json.isNull("codCollectedAmount")) json.getDouble("codCollectedAmount").takeIf { !it.isNaN() && it >= 0.0 } else null,
            codReconciled = json.optBoolean("codReconciled", false),
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
}
