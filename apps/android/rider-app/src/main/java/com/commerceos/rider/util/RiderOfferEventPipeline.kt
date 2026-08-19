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
        // 1. Always emit into live in-app SharedFlow so RiderMainScreen renders the interactive offer card
        _incomingOfferEvents.tryEmit(offer)

        // 2. Deduplicate system tray notifications
        val dedupKey = "offer_${offer.offerId}"
        val isAlreadyPosted = RiderNotificationManager.isEventSuccessfullyPosted(context, dedupKey)
        if (isAlreadyPosted) {
            return offer
        }

        Log.d(TAG, "[$source] Authoritative new offer event processing: $dedupKey (OfferId: ${offer.offerId}, Expires in ${offer.remainingSeconds}s)")

        // 3. Strict Telemetry: Report DEVICE_RECEIVED exactly once per unique eventId upon genuine FCM reception
        if (source == OfferEventSource.FCM) {
            if (!RiderNotificationManager.isDeviceReceiptAcked(context, offer.eventId)) {
                RiderNotificationManager.markDeviceReceiptAcked(context, offer.eventId)
                sendAckTelemetry(context, offer.offerId, "DEVICE_RECEIVED")
            }
        }

        // 4. Post scannable high-priority system tray notification with audio alert
        val notifResult = RiderNotificationManager.postDirectOfferNotification(context, offer)

        // 5. Strict Telemetry & Dedup: Mark permanently consumed only if notification posting succeeded
        if (notifResult == NotificationPostResult.POSTED) {
            RiderNotificationManager.markEventSuccessfullyPosted(context, dedupKey)
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
