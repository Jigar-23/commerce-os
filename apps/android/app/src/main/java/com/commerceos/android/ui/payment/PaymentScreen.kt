package com.commerceos.android.ui.payment

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.MedicineImageResolver
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.CheckoutUiState
import com.commerceos.android.viewmodel.ServiceabilityState
import java.math.BigDecimal

object PaymentConstants {
    const val COD = "COD"
    const val UPI_INTENT = "UPI_INSTANT"
}

/**
 * Modern Review & Pay screen inspired by Blinkit, Swiggy, and Tata 1mg.
 * Eliminates text wrapping defects, displays ordered items with authentic thumbnails,
 * clear address verification, and solid COD placement CTA.
 */
@Composable
fun PaymentScreen(
    checkoutUiState: CheckoutUiState,
    onAuthorize: (String) -> Unit,
    onChangeAddress: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    var selectedMethod by remember { mutableStateOf(PaymentConstants.COD) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // Top App Bar
        Surface(
            color = Color.White,
            shadowElevation = 0.5.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
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
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Review and pay",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${checkoutUiState.items.size} ${if (checkoutUiState.items.size == 1) "item" else "items"} • 10-Minute Express Delivery",
                        fontSize = 11.sp,
                        color = Color(0xFF059669),
                        fontWeight = FontWeight.Medium
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
            // 1. DELIVERY ADDRESS CARD
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Deliver to",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = checkoutUiState.address?.tag ?: "Home",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = checkoutUiState.address?.let {
                                    listOfNotNull(it.addressLine, it.city).filter { s -> s.isNotBlank() }.joinToString(", ")
                                } ?: "Delivery address required",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (onChangeAddress != null) {
                            TextButton(
                                onClick = onChangeAddress,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "Change",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669)
                                )
                            }
                        }
                    }
                }
            }

            // 2. ITEMS SUMMARY ACCORDION
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Order Items (${checkoutUiState.items.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            Surface(
                                color = Color(0xFFECFDF5),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "⚡ 10 MINS SLA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF059669),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)

                        checkoutUiState.items.forEachIndexed { idx, item ->
                            val resolvedImage = remember(item.sku, item.name, item.image) {
                                MedicineImageResolver.resolve(sku = item.sku, name = item.name, rawImage = item.image)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProductImage(
                                    imageUrl = resolvedImage,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Fit,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .size(42.dp)
                                        .background(Color(0xFFF8FAFC), shape = RoundedCornerShape(6.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name.ifBlank { "Medicine Item" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Qty: ${item.quantity} • ${item.packSize ?: "Pack"}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                                Text(
                                    text = MoneyFormatter.format(item.unitPrice.multiply(BigDecimal.valueOf(item.quantity.toLong()))),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                            if (idx < checkoutUiState.items.size - 1) {
                                HorizontalDivider(color = Color(0xFFF8FAFC), thickness = 1.dp)
                            }
                        }
                    }
                }
            }

            // 3. BILL SUMMARY
            item {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Bill Breakdown",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        BillLineRow(label = "Item total", value = MoneyFormatter.format(checkoutUiState.itemsSubtotal))
                        BillLineRow(
                            label = "Delivery fee",
                            value = if (checkoutUiState.deliveryFee.signum() == 0) "FREE" else MoneyFormatter.format(checkoutUiState.deliveryFee),
                            isFree = checkoutUiState.deliveryFee.signum() == 0
                        )
                        if (checkoutUiState.coldChainFee.signum() > 0) {
                            BillLineRow(label = "Temperature-safe packing", value = MoneyFormatter.format(checkoutUiState.coldChainFee))
                        }
                        if (checkoutUiState.couponDiscount.signum() > 0) {
                            BillLineRow(
                                label = "Promo discount",
                                value = "- ${MoneyFormatter.format(checkoutUiState.couponDiscount)}",
                                isDiscount = true
                            )
                        }

                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "To Pay",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = MoneyFormatter.format(checkoutUiState.grandTotal),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }
            }

            // 4. PAYMENT OPTIONS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Payment Method",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )

                    // Cash on Delivery Option (Active)
                    Surface(
                        color = if (selectedMethod == PaymentConstants.COD) Color(0xFFECFDF5) else Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.5.dp,
                            if (selectedMethod == PaymentConstants.COD) Color(0xFF059669) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMethod = PaymentConstants.COD }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedMethod == PaymentConstants.COD,
                                onClick = { selectedMethod = PaymentConstants.COD },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF059669))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cash on Delivery",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Pay via Cash or UPI when your order reaches your door",
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "RECOMMENDED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF065F46),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // UPI Instant Option (Coming soon, fixed horizontal layout)
                    Surface(
                        color = Color.White.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = false,
                                onClick = null,
                                enabled = false
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "UPI (GPay / PhonePe / Paytm)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF64748B)
                                    )
                                    Surface(
                                        color = Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Coming Soon",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64748B),
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Text(
                                    text = "Online payment gateway rollout in progress. COD is 100% active.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            if (checkoutUiState.errorMessage != null) {
                item {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = checkoutUiState.errorMessage,
                            fontSize = 12.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // STICKY BOTTOM ACTION BAR (Place Order)
        Surface(
            color = Color.White,
            shadowElevation = 12.dp,
            border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "TOTAL AMOUNT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = MoneyFormatter.format(checkoutUiState.grandTotal),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF0F172A)
                    )
                }

                Button(
                    onClick = { onAuthorize(selectedMethod) },
                    enabled = !checkoutUiState.isProcessing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF94A3B8)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    if (checkoutUiState.isProcessing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Place Order ▶",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BillLineRow(
    label: String,
    value: String,
    isFree: Boolean = false,
    isDiscount: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = Color(0xFF64748B))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = if (isFree || isDiscount) FontWeight.Bold else FontWeight.Medium,
            color = when {
                isFree -> Color(0xFF059669)
                isDiscount -> Color(0xFF059669)
                else -> Color(0xFF0F172A)
            }
        )
    }
}
