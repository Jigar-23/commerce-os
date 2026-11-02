package com.commerceos.rider.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.RiderNotificationItem
import com.commerceos.rider.theme.RiderColors

/**
 * World-Class Quick-Commerce Delivery Partner Notification & Alerts Hub
 * Styled after Swiggy Delivery Partner, Zomato Partner, and Blinkit Rider apps.
 */
@Composable
fun RiderNotificationCenterScreen(
    notifications: List<RiderNotificationItem>,
    unreadCount: Int,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onNotificationClick: (RiderNotificationItem) -> Unit,
    onDismissNotification: ((RiderNotificationItem) -> Unit)? = null,
    onMarkAllRead: () -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf("ALL", "ORDERS", "EARNINGS", "SYSTEM")

    val filteredNotifications = remember(notifications, selectedCategory) {
        if (selectedCategory.equals("ALL", ignoreCase = true)) {
            notifications
        } else {
            notifications.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RiderColors.Background)
    ) {
        // Category Pill Filter Bar (Clean Sticky Filter Row)
        Surface(
            color = RiderColors.Surface,
            border = BorderStroke(1.dp, RiderColors.Border),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { cat ->
                    val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                    val label = when (cat) {
                        "ALL" -> "All Alerts"
                        "ORDERS" -> "Dispatches"
                        "EARNINGS" -> "Store Updates"
                        "SYSTEM" -> "System"
                        else -> cat
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) RiderColors.Primary else RiderColors.SurfaceSubtle,
                        border = BorderStroke(1.dp, if (isSelected) RiderColors.Primary else RiderColors.Border),
                        modifier = Modifier.clickable { onCategorySelected(cat) }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else RiderColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        // 3. Notifications List or Empty State
        if (filteredNotifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                    border = BorderStroke(1.dp, RiderColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = RiderColors.PrimarySoft,
                            shape = CircleShape,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = RiderColors.Primary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        Text(
                            text = "No alerts in this category",
                            color = RiderColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Realtime store dispatch offers, order handoffs, and fulfillment updates will appear here as soon as they are assigned.",
                            color = RiderColors.TextMuted,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                items(filteredNotifications, key = { it.notificationId }) { item ->
                    NotificationCardItem(
                        item = item,
                        onClick = { onNotificationClick(item) },
                        onDismiss = { onDismissNotification?.invoke(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCardItem(
    item: RiderNotificationItem,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val isUnread = item.readAt == null
    val context = LocalContext.current

    val categoryColor = when (item.category.uppercase()) {
        "ORDERS" -> RiderColors.Primary
        "EARNINGS" -> Color(0xFFD97706) // Amber
        else -> RiderColors.AccentBlue
    }

    val categoryBg = when (item.category.uppercase()) {
        "ORDERS" -> RiderColors.PrimarySoft
        "EARNINGS" -> RiderColors.WarningSoft
        else -> RiderColors.AccentBlueSoft
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
        border = BorderStroke(1.dp, if (isUnread) categoryColor.copy(alpha = 0.4f) else RiderColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnread) 3.dp else 1.dp),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left Accent Strip for Unread Alerts (Zomato/Swiggy pattern)
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(categoryColor)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Row: Category Icon + Title + Status Badges + Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = categoryBg,
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                when (item.category.uppercase()) {
                                    "ORDERS" -> Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = categoryColor, modifier = Modifier.size(18.dp))
                                    "EARNINGS" -> Icon(Icons.Default.Star, contentDescription = null, tint = categoryColor, modifier = Modifier.size(18.dp))
                                    else -> Icon(Icons.Default.Notifications, contentDescription = null, tint = categoryColor, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = RiderColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val catLabel = when (item.category.uppercase()) {
                                "ORDERS" -> "DISPATCH OFFER"
                                "EARNINGS" -> "FULFILLMENT UPDATE"
                                else -> "SYSTEM ANNOUNCEMENT"
                            }
                            Text(
                                text = catLabel,
                                color = categoryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isUnread) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = RiderColors.PrimarySoft
                            ) {
                                Text(
                                    text = "NEW",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RiderColors.PrimarySoftText,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = RiderColors.TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Message Body
                Text(
                    text = item.body,
                    color = RiderColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )

                // Optional Order ID Chip with Clean Horizontal Copy
                if (!item.orderId.isNullOrBlank()) {
                    val shortOrderId = if (item.orderId.length > 20) item.orderId.take(18) + "…" else item.orderId
                    Surface(
                        color = RiderColors.SurfaceSubtle,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        modifier = Modifier.clickable {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Order ID", item.orderId))
                            Toast.makeText(context, "Copied Order ID: #${item.orderId}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "ORD #$shortOrderId",
                                color = RiderColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.Primary
                            )
                        }
                    }
                }

                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                // Bottom Action & Status Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isAvailable == true) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RiderColors.PrimarySoft
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = RiderColors.Primary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(6.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Ready to Accept",
                                    color = RiderColors.PrimarySoftText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (item.isAvailable == false) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RiderColors.DangerSoft
                        ) {
                            Text(
                                text = "Claimed by another rider",
                                color = RiderColors.DangerText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "Tap to view",
                            color = RiderColors.TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (item.isAvailable == false) "Details" else "View Details",
                            color = if (item.isAvailable == false) RiderColors.TextMuted else RiderColors.Primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (item.isAvailable == false) RiderColors.TextMuted else RiderColors.Primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}
