package com.commerceos.android.ui.cart

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.model.CartItem
import com.commerceos.android.model.MedicineImageResolver
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
    deliveryAddress: ApiAddress? = null,
    onChangeAddress: (() -> Unit)? = null,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onUploadPrescription: () -> Unit,
    onAttachPrescription: (String?) -> Unit,
    onProceedToAddress: () -> Unit,
    onStartShopping: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    if (cartItems.isEmpty()) {
        EmptyCart(onStartShopping = onStartShopping, onBack = onBack)
        return
    }

    val totalItemCount = cartItems.sumOf { it.quantity }
    val rxRequiredInCart = cartItems.any { it.prescriptionRequired }
    val isRxBlocked = rxRequiredInCart && attachedPrescriptionId.isNullOrBlank()
    val isAddressSaved = deliveryAddress != null &&
        !deliveryAddress.id.startsWith("temp_gps_") &&
        !deliveryAddress.tag.equals("Current Location", ignoreCase = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top Header with Back button and Item count
        Surface(
            color = Color.White,
            shadowElevation = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onBack?.invoke() ?: onStartShopping() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Checkout",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "$totalItemCount ${if (totalItemCount == 1) "item" else "items"} in cart",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
        ) {
            // Server-authoritative Free Delivery Progress
            item {
                FreeDeliveryBanner(
                    subtotal = cartSubtotal,
                    threshold = freeDeliveryThreshold,
                    eligible = freeDeliveryEligible,
                    remaining = remainingForFreeDelivery
                )
            }

            // Blinkit-style Unified Shipment Card
            item {
                CartShipmentCard(
                    cartItems = cartItems,
                    onQuantityChange = onQuantityChange,
                    onRemoveItem = onRemoveItem,
                    onAddMoreItems = onStartShopping
                )
            }

            // Prescription verification card
            item {
                OptionalPrescriptionCard(
                    attachedRx = prescriptions.firstOrNull { it.id == attachedPrescriptionId },
                    savedPrescriptions = prescriptions,
                    onUploadPrescription = onUploadPrescription,
                    onAttachPrescription = onAttachPrescription
                )
            }

            // Bill breakdown
            item {
                SummaryCard(
                    itemsSubtotal = itemsSubtotal,
                    totalSavings = totalSavings,
                    expressFee = expressFee,
                    coldChainFee = coldChainFee,
                    grandTotal = grandTotal
                )
            }

            // Cancellation Policy widget
            item {
                CancellationPolicyWidget()
            }
        }

        // Pinned sticky bottom checkout bar (Address strip + Total + CTA)
        StickyCheckoutBar(
            grandTotal = grandTotal,
            totalSavings = totalSavings,
            rxBlocked = isRxBlocked,
            hasSavedAddress = isAddressSaved,
            deliveryAddress = deliveryAddress,
            onChangeAddress = onChangeAddress,
            onProceed = onProceedToAddress,
            onUploadPrescription = onUploadPrescription
        )
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
private fun EmptyCart(
    onStartShopping: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        Surface(
            color = Color.White,
            shadowElevation = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A)
                        )
                    }
                }
                Text(
                    text = "Checkout",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.padding(start = if (onBack != null) 4.dp else 16.dp)
                )
            }
        }
        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)

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

        Surface(
            color = if (unlocked) Color(0xFFECFDF5) else Color.White,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, if (unlocked) Color(0xFF86EFAC) else Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (unlocked) "🎉" else "⚡",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    if (unlocked) {
                        Text(
                            text = "Free Express Delivery unlocked!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Add ", fontSize = 12.sp, color = Color(0xFF334155))
                            Text(
                                MoneyFormatter.format(rem),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF059669)
                            )
                            Text(" more for ", fontSize = 12.sp, color = Color(0xFF334155))
                            Text(
                                "FREE Delivery",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    color = if (unlocked) Color(0xFF10B981) else Color(0xFF059669),
                    trackColor = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                )
            }
        }
    }
}

@Composable
private fun CartShipmentCard(
    cartItems: List<CartItem>,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit,
    onAddMoreItems: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Shipment Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("⚡", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "10 MINS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF059669)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                val totalQty = cartItems.sumOf { it.quantity }
                Text(
                    text = "Delivery in 10 minutes • $totalQty ${if (totalQty == 1) "item" else "items"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Items List
            cartItems.forEachIndexed { index, item ->
                CartItemRow(
                    item = item,
                    onQuantityChange = onQuantityChange,
                    onRemoveItem = onRemoveItem
                )
                if (index < cartItems.size - 1) {
                    HorizontalDivider(
                        color = Color(0xFFF1F5F9),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

            // Footer: Add more items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddMoreItems() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add more items",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onQuantityChange: (String, Int) -> Unit,
    onRemoveItem: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val resolvedImage = remember(item.sku, item.name, item.image) {
            item.image?.takeIf { it.isNotBlank() } ?: MedicineImageResolver.resolve(item.sku, item.name)
        }
        ProductImage(
            imageUrl = resolvedImage,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .size(56.dp)
                .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(8.dp))
                .border(BorderStroke(0.5.dp, Color(0xFFE2E8F0)), shape = RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name.orEmpty().ifBlank { "Medicine Item" },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            val identity = listOfNotNull(item.brand, item.packSize).filter { it.isNotBlank() }
            if (identity.isNotEmpty()) {
                Text(
                    identity.joinToString(" • "),
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (item.prescriptionRequired) {
                Surface(
                    color = Color(0xFFFFE4E6),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Text(
                        "Rx Required",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE11D48),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val mrpVal = item.mrp
            val lineSavings = (mrpVal - item.unitPrice).multiply(BigDecimal.valueOf(item.quantity.toLong())).coerceAtLeast(BigDecimal.ZERO)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    MoneyFormatter.format(item.unitPrice),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                if (mrpVal > item.unitPrice) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        MoneyFormatter.format(mrpVal),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        textDecoration = TextDecoration.LineThrough
                    )
                }
                if (lineSavings > BigDecimal.ZERO) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Save ${MoneyFormatter.format(lineSavings)}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        QuantityControls(
            quantity = item.quantity,
            onDecrease = {
                if (item.quantity <= 1) onRemoveItem(item.sku)
                else onQuantityChange(item.sku, item.quantity - 1)
            },
            onIncrease = { onQuantityChange(item.sku, item.quantity + 1) }
        )
    }
}

@Composable
private fun QuantityControls(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Surface(
        color = Color(0xFFECFDF5),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(30.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clickable { onDecrease() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "−",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF059669)
                )
            }
            Text(
                text = quantity.toString(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF059669),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clickable { onIncrease() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF059669)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionalPrescriptionCard(
    attachedRx: Prescription?,
    savedPrescriptions: List<Prescription>,
    onUploadPrescription: () -> Unit,
    onAttachPrescription: (String?) -> Unit
) {
    var showSavedRxSheet by remember { mutableStateOf(false) }
    var previewPrescription by remember { mutableStateOf<Prescription?>(null) }

    if (showSavedRxSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSavedRxSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Select from Saved Prescriptions",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Choose a prescription already uploaded in your vault",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    IconButton(onClick = { showSavedRxSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (savedPrescriptions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No saved prescriptions found in your vault", fontSize = 14.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    showSavedRxSheet = false
                                    onUploadPrescription()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Upload New Prescription", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                    ) {
                        items(savedPrescriptions) { rx ->
                            val isSelected = rx.id == attachedRx?.id
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFECFDF5) else Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isSelected) Color(0xFF059669) else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAttachPrescription(rx.id)
                                        showSavedRxSheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = if (isSelected) Color(0xFF059669) else Color(0xFFF1F5F9),
                                        shape = CircleShape,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = rx.patientName.ifBlank { "Patient Prescription" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "Rx #${rx.id.takeLast(6).uppercase()} • ${if (rx.status == "APPROVED") "Verified Doctor Rx" else "Uploaded for Pharmacist"}",
                                            fontSize = 12.sp,
                                            color = if (rx.status == "APPROVED") Color(0xFF059669) else Color(0xFF64748B)
                                        )
                                    }
                                    IconButton(
                                        onClick = { previewPrescription = rx },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(com.commerceos.android.R.drawable.ic_visibility),
                                            contentDescription = "Quick view prescription",
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (isSelected) {
                                        Surface(
                                            color = Color(0xFF059669),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                "Selected",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedButton(
                        onClick = {
                            showSavedRxSheet = false
                            onUploadPrescription()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Upload New Photo Instead", color = Color(0xFF059669), fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(Radius.Card),
        border = BorderStroke(1.dp, if (attachedRx != null) Color(0xFF059669).copy(alpha = 0.3f) else Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (attachedRx != null) Color(0xFFECFDF5) else Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (attachedRx != null) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Doctor's Prescription",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Surface(
                    color = if (attachedRx != null) Color(0xFFECFDF5) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (attachedRx != null) "ATTACHED" else "OPTIONAL",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (attachedRx != null) Color(0xFF059669) else Color(0xFF64748B),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (attachedRx != null) {
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Attached: ${attachedRx.patientName.ifBlank { "Patient Rx" }}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Rx #${attachedRx.id.takeLast(6).uppercase()} • Pharmacist will verify",
                                fontSize = 11.sp,
                                color = Color(0xFF059669)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { showSavedRxSheet = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Change", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                            }
                            TextButton(
                                onClick = { onAttachPrescription(null) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Upload prescription for licensed pharmacist verification (optional). You can still proceed without it.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (savedPrescriptions.isNotEmpty()) {
                                showSavedRxSheet = true
                            } else {
                                onUploadPrescription()
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = null,
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (savedPrescriptions.isNotEmpty()) "From Vault (${savedPrescriptions.size})" else "Select Saved",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        )
                    }

                    Button(
                        onClick = onUploadPrescription,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Upload New",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }

    if (previewPrescription != null) {
        PrescriptionQuickViewDialog(
            prescription = previewPrescription!!,
            onDismiss = { previewPrescription = null }
        )
    }
}

@Composable
private fun PrescriptionQuickViewDialog(
    prescription: Prescription,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = CircleShape,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Rx", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF059669))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Prescription Slip",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "ID: #${prescription.id.takeLast(6).uppercase()}",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Prescription Image or Clinical Certificate
                val firstAttachment = prescription.attachments.firstOrNull()?.takeIf { it.isNotBlank() }
                if (firstAttachment != null) {
                    Surface(
                        color = Color(0xFF0F172A),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 340.dp)
                    ) {
                        SubcomposeAsyncImage(
                            model = firstAttachment,
                            contentDescription = "Prescription Image",
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                                }
                            },
                            error = {
                                ClinicalPrescriptionCard(prescription = prescription)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    ClinicalPrescriptionCard(prescription = prescription)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Meta summary strip
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Patient Name", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(prescription.patientName.ifBlank { "Patient Rx" }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        }
                        if (!prescription.doctorName.isNullOrBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Prescribing Doctor", fontSize = 12.sp, color = Color(0xFF64748B))
                                Text("Dr. ${prescription.doctorName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pharmacist Status", fontSize = 12.sp, color = Color(0xFF64748B))
                            Surface(
                                color = if (prescription.status == "APPROVED") Color(0xFFECFDF5) else Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (prescription.status == "APPROVED") "VERIFIED & VALID" else "PHARMACIST VERIFICATION PENDING",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (prescription.status == "APPROVED") Color(0xFF059669) else Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ClinicalPrescriptionCard(prescription: Prescription) {
    Surface(
        color = Color(0xFFF0FDF4),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, Color(0xFF86EFAC)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DIGITAL PRESCRIPTION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF166534),
                    letterSpacing = 1.sp
                )
                Text(
                    "VALID MEDICAL SLIP",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF15803D)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Dr. ${prescription.doctorName ?: "R. K. Sharma, M.D."}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = "Registration: ${prescription.doctorRegistrationNo ?: "MCI-48921 / KA"}",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFBBF7D0), thickness = 0.8.dp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Patient: ${prescription.patientName.ifBlank { "Authorized Patient" }}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1E293B)
            )
            if (prescription.note.isNotBlank()) {
                Text(
                    text = "Notes: ${prescription.note}",
                    fontSize = 12.sp,
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pharmacist Verified", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
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
            // Domino's / Blinkit Style Savings Banner
            if (!totalSavings.isNullOrBlank() && totalSavings != "₹0.00" && totalSavings != "₹0") {
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC).copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏷️", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "You are saving $totalSavings on this order!",
                            style = CommerceTypography.BodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF166534)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            Text("Bill Summary", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            SummaryRow(label = "Item total", value = itemsSubtotal ?: "₹0.00")
            if (!totalSavings.isNullOrBlank() && totalSavings != "₹0.00") {
                SummaryRow(label = "Product discount", value = "- $totalSavings", valueColor = CommerceColors.Savings)
            }
            SummaryRow(label = "Delivery partner fee", value = expressFee ?: "FREE", valueColor = if (expressFee == "FREE" || expressFee == null) Color(0xFF16A34A) else CommerceColors.TextPrimary)
            if (!coldChainFee.isNullOrBlank()) {
                SummaryRow(label = "Handling & packaging", value = coldChainFee)
            }
            HorizontalDivider(color = CommerceColors.Border, thickness = 0.5.dp)
            SummaryRow(label = "Grand Total", value = grandTotal ?: itemsSubtotal ?: "₹0.00", boldValue = true, labelColor = CommerceColors.TextPrimary)
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
    hasSavedAddress: Boolean = true,
    deliveryAddress: ApiAddress? = null,
    onChangeAddress: (() -> Unit)? = null,
    onProceed: () -> Unit,
    onUploadPrescription: () -> Unit
) {
    val isCompleteAddress = deliveryAddress != null &&
        !deliveryAddress.id.startsWith("temp_gps_") &&
        !deliveryAddress.tag.equals("Current Location", ignoreCase = true)

    Surface(
        color = Color.White,
        shadowElevation = 12.dp,
        border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Blinkit-style pinned address strip directly above the CTA
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .then(if (onChangeAddress != null) Modifier.clickable { onChangeAddress() } else Modifier)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = if (isCompleteAddress) Color(0xFFECFDF5) else Color(0xFFFEF3C7),
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isCompleteAddress) Icons.Default.Place else Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isCompleteAddress) Color(0xFF059669) else Color(0xFFD97706),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isCompleteAddress) {
                                    "Delivering to ${deliveryAddress?.tag?.ifBlank { "Home" }}"
                                } else {
                                    "Select delivery address"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        val addressText = if (isCompleteAddress) {
                            listOfNotNull(
                                deliveryAddress?.addressLine?.takeIf { it.isNotBlank() },
                                deliveryAddress?.city?.takeIf { it.isNotBlank() }
                            ).joinToString(", ").ifBlank { "Saved address" }
                        } else {
                            "Tap to choose or enter your address"
                        }
                        Text(
                            text = addressText,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (isCompleteAddress) "Change ›" else "Add ›",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF059669)
                )
            }

            HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)

            // Checkout CTA Action row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "⚡ 10 MINS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF059669),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        totalSavings?.takeIf { it != "₹0.00" && it != "₹0" }?.let {
                            Text(
                                "Saved $it",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = grandTotal ?: "₹0.00",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                }

                if (rxBlocked) {
                    Button(
                        onClick = onUploadPrescription,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFE11D48),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("Upload Prescription", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (!hasSavedAddress) {
                    Button(
                        onClick = onProceed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("Select Address ➔", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                } else {
                    Button(
                        onClick = onProceed,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF059669),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                        modifier = Modifier.height(46.dp)
                    ) {
                        Text("Continue ➔", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}