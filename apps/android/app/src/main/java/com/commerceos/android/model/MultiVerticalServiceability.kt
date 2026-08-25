package com.commerceos.android.model

/**
 * Deprecated legacy model replaced by [FulfillmentContext] and [VerticalAvailability].
 * Preserved for backwards binary interface compatibility.
 */
@Deprecated("Use FulfillmentContext and VerticalAvailability instead.", ReplaceWith("FulfillmentContext"))
typealias LegacyMultiVerticalServiceability = FulfillmentContext
