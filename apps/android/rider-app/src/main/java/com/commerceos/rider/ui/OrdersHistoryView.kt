package com.commerceos.rider.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.theme.RiderColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OrdersHistoryView(
    activeSession: ServerDeliverySession?,
    completedSessions: List<ServerDeliverySession> = emptyList(),
    onSelectActiveOrder: () -> Unit,
    onViewOrderDetail: ((ServerDeliverySession) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(RiderColors.Background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Current Active Delivery Card (High Priority Callout)
        if (activeSession != null && activeSession.state != "DELIVERED") {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACTIVE DISPATCH IN PROGRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RiderColors.Primary,
                        letterSpacing = 0.8.sp
                    )
                    Surface(
                        color = RiderColors.PrimarySoft,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Surface(
                                color = RiderColors.Primary,
                                shape = CircleShape,
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Live on Route",
                                color = RiderColors.PrimarySoftText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                    border = BorderStroke(1.5.dp, RiderColors.Primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    onClick = {
                        onViewOrderDetail?.invoke(activeSession) ?: onSelectActiveOrder()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Order Header: Status & Payout
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = RiderColors.AccentBlueSoft,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "● " + activeSession.state.replace("_", " "),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.AccentBlueText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            val activeOrderVal = activeSession.orderTotal
                                ?: activeSession.items.sumOf { it.price * it.quantity }.takeIf { it > 0.0 }
                                ?: 32.0
                            Surface(
                                color = RiderColors.PrimarySoft,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "Order Value: ₹${activeOrderVal.toInt()}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.PrimarySoftText,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Order ID Chip with Copy
                        val activeShortId = if (activeSession.orderId.length > 20) activeSession.orderId.take(18) + "…" else activeSession.orderId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RiderColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, RiderColors.Border),
                            modifier = Modifier.clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Order ID", activeSession.orderId))
                                Toast.makeText(context, "Copied Order ID: ${activeSession.orderId}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ORD #$activeShortId",
                                    fontWeight = FontWeight.SemiBold,
                                    color = RiderColors.TextSecondary,
                                    fontSize = 12.sp
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

                        // Visual Route Timeline
                        OrderRouteTimeline(
                            merchantName = activeSession.merchantName.ifBlank { "Rewari Central Hub" },
                            customerAddress = activeSession.customerAddress.ifBlank { "Customer Address" }
                        )

                        // Bottom Action Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val itemCount = activeSession.items.sumOf { it.quantity }.takeIf { it > 0 } ?: 1
                            Text(
                                text = "📦 $itemCount item(s) • ${if (activeSession.isCod) "Cash on Delivery" else "Store Order"}",
                                fontSize = 12.sp,
                                color = RiderColors.TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Navigate Live",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.Primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = RiderColors.Primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // 2. Section Header: Completed Deliveries Summary
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FULFILLED DELIVERIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RiderColors.TextSecondary,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = "Store orders fulfilled with customer OTP verification",
                        fontSize = 11.sp,
                        color = RiderColors.TextMuted
                    )
                }
                if (completedSessions.isNotEmpty()) {
                    Surface(
                        color = RiderColors.SurfaceSubtle,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RiderColors.Border)
                    ) {
                        Text(
                            text = "${completedSessions.size} trips",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = RiderColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 3. Completed Sessions List or Empty State
        if (completedSessions.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                    border = BorderStroke(1.dp, RiderColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = RiderColors.PrimarySoft,
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = RiderColors.Primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Text(
                            text = "No fulfilled deliveries yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = RiderColors.TextPrimary
                        )
                        Text(
                            text = "When you complete deliveries from Rewari Central Hub, trip routes, timestamps, and payouts will appear here.",
                            fontSize = 12.sp,
                            color = RiderColors.TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            items(completedSessions) { itemSession ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                    border = BorderStroke(1.dp, RiderColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    onClick = {
                        onViewOrderDetail?.invoke(itemSession)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header Row 1: Status Badge + Delivery Timestamp + Payout Pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Surface(
                                    color = RiderColors.PrimarySoft,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Delivered",
                                            tint = RiderColors.Primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "DELIVERED",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RiderColors.PrimarySoftText
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                val timestampText = remember(itemSession) { formatTripTime(itemSession) }
                                Text(
                                    text = timestampText,
                                    fontSize = 11.sp,
                                    color = RiderColors.TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Order Total Value Badge
                            val completedOrderVal = itemSession.orderTotal
                                ?: itemSession.items.sumOf { it.price * it.quantity }.takeIf { it > 0.0 }
                                ?: 32.0
                            Surface(
                                color = RiderColors.PrimarySoft,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = "₹${completedOrderVal.toInt()}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RiderColors.Primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Header Row 2: Order ID Chip with Clean Horizontal Copy
                        val cShortId = if (itemSession.orderId.length > 20) {
                            itemSession.orderId.take(18) + "…"
                        } else {
                            itemSession.orderId
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RiderColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, RiderColors.Border),
                            modifier = Modifier.clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Order ID", itemSession.orderId))
                                Toast.makeText(context, "Copied Order ID: ${itemSession.orderId}", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ORD #$cShortId",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RiderColors.TextSecondary
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

                        // Visual Route Timeline Connector (Store -> Doorstep)
                        OrderRouteTimeline(
                            merchantName = itemSession.merchantName.ifBlank { "Rewari Central Hub" },
                            customerAddress = itemSession.customerAddress.ifBlank { "Customer Delivery Location" }
                        )

                        // Bottom Meta Row (Items count, COD tag, Distance, Details button)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val qty = itemSession.items.sumOf { it.quantity }.takeIf { it > 0 } ?: 1
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    color = RiderColors.SurfaceSubtle,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, RiderColors.Border)
                                ) {
                                    Text(
                                        text = "📦 $qty item(s)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = RiderColors.TextSecondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                if (itemSession.isCod) {
                                    Surface(
                                        color = RiderColors.WarningSoft,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "COD",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RiderColors.WarningText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                val distKm = itemSession.distanceKm ?: 2.0
                                Surface(
                                    color = RiderColors.SurfaceSubtle,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, RiderColors.Border)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1f km", distKm),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = RiderColors.TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onViewOrderDetail?.invoke(itemSession) }
                            ) {
                                Text(
                                    text = "Details",
                                    fontSize = 12.sp,
                                    color = RiderColors.Primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = RiderColors.Primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Visual Route Timeline displaying pickup origin and doorstep drop
 */
@Composable
private fun OrderRouteTimeline(
    merchantName: String,
    customerAddress: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RiderColors.SurfaceSubtle, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Visual indicator dots & connector bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 3.dp, end = 10.dp)
        ) {
            Surface(
                color = RiderColors.AccentBlueSoft,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, RiderColors.AccentBlue),
                modifier = Modifier.size(9.dp)
            ) {}
            Box(
                modifier = Modifier
                    .width(1.5.dp)
                    .height(28.dp)
                    .background(Color(0xFFCBD5E1))
            )
            Surface(
                color = RiderColors.PrimarySoft,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, RiderColors.Primary),
                modifier = Modifier.size(9.dp)
            ) {}
        }

        // Route details
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Column {
                Text(
                    text = merchantName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RiderColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Store Pickup Verified",
                    fontSize = 10.sp,
                    color = RiderColors.TextMuted
                )
            }

            Column {
                Text(
                    text = customerAddress,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RiderColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Doorstep Handover • PIN Verified",
                    fontSize = 10.sp,
                    color = RiderColors.Primary
                )
            }
        }
    }
}

private fun formatTripTime(session: ServerDeliverySession): String {
    val rawTimestamp = session.history.lastOrNull { it.state == "DELIVERED" || it.state == "COMPLETED" }?.timestamp
        ?: session.history.lastOrNull()?.timestamp
    if (!rawTimestamp.isNullOrBlank()) {
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    if (pattern.contains("'Z'") || pattern.contains("XXX")) {
                        timeZone = java.util.TimeZone.getTimeZone("UTC")
                    }
                }
                val date = parser.parse(rawTimestamp)
                if (date != null) {
                    val outFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.US)
                    return outFormat.format(date)
                }
            } catch (_: Exception) {}
        }
    }
    return "Delivered • Order Fulfilled"
}
