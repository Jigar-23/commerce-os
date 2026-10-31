package com.commerceos.android.ui.product

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.ApiMedicine
import com.commerceos.android.model.CartItem
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.MedicineImageResolver
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.components.ShimmerBox
import com.commerceos.android.ui.components.SkeletonText
import com.commerceos.android.ui.components.formatHealthcareBrand
import com.commerceos.android.ui.components.formatHealthcareSalt
import com.commerceos.android.ui.components.formatHealthcareTitle
import com.commerceos.android.ui.components.formatPackSize
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.PlusJakartaSans
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.ProductDetailUiState

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
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.xl)
        ) {
            item { HeroImageSection(product = product) }

            item { TitleBlock(product = product) }

            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PriceBlock(product = product)
                    Spacer(modifier = Modifier.height(10.dp))
                    DeliveryPromiseRow(product = product)
                }
            }

            item {
                GenericSubstituteTeaserCard(
                    product = product,
                    onSwitch = onAddToCart
                )
            }

            item {
                TrustBadgesSection()
            }

            item {
                AboutMedicineDetailsSection(product = product)
            }

            item {
                SafetyAdviceSection()
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
    val resolvedImage = remember(product.sku, product.name, product.image) {
        MedicineImageResolver.resolve(sku = product.sku, name = product.name, rawImage = product.image)
    }
    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        shadowElevation = 1.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            ProductImage(
                imageUrl = resolvedImage,
                contentDescription = product.name,
                contentScale = ContentScale.Fit,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            )

            // Authenticity badge on top-left
            Surface(
                color = Color(0xFFECFDF5),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF047857),
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "100% Genuine Medicine",
                        fontSize = 10.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857)
                    )
                }
            }

            // Rating pill anchored on the bottom-left of the image tile (Tata 1mg benchmark)
            val ratingVal = product.rating ?: 4.4
            Surface(
                color = Color(0xFF166534),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", ratingVal),
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TitleBlock(product: CommerceProduct) {
    val brand = formatHealthcareBrand(product.brandName ?: product.brand, product.name)
    val title = formatHealthcareTitle(product.name)
    val salt = formatHealthcareSalt(product.name, product.medicineDetails?.composition)
    val pack = formatPackSize(product.unitLabel)

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Brand / Manufacturer overline
            Text(
                text = "By $brand".uppercase(),
                fontSize = 11.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF059669),
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Medicine Title
            Text(
                text = title,
                fontSize = 19.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Salt Composition Chip (Tata 1mg signature)
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        Icons.Default.Medication,
                        contentDescription = null,
                        tint = Color(0xFF047857),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = salt,
                        fontSize = 12.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pack size & Prescription status row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = pack,
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    color = Color(0xFF64748B)
                )

                Text("•", fontSize = 12.sp, color = Color(0xFFCBD5E1))

                val isRx = product.medicineDetails?.prescriptionRequired == true
                Surface(
                    color = if (isRx) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isRx) "Rx Required" else "OTC • No Prescription Required",
                        fontSize = 10.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = if (isRx) Color(0xFFDC2626) else Color(0xFF166534),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceBlock(product: CommerceProduct) {
    val mrp = product.price
    val sellingPrice = product.sellingPrice
    val discountPercent = if (mrp > sellingPrice) (((mrp - sellingPrice) / mrp) * 100).toInt() else 0
    val savings = mrp - sellingPrice

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = MoneyFormatter.format(sellingPrice),
                    fontSize = 24.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                if (savings > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MRP ${MoneyFormatter.format(mrp)}",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8),
                        textDecoration = TextDecoration.LineThrough,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "$discountPercent% OFF",
                            fontSize = 12.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (savings > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You save ${MoneyFormatter.format(savings)} on this purchase",
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A)
                )
            }
            Text(
                text = "Inclusive of all taxes",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun DeliveryPromiseRow(product: CommerceProduct) {
    val inStock = product.inStock != false

    Surface(
        color = if (inStock) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (inStock) Color(0xFFA7F3D0) else Color(0xFFFECACA)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = if (inStock) Color(0xFF047857) else Color(0xFFDC2626),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (inStock) Icons.Default.ElectricBolt else Icons.Default.LocalPharmacy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = if (inStock) "⚡ Guaranteed 10-Minute Delivery" else "Currently Out of Stock",
                    fontSize = 13.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = if (inStock) Color(0xFF065F46) else Color(0xFF991B1B)
                )
                Text(
                    text = if (inStock) "Direct from licensed retail chemist in your locality" else "We will notify you once fresh pharmacy stock arrives",
                    fontSize = 11.sp,
                    fontFamily = PlusJakartaSans,
                    color = if (inStock) Color(0xFF047857) else Color(0xFFB91C1C)
                )
            }
        }
    }
}

@Composable
private fun GenericSubstituteTeaserCard(
    product: CommerceProduct,
    onSwitch: () -> Unit
) {
    val title = product.name.lowercase()
    if (!title.contains("para") && !title.contains("dolo") && !title.contains("crocin")) return

    Surface(
        color = Color(0xFFF0FDF4),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, Color(0xFFA7F3D0)),
        shadowElevation = 0.5.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF047857),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        "SAVE UP TO 40%",
                        fontSize = 9.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Certified Branded Substitute Available",
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF065F46)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Paracip 650mg Tablet",
                        fontSize = 13.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        "Cipla Limited • Same Salt Paracetamol (650mg)",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            "₹18.00",
                            fontSize = 14.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF047857)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "MRP ₹22.00",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }

                Button(
                    onClick = onSwitch,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(
                        "SWITCH",
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun TrustBadgesSection() {
    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            TrustPill(icon = Icons.Default.Shield, label = "Authenticity", sub = "100% Genuine")
            TrustPill(icon = Icons.Default.CheckCircle, label = "NPPA Capped", sub = "Standard Price")
            TrustPill(icon = Icons.Default.VerifiedUser, label = "Licensed", sub = "Chemist Verified")
        }
    }
}

@Composable
private fun TrustPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sub: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = Color(0xFFECFDF5),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text(sub, fontSize = 10.sp, color = Color(0xFF64748B))
    }
}

@Composable
private fun AboutMedicineDetailsSection(product: CommerceProduct) {
    val details = product.medicineDetails
    val salt = formatHealthcareSalt(product.name, details?.composition)
    val manufacturer = formatHealthcareBrand(product.brandName ?: product.brand, product.name)
    val packaging = formatPackSize(product.unitLabel)

    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "About this medicine",
                fontSize = 15.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(12.dp))

            InfoRow(title = "Active Salt Composition", body = salt)
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
            InfoRow(title = "Manufacturer / Marketer", body = manufacturer)
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
            InfoRow(title = "Packaging Type", body = packaging)
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
            InfoRow(title = "Storage Instructions", body = "Store below 30°C in a dry place. Protect from direct sunlight and moisture.")
        }
    }
}

@Composable
private fun SafetyAdviceSection() {
    Surface(
        color = Color.White,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Safety Advice & Warnings",
                fontSize = 15.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )

            Spacer(modifier = Modifier.height(10.dp))

            SafetyItem(title = "Alcohol: Avoid", desc = "Consuming alcohol with paracetamol can increase the risk of serious liver damage.")
            Spacer(modifier = Modifier.height(8.dp))
            SafetyItem(title = "Pregnancy & Lactation: Consult Doctor", desc = "Generally considered safe under medical supervision. Consult doctor for optimal dosage.")
            Spacer(modifier = Modifier.height(8.dp))
            SafetyItem(title = "Driving: Safe", desc = "This medicine does not impair alertness or motor skills.")
        }
    }
}

@Composable
private fun SafetyItem(title: String, desc: String) {
    Column {
        Text(title, fontSize = 12.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
        Spacer(modifier = Modifier.height(2.dp))
        Text(desc, fontSize = 11.sp, fontFamily = PlusJakartaSans, color = Color(0xFF64748B), lineHeight = 15.sp)
    }
}

@Composable
private fun InfoRow(title: String, body: String) {
    Column {
        Text(title, fontSize = 11.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))
        Spacer(modifier = Modifier.height(2.dp))
        Text(body, fontSize = 13.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
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
        color = Color.White,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    MoneyFormatter.format(product.sellingPrice),
                    fontSize = 20.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                if (product.price > product.sellingPrice) {
                    Text(
                        "MRP ${MoneyFormatter.format(product.price)}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }

            if (cartQuantity > 0) {
                Surface(
                    color = Color(0xFF047857),
                    shape = RoundedCornerShape(10.dp),
                    shadowElevation = 2.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (cartQuantity <= 1) onRemoveItem() else onQuantityChange(cartQuantity - 1)
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Text(
                            text = cartQuantity.toString(),
                            fontSize = 15.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        IconButton(
                            onClick = { onQuantityChange(cartQuantity + 1) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            } else {
                Button(
                    onClick = onAddToCart,
                    enabled = !outOfStock,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF047857),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFF1F5F9),
                        disabledContentColor = Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.height(46.dp)
                ) {
                    if (outOfStock) {
                        Text("Out of Stock", fontWeight = FontWeight.Bold)
                    } else {
                        Text("ADD TO CART", fontSize = 13.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                    }
                }
            }
        }
    }
}