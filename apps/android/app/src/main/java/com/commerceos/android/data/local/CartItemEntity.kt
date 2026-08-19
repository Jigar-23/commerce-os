package com.commerceos.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.commerceos.android.model.CartItem
import com.commerceos.android.model.PharmacyCartAttributes
import java.math.BigDecimal

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey
    val sku: String,
    val customerId: String,
    val productId: String,
    val name: String,
    val unitPrice: Double,
    val quantity: Int,
    val verticalId: String = "general",
    val merchantId: String? = null,
    val mrp: Double = unitPrice,
    val brand: String? = null,
    val packSize: String? = null,
    val image: String? = null,
    val prescriptionRequired: Boolean = false,
    val coldChain: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toCartItem(): CartItem = CartItem(
        productId = productId.ifBlank { sku },
        sku = sku,
        name = name.ifBlank { "Medicine Item" },
        unitPrice = BigDecimal.valueOf(unitPrice),
        quantity = quantity.coerceAtLeast(1),
        verticalId = verticalId.ifBlank { "general" },
        merchantId = merchantId,
        mrp = BigDecimal.valueOf(if (mrp > 0.0) mrp else unitPrice),
        brand = brand,
        packSize = packSize,
        image = image,
        pharmacyAttributes = if (prescriptionRequired || coldChain) {
            PharmacyCartAttributes(
                prescriptionRequired = prescriptionRequired,
                coldChain = coldChain
            )
        } else null
    )

    companion object {
        fun fromCartItem(item: CartItem, customerId: String): CartItemEntity = CartItemEntity(
            sku = item.sku,
            customerId = customerId,
            productId = (item.productId ?: item.sku).ifBlank { item.sku },
            name = (item.name ?: "Medicine Item").ifBlank { "Medicine Item" },
            unitPrice = (item.unitPrice ?: BigDecimal.ZERO).toDouble(),
            quantity = (item.quantity ?: 1).coerceAtLeast(1),
            verticalId = (item.verticalId ?: "general").ifBlank { "general" },
            merchantId = item.merchantId,
            mrp = (item.mrp ?: item.unitPrice ?: BigDecimal.ZERO).toDouble(),
            brand = item.brand,
            packSize = item.packSize,
            image = item.image,
            prescriptionRequired = item.prescriptionRequired,
            coldChain = item.coldChain,
            updatedAt = System.currentTimeMillis()
        )
    }
}
