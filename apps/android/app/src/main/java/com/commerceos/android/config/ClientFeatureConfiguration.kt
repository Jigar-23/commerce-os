package com.commerceos.android.config

/**
 * Feature Configuration Flags governing capability availability in the white-label app.
 */
data class ClientFeatureConfiguration(
    val enableWishlist: Boolean = true,
    val enablePrescriptionUpload: Boolean = false,
    val enableProductComparison: Boolean = false,
    val enableReorder: Boolean = true,
    val enableVoiceSearch: Boolean = true,
    val enableCameraSearch: Boolean = true,
    val enableBarcodeSearch: Boolean = true,
    val enableStoreLocationPicker: Boolean = true,
    val enableServiceBooking: Boolean = false,
    val enableReviews: Boolean = true,
    val enableCoupons: Boolean = true,
    val enableSubscriptions: Boolean = false
)
