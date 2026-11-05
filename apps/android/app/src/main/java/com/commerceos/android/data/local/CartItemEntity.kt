package com.commerceos.android.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.commerceos.android.model.CartItem
import com.commerceos.android.model.PharmacyCartAttributes
import java.math.BigDecimal

@Entity(
    tableName = "cart_items",
    primaryKeys = ["customerId", "sku"]
)
data class CartItemEntity(
    val customerId: String,
    val sku: String,
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
    fun toCartItem(): CartItem {
        return CartItem(
            productId = productId.ifBlank { sku },
            sku = sku,
            name = name.ifBlank { sku },
            unitPrice = BigDecimal.valueOf(unitPrice),
            quantity = quantity,
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
    }

    companion object {
        fun fromCartItem(item: CartItem, customerId: String): CartItemEntity = CartItemEntity(
            sku = item.sku,
            customerId = customerId,
            productId = item.productId.ifBlank { item.sku },
            name = item.name.ifBlank { item.sku.ifBlank { "Prescribed Medicine" } },
            unitPrice = item.unitPrice.toDouble(),
            quantity = item.quantity.coerceAtLeast(1),
            verticalId = item.verticalId.ifBlank { "general" },
            merchantId = item.merchantId,
            mrp = item.mrp.toDouble(),
            brand = item.brand,
            packSize = item.packSize,
            image = item.image,
            prescriptionRequired = item.prescriptionRequired,
            coldChain = item.coldChain,
            updatedAt = System.currentTimeMillis()
        )
    }
}
