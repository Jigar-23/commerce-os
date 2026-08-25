package com.commerceos.android.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 🔵 P3 — AI ENGINE SUITE CONFIGURATION TEST
 */
class AiConfigurationTest {

    @Test
    fun testAiEngineSuiteDefaults() {
        val suite = AiEngineSuiteConfiguration()
        assertTrue(suite.isEnabled)
        assertTrue(suite.search.enableSemanticSearch)
        assertEquals("text-embedding-004", suite.search.vectorEmbeddingModel)
        assertTrue(suite.ranking.enablePersonalizedRanking)
        assertTrue(suite.merchandising.dynamicHeroBanners)
        assertTrue(suite.recommendation.enablePersonalizedFeed)
    }
}
