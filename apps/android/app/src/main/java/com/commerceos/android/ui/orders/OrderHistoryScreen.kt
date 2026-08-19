package com.commerceos.android.ui.orders

import androidx.compose.foundation.BorderStroke
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Orders & Reorder", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Text("Track active deliveries and reorder medicines", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Orders", tint = CommerceColors.Primary)
            }
        }
        Spacer(modifier = Modifier.height(Spacing.md))

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
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    items(history.orders) { order ->
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

@Composable
private fun OrderHistoryCard(
    order: CustomerOrderApiResponse,
    onCancelOrder: () -> Unit,
    onReorderItem: (OrderItem) -> Unit,
    onTrackOrder: () -> Unit
) {
    val presented = OrderStatusPresentationMapper.present(order.orderStatus)
    val items = order.items.orEmpty()

    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.lg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order #${order.id.takeLast(8)}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Surface(
                    color = presented.chipBackground,
                    shape = RoundedCornerShape(Radius.Chip)
                ) {
                    Text(
                        presented.presentedLabel,
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = presented.chipContent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Product thumbnails + names make the card recognizable at a glance.
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
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    items.take(2).joinToString(" + ") { it.name } + if (items.size > 2) " +${items.size - 2} more" else "",
                    style = CommerceTypography.BodySmall,
                    color = CommerceColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
            }

            Text(
                "${items.size} ${if (items.size == 1) "item" else "items"} • ${MoneyFormatter.format(order.totalAmount)}",
                style = CommerceTypography.BodySmall,
                fontWeight = FontWeight.SemiBold,
                color = CommerceColors.TextPrimary
            )
            Text(
                "${order.paymentMethod.uppercase()} • ${if (order.paymentStatus == "PAID") "Paid" else "Payment pending"}",
                style = CommerceTypography.Meta,
                color = CommerceColors.TextMuted
            )

            if (presented.isTerminal) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedButton(
                    onClick = onTrackOrder,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CommerceColors.Primary),
                    border = BorderStroke(1.dp, CommerceColors.Primary.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(Radius.Button),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("View order details", fontWeight = FontWeight.Bold) }
            } else {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(
                        onClick = onTrackOrder,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CommerceColors.Primary),
                        border = BorderStroke(1.dp, CommerceColors.Primary.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(Radius.Button),
                        modifier = Modifier.weight(1f)
                    ) { Text("Track", fontWeight = FontWeight.Bold) }
                    if (presented.hasCancelAction) {
                        OutlinedButton(
                            onClick = onCancelOrder,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CommerceColors.Danger),
                            border = BorderStroke(1.dp, CommerceColors.Danger.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(Radius.Button),
                            modifier = Modifier.weight(1f)
                        ) { Text("Cancel", fontWeight = FontWeight.Bold) }
                    }
                }
            }

            // Buy Again per item — the reorder journey starts from real line items.
            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                items.forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            item.name,
                            style = CommerceTypography.Label,
                            color = CommerceColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { onReorderItem(item) }) {
                            Text("Buy again", fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                        }
                    }
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