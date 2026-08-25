package com.commerceos.android.ui.orders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CustomerOrderApiResponse
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Your Orders", style = CommerceTypography.Title, fontWeight = FontWeight.Black, color = CommerceColors.TextPrimary)
                Text("Track deliveries & rate past orders", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Orders", tint = CommerceColors.Primary)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Segmented Filter Rail (Domino's / Blinkit pattern)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All", "ACTIVE" to "Active", "DELIVERED" to "Delivered").forEach { (tabKey, label) ->
                val isSelected = selectedFilterTab == tabKey
                Surface(
                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(10.dp),
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

        Spacer(modifier = Modifier.height(12.dp))

        when (history) {
            is OrderHistoryUiState.Loading -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CommerceColors.Primary)
                }
            }
            is OrderHistoryUiState.Empty -> {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No past orders found.", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted, fontWeight = FontWeight.Bold)
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
                        Text("No orders in this category.", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(filteredOrders) { order ->
                            OrderHistoryCard(
                                order = order,
                                onCancelOrder = { onCancelOrder(order) },
                                onReorderItem = onReorderItem,
                                onTrackOrder = { onTrackOrder(order) }
                            )
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
    val isActive = !presented.isTerminal
    var userRating by remember { mutableIntStateOf(0) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            if (isActive) 1.5.dp else 1.dp,
            if (isActive) Color(0xFF059669) else Color(0xFFE2E8F0)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 3.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order #${order.id.takeLast(8).uppercase()}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Black, color = CommerceColors.TextPrimary)
                Surface(
                    color = presented.chipBackground,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        presented.presentedLabel,
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Black,
                        color = presented.chipContent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Product thumbnails + names
            if (items.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val visible = items.take(3)
                    visible.forEach { item ->
                        ProductThumb(item = item)
                        Spacer(modifier = Modifier.width(Spacing.sm))
                    }
                    if (items.size > visible.size) {
                        Text(
                            "+${items.size - visible.size}",
                            style = CommerceTypography.Label,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.TextMuted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    items.take(2).joinToString(" + ") { it.name } + if (items.size > 2) " +${items.size - 2} more" else "",
                    style = CommerceTypography.BodySmall,
                    color = CommerceColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Text(
                "${items.size} ${if (items.size == 1) "item" else "items"} • ${MoneyFormatter.format(order.totalAmount)}",
                style = CommerceTypography.BodySmall,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.TextPrimary
            )
            Text(
                "${order.paymentMethod.uppercase()} • ${if (order.paymentStatus == "PAID") "Paid" else "Payment pending"}",
                style = CommerceTypography.Meta,
                color = CommerceColors.TextMuted
            )

            // Delivery PIN Badge for Active Orders
            if (isActive && !order.deliveryOtp.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("🔑 Delivery PIN to give rider:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                        Text(order.deliveryOtp ?: "", fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFFB45309), letterSpacing = 2.sp)
                    }
                }
            }

            // Domino's Style 5-Star Interactive Rating for Delivered Orders
            if (isDelivered) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (userRating > 0) "Rated $userRating/5 ⭐" else "Rate your order:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
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

            Spacer(modifier = Modifier.height(10.dp))

            if (isActive) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onTrackOrder,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("⚡ Track Order", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (presented.hasCancelAction) {
                        OutlinedButton(
                            onClick = onCancelOrder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CommerceColors.Danger),
                            border = BorderStroke(1.dp, CommerceColors.Danger.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onTrackOrder,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("View Details", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ProductThumb(item: OrderItem) {
    ProductImage(
        imageUrl = null,
        contentDescription = item.name,
        modifier = Modifier.size(40.dp),
        shape = RoundedCornerShape(Radius.ImageTile)
    )
}