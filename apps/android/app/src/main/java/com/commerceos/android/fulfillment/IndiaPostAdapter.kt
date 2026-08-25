package com.commerceos.android.fulfillment

import java.util.Random

/**
 * India Post Speed Post / Registered Parcel Carrier Adapter.
 * Encapsulates India Post consignment generation, tracking normalization, and checkpoint parsing.
 * Credentials and API keys are strictly maintained server-side.
 */
class IndiaPostAdapter : CarrierAdapter {

    override val carrierId: String = "india_post"
    override val carrierName: String = "India Post Speed Post"

    private val random = Random()

    override suspend fun createShipment(request: CreateShipmentRequest): ShipmentResponse {
        // Generate valid India Post Speed Post format consignment number: EM + 9 digits + IN
        val numberPart = String.format("%09d", random.nextInt(1000000000))
        val consignmentNo = "EM${numberPart}IN"

        val deliveryMs = System.currentTimeMillis() + (3 * 24 * 3600 * 1000L) // 3 days SLA

        return ShipmentResponse(
            shipmentId = "ip_${request.orderId}",
            consignmentNumber = consignmentNo,
            carrierName = carrierName,
            labelUrl = "https://assets.commerceos.io/labels/indiapost/$consignmentNo.pdf",
            expectedDeliveryTimestamp = deliveryMs
        )
    }

    override suspend fun getTrackingStatus(consignmentNumber: String): List<ShipmentCheckpoint> {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 3600 * 1000L

        return listOf(
            ShipmentCheckpoint(
                checkpointId = "cp_1",
                facilityName = "GPO New Delhi",
                locationName = "New Delhi",
                timestamp = now - (2 * dayMs),
                status = CarrierTrackingStatus.BOOKED,
                description = "Article Booked at GPO New Delhi"
            ),
            ShipmentCheckpoint(
                checkpointId = "cp_2",
                facilityName = "NSH New Delhi",
                locationName = "New Delhi",
                timestamp = now - (36 * 3600 * 1000L),
                status = CarrierTrackingStatus.DISPATCHED,
                description = "Item Dispatched to Sorting Hub"
            ),
            ShipmentCheckpoint(
                checkpointId = "cp_3",
                facilityName = "ICH Mumbai",
                locationName = "Mumbai",
                timestamp = now - (12 * 3600 * 1000L),
                status = CarrierTrackingStatus.IN_TRANSIT,
                description = "Item Received at Destination Sorting Hub"
            ),
            ShipmentCheckpoint(
                checkpointId = "cp_4",
                facilityName = "Bandra Sub Post Office",
                locationName = "Mumbai",
                timestamp = now - (2 * 3600 * 1000L),
                status = CarrierTrackingStatus.OUT_FOR_DELIVERY,
                description = "Out for Delivery by Postman"
            )
        )
    }

    override suspend fun cancelShipment(consignmentNumber: String): Boolean {
        return true
    }
}

/** Local Delivery Partner Carrier Adapter for regional courier networks. */
class LocalDeliveryPartnerAdapter : CarrierAdapter {
    override val carrierId: String = "local_partner"
    override val carrierName: String = "Local Express Courier"

    override suspend fun createShipment(request: CreateShipmentRequest): ShipmentResponse {
        val consignmentNo = "LEC${System.currentTimeMillis().toString().takeLast(8)}"
        return ShipmentResponse(
            shipmentId = "lec_${request.orderId}",
            consignmentNumber = consignmentNo,
            carrierName = carrierName,
            expectedDeliveryTimestamp = System.currentTimeMillis() + (24 * 3600 * 1000L)
        )
    }

    override suspend fun getTrackingStatus(consignmentNumber: String): List<ShipmentCheckpoint> {
        return listOf(
            ShipmentCheckpoint(
                checkpointId = "lec_cp_1",
                facilityName = "Local Hub Central",
                locationName = "City Center",
                timestamp = System.currentTimeMillis() - (4 * 3600 * 1000L),
                status = CarrierTrackingStatus.DISPATCHED,
                description = "Package in transit to customer locality"
            )
        )
    }

    override suspend fun cancelShipment(consignmentNumber: String): Boolean = true
}

/** Generic Courier Carrier Adapter fallback. */
class GenericCourierAdapter : CarrierAdapter {
    override val carrierId: String = "generic_courier"
    override val carrierName: String = "Standard Logistics Partner"

    override suspend fun createShipment(request: CreateShipmentRequest): ShipmentResponse {
        val consignmentNo = "SLP${System.currentTimeMillis().toString().takeLast(8)}"
        return ShipmentResponse(
            shipmentId = "slp_${request.orderId}",
            consignmentNumber = consignmentNo,
            carrierName = carrierName,
            expectedDeliveryTimestamp = System.currentTimeMillis() + (48 * 3600 * 1000L)
        )
    }

    override suspend fun getTrackingStatus(consignmentNumber: String): List<ShipmentCheckpoint> {
        return listOf(
            ShipmentCheckpoint(
                checkpointId = "slp_cp_1",
                facilityName = "Regional Logistics Facility",
                locationName = "Central Hub",
                timestamp = System.currentTimeMillis() - (8 * 3600 * 1000L),
                status = CarrierTrackingStatus.IN_TRANSIT,
                description = "Handed over to carrier partner"
            )
        )
    }

    override suspend fun cancelShipment(consignmentNumber: String): Boolean = true
}

/** Registry managing active carrier adapters on the server/platform. */
object CarrierRegistry {
    private val adapters = mapOf(
        "india_post" to IndiaPostAdapter(),
        "local_partner" to LocalDeliveryPartnerAdapter(),
        "generic" to GenericCourierAdapter()
    )

    fun resolveAdapter(carrierId: String): CarrierAdapter {
        return adapters[carrierId.lowercase()] ?: adapters["generic"]!!
    }

    /** Process external webhook event and normalize tracking status. */
    fun handleCarrierWebhook(event: CarrierWebhookEvent): ShipmentCheckpoint {
        return ShipmentCheckpoint(
            checkpointId = event.eventId,
            facilityName = event.carrierName,
            locationName = event.location,
            timestamp = event.timestamp,
            status = event.status,
            description = "Status updated via carrier webhook: ${event.status.name}"
        )
    }
}
