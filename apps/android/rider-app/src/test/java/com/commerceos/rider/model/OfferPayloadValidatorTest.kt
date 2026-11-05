package com.commerceos.rider.model

import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
