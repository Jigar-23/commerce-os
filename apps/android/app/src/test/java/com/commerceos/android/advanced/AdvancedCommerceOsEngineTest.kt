package com.commerceos.android.advanced

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.model.CommerceProduct
import org.junit.Assert.*
import org.junit.Test

/**
 * 🔵 P3 — ADVANCED COMMERCE OS TEST SUITE
 * Complete verification for Enterprise AI, Visual Search, Hyper-Personalization, and Analytics.
 */
class AdvancedCommerceOsEngineTest {

    private val config = ClientConfiguration.DefaultGeneric

    @Test
    fun testAiNaturalLanguageSearch_ParsesQueryIntents() {
        val resultPrice = AiSearchEngine.parseNaturalLanguageQuery("cheap wireless earbuds under 2000", config)
        assertEquals("PRICE_SENSITIVE_SEARCH", resultPrice.parsedIntent)
        assertTrue(resultPrice.confidenceScore > 0.9)

        val resultFast = AiSearchEngine.parseNaturalLanguageQuery("express emergency medicine delivery", config)
        assertEquals("FAST_DELIVERY_SEARCH", resultFast.parsedIntent)
    }

    @Test
    fun testVisualAndVoiceSearchEngines_ProcessInputs() {
        val imageResult = AiSearchEngine.processImageSearch(byteArrayOf(1, 2, 3), config)
        assertEquals("IMAGE_FEATURE_MATCHING", imageResult.parsedIntent)

        val voiceResult = AiSearchEngine.processVoiceQuery("order organic milk", config)
        assertNotNull(voiceResult.parsedIntent)

        val barcodeResult = AiSearchEngine.processBarcodeScan("8901234567890", config)
        assertEquals("BARCODE_LOOKUP", barcodeResult.parsedIntent)
        assertEquals("8901234567890", barcodeResult.extractedFilters["barcode"])
    }

    @Test
    fun testAiShoppingAssistant_GeneratesContextualResponses() {
        val response = AiShoppingAssistant.generateAssistantResponse("Help me buy skin care routine", config)
        assertNotNull(response.message)
        assertEquals("ADD_TO_BASKET", response.actionIntent)
    }

    @Test
    fun testAiProductComparisonEngine_ComparesProductSpecs() {
        val p1 = CommerceProduct(id = "p1", sku = "s1", name = "Laptop Alpha", price = 50000.0, sellingPrice = 50000.0, rating = 4.5)
        val p2 = CommerceProduct(id = "p2", sku = "s2", name = "Laptop Beta", price = 55000.0, sellingPrice = 55000.0, rating = 4.8)

        val matrix = AiProductComparisonEngine.compareProducts(listOf(p1, p2))
        assertEquals(2, matrix.productIds.size)
        assertEquals("p2", matrix.recommendedWinnerId)
        assertTrue(matrix.featureComparison.containsKey("Price"))
        assertTrue(matrix.featureComparison.containsKey("Rating"))
    }

    @Test
    fun testCrossVerticalBundleEngine_CompleteTheLookAndBundles() {
        val p = CommerceProduct(id = "jacket_1", sku = "jk1", name = "Denim Jacket", price = 2499.0, sellingPrice = 2499.0)
        val bundle = CrossVerticalBundleEngine.getCompleteTheLook(p, config)

        assertNotNull(bundle)
        assertEquals("Complete the Look", bundle?.title)
        assertEquals("Save 15% on Bundle", bundle?.savingsLabel)
    }

    @Test
    fun testPersonalizationAndSmartReorder_FunctionsCorrectly() {
        val customerId = "cust_777"
        val smartItems = PersonalizationEngine.getSmartReorderItems(customerId, config)
        assertNotNull(smartItems)

        val tracked = PersonalizationEngine.trackPriceAlerts(customerId, "p1", 45000.0)
        assertTrue(tracked)
    }

    @Test
    fun testClientAnalyticsAndAbExperimentation_ReturnsMetrics() {
        val summary = ClientAnalyticsAndAbEngine.getClientAnalyticsSummary(config)
        assertEquals("generic_os", summary.clientId)
        assertEquals("VARIANT_B_AI_RANKING", summary.activeExperimentVariant)
        assertTrue(summary.aiConversionLiftPercent > 0.0)
    }
}
