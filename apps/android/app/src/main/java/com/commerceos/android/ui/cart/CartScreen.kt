package com.commerceos.android.ui.cart

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CartItem
import com.commerceos.android.model.Prescription
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceElevation
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun CartScreen(
    cartItems: List<CartItem>,
    cartSubtotal: BigDecimal?,
    freeDeliveryThreshold: BigDecimal?,
    freeDeliveryEligible: Boolean?,
    remainingForFreeDelivery: BigDecimal?,
    itemsSubtotal: String?,
    totalSavings: String?,
    expressFee: String?,
    coldChainFee: String?,
    grandTotal: String?,
    prescriptions: List<Prescription>,
    attachedPrescriptionId: String?,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onUploadPrescription: () -> Unit,
    onAttachPrescription: (String?) -> Unit,
    onProceedToAddress: () -> Unit,
    onStartShopping: () -> Unit
) {
    if (cartItems.isEmpty()) {
        EmptyCart(onStartShopping = onStartShopping)
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.lg)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            contentPadding = PaddingValues(top = Spacing.md, bottom = Spacing.lg)
        ) {
            item {
                FreeDeliveryBanner(
                    subtotal = cartSubtotal,
                    threshold = freeDeliveryThreshold,
                    eligible = freeDeliveryEligible,
                    remaining = remainingForFreeDelivery
                )
            }

            items(cartItems) { item ->
                CartItemCard(item = item, onQuantityChange = onQuantityChange, onRemoveItem = onRemoveItem)
            }

            item {
                DeliveryPartnerTipWidget()
            }

            item {
                CancellationPolicyWidget()
            }

            item {
                SummaryCard(
                    itemsSubtotal = itemsSubtotal,
                    totalSavings = totalSavings,
                    expressFee = expressFee,
                    coldChainFee = coldChainFee,
                    grandTotal = grandTotal
                )
            }
        }

        StickyCheckoutBar(
            grandTotal = grandTotal,
            totalSavings = totalSavings,
            rxBlocked = false,
            onProceed = onProceedToAddress,
            onUploadPrescription = {}
        )
    }
}

@Composable
private fun DeliveryPartnerTipWidget() {
    var selectedTip by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val tipOptions = listOf(10, 20, 30, 50)

    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("🛵", fontSize = 16.sp)
                    }
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                Column {
                    Text("Tip your delivery partner", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Text("100% of the tip goes to your partner", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                tipOptions.forEach { tip ->
                    val isSelected = selectedTip == tip
                    Surface(
                        color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.15f) else CommerceColors.SurfaceSubtle,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color(0xFF10B981) else CommerceColors.Border
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTip = if (isSelected) 0 else tip }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = "₹$tip",
                                style = CommerceTypography.BodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF10B981) else CommerceColors.TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CancellationPolicyWidget() {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.SurfaceSubtle),
        shape = RoundedCornerShape(Radius.Card),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("ℹ️", fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column {
                Text("Cancellation Policy", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Text("Orders cannot be cancelled once packed by the store to ensure 10-minute delivery speed.", style = CommerceTypography.Meta, color = CommerceColors.TextMuted, lineHeight = 14.sp)
            }
        }
    }
}

@Composable
private fun EmptyCart(onStartShopping: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(Spacing.xxl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = CommerceColors.NeutralLight, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(Spacing.lg))
            Text("Your cart is empty", style = CommerceTypography.Title, fontWeight = FontWeight.SemiBold, color = CommerceColors.TextPrimary)
            Text("Add medicines to your cart to start your order", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = onStartShopping,
                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary, contentColor = CommerceColors.OnPrimary),
                shape = RoundedCornerShape(Radius.Button)
            ) {
                Text("Start shopping", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Free-delivery progress. The threshold and the fee waiver itself are
 * SERVER-AUTHORITATIVE (returned by GET /cart) — this bar only mirrors reality.
 */
@Composable
private fun FreeDeliveryBanner(
    subtotal: BigDecimal?,
    threshold: BigDecimal?,
    eligible: Boolean?,
    remaining: BigDecimal?
) {
    if (threshold != null && threshold > BigDecimal.ZERO) {
        val current = subtotal ?: BigDecimal.ZERO
        val rem = remaining ?: (threshold - current).coerceAtLeast(BigDecimal.ZERO)
        val unlocked = eligible ?: (rem <= BigDecimal.ZERO)
        val progress = if (threshold > BigDecimal.ZERO) {
            (current.toDouble() / threshold.toDouble()).toFloat().coerceIn(0f, 1f)
        } else 0f

        Card(
            colors = CardDefaults.cardColors(containerColor = if (unlocked) CommerceColors.SavingsSoft else CommerceColors.Surface),
            shape = RoundedCornerShape(Radius.Card),
            elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Flat)
        ) {
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    if (unlocked) "🎉 Free delivery unlocked!"
                    else "Add ${MoneyFormatter.format(rem)} more for FREE Express Delivery",
                    style = CommerceTypography.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (unlocked) CommerceColors.Savings else CommerceColors.TextPrimary
                )
                LinearProgressIndicator(
                    progress = { progress },
                    color = if (unlocked) CommerceColors.Savings else CommerceColors.Primary,
                    trackColor = CommerceColors.Placeholder,
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
            }
        }
    }
}

@Composable
private fun CartItemCard(
    item: CartItem,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised)
    ) {
        Row(modifier = Modifier.padding(Spacing.md)) {
            ProductImage(
                imageUrl = item.image,
                contentDescription = item.name,
                contentScale = ContentScale.Fit,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(76.dp)
            )

            Spacer(modifier = Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = item.name.orEmpty().ifBlank { "Medicine Item" },
                        style = CommerceTypography.Body,
                        fontWeight = FontWeight.SemiBold,
                        color = CommerceColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onRemoveItem(item.sku) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = CommerceColors.NeutralLight, modifier = Modifier.size(18.dp))
                    }
                }

                val identity = listOfNotNull(item.brand, item.packSize).filter { it.isNotBlank() }
                if (identity.isNotEmpty()) {
                    Text(
                        identity.joinToString(" • "),
                        style = CommerceTypography.Meta,
                        color = CommerceColors.TextMuted,
                        maxLines = 1
                    )
                }

                if (item.prescriptionRequired) {
                    Text(
                        "Prescription required",
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.SemiBold,
                        color = CommerceColors.Rx
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                val mrpVal = item.mrp ?: item.unitPrice
                val lineSavings = (mrpVal - item.unitPrice).multiply(BigDecimal.valueOf(item.quantity.toLong())).coerceAtLeast(BigDecimal.ZERO)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(MoneyFormatter.format(item.unitPrice), style = CommerceTypography.Body, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    if (mrpVal > item.unitPrice) {
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("MRP ${MoneyFormatter.format(mrpVal)}", style = CommerceTypography.Meta, color = CommerceColors.TextMuted, textDecoration = TextDecoration.LineThrough)
                    }
                    if (lineSavings > BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Text("Save ${MoneyFormatter.format(lineSavings)}", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Savings)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    QuantityControls(
                        quantity = item.quantity,
                        onDecrease = {
                            if (item.quantity <= 1) onRemoveItem(item.sku)
                            else onQuantityChange(item.sku, item.quantity - 1)
                        },
                        onIncrease = { onQuantityChange(item.sku, item.quantity + 1) }
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        MoneyFormatter.format(item.unitPrice.multiply(BigDecimal.valueOf(item.quantity.toLong()))),
                        style = CommerceTypography.Body,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantityControls(quantity: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = onDecrease,
            shape = RoundedCornerShape(Radius.Button),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (quantity <= 1) CommerceColors.DangerSoft else CommerceColors.SurfaceSubtle,
                contentColor = if (quantity <= 1) CommerceColors.Danger else CommerceColors.TextPrimary
            ),
            modifier = Modifier.size(40.dp)
        ) {
            if (quantity <= 1) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove item from cart",
                    tint = CommerceColors.Danger,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text("−", style = CommerceTypography.BodyLarge, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            quantity.toString(),
            style = CommerceTypography.BodySmall,
            fontWeight = FontWeight.Bold,
            color = CommerceColors.TextPrimary,
            modifier = Modifier.padding(horizontal = Spacing.md)
        )
        FilledTonalIconButton(
            onClick = onIncrease,
            shape = RoundedCornerShape(Radius.Button),
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase quantity", modifier = Modifier.size(18.dp))
        }
    }
}

/** Rx block with patient-facing wording — never internal prescription ids. */
@Composable
private fun PrescriptionCard(
    attachedRx: Prescription?,
    approvedPrescriptions: List<Prescription>,
    onUploadPrescription: () -> Unit,
    onAttachPrescription: (String?) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Flat)
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            when {
                attachedRx?.status == "APPROVED" -> {
                    Surface(color = CommerceColors.SavingsSoft, shape = RoundedCornerShape(Radius.Card), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Prescription approved", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.Savings)
                                Text("Patient: ${attachedRx.patientName.orEmpty().ifBlank { "Verified Patient" }}", style = CommerceTypography.Meta, color = CommerceColors.TextSecondary)
                            }
                            TextButton(onClick = { onAttachPrescription(null) }) {
                                Text("Detach", style = CommerceTypography.Label, color = CommerceColors.TextMuted)
                            }
                        }
                    }
                }

                attachedRx != null -> {
                    Surface(color = CommerceColors.VerificationSoft, shape = RoundedCornerShape(Radius.Card), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text("Prescription submitted", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.Verification)
                            Text("Awaiting pharmacist review before checkout.", style = CommerceTypography.Meta, color = CommerceColors.TextSecondary)
                        }
                    }
                }

                else -> {
                    Surface(color = CommerceColors.RxSoft, shape = RoundedCornerShape(Radius.Card), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text("Prescription required", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.Rx)
                            Text("A pharmacist-approved prescription is needed for items in this cart.", style = CommerceTypography.Meta, color = CommerceColors.TextSecondary)
                        }
                    }
                }
            }

            if (attachedRx?.status != "APPROVED") {
                OutlinedButton(
                    onClick = onUploadPrescription,
                    shape = RoundedCornerShape(Radius.Button),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Upload prescription", style = CommerceTypography.Label, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                }
                if (approvedPrescriptions.isNotEmpty()) {
                    Text("Or attach an approved one:", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                    approvedPrescriptions.forEach { rx ->
                        Surface(
                            color = CommerceColors.SurfaceSubtle,
                            shape = RoundedCornerShape(Radius.Chip),
                            onClick = { onAttachPrescription(rx.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${rx.patientName.orEmpty().ifBlank { "Patient" }} • Pharmacist approved", style = CommerceTypography.BodySmall, color = CommerceColors.TextPrimary, modifier = Modifier.padding(Spacing.md))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    itemsSubtotal: String?,
    totalSavings: String?,
    expressFee: String?,
    coldChainFee: String?,
    grandTotal: String?
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Price details", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            SummaryRow(label = "Items total", value = itemsSubtotal ?: "₹0.00")
            if (!totalSavings.isNullOrBlank() && totalSavings != "₹0.00") {
                SummaryRow(label = "You save", value = totalSavings, valueColor = CommerceColors.Savings)
            }
            SummaryRow(label = "Delivery fee", value = expressFee ?: "FREE")
            if (!coldChainFee.isNullOrBlank()) {
                SummaryRow(label = "Cold-chain packaging", value = coldChainFee)
            }
            HorizontalDivider(color = CommerceColors.Border, thickness = 0.5.dp)
            SummaryRow(label = "Total Amount", value = grandTotal ?: itemsSubtotal ?: "₹0.00", boldValue = true, labelColor = CommerceColors.TextPrimary)
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String?,
    valueColor: androidx.compose.ui.graphics.Color = CommerceColors.TextPrimary,
    boldValue: Boolean = false,
    labelColor: androidx.compose.ui.graphics.Color = CommerceColors.TextMuted
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = CommerceTypography.BodySmall, color = labelColor)
        Text(value ?: "₹0.00", style = CommerceTypography.BodySmall, fontWeight = if (boldValue) FontWeight.Bold else FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun StickyCheckoutBar(
    grandTotal: String?,
    totalSavings: String?,
    rxBlocked: Boolean,
    onProceed: () -> Unit,
    onUploadPrescription: () -> Unit
) {
    Surface(
        color = CommerceColors.Surface,
        shadowElevation = CommerceElevation.Floating,
        shape = RoundedCornerShape(topStart = Radius.CardLarge, topEnd = Radius.CardLarge)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    grandTotal ?: "₹0.00",
                    style = CommerceTypography.Price,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.TextPrimary
                )
                totalSavings?.takeIf { it != "₹0.00" }?.let {
                    Text(
                        "You save $it",
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.Savings
                    )
                }
            }
            if (rxBlocked) {
                Button(
                    onClick = onUploadPrescription,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CommerceColors.Rx,
                        contentColor = CommerceColors.OnPrimary
                    ),
                    shape = RoundedCornerShape(Radius.Button),
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.md),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Upload Prescription", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onProceed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CommerceColors.Primary,
                        contentColor = CommerceColors.OnPrimary
                    ),
                    shape = RoundedCornerShape(Radius.Button),
                    contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = Spacing.md),
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Checkout", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}