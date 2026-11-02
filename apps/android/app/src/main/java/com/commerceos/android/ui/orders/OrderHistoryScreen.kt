package com.commerceos.android.ui.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.commerceos.android.model.CustomerOrderApiResponse
import com.commerceos.android.model.MedicineImageResolver
import com.commerceos.android.model.OrderItem
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.OrderHistoryUiState

@Composable
fun OrderHistoryScreen(
    history: OrderHistoryUiState,
    onRefresh: () -> Unit,
    onCancelOrder: (CustomerOrderApiResponse) -> Unit,
    onReorderItem: (OrderItem) -> Unit = {},
    onReorderOrder: (CustomerOrderApiResponse) -> Unit = {},
    onTrackOrder: (CustomerOrderApiResponse) -> Unit = {}
) {
    var selectedFilterTab by remember { mutableStateOf("ALL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)
    ) {
        // Top Header at exact same height as HomePage top row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Your Orders",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Track deliveries & view past medicines",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )
            }
            Surface(
                color = Color.White,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 0.5.dp,
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Orders",
                        tint = Color(0xFF0E675E),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Pills matching Tata 1mg Benchmark
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All Orders", "ACTIVE" to "Active", "DELIVERED" to "Delivered").forEach { (tabKey, label) ->
                val isSelected = selectedFilterTab == tabKey
                Surface(
                    color = if (isSelected) Color(0xFF0E675E) else Color.White,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF0E675E) else Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { selectedFilterTab = tabKey }
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        when (history) {
            is OrderHistoryUiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF059669))
                }
            }
            is OrderHistoryUiState.Empty -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.LocalPharmacy,
                                    contentDescription = null,
                                    tint = Color(0xFF047857),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No past medicine orders",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Your prescription orders and daily health essentials will appear here.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            is OrderHistoryUiState.Error -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(history.message, style = CommerceTypography.BodySmall, color = CommerceColors.Danger, fontWeight = FontWeight.Bold)
                }
            }
            is OrderHistoryUiState.Content -> {
                val filteredOrders = remember(history.orders, selectedFilterTab) {
                    when (selectedFilterTab) {
                        "ACTIVE" -> history.orders.filter { it.orderStatus.uppercase() !in listOf("DELIVERED", "CANCELLED") }
                        "DELIVERED" -> history.orders.filter { it.orderStatus.uppercase() == "DELIVERED" }
                        else -> history.orders
                    }
                }

                if (filteredOrders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "No orders in this category",
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(filteredOrders, key = { it.id }) { order ->
                            OrderHistoryCard(
                                order = order,
                                onCancelOrder = { onCancelOrder(order) },
                                onReorderItem = onReorderItem,
                                onReorderOrder = onReorderOrder,
                                onTrackOrder = { onTrackOrder(order) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryCard(
    order: CustomerOrderApiResponse,
    onCancelOrder: () -> Unit,
    onReorderItem: (OrderItem) -> Unit,
    onReorderOrder: (CustomerOrderApiResponse) -> Unit = {},
    onTrackOrder: () -> Unit
) {
    val currentStatus = order.effectiveStatus
    val presented = OrderStatusPresentationMapper.present(currentStatus)
    val items = order.items.orEmpty()
    val isDelivered = currentStatus.equals("DELIVERED", ignoreCase = true)
    val isCancelled = currentStatus.equals("CANCELLED", ignoreCase = true) || presented.isCancelled
    val isActive = !presented.isTerminal && !isCancelled && !isDelivered

    val effectiveTotal = when {
        order.effectiveTotalAmount > java.math.BigDecimal.ZERO -> order.effectiveTotalAmount
        items.isNotEmpty() -> items.map { it.effectivePrice * java.math.BigDecimal(it.effectiveQuantity) }.fold(java.math.BigDecimal.ZERO, java.math.BigDecimal::add) + (order.effectiveDeliveryFee)
        else -> java.math.BigDecimal.ZERO
    }

    val (headerBg, headerPillBg, headerTextColor) = when {
        isDelivered -> Triple(Color(0xFFF0F7FF), Color(0xFFDBEAFE), Color(0xFF1D4ED8))
        isCancelled -> Triple(Color(0xFFFFF5F5), Color(0xFFFEE2E2), Color(0xFFDC2626))
        isActive -> Triple(Color(0xFFF0FDF4), Color(0xFFDCFCE7), Color(0xFF0E675E))
        else -> Triple(Color(0xFFF8FAFC), Color(0xFFF1F5F9), Color(0xFF475569))
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) Color(0xFF0E675E).copy(alpha = 0.6f) else Color(0xFFE2E8F0)
        ),
        shadowElevation = if (isActive) 2.dp else 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTrackOrder)
    ) {
        Column {
            // Tata 1mg Benchmark Header Ribbon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerBg)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = headerPillBg,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = presented.presentedLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = headerTextColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Text(
                        text = when {
                            isDelivered -> "Delivered on ${order.displayOrderDate}"
                            isCancelled -> "Placed on ${order.displayOrderDateTime}"
                            else -> "⚡ 10 Mins Delivery"
                        },
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "View Details",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.padding(14.dp)) {
                // Items List with Medicine Photos
                if (items.isNotEmpty()) {
                    val displayItems = items.take(2)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        displayItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // High-Res Medicine Thumbnail Tile
                                ProductThumbTile(item = item)

                                Spacer(modifier = Modifier.width(12.dp))

                                // Item Description & Pricing
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E293B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Qty: ${item.quantity}",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B),
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = " • ₹${item.unitPrice.toInt()} each",
                                            fontSize = 12.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }

                                // Line total
                                Text(
                                    text = "₹${item.unitPrice.toInt() * item.quantity}",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        if (items.size > displayItems.size) {
                            Text(
                                text = "+${items.size - displayItems.size} more items",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0E675E),
                                modifier = Modifier.padding(start = 66.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Summary Row: Total amount and payment status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${items.sumOf { it.quantity }} ${if (items.sumOf { it.quantity } == 1) "item" else "items"} • Total ₹${if (effectiveTotal.remainder(java.math.BigDecimal.ONE).compareTo(java.math.BigDecimal.ZERO) == 0) effectiveTotal.toInt() else effectiveTotal}",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "${if (order.paymentMethod.equals("COD", ignoreCase = true)) "Cash on Delivery" else "Paid Online"} • ${if (order.paymentStatus == "PAID") "Paid" else "Payment pending"}",
                            fontSize = 11.5.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Text(
                        text = "Order #${order.displayOrderId.takeLast(8)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8)
                    )
                }

                // Delivery PIN Badge for Active Orders
                val pin = order.effectiveDeliveryPin
                if (isActive && !pin.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Delivery PIN",
                                    tint = Color(0xFFB45309),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Delivery PIN to give rider:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF92400E)
                                )
                            }
                            Text(
                                text = pin,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFB45309),
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }

                // Action Footer
                if (isActive) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onTrackOrder,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0E675E)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⚡ Track Live", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
                        if (presented.hasCancelAction) {
                            OutlinedButton(
                                onClick = onCancelOrder,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                border = BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(0.7f)
                            ) {
                                Text("Cancel", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else if (items.isNotEmpty()) {
                    // Tata 1mg Benchmark Clean Reorder Strip
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onReorderOrder(order)
                            }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color(0xFF0E675E),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reorder",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0E675E)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Clean Medicine Product Thumbnail Tile with Authentic Packaging Photos
 */
@Composable
private fun ProductThumbTile(item: OrderItem) {
    val sku = item.effectiveSku
    val name = item.effectiveName
    val resolvedUrl = remember(sku, name) {
        MedicineImageResolver.resolve(sku, name)
    }

    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.size(52.dp)
    ) {
        ProductImage(
            imageUrl = resolvedUrl,
            contentDescription = name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            shape = RoundedCornerShape(8.dp)
        )
    }
}