package com.commerceos.android.ui.navigation

import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.model.UniversalSearchQuery

/** Canonical screen destinations in Commerce OS. */
sealed class Screen {
    object Auth : Screen()
    object Home : Screen()
    object Categories : Screen()
    data class ProductDetail(val productId: String) : Screen()
    data class Catalog(val query: CatalogQuery) : Screen()
    object Cart : Screen()
    data class AddressSelection(val fromCheckout: Boolean = false, val fromProfile: Boolean = false) : Screen()
    object PaymentGateway : Screen()
    data class OrderTracking(val orderId: String) : Screen()
    data class UnifiedOrderTracking(val orderId: String) : Screen()
    object OrderHistory : Screen()
    object Account : Screen()
    object Prescriptions : Screen()
    data class Search(val query: UniversalSearchQuery = UniversalSearchQuery()) : Screen()
    data class VerticalHome(val verticalId: String) : Screen()
    data class Store(val storeId: String) : Screen()
    data class Restaurant(val restaurantId: String) : Screen()
    data class Service(val serviceId: String) : Screen()
    data class Campaign(val campaignId: String) : Screen()
    data class Brand(val brandId: String) : Screen()
    data class Collection(val collectionId: String) : Screen()
    data class Offer(val offerId: String) : Screen()
    data class FeatureDisabled(val featureName: String, val message: String) : Screen()
    object AdminControlPlane : Screen()
    object RiderPartnerApp : Screen()
    data class TenantSuspended(val reason: String? = null) : Screen()
}
