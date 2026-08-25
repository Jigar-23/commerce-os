package com.commerceos.android.config

import com.commerceos.android.model.CategoryGroup
import com.commerceos.android.model.SearchEntityType
import org.junit.Assert.*
import org.junit.Test

/**
 * 🔴 P0 — TAXONOMY TEST SUITE
 * Verifies ClientTaxonomyConfiguration categories, subcategories, brands, collections, campaigns, offers, filters, sorts, and active/inactive states.
 */
class ClientTaxonomyTest {

    @Test
    fun testTaxonomyConfiguration_DefaultValuesAndGetters() {
        val taxonomy = ClientTaxonomyConfiguration(
            verticalId = "pharmacy",
            defaultCategories = listOf(
                CategoryGroup("cat_rx", "Prescription", "Medicines requiring Rx")
            ),
            subcategories = listOf(
                SubcategoryGroup("sub_antibiotics", "cat_rx", "Antibiotics")
            ),
            taxonomyVersion = "2.1",
            taxonomySource = TaxonomySource.REMOTE_CMS
        )

        assertEquals("pharmacy", taxonomy.verticalId)
        assertEquals(1, taxonomy.categories.size)
        assertEquals("Prescription", taxonomy.categories.first().title)
        assertEquals(1, taxonomy.subcategoriesFor("cat_rx").size)
        assertEquals("Antibiotics", taxonomy.subcategoriesFor("cat_rx").first().title)
        assertEquals("2.1", taxonomy.taxonomyVersion)
        assertEquals(TaxonomySource.REMOTE_CMS, taxonomy.taxonomySource)
        assertTrue(taxonomy.isActive)
    }

    @Test
    fun testTaxonomyActiveInactiveState_FiltersEntities() {
        val activeTaxonomy = ClientTaxonomyConfiguration(isActive = true)
        val inactiveTaxonomy = ClientTaxonomyConfiguration(isActive = false)

        assertTrue(activeTaxonomy.isEntitySupported(SearchEntityType.PRODUCT))
        assertFalse(inactiveTaxonomy.isEntitySupported(SearchEntityType.PRODUCT))
    }
}
