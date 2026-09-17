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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
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
    onTrackOrder: (CustomerOrderApiResponse) -> Unit = {}
) {
    var selectedFilterTab by remember { mutableStateOf("ALL") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F7))
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
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Track deliveries & view past medicines",
                    fontSize = 12.sp,
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
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All Orders", "ACTIVE" to "Active", "DELIVERED" to "Delivered").forEach { (tabKey, label) ->
                val isSelected = selectedFilterTab == tabKey
                Surface(
                    color = if (isSelected) Color(0xFF059669) else Color.White,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF059669) else Color(0xFFE2E8F0)),
                    modifier = Modifier.clickable { selectedFilterTab = tabKey }
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No past orders found",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Your ordered medicines will show up here",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
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
    onTrackOrder: () -> Unit
) {
    val presented = OrderStatusPresentationMapper.present(order.orderStatus)
    val items = order.items.orEmpty()
    val isDelivered = order.orderStatus.uppercase() == "DELIVERED"
    val isCancelled = order.orderStatus.uppercase() == "CANCELLED"
    val isActive = !presented.isTerminal
    var userRating by remember { mutableIntStateOf(0) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) Color(0xFF059669).copy(alpha = 0.5f) else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTrackOrder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Order Number & Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Order #${order.id}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                val (chipBg, chipTextColor) = when {
                    isDelivered -> Color(0xFFEFF6FF) to Color(0xFF1D4ED8)
                    isCancelled -> Color(0xFFFEF2F2) to Color(0xFFDC2626)
                    isActive -> Color(0xFFECFDF5) to Color(0xFF059669)
                    else -> Color(0xFFF1F5F9) to Color(0xFF475569)
                }

                Surface(
                    color = chipBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = presented.presentedLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = chipTextColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Items List with REAL Medicine Photos
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
                                    fontSize = 13.sp,
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
                                fontSize = 13.sp,
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
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(start = 66.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
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
                        text = "${items.sumOf { it.quantity }} ${if (items.sumOf { it.quantity } == 1) "item" else "items"} • Total ₹${order.totalAmount}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${if (order.paymentMethod.equals("COD", ignoreCase = true)) "Cash on Delivery" else "Paid Online"} • ${if (order.paymentStatus == "PAID") "Paid" else "Payment pending"}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Delivery SLA ETA badge if active
                if (isActive) {
                    Surface(
                        color = Color(0xFFECFDF5),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "⚡ In ${order.deliverySlaMins} mins",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
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
                            Text("🔑", fontSize = 14.sp)
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

            // Star Rating for Delivered Orders
            if (isDelivered) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (userRating > 0) "Rated $userRating/5 ⭐" else "Rate your order:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                        Row {
                            (1..5).forEach { star ->
                                Text(
                                    text = if (star <= userRating) "⭐" else "☆",
                                    fontSize = 16.sp,
                                    modifier = Modifier
                                        .clickable { userRating = star }
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            if (isActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onTrackOrder,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚡ Track Order", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
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
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onTrackOrder,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF059669)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("View Complete Details", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    if (items.isNotEmpty() && !isCancelled) {
                        Button(
                            onClick = { items.forEach { onReorderItem(it) } },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Text("Order Again", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                        }
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
    val resolvedUrl = remember(item.sku, item.name) {
        MedicineImageResolver.resolve(item.sku, item.name)
    }

    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.size(52.dp)
    ) {
        ProductImage(
            imageUrl = resolvedUrl,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            shape = RoundedCornerShape(8.dp)
        )
    }
}