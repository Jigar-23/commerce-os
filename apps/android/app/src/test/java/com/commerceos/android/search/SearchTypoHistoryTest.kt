package com.commerceos.android.search

import com.commerceos.android.config.SearchHistoryBehavior
import org.junit.Assert.*
import org.junit.Test

/**
 * 🟡 P2 — SEARCH TYPO, SYNONYMS & HISTORY TEST SUITE
 */
class SearchTypoHistoryTest {

    @Test
    fun testTypoCorrection() {
        val res = SearchTypoSynonymEngine.correctTypo("paracetaml")
        assertTrue(res.wasCorrected)
        assertEquals("paracetamol", res.correctedQuery)
    }

    @Test
    fun testSynonymExpansion() {
        val synonyms = SearchTypoSynonymEngine.getSynonyms("crocin")
        assertTrue(synonyms.contains("paracetamol"))
    }

    @Test
    fun testZeroResultSuggestions() {
        val suggestions = SearchTypoSynonymEngine.getZeroResultSuggestions("paracetmol")
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.contains("paracetamol"))
    }

    @Test
    fun testSearchHistoryManager_AddRemoveClear() {
        val manager = SearchHistoryManager(SearchHistoryBehavior(maxHistoryItems = 3))
        manager.addQuery("paracetamol")
        manager.addQuery("t-shirt")
        manager.addQuery("headphones")
        manager.addQuery("smartphone")

        val history = manager.getHistory()
        assertEquals(3, history.size)
        assertEquals("smartphone", history.first())

        assertTrue(manager.removeQuery("t-shirt"))
        assertEquals(2, manager.getHistory().size)

        assertTrue(manager.clearAll())
        assertTrue(manager.getHistory().isEmpty())
    }
}
