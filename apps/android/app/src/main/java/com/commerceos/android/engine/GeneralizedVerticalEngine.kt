package com.commerceos.android.engine

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.config.ClientSearchConfiguration
import com.commerceos.android.config.ClientTaxonomyConfiguration
import com.commerceos.android.config.TerminologyConfiguration
import com.commerceos.android.model.HomeSectionType
import com.commerceos.android.registry.CardVariant
import com.commerceos.android.registry.ClientCardRegistry
import com.commerceos.android.registry.ClientPresentationRegistry
import com.commerceos.android.registry.VerticalPresentation
import com.commerceos.android.registry.WorkflowType

/**
 * Definition of a custom domain vertical registered at runtime.
 */
data class CustomVerticalDefinition(
    val verticalId: String,
    val verticalName: String,
    val iconUrl: String? = null,
    val visualSymbol: String = "✨",
    val displayOrder: Int = 10,
    val sections: List<HomeSectionType> = listOf(
        HomeSectionType.HERO_CAMPAIGN,
        HomeSectionType.CATEGORY_GRID,
        HomeSectionType.DEAL_GRID,
        HomeSectionType.RECOMMENDED_FEED
    ),
    val workflows: Set<WorkflowType> = setOf(
        WorkflowType.AUTHENTICATION,
        WorkflowType.CART,
        WorkflowType.CHECKOUT,
        WorkflowType.PAYMENT,
        WorkflowType.DELIVERY
    ),
    val taxonomy: ClientTaxonomyConfiguration = ClientTaxonomyConfiguration(verticalId = verticalId),
    val searchConfig: ClientSearchConfiguration = ClientSearchConfiguration(),
    val cardVariant: CardVariant = CardVariant.GENERIC_PRODUCT,
    val terminology: TerminologyConfiguration = TerminologyConfiguration(),
    val defaultCta: String = "Explore $verticalName",
    val catalogHeader: String = "$verticalName Catalog"
)

/**
 * Generalized Vertical Engine enabling white-label client apps and third-party developers to register,
 * resolve, and render arbitrary domain verticals without making any generic UI source modifications.
 */
object GeneralizedVerticalEngine {

    private val registeredVerticals = mutableMapOf<String, CustomVerticalDefinition>()

    /** Registers a custom vertical at runtime. */
    fun registerVertical(definition: CustomVerticalDefinition) {
        registeredVerticals[definition.verticalId.lowercase()] = definition

        // Register presentation in ClientPresentationRegistry
        ClientPresentationRegistry.registerCustomVertical(
            verticalId = definition.verticalId,
            presentation = VerticalPresentation(
                visualSymbol = definition.visualSymbol,
                catalogHeader = definition.catalogHeader,
                defaultCta = definition.defaultCta,
                preferredSectionOrder = definition.sections
            )
        )
    }

    /** Resolves vertical definition by ID. */
    fun getVertical(verticalId: String): CustomVerticalDefinition? {
        return registeredVerticals[verticalId.lowercase()]
    }

    /** Resolves ordered vertical definitions for a given client configuration. */
    fun resolveActiveVerticals(config: ClientConfiguration): List<CustomVerticalDefinition> {
        val builtIn = listOf(
            CustomVerticalDefinition("general", "All Products", visualSymbol = "🛍️", displayOrder = 1),
            CustomVerticalDefinition("food", "Food & Dining", visualSymbol = "🍕", displayOrder = 2),
            CustomVerticalDefinition("fashion", "Apparel & Style", visualSymbol = "👗", displayOrder = 3),
            CustomVerticalDefinition("electronics", "Tech & Devices", visualSymbol = "⚡", displayOrder = 4),
            CustomVerticalDefinition("pharmacy", "Medicines & Rx", visualSymbol = "💊", displayOrder = 5),
            CustomVerticalDefinition("services", "Home Services", visualSymbol = "🛠️", displayOrder = 6)
        )

        val customList = registeredVerticals.values.toList()
        val combined = builtIn + customList
        return combined.sortedBy { it.displayOrder }
    }
}
