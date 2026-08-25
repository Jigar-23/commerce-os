package com.commerceos.android.navigation

import com.commerceos.android.admin.TenantSuspensionEngine
import com.commerceos.android.config.ClientConfigProvider
import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.model.HomeDestination
import com.commerceos.android.model.UniversalSearchQuery
import com.commerceos.android.registry.WorkflowRegistry
import com.commerceos.android.registry.WorkflowType
import com.commerceos.android.ui.navigation.Screen

/**
 * Pure, unit-testable application navigation router.
 * Maps universal [HomeDestination] intent payloads and deep link URIs directly to concrete [Screen] routes.
 * Enforces Feature Flag, Tenant Suspension, & WorkflowRegistry navigation blocks based on active [ClientConfiguration].
 */
class AppDestinationRouter {

    fun resolve(
        destination: HomeDestination,
        config: ClientConfiguration = ClientConfigProvider.activeConfig()
    ): Screen {
        if (TenantSuspensionEngine.isTenantSuspended(config.identity.clientId)) {
            return Screen.TenantSuspended("Service temporarily suspended for tenant ${config.identity.clientId}")
        }

        return when (destination) {
            is HomeDestination.Orders -> Screen.OrderHistory
            is HomeDestination.Cart -> Screen.Cart
            is HomeDestination.Prescriptions -> {
                if (WorkflowRegistry.isWorkflowSupported(WorkflowType.PRESCRIPTION, config)) {
                    Screen.Prescriptions
                } else {
                    Screen.FeatureDisabled("Prescriptions", "Prescription management is not available in ${config.identity.clientName}.")
                }
            }
            is HomeDestination.Categories -> Screen.Categories
            is HomeDestination.Offer -> Screen.Offer(destination.offerId)
            is HomeDestination.Search -> resolveSearch(destination.query)
            is HomeDestination.Product -> Screen.ProductDetail(destination.productId)
            is HomeDestination.Category -> Screen.Catalog(
                CatalogQuery(categoryId = destination.categoryId, vertical = destination.vertical)
            )
            is HomeDestination.Brand -> Screen.Brand(destination.brandId)
            is HomeDestination.Vertical -> Screen.VerticalHome(destination.verticalId)
            is HomeDestination.Store -> Screen.Store(destination.storeId)
            is HomeDestination.Restaurant -> Screen.Restaurant(destination.restaurantId)
            is HomeDestination.Service -> {
                if (WorkflowRegistry.isWorkflowSupported(WorkflowType.SERVICE_BOOKING, config)) {
                    Screen.Service(destination.serviceId)
                } else {
                    Screen.FeatureDisabled("Service Booking", "Service bookings are not enabled in ${config.identity.clientName}.")
                }
            }
            is HomeDestination.Campaign -> Screen.Campaign(destination.campaignId)
            is HomeDestination.Collection -> Screen.Collection(destination.collectionId)
        }
    }

    fun resolveSearch(query: UniversalSearchQuery): Screen {
        return Screen.Search(query)
    }

    /**
     * Resolves a deep link URL against active client configuration and blocks unsupported workflow routes.
     */
    fun resolveDeepLink(
        deepLinkUrl: String,
        config: ClientConfiguration = ClientConfigProvider.activeConfig()
    ): Screen {
        if (TenantSuspensionEngine.isTenantSuspended(config.identity.clientId)) {
            return Screen.TenantSuspended("Tenant ${config.identity.clientId} is suspended.")
        }

        val cleanPath = deepLinkUrl.substringAfter("://").removePrefix("/")
        return when {
            cleanPath.startsWith("admin") -> Screen.AdminControlPlane
            cleanPath.startsWith("rider") -> Screen.RiderPartnerApp
            cleanPath.startsWith("tracking/") -> Screen.UnifiedOrderTracking(cleanPath.substringAfter("tracking/"))
            cleanPath.startsWith("prescriptions") || cleanPath.startsWith("rx") -> {
                if (WorkflowRegistry.isWorkflowSupported(WorkflowType.PRESCRIPTION, config)) {
                    Screen.Prescriptions
                } else {
                    Screen.FeatureDisabled("Prescriptions", "Prescription workflow is disabled for ${config.identity.clientName}.")
                }
            }
            cleanPath.startsWith("services") || cleanPath.startsWith("book") -> {
                if (WorkflowRegistry.isWorkflowSupported(WorkflowType.SERVICE_BOOKING, config)) {
                    val serviceId = cleanPath.substringAfter("/").ifBlank { "s_default" }
                    Screen.Service(serviceId)
                } else {
                    Screen.FeatureDisabled("Service Booking", "Service booking workflow is disabled for ${config.identity.clientName}.")
                }
            }
            cleanPath.startsWith("compare") -> {
                if (WorkflowRegistry.isWorkflowSupported(WorkflowType.COMPARISON, config)) {
                    Screen.Catalog(CatalogQuery())
                } else {
                    Screen.FeatureDisabled("Comparison", "Comparison workflow is disabled for ${config.identity.clientName}.")
                }
            }
            cleanPath.startsWith("wishlist") || cleanPath.startsWith("favorites") -> {
                if (WorkflowRegistry.isWorkflowSupported(WorkflowType.WISHLIST, config)) {
                    Screen.Catalog(CatalogQuery())
                } else {
                    Screen.FeatureDisabled("Wishlist", "Wishlist workflow is disabled for ${config.identity.clientName}.")
                }
            }
            cleanPath.startsWith("cart") -> Screen.Cart
            cleanPath.startsWith("orders") -> Screen.OrderHistory
            cleanPath.startsWith("product/") -> Screen.ProductDetail(cleanPath.substringAfter("product/"))
            cleanPath.startsWith("store/") -> Screen.Store(cleanPath.substringAfter("store/"))
            cleanPath.startsWith("restaurant/") -> Screen.Restaurant(cleanPath.substringAfter("restaurant/"))
            else -> Screen.Home
        }
    }
}
