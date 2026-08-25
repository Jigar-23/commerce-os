package com.commerceos.android.config

/** AI Search Configuration (P3). */
data class AiSearchConfiguration(
    val enableSemanticSearch: Boolean = true,
    val vectorEmbeddingModel: String = "text-embedding-004",
    val vectorSearchWeight: Double = 0.5,
    val autoCorrectionEnabled: Boolean = true,
    val intentRecognitionEnabled: Boolean = true
)

/** AI Ranking Configuration (P3). */
data class AiRankingConfiguration(
    val enablePersonalizedRanking: Boolean = true,
    val realTimeClickBoost: Double = 0.2,
    val conversionRateBoost: Double = 0.3,
    val marginBoost: Double = 0.1
)

/** AI Merchandising Configuration (P3). */
data class AiMerchandisingConfiguration(
    val dynamicHeroBanners: Boolean = true,
    val automatedCategoryPlacement: Boolean = true,
    val smartDiscountPills: Boolean = true
)

/** AI Recommendation Configuration (P3). */
data class AiRecommendationConfiguration(
    val enablePersonalizedFeed: Boolean = true,
    val buyAgainRecencyDays: Int = 30,
    val crossVerticalRecommendations: Boolean = true,
    val similarItemAlgorithm: String = "COLLABORATIVE_FILTERING"
)

/** Integrated AI Engine Suite Configuration. */
data class AiEngineSuiteConfiguration(
    val search: AiSearchConfiguration = AiSearchConfiguration(),
    val ranking: AiRankingConfiguration = AiRankingConfiguration(),
    val merchandising: AiMerchandisingConfiguration = AiMerchandisingConfiguration(),
    val recommendation: AiRecommendationConfiguration = AiRecommendationConfiguration(),
    val isEnabled: Boolean = true
)
