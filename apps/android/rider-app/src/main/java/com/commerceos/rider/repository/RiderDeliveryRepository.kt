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

    suspend fun sendRiderOtp(phone: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = baseUrlProvider().trimEnd('/')
            if (baseUrl.isBlank()) return@withContext Result.failure(Exception("Base URL empty"))
            val url = URL("$baseUrl/api/v1/auth/rider/send-otp")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = JSONObject().apply {
                put("phone", phone)
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
        try {
            val baseUrl = baseUrlProvider().trimEnd('/')
            if (baseUrl.isBlank()) return@withContext Result.failure(Exception("Base URL empty"))
            val url = URL("$baseUrl/api/v1/auth/rider/verify-otp")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
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
                val obj = JSONObject(jsonStr)
                val token = obj.optString("accessToken", "")
                val riderObj = obj.optJSONObject("rider")
                val riderId = riderObj?.optString("id")?.takeIf { it.isNotBlank() }
                    ?: riderObj?.optString("riderId")?.takeIf { it.isNotBlank() }
                    ?: obj.optString("riderId").takeIf { it.isNotBlank() }
                    ?: return@withContext Result.failure(Exception("Server response missing rider identity"))
                val riderName = riderObj?.optString("name")?.takeIf { it.isNotBlank() } ?: name.ifBlank { "Rider" }
                val riderPhone = riderObj?.optString("phone")?.takeIf { it.isNotBlank() } ?: phone
                val riderVehicle = riderObj?.optString("vehicle")?.takeIf { it.isNotBlank() } ?: vehicle
                val rating = if (riderObj != null && riderObj.has("rating") && !riderObj.isNull("rating")) riderObj.getDouble("rating") else null
                val profile = RiderProfile(
                    riderId = riderId,
                    name = riderName,
                    phone = riderPhone,
                    vehicleNumber = riderVehicle,
                    rating = rating,
                    completedToday = riderObj?.optInt("completedToday", 0) ?: 0,
                    earningsTodayFormatted = riderObj?.optString("earningsTodayFormatted", "₹0") ?: "₹0",
                    shiftStatus = riderObj?.optString("shiftStatus", "UNKNOWN") ?: "UNKNOWN",
                    assignedHub = riderObj?.optString("assignedHub", "") ?: ""
                )
                return@withContext Result.success(Pair(token, profile))
            }
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            val errMsg = try { JSONObject(errStr).optString("message", errStr) } catch (_: Exception) { errStr }
            return@withContext Result.failure(Exception(errMsg))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
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
                    val rId = item.optString("riderId").ifBlank { item.optString("rider_id") }
                    if (rId.isBlank()) {
                        // Strict fail-closed: Skip notifications with unidentifiable or missing rider ID
                        continue
                    }
                    val notifId = item.optString("notificationId").ifBlank { item.optString("id", UUID.randomUUID().toString()) }
                    list.add(
                        RiderNotificationItem(
                            notificationId = notifId,
                            eventId = item.optString("eventId").ifBlank { notifId },
                            type = item.optString("type", "ORDER_OFFER"),
                            category = item.optString("category", "ORDERS"),
                            priority = item.optString("priority", "HIGH"),
                            riderId = rId,
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
                val obj = JSONObject(jsonStr)
                val sessionObj = obj.optJSONObject("session")
                if (sessionObj != null) {
                    return@withContext parseSessionJson(sessionObj)
                }
                if (obj.optBoolean("active", true) && obj.has("deliveryId")) {
                    return@withContext parseSessionJson(obj)
                }
                return@withContext null
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
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext Result.success(parseSessionJson(JSONObject(jsonStr)))
            }
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            val errMsg = try { JSONObject(errStr).optString("message", errStr) } catch (_: Exception) { errStr }
            return@withContext Result.failure(Exception("Arrive store failed: $errMsg"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
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
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            val errMsg = try { JSONObject(errStr).optString("message", errStr) } catch (_: Exception) { errStr }
            return@withContext Result.failure(Exception("Pickup confirmation failed: $errMsg"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    suspend fun arriveCustomer(deliveryId: String): Result<ServerDeliverySession> = withContext(Dispatchers.IO) {
        try {
            val conn = createConnection("/api/v1/delivery/session/$deliveryId/arrive-customer", "POST")
            conn.doOutput = true
            conn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext Result.success(parseSessionJson(JSONObject(jsonStr)))
            }
            val errStr = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP ${conn.responseCode}"
            val errMsg = try { JSONObject(errStr).optString("message", errStr) } catch (_: Exception) { errStr }
            return@withContext Result.failure(Exception("Arrive customer failed: $errMsg"))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
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
        val stateStr = json.optString("state").takeIf { it.isNotBlank() }
            ?: json.optString("deliveryStatus").takeIf { it.isNotBlank() }
            ?: json.optString("status").takeIf { it.isNotBlank() }
            ?: "ASSIGNED"

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

        val deliveryId = json.optString("deliveryId").takeIf { it.isNotBlank() }
            ?: json.optString("id").takeIf { it.isNotBlank() }
            ?: "del_active"
        val orderId = json.optString("orderId").takeIf { it.isNotBlank() }
            ?: json.optString("order_id").takeIf { it.isNotBlank() }
            ?: "ord_active"

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
