package com.commerceos.android.search

data class TypoCorrectionResult(
    val originalQuery: String,
    val correctedQuery: String,
    val wasCorrected: Boolean
)

/**
 * Typo Correction, Synonym Expansion & Zero-Result Recovery Engine.
 */
object SearchTypoSynonymEngine {

    private val synonymMap = mapOf(
        "crocin" to listOf("paracetamol", "fever medicine"),
        "dolo" to listOf("paracetamol", "pain relief"),
        "tshirt" to listOf("t-shirt", "apparel"),
        "phone" to listOf("smartphone", "mobile"),
        "laptop" to listOf("notebook", "computer"),
        "plumber" to listOf("pipe repair", "drainage")
    )

    private val commonTypos = mapOf(
        "paracetaml" to "paracetamol", "paracetmol" to "paracetamol",
        "smartfone" to "smartphone", "leptop" to "laptop",
        "t-shrt" to "t-shirt", "fasion" to "fashion"
    )

    fun correctTypo(query: String): TypoCorrectionResult {
        val lower = query.lowercase().trim()
        val corrected = commonTypos[lower] ?: query
        return TypoCorrectionResult(
            originalQuery = query,
            correctedQuery = corrected,
            wasCorrected = corrected != query
        )
    }

    fun getSynonyms(query: String): List<String> {
        val lower = query.lowercase().trim()
        return synonymMap[lower] ?: emptyList()
    }

    fun getZeroResultSuggestions(query: String): List<String> {
        val corrected = correctTypo(query).correctedQuery
        val synonyms = getSynonyms(corrected)
        return (listOf(corrected) + synonyms + listOf("Top Products", "Popular Deals")).distinct().take(4)
    }
}
