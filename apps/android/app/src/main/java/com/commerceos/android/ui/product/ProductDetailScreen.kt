package com.commerceos.android.ui.product

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.ApiMedicine
import com.commerceos.android.model.CartItem
import com.commerceos.android.model.MedicineInfo
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.components.ShimmerBox
import com.commerceos.android.ui.components.SkeletonText
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceElevation
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.ProductDetailUiState

import com.commerceos.android.model.CommerceProduct

@Composable
fun ProductDetailScreen(
    uiState: ProductDetailUiState,
    cartItems: List<CartItem>,
    onAddToCart: (CommerceProduct) -> Unit,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (uiState) {
        is ProductDetailUiState.Loading -> ProductDetailSkeleton()
        is ProductDetailUiState.Error -> ProductDetailError(message = uiState.message, onRetry = onRetry)
        is ProductDetailUiState.Content -> {
            val product = uiState.product
            val cartQuantity = cartItems.firstOrNull { it.sku == product.sku || it.productId == product.id }?.quantity ?: 0
            ProductDetailContent(
                product = product,
                cartQuantity = cartQuantity,
                onAddToCart = { onAddToCart(product) },
                onQuantityChange = { onQuantityChange(product.sku, it) },
                onRemoveItem = { onRemoveItem(product.sku) }
            )
        }
    }
}

@Composable
private fun ProductDetailSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        ShimmerBox(
            shape = RoundedCornerShape(Radius.ImageTile),
            modifier = Modifier.fillMaxWidth().aspectRatio(1.1f)
        )
        SkeletonText(width = 220.dp, height = 18.dp)
        SkeletonText(width = 160.dp, height = 13.dp)
        SkeletonText(width = 120.dp, height = 13.dp)
        SkeletonText(width = 200.dp, height = 28.dp)
        SkeletonText(width = 260.dp, height = 88.dp, shape = RoundedCornerShape(Radius.Card))
    }
}

@Composable
private fun ProductDetailError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Couldn't load this medicine", style = CommerceTypography.Body, fontWeight = FontWeight.SemiBold, color = CommerceColors.TextPrimary)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(message, style = CommerceTypography.Caption, color = CommerceColors.TextMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary), shape = RoundedCornerShape(Radius.Button)) {
                Text("Try again", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProductDetailContent(
    product: CommerceProduct,
    cartQuantity: Int,
    onAddToCart: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    onRemoveItem: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(CommerceColors.Background)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.xl)
        ) {
            item { HeroImageSection(product = product) }

            item { TitleBlock(product = product) }

            item {
                Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
                    PriceBlock(product = product)
                    DeliveryPromiseRow(product = product)
                }
            }

            if (product.medicineDetails != null) {
                item { AboutMedicineDetailsSection(product = product, details = product.medicineDetails) }
            }
        }

        StickyPurchaseBar(
            product = product,
            cartQuantity = cartQuantity,
            onAddToCart = onAddToCart,
            onQuantityChange = onQuantityChange,
            onRemoveItem = onRemoveItem
        )
    }
}

@Composable
private fun HeroImageSection(product: CommerceProduct) {
    val discountPercent = product.discountPercent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CommerceColors.Surface)
            .padding(Spacing.lg)
    ) {
        ProductImage(
            imageUrl = product.image,
            contentDescription = product.name,
            contentScale = ContentScale.Fit,
            shape = RoundedCornerShape(Radius.ImageTile),
            modifier = Modifier.fillMaxWidth().aspectRatio(1.1f)
        )
        if (discountPercent > 0) {
            Surface(
                color = CommerceColors.Discount,
                shape = RoundedCornerShape(Radius.Chip),
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md)
            ) {
                Text(
                    "$discountPercent% off",
                    style = CommerceTypography.Meta,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                )
            }
        }
    }
}

@Composable
private fun TitleBlock(product: CommerceProduct) {
    Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        val brandStr = product.brandName ?: product.brand
        if (!brandStr.isNullOrBlank()) {
            Text(brandStr, style = CommerceTypography.Label, fontWeight = FontWeight.SemiBold, color = CommerceColors.Delivery)
        }
        Text(product.name, style = CommerceTypography.Heading, fontWeight = FontWeight.SemiBold, color = CommerceColors.TextPrimary)
        val unitStr = product.unitLabel
        if (!unitStr.isNullOrBlank()) {
            Text(unitStr, style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
        }

        val rating = product.rating
        if (rating != null) {
            Spacer(modifier = Modifier.height(Spacing.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, contentDescription = null, tint = CommerceColors.Rating, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    "${rating}${product.reviewCount?.let { " · $it reviews" } ?: ""}",
                    style = CommerceTypography.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = CommerceColors.NeutralDark
                )
            }
        }
    }
}

@Composable
private fun PriceBlock(product: CommerceProduct) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                MoneyFormatter.format(product.sellingPrice),
                style = CommerceTypography.Price,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.TextPrimary
            )
            if (product.price > product.sellingPrice) {
                Spacer(modifier = Modifier.width(Spacing.md))
                Text(
                    "MRP ${MoneyFormatter.format(product.price)}",
                    style = CommerceTypography.Body,
                    color = CommerceColors.TextMuted,
                    textDecoration = TextDecoration.LineThrough,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            if (product.discountPercent > 0) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                Surface(color = CommerceColors.DiscountSoft, shape = RoundedCornerShape(Radius.Chip)) {
                    Text(
                        "${product.discountPercent}% off",
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.Discount,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                    )
                }
            }
        }
        if (product.price > product.sellingPrice) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                "You save ${MoneyFormatter.format(product.price - product.sellingPrice)}",
                style = CommerceTypography.Label,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.Savings
            )
        }
    }
}

@Composable
private fun DeliveryPromiseRow(product: CommerceProduct) {
    Spacer(modifier = Modifier.height(Spacing.md))
    Surface(color = CommerceColors.DeliverySoft, shape = RoundedCornerShape(Radius.Card), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                "Standard Delivery Available",
                style = CommerceTypography.BodySmall,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.Delivery
            )
            if (product.medicineDetails?.coldChain == true) {
                Text(
                    "Temperature-controlled (2-8°C) delivery",
                    style = CommerceTypography.Meta,
                    color = CommerceColors.ColdChain
                )
            }
            if (product.inStock == false) {
                Text("Out of stock", style = CommerceTypography.Meta, fontWeight = FontWeight.SemiBold, color = CommerceColors.OutOfStock)
            }
        }
    }
}

@Composable
private fun SubstitutesSection(substitutes: List<ApiMedicine>, current: ApiMedicine, onAddSubstitute: (ApiMedicine) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Flat),
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text("Save with an equivalent", style = CommerceTypography.Title, fontWeight = FontWeight.SemiBold, color = CommerceColors.TextPrimary)
            Text("Same composition or category, compared live from the catalog", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)

            Spacer(modifier = Modifier.height(Spacing.md))
            substitutes.forEachIndexed { index, sub ->
                SubstituteRow(substitute = sub, currentPrice = current.discountedPrice, onAdd = { onAddSubstitute(sub) })
                if (index != substitutes.lastIndex) {
                    HorizontalDivider(color = CommerceColors.Border, thickness = 0.5.dp, modifier = Modifier.padding(vertical = Spacing.sm))
                }
            }
        }
    }
}

@Composable
private fun SubstituteRow(substitute: ApiMedicine, currentPrice: Double, onAdd: () -> Unit) {
    val save = if (currentPrice > substitute.discountedPrice) currentPrice - substitute.discountedPrice else 0.0
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(substitute.name, style = CommerceTypography.Body, fontWeight = FontWeight.SemiBold, color = CommerceColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(substitute.brandName, substitute.packSize).filter { it.isNotBlank() }.joinToString(" • "),
                style = CommerceTypography.Meta,
                color = CommerceColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(MoneyFormatter.format(substitute.discountedPrice), style = CommerceTypography.Body, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                if (substitute.price > substitute.discountedPrice) {
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text("MRP ${MoneyFormatter.format(substitute.price)}", style = CommerceTypography.Meta, color = CommerceColors.TextMuted, textDecoration = TextDecoration.LineThrough)
                }
                if (save > 0) {
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text("Save ₹${MoneyFormatter.format(save)}", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Savings)
                }
            }
        }
        Spacer(modifier = Modifier.width(Spacing.md))
        Button(
            onClick = onAdd,
            enabled = substitute.inStock != false,
            colors = ButtonDefaults.buttonColors(
                containerColor = CommerceColors.Primary,
                contentColor = CommerceColors.OnPrimary,
                disabledContainerColor = CommerceColors.OutOfStockSoft,
                disabledContentColor = CommerceColors.OutOfStock
            ),
            shape = RoundedCornerShape(Radius.Button),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            modifier = Modifier.defaultMinSize(minHeight = 36.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text("Add", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AboutMedicineDetailsSection(product: CommerceProduct, details: com.commerceos.android.model.MedicineAttributes) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Flat),
        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Text("About this medicine", style = CommerceTypography.Title, fontWeight = FontWeight.SemiBold, color = CommerceColors.TextPrimary)

            if (!details.composition.isNullOrBlank()) {
                InfoSection(title = "Composition", body = details.composition)
            }
            if (!details.manufacturer.isNullOrBlank()) {
                InfoSection(title = "Manufacturer", body = details.manufacturer)
            }
            if (!details.packaging.isNullOrBlank()) {
                InfoSection(title = "Packaging", body = details.packaging)
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, body: String) {
    Column {
        Text(title, style = CommerceTypography.Label, fontWeight = FontWeight.SemiBold, color = CommerceColors.TextPrimary)
        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(body, style = CommerceTypography.BodySmall, color = CommerceColors.TextSecondary)
    }
}

@Composable
private fun StickyPurchaseBar(
    product: CommerceProduct,
    cartQuantity: Int,
    onAddToCart: () -> Unit,
    onQuantityChange: (Int) -> Unit,
    onRemoveItem: () -> Unit
) {
    val outOfStock = product.inStock == false
    Surface(
        color = CommerceColors.Surface,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    MoneyFormatter.format(product.sellingPrice),
                    style = CommerceTypography.Price,
                    fontWeight = FontWeight.Black,
                    color = CommerceColors.TextPrimary
                )
                if (product.price > product.sellingPrice) {
                    Text(
                        "MRP ${MoneyFormatter.format(product.price)}",
                        style = CommerceTypography.Meta,
                        color = CommerceColors.TextMuted,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }

            if (cartQuantity > 0) {
                QuantityStepper(
                    quantity = cartQuantity,
                    onIncrease = { onQuantityChange(cartQuantity + 1) },
                    onDecrease = {
                        if (cartQuantity <= 1) onRemoveItem() else onQuantityChange(cartQuantity - 1)
                    }
                )
            } else {
                Button(
                    onClick = onAddToCart,
                    enabled = !outOfStock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0C831F),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFF1F5F9),
                        disabledContentColor = Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    if (outOfStock) {
                        Text("Out of stock", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Add to cart", fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantityStepper(quantity: Int, onIncrease: () -> Unit, onDecrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(
            onClick = onDecrease,
            shape = RoundedCornerShape(Radius.Button),
            colors = IconButtonDefaults.outlinedIconButtonColors(contentColor = CommerceColors.Primary),
            modifier = Modifier.size(44.dp)
        ) {
            Text("−", style = CommerceTypography.BodyLarge, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
        }
        Text(
            quantity.toString(),
            style = CommerceTypography.Body,
            fontWeight = FontWeight.Bold,
            color = CommerceColors.TextPrimary,
            modifier = Modifier.padding(horizontal = Spacing.lg)
        )
        Button(
            onClick = onIncrease,
            colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary, contentColor = CommerceColors.OnPrimary),
            shape = RoundedCornerShape(Radius.Button),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(44.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
        }
    }
}

private fun discountPercentOf(medicine: ApiMedicine): Int =
    if (medicine.price > medicine.discountedPrice) {
        (((medicine.price - medicine.discountedPrice) / medicine.price) * 100).toInt()
    } else 0