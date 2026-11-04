package com.commerceos.rider.util

import android.content.Context
import android.util.Log
import com.commerceos.rider.model.OfferPayloadValidator
import com.commerceos.rider.model.ServerOffer
import com.commerceos.rider.repository.RiderDeliveryRepository
import com.commerceos.rider.session.RiderSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Authoritative Transport Sources for incoming offer events.
 */
enum class OfferEventSource {
    FCM,
    SSE,
    RECONCILIATION
}

/**
 * Canonical Shared Offer Event Processing & Deduplication Pipeline.
 * All transports (FCM Push, SSE Stream, HTTP Reconciliation) ingest through this pipeline.
 */
object RiderOfferEventPipeline {

    private const val TAG = "OfferPipeline"
    private val pipelineScope = CoroutineScope(Dispatchers.IO)

    private val _incomingOfferEvents = MutableSharedFlow<ServerOffer>(extraBufferCapacity = 32)
    val incomingOfferEvents: SharedFlow<ServerOffer> = _incomingOfferEvents.asSharedFlow()

    /**
     * Ingestion point for FCM remote message data map.
     */
    fun processIncomingFcmOffer(
        context: Context,
        data: Map<String, String>,
        authenticatedRiderId: String? = null
    ): ServerOffer? {
        val offer = OfferPayloadValidator.parseAndValidate(data, authenticatedRiderId) ?: return null
        return processValidatedOffer(context, offer, source = OfferEventSource.FCM)
    }

    /**
     * Ingestion point for SSE stream JSON event object.
     */
    fun processIncomingSseOffer(
        context: Context,
        json: JSONObject,
        authenticatedRiderId: String? = null
    ): ServerOffer? {
        val offer = OfferPayloadValidator.parseAndValidate(json, authenticatedRiderId) ?: return null
        return processValidatedOffer(context, offer, source = OfferEventSource.SSE)
    }

    /**
     * Common Canonical Ingestion point for validated ServerOffer.
     */
    fun processValidatedOffer(
        context: Context,
        offer: ServerOffer,
        source: OfferEventSource = OfferEventSource.RECONCILIATION
    ): ServerOffer? {
        // Internal Storage Check: Only show / notify if BOTH logged in AND online
        val sessionMgr = RiderSessionManager.getInstance(context)
        if (!sessionMgr.isLoggedIn()) {
            Log.d(TAG, "[$source] LOGGED_OUT_DROPPED: Rider is not logged in. Suppressing offer ${offer.offerId} completely.")
            return null
        }
        if (!sessionMgr.isShiftOnline()) {
            Log.d(TAG, "[$source] OFFLINE_SHIFT_MUTED: Rider is logged in but status is OFFLINE. Suppressing offer ${offer.offerId}.")
            return null
        }

        // 1. Silently persist notification in on-device local database
        val notifItem = com.commerceos.rider.model.RiderNotificationItem(
            notificationId = offer.notificationId.ifBlank { "notif_${offer.offerId}" },
            eventId = offer.eventId,
            type = "ORDER_OFFER",
            category = "ORDERS",
            priority = "HIGH",
            riderId = offer.riderId ?: "broadcast",
            orderId = offer.orderId,
            deliveryId = offer.deliveryId,
            offerId = offer.offerId,
            title = "⚡ Delivery Offer #${offer.orderId.takeLast(8).uppercase()}",
            body = "Pickup: ${offer.merchantName} • Bill: ₹${offer.orderTotal?.toInt() ?: 0} (~${offer.estimatedDurationMins} min)",
            deepLink = "commerceos://rider/offer/${offer.offerId}",
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.format(java.util.Date(offer.offerCreatedAt)),
            expiresAt = offer.offerExpiresAt,
            readAt = null,
            offerStatus = "CREATED",
            isAvailable = true
        )
        RiderLocalNotificationStore.saveNotification(context, notifItem)

        // 3. Online State: Emit into live in-app SharedFlow so RiderMainScreen renders interactive card
        _incomingOfferEvents.tryEmit(offer)

        // 4. Reconciliation Suppression: Periodic polling MUST NEVER post system notifications or buzz the device
        if (source == OfferEventSource.RECONCILIATION) {
            Log.d(TAG, "[$source] Reconciliation offer synced silently: ${offer.offerId}")
            return offer
        }

        // 5. Deduplicate system tray notifications
        // Deduplicate per distinct event/offer instance to filter duplicate network packets (FCM retry or SSE race),
        // while allowing deliberate merchant re-broadcasts to alert the fleet.
        val dedupKey = if (offer.eventId.isNotBlank()) {
            if (offer.eventId.startsWith("evt_")) offer.eventId else "evt_${offer.eventId}"
        } else {
            "offer_${offer.offerId}"
        }
        val isAlreadyPosted = RiderNotificationManager.isEventSuccessfullyPosted(context, dedupKey)
        if (isAlreadyPosted) {
            Log.d(TAG, "[$source] Notification already posted for $dedupKey. Suppressing duplicate.")
            return offer
        }

        Log.d(TAG, "[$source] Authoritative new offer event processing: $dedupKey (OfferId: ${offer.offerId}, Expires in ${offer.remainingSeconds}s)")

        // 6. Strict Telemetry: Report DEVICE_RECEIVED exactly once per unique eventId upon genuine FCM reception
        if (source == OfferEventSource.FCM) {
            if (!RiderNotificationManager.isDeviceReceiptAcked(context, offer.eventId)) {
                RiderNotificationManager.markDeviceReceiptAcked(context, offer.eventId)
                sendAckTelemetry(context, offer.offerId, "DEVICE_RECEIVED")
            }
        }

        // 7. Post scannable high-priority system tray notification with audio alert
        val notifResult = RiderNotificationManager.postDirectOfferNotification(context, offer)

        // 8. Strict Telemetry & Dedup: Mark permanently consumed only if notification posting succeeded
        if (notifResult == NotificationPostResult.POSTED) {
            RiderNotificationManager.markEventSuccessfullyPosted(context, dedupKey)
            RiderNotificationManager.markEventSuccessfullyPosted(context, "offer_${offer.offerId}")
            sendAckTelemetry(context, offer.offerId, "NOTIFICATION_POSTED")
        } else {
            Log.w(TAG, "[$source] Notification posting failed ($notifResult). Event $dedupKey kept retryable.")
        }

        return offer
    }

    private fun sendAckTelemetry(context: Context, offerId: String, status: String) {
        pipelineScope.launch {
            try {
                val sessionMgr = RiderSessionManager.getInstance(context)
                val baseUrl = sessionMgr.getBaseUrl()
                val token = sessionMgr.getAuthToken()
                if (baseUrl.isNotBlank() && token.isNotBlank()) {
                    val repo = RiderDeliveryRepository(
                        baseUrlProvider = { baseUrl },
                        authTokenProvider = { token }
                    )
                    repo.ackOffer(offerId, status)
                }
            } catch (e: Exception) {
                // Ignore transient telemetry ack errors
            }
        }
    }
}
