package com.commerceos.rider.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferPayloadValidatorTest {

    @Test
    fun testParseAndValidate_fcmMap_acceptsPodPaymentMethodWhenCod() {
        val data = mapOf(
            "offerId" to "off_123",
            "deliveryId" to "del_123",
            "orderId" to "ord_123",
            "riderId" to "broadcast",
            "isCod" to "true",
            "paymentMethod" to "POD",
            "codAmount" to "250.0",
            "merchantName" to "Apollo Pharmacy",
            "merchantAddress" to "Indiranagar",
            "merchantLat" to "12.9716",
            "merchantLng" to "77.5946"
        )
        val offer = OfferPayloadValidator.parseAndValidate(data)
        assertNotNull("Offer with isCod=true and paymentMethod=POD should be accepted", offer)
        assertTrue(offer!!.isCod)
        assertEquals(250.0, offer.codAmount ?: 0.0, 0.001)
    }

    @Test
    fun testParseAndValidate_fcmMap_rejectsWhenNotCodOrNonCodPaymentMethod() {
        // Non-COD payment method
        val onlineData = mapOf(
            "offerId" to "off_124",
            "deliveryId" to "del_124",
            "orderId" to "ord_124",
            "riderId" to "broadcast",
            "isCod" to "true",
            "paymentMethod" to "ONLINE",
            "codAmount" to "250.0"
        )
        assertNull("Offer with paymentMethod=ONLINE must be rejected", OfferPayloadValidator.parseAndValidate(onlineData))

        // isCod != true
        val nonCodData = mapOf(
            "offerId" to "off_125",
            "deliveryId" to "del_125",
            "orderId" to "ord_125",
            "riderId" to "broadcast",
            "isCod" to "false",
            "paymentMethod" to "POD",
            "codAmount" to "250.0"
        )
        assertNull("Offer with isCod=false must be rejected", OfferPayloadValidator.parseAndValidate(nonCodData))
    }

    @Test
    fun testParseAndValidate_json_acceptsPodPaymentMethodWhenCod() {
        val json = JSONObject("""
            {
                "offerId": "off_json_123",
                "deliveryId": "del_json_123",
                "orderId": "ord_json_123",
                "riderId": "broadcast",
                "isCod": true,
                "paymentMethod": "POD",
                "codAmount": 350.0,
                "merchantName": "MedPlus",
                "merchantAddress": "Koramangala",
                "merchantLat": 12.9352,
                "merchantLng": 77.6245
            }
        """.trimIndent())
        val offer = OfferPayloadValidator.parseAndValidate(json)
        assertNotNull("JSON offer with isCod=true and paymentMethod=POD should be accepted", offer)
        assertTrue(offer!!.isCod)
        assertEquals(350.0, offer.codAmount ?: 0.0, 0.001)
    }

    @Test
    fun testParseAndValidate_json_rejectsWhenNotCodOrNonCodPaymentMethod() {
        val nonCodJson = JSONObject("""
            {
                "offerId": "off_json_124",
                "deliveryId": "del_json_124",
                "orderId": "ord_json_124",
                "riderId": "broadcast",
                "isCod": false,
                "paymentMethod": "POD",
                "codAmount": 350.0
            }
        """.trimIndent())
        assertNull("JSON offer with isCod=false must be rejected", OfferPayloadValidator.parseAndValidate(nonCodJson))

        val onlineJson = JSONObject("""
            {
                "offerId": "off_json_125",
                "deliveryId": "del_json_125",
                "orderId": "ord_json_125",
                "riderId": "broadcast",
                "isCod": true,
                "paymentMethod": "CARD",
                "codAmount": 350.0
            }
        """.trimIndent())
        assertNull("JSON offer with paymentMethod=CARD must be rejected", OfferPayloadValidator.parseAndValidate(onlineJson))
    }

    @Test
    fun testParseAndValidate_assignedStatusRedactsPiiAndCoordinates_fcmMap() {
        val data = mapOf(
            "offerId" to "off_assigned_fcm",
            "deliveryId" to "del_assigned_fcm",
            "orderId" to "ord_assigned_fcm",
            "riderId" to "rdr_assigned_1",
            "status" to "ASSIGNED",
            "customerName" to "John Doe",
            "customerAddress" to "Flat 404, Building B, Indiranagar, Bengaluru",
            "locality" to "Indiranagar",
            "customerLat" to "12.9784",
            "customerLng" to "77.6408",
            "isCod" to "true",
            "paymentMethod" to "COD",
            "codAmount" to "150.0",
            "merchantName" to "MedStore",
            "merchantAddress" to "Indiranagar"
        )
        val offer = OfferPayloadValidator.parseAndValidate(data, authenticatedRiderId = "rdr_assigned_1")
        assertNotNull("Assigned offer should parse successfully", offer)
        assertFalse("ASSIGNED status must NOT be treated as accepted", offer!!.isAccepted)
        assertEquals("Customer", offer.customerName)
        assertFalse("Assigned offer address must not leak flat/building PII", offer.customerAddress.contains("Flat 404"))
        assertFalse("Assigned offer address must not leak flat/building PII", offer.customerAddress.contains("Building B"))
        assertTrue("Assigned offer address must contain coarse locality", offer.customerAddress.contains("Indiranagar"))
        assertEquals("Coordinates must be zeroed for unaccepted ASSIGNED offer", 0.0, offer.customerLat, 0.0001)
        assertEquals("Coordinates must be zeroed for unaccepted ASSIGNED offer", 0.0, offer.customerLng, 0.0001)
    }

    @Test
    fun testParseAndValidate_assignedStatusRedactsPiiAndCoordinates_json() {
        val json = JSONObject("""
            {
                "offerId": "off_assigned_json",
                "deliveryId": "del_assigned_json",
                "orderId": "ord_assigned_json",
                "riderId": "rdr_assigned_1",
                "status": "ASSIGNED",
                "customerName": "Jane Smith",
                "customerAddress": "B-204, Royal Palms, South Delhi, New Delhi",
                "locality": "South Delhi",
                "customerLat": 28.5355,
                "customerLng": 77.2410,
                "isCod": true,
                "paymentMethod": "POD",
                "codAmount": 280.0,
                "merchantName": "Delhi Pharmacy",
                "merchantAddress": "South Delhi Hub"
            }
        """.trimIndent())
        val offer = OfferPayloadValidator.parseAndValidate(json, authenticatedRiderId = "rdr_assigned_1")
        assertNotNull("Assigned JSON offer should parse successfully", offer)
        assertFalse("ASSIGNED status must NOT be treated as accepted", offer!!.isAccepted)
        assertEquals("Customer", offer.customerName)
        assertFalse("Assigned JSON offer must not leak unit/premise PII", offer.customerAddress.contains("B-204"))
        assertTrue("Assigned JSON offer must contain coarse locality", offer.customerAddress.contains("South Delhi"))
        assertEquals("Coordinates must be zeroed for unaccepted ASSIGNED offer", 0.0, offer.customerLat, 0.0001)
        assertEquals("Coordinates must be zeroed for unaccepted ASSIGNED offer", 0.0, offer.customerLng, 0.0001)
    }

    @Test
    fun testParseAndValidate_acceptedStatusRevealsFullDetails() {
        val json = JSONObject("""
            {
                "offerId": "off_accepted_json",
                "deliveryId": "del_accepted_json",
                "orderId": "ord_accepted_json",
                "riderId": "rdr_assigned_1",
                "status": "ACCEPTED",
                "customerName": "Jane Smith",
                "customerAddress": "B-204, Royal Palms, South Delhi, New Delhi",
                "locality": "South Delhi",
                "customerLat": 28.5355,
                "customerLng": 77.2410,
                "isCod": true,
                "paymentMethod": "POD",
                "codAmount": 280.0,
                "merchantName": "Delhi Pharmacy",
                "merchantAddress": "South Delhi Hub"
            }
        """.trimIndent())
        val offer = OfferPayloadValidator.parseAndValidate(json, authenticatedRiderId = "rdr_assigned_1")
        assertNotNull("Accepted JSON offer should parse successfully", offer)
        assertTrue("ACCEPTED status must be treated as accepted", offer!!.isAccepted)
        assertEquals("Jane Smith", offer.customerName)
        assertEquals("B-204, Royal Palms, South Delhi, New Delhi", offer.customerAddress)
        assertEquals(28.5355, offer.customerLat, 0.0001)
        assertEquals(77.2410, offer.customerLng, 0.0001)
    }
}
