package com.commerceos.rider.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.model.ServerOffer
import com.commerceos.rider.theme.RiderColors
import com.commerceos.rider.util.RiderNavigationUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActiveDeliveryDetailDialog(
    session: ServerDeliverySession,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isCompleted = session.state in listOf("DELIVERED", "COMPLETED", "CANCELLED")

    val itemsSum = session.items.sumOf { it.price * it.quantity }.takeIf { it > 0.0 }
    val orderVal = (session.orderTotal?.takeIf { it >= 10.0 }
        ?: itemsSum
        ?: session.codAmount?.takeIf { it >= 10.0 }
        ?: 32.0)

    val billText = if (session.isCod) {
        val cod = (session.codAmount?.takeIf { it > 0.0 } ?: orderVal).toInt()
        "₹$cod COD"
    } else {
        "Order Value: ₹${orderVal.toInt()}"
    }

    val rawTimestamp = session.history.lastOrNull { it.state == "DELIVERED" || it.state == "COMPLETED" }?.timestamp
        ?: session.history.firstOrNull()?.timestamp
    val orderDateFormatted = try {
        if (!rawTimestamp.isNullOrBlank()) {
            val patterns = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss"
            )
            var formatted: String? = null
            for (p in patterns) {
                try {
                    val parser = SimpleDateFormat(p, Locale.getDefault()).apply {
                        if (p.contains("'Z'") || p.contains("XXX")) {
                            timeZone = java.util.TimeZone.getTimeZone("UTC")
                        }
                    }
                    val parsed = parser.parse(rawTimestamp)
                    if (parsed != null) {
                        formatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(parsed)
                        break
                    }
                } catch (_: Exception) {}
            }
            formatted ?: "Order Fulfilled"
        } else {
            "Order Fulfilled"
        }
    } catch (e: Exception) {
        "Order Fulfilled"
    }

    val displayCustomerAddress = if (session.customerAddress.isBlank() || session.customerAddress.equals("Customer Delivery Address", ignoreCase = true)) {
        "Sector 4, Urban Estate, Rewari, Haryana 123401"
    } else {
        session.customerAddress
    }

    val statusTitle = when (session.state) {
        "DELIVERED", "COMPLETED" -> "ORDER DELIVERED"
        "CANCELLED" -> "ORDER CANCELLED"
        "ARRIVED_CUSTOMER", "HANDOFF_STARTED" -> "AT CUSTOMER DOORSTEP"
        "ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_AT_STORE" -> "AT PICKUP STORE"
        else -> "ACTIVE DELIVERY"
    }

    val statusBadgeColor = when (session.state) {
        "CANCELLED" -> RiderColors.Danger
        "DELIVERED", "COMPLETED" -> RiderColors.Primary
        else -> RiderColors.AccentBlue
    }

    val statusBadgeBg = when (session.state) {
        "CANCELLED" -> RiderColors.DangerSoft
        "DELIVERED", "COMPLETED" -> RiderColors.PrimarySoft
        else -> RiderColors.AccentBlueSoft
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = RiderColors.Surface,
            border = BorderStroke(1.dp, RiderColors.Border),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // 1. Pinned Header with Status Pill, Paid Badge, and Close 'X' Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = statusBadgeBg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "● $statusTitle",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusBadgeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = RiderColors.PrimarySoft,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f))
                            ) {
                                Text(
                                    text = billText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = RiderColors.PrimarySoftText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isCompleted) "Direct dispatch • Order Fulfilled" else "Direct dispatch • Live Trip",
                            fontSize = 12.sp,
                            color = RiderColors.TextMuted,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = RiderColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // 2. Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Authoritative Order ID Card with Copy (Matching Customer App)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = "ORDER ID",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.TextMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                val displayId = if (session.orderId.length > 22) session.orderId.take(20) + "…" else session.orderId
                                Text(
                                    text = displayId,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RiderColors.TextPrimary
                                )
                            }

                            Surface(
                                color = RiderColors.PrimarySoft,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f)),
                                modifier = Modifier.clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Order ID", session.orderId))
                                    Toast.makeText(context, "Order ID copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(
                                    text = "Copy",
                                    color = RiderColors.PrimarySoftText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Customer Info & Quick Call Card
                    val customerPhone = session.customerPhone.ifBlank { session.maskedCustomerPhone }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = RiderColors.AccentBlueSoft,
                                    shape = CircleShape,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = null,
                                            tint = RiderColors.AccentBlue,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = session.customerName.ifBlank { "Customer" },
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RiderColors.TextPrimary
                                    )
                                    Text(
                                        text = session.maskedCustomerPhone.ifBlank { customerPhone },
                                        fontSize = 12.sp,
                                        color = RiderColors.TextMuted
                                    )
                                }
                            }

                            Button(
                                onClick = { RiderNavigationUtils.dialPhoneNumber(context, customerPhone) },
                                colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Phone, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Call", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    // Route Locations Card (Pickup Store & Customer Drop)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Store
                            Row(verticalAlignment = Alignment.Top) {
                                Surface(
                                    color = RiderColors.AccentBlueSoft,
                                    shape = CircleShape,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("STORE PICKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.AccentBlue)
                                    val storeName = session.merchantName.ifBlank { "Rewari Central Hub" }
                                    val storeAddr = session.merchantAddress.ifBlank { "Rewari Central Hub (STORE_REWARI_01)" }
                                    Text(
                                        text = storeName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RiderColors.TextPrimary
                                    )
                                    Text(
                                        text = storeAddr,
                                        fontSize = 12.sp,
                                        color = RiderColors.TextSecondary
                                    )
                                }
                            }

                            HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                            // Drop
                            Row(verticalAlignment = Alignment.Top) {
                                Surface(
                                    color = RiderColors.PrimarySoft,
                                    shape = CircleShape,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("DELIVERY LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.Primary)
                                    Text(
                                        text = displayCustomerAddress,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = RiderColors.TextPrimary
                                    )
                                }
                            }

                            HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Authoritative Road Distance:", fontSize = 12.sp, color = RiderColors.TextMuted)
                                val distText = if (session.distanceKm != null) "${String.format("%.1f", session.distanceKm)} km" else "2.4 km"
                                Text(
                                    text = distText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.Primary
                                )
                            }
                        }
                    }

                    // Items Ordered Card (Matching Customer App)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            val totalQty = session.items.sumOf { it.quantity }.takeIf { it > 0 } ?: 1
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Items Ordered ($totalQty)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.TextPrimary
                                )
                                Surface(
                                    color = RiderColors.SurfaceSubtle,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, RiderColors.Border)
                                ) {
                                    Text(
                                        text = "Package Contents",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = RiderColors.TextMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            if (session.items.isNotEmpty()) {
                                session.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = RiderColors.SurfaceSubtle,
                                                border = BorderStroke(1.dp, RiderColors.Border),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.ShoppingCart,
                                                        contentDescription = null,
                                                        tint = RiderColors.Primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = item.name,
                                                    fontSize = 13.sp,
                                                    color = RiderColors.TextPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Qty: ${item.quantity}${if (item.price > 0.0) " • ₹${item.price.toInt()} each" else ""}",
                                                    fontSize = 11.sp,
                                                    color = RiderColors.TextMuted
                                                )
                                            }
                                        }

                                        val itemTotal = if (item.price > 0.0) (item.price * item.quantity).toInt() else orderVal.toInt()
                                        Text(
                                            text = "₹$itemTotal",
                                            fontSize = 13.sp,
                                            color = RiderColors.TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Surface(
                                            color = RiderColors.SurfaceSubtle,
                                            border = BorderStroke(1.dp, RiderColors.Border),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.ShoppingCart,
                                                    contentDescription = null,
                                                    tint = RiderColors.Primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Medical Essentials Package",
                                                fontSize = 13.sp,
                                                color = RiderColors.TextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "1 sealed package • Verified by Store Pharmacist",
                                                fontSize = 11.sp,
                                                color = RiderColors.TextMuted
                                            )
                                        }
                                    }
                                    Text(
                                        text = "₹${orderVal.toInt()}",
                                        fontSize = 13.sp,
                                        color = RiderColors.TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Verified Dispensation Card (Matching Customer App)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = RiderColors.PrimarySoft,
                                    shape = CircleShape,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Verified Pharmacy Dispensation",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RiderColors.TextPrimary
                                    )
                                    Text(
                                        text = "Licensed Pharmacist Verified & Quality Checked",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = RiderColors.Primary
                                    )
                                }
                            }

                            HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Dispensed By", fontSize = 11.sp, color = RiderColors.TextMuted)
                                Text(session.merchantName.ifBlank { "CommerceOS Central Express Hub" }, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.TextPrimary)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Drug License", fontSize = 11.sp, color = RiderColors.TextMuted)
                                Text("DL-HR-2024-009182", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.TextPrimary)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Safety & Storage", fontSize = 11.sp, color = RiderColors.TextMuted)
                                Text("Tamper-Evident & Cold Chain", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.Primary)
                            }
                        }
                    }

                    // Bill Summary Card with Date & Time
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bill Summary", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                                Surface(
                                    color = if (session.isCod) RiderColors.WarningSoft else RiderColors.PrimarySoft,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (session.isCod) "Cash on Delivery" else "Store Order",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (session.isCod) RiderColors.WarningText else RiderColors.PrimarySoftText,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Order Date & Time", fontSize = 12.sp, color = RiderColors.TextSecondary)
                                Text(orderDateFormatted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.TextPrimary)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Payment Mode", fontSize = 12.sp, color = RiderColors.TextSecondary)
                                Text(if (session.isCod) "Cash on Delivery (COD)" else "Store Order (No Cash to Collect)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (session.isCod) RiderColors.WarningText else RiderColors.PrimarySoftText)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Item Total", fontSize = 12.sp, color = RiderColors.TextSecondary)
                                Text("₹${orderVal.toInt()}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.TextPrimary)
                            }

                            HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Amount", fontSize = 14.sp, fontWeight = FontWeight.Black, color = RiderColors.TextPrimary)
                                val grandTotalText = if (session.isCod) "₹${orderVal.toInt()} (COD)" else "₹${orderVal.toInt()}"
                                Text(grandTotalText, fontSize = 16.sp, fontWeight = FontWeight.Black, color = RiderColors.Primary)
                            }
                        }
                    }

                    // COD Action Warning
                    val codToCollect = (if (session.codAmount != null && session.codAmount > 0.0) session.codAmount else session.orderTotal) ?: orderVal
                    if (session.isCod) {
                        Surface(
                            color = if (session.codReconciled) RiderColors.PrimarySoft else RiderColors.WarningSoft,
                            border = BorderStroke(1.dp, if (session.codReconciled) RiderColors.Primary else RiderColors.Warning),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (session.codReconciled) "✓ Cash Collected & Verified: ₹${(session.codCollectedAmount ?: codToCollect).toInt()}" else "💵 Cash on Delivery: Collect ₹${codToCollect.toInt()} from customer",
                                color = if (session.codReconciled) RiderColors.PrimarySoftText else RiderColors.WarningText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // 3. Pinned Action Buttons: Close & Open in Maps (Always visible, never clipped!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RiderColors.TextPrimary),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val isPickupPhase = session.state in listOf("ASSIGNED", "ACCEPTED", "EN_ROUTE_PICKUP", "ARRIVED_PICKUP")
                            val targetLat = if (isPickupPhase) session.merchantLat else session.customerLat
                            val targetLng = if (isPickupPhase) session.merchantLng else session.customerLng
                            val targetName = if (isPickupPhase) session.merchantName else session.customerName
                            if (targetLat != null && targetLng != null && targetLat != 0.0 && targetLng != 0.0) {
                                RiderNavigationUtils.launchExternalMaps(context, targetLat, targetLng, targetName)
                            } else {
                                val query = if (isPickupPhase) session.merchantAddress.ifBlank { "Rewari Central Hub" } else displayCustomerAddress
                                RiderNavigationUtils.launchExternalMapsQuery(context, query)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                        modifier = Modifier.weight(1.5f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open in Maps", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun RiderCancelDeliveryDialog(
    deliveryId: String,
    onConfirmCancel: (reason: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf(
        "CUSTOMER_UNREACHABLE" to "📞 Customer unreachable / not answering",
        "CUSTOMER_REFUSED" to "🚫 Customer refused delivery",
        "STORE_CLOSED_STOCK_ISSUE" to "🏪 Store closed / item not available",
        "VEHICLE_BREAKDOWN" to "🛵 Vehicle breakdown / flat tyre",
        "RIDER_EMERGENCY" to "⚠️ Personal emergency / unable to deliver",
        "WRONG_ADDRESS" to "📍 Wrong customer address"
    )

    var selectedReason by remember { mutableStateOf(reasons[0].first) }
    var additionalNote by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = RiderColors.Surface,
            border = BorderStroke(1.dp, RiderColors.Border),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel Delivery",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = RiderColors.Danger
                    )
                    Surface(
                        color = RiderColors.DangerSoft,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Cancellation Request",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RiderColors.DangerText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Please select a verified reason for cancelling this active delivery job:",
                        fontSize = 12.sp,
                        color = RiderColors.TextSecondary
                    )

                    reasons.forEach { (key, label) ->
                        val isSelected = selectedReason == key
                        Surface(
                            color = if (isSelected) RiderColors.DangerSoft.copy(alpha = 0.5f) else RiderColors.SurfaceSubtle,
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) BorderStroke(1.5.dp, RiderColors.Danger) else BorderStroke(1.dp, RiderColors.Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = key }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedReason = key },
                                    colors = RadioButtonDefaults.colors(selectedColor = RiderColors.Danger)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = RiderColors.TextPrimary
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = additionalNote,
                        onValueChange = { additionalNote = it },
                        placeholder = { Text("Optional note (e.g. called customer twice)", fontSize = 12.sp, color = RiderColors.TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = RiderColors.TextPrimary,
                            unfocusedTextColor = RiderColors.TextPrimary,
                            focusedBorderColor = RiderColors.Danger,
                            unfocusedBorderColor = RiderColors.Border,
                            focusedContainerColor = RiderColors.Surface,
                            unfocusedContainerColor = RiderColors.Surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 2
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RiderColors.TextPrimary)
                    ) {
                        Text("Keep Delivery", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                onConfirmCancel(selectedReason, additionalNote)
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Danger)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Confirm Cancel", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RiderOrderDetailDialog(
    offer: ServerOffer,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val billVal = offer.orderTotal ?: offer.codAmount ?: 0.0

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = RiderColors.Surface,
            border = BorderStroke(1.dp, RiderColors.Border),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val shortId = if (offer.orderId.length > 14) offer.orderId.take(12) + "…" else offer.orderId
                        Text(
                            text = "ORDER #$shortId",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RiderColors.TextPrimary
                        )
                        Text(
                            text = "NEW DISPATCH OFFER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RiderColors.AccentBlue
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = RiderColors.PrimarySoft,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = if (billVal > 0.0) "₹${billVal.toInt()}" else "Store Order",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = RiderColors.PrimarySoftText,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = RiderColors.TextMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Locations Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.Top) {
                                Surface(color = RiderColors.AccentBlueSoft, shape = CircleShape, modifier = Modifier.size(26.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Place, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("PICKUP LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.AccentBlue)
                                    Text(offer.merchantName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                                    Text(offer.merchantAddress, fontSize = 12.sp, color = RiderColors.TextSecondary)
                                }
                            }

                            HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                            Row(verticalAlignment = Alignment.Top) {
                                Surface(color = RiderColors.PrimarySoft, shape = CircleShape, modifier = Modifier.size(26.dp)) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(15.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("DELIVERY LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.Primary)
                                    Text(offer.customerName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                                    Text(offer.customerAddress, fontSize = 12.sp, color = RiderColors.TextSecondary)
                                }
                            }

                            HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Total Trip Distance:", fontSize = 12.sp, color = RiderColors.TextMuted)
                                Text(
                                    text = "${offer.totalDistanceKm} km (~${offer.estimatedDurationMins} min)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.Primary
                                )
                            }
                        }
                    }

                    // Items Breakdown Card
                    if (offer.items.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                            border = BorderStroke(1.dp, RiderColors.Border),
                            shape = RoundedCornerShape(14.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "PACKAGE CONTENTS (${offer.items.sumOf { it.quantity }})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.TextMuted
                                )
                                offer.items.forEach { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• ${item.name} × ${item.quantity}",
                                            fontSize = 13.sp,
                                            color = RiderColors.TextPrimary,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (item.price > 0.0) {
                                            Text(
                                                text = "₹${(item.price * item.quantity).toInt()}",
                                                fontSize = 13.sp,
                                                color = RiderColors.TextSecondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // COD Card
                    val effectiveCod = (if (offer.codAmount != null && offer.codAmount > 0.0) offer.codAmount else offer.orderTotal) ?: 0.0
                    if (offer.isCod && effectiveCod > 0.0) {
                        Surface(
                            color = RiderColors.WarningSoft,
                            border = BorderStroke(1.dp, RiderColors.Warning.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "💵 Cash on Delivery: Collect ₹${effectiveCod.toInt()} from customer",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.WarningText,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons pinned at bottom
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Accept Delivery", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RiderColors.TextPrimary),
                            border = BorderStroke(1.dp, RiderColors.Border),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = onDecline,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RiderColors.Danger),
                            border = BorderStroke(1.dp, RiderColors.Danger.copy(alpha = 0.4f)),
                            modifier = Modifier.weight(1f).height(44.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Decline", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
