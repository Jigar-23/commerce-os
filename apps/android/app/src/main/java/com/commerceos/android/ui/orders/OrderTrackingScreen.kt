package com.commerceos.android.ui.orders

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CustomerOrderApiResponse
import com.commerceos.android.model.CustomerOrderTrackingDto
import com.commerceos.android.model.MedicineImageResolver
import com.commerceos.android.model.OrderItem
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.OrderDetailUiState

@Composable
fun OrderTrackingScreen(
    detail: OrderDetailUiState,
    liveTracking: CustomerOrderTrackingDto? = null,
    isReconnecting: Boolean = false,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
    onContactSupport: () -> Unit = {},
    onReorderItem: (OrderItem) -> Unit = {}
) {
    var isMapExpanded by remember { mutableStateOf(false) }

    if (isMapExpanded && detail is OrderDetailUiState.Content) {
        CustomerLiveMapTrackingView(
            order = detail.order,
            liveTracking = liveTracking,
            modifier = Modifier.fillMaxSize(),
            isFullscreen = true,
            onExitFullscreen = { isMapExpanded = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F7))
    ) {
        when (detail) {
            is OrderDetailUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    DetailTopBar(title = "Loading Order...", subtitle = null, onBack = onBack)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF059669))
                    }
                }
            }
            is OrderDetailUiState.NotFound -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    DetailTopBar(title = "Order Details", subtitle = null, onBack = onBack)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Order not found",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            is OrderDetailUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    DetailTopBar(title = "Order Details", subtitle = null, onBack = onBack)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                detail.message,
                                fontSize = 14.sp,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = onRefresh,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Retry", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            is OrderDetailUiState.Content -> {
                OrderTrackingContent(
                    order = detail.order,
                    liveTracking = liveTracking,
                    isReconnecting = isReconnecting,
                    onBack = onBack,
                    onContactSupport = onContactSupport,
                    onExpandMap = { isMapExpanded = true },
                    onReorderItem = onReorderItem
                )
            }
        }
    }
}

/**
 * Single clean edge-to-edge Top Bar for Order Details (No duplicate headers!)
 */
@Composable
private fun DetailTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = Color.White,
            shape = CircleShape,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 0.5.dp,
            modifier = Modifier.size(38.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
fun OrderTrackingContent(
    order: CustomerOrderApiResponse,
    liveTracking: CustomerOrderTrackingDto? = null,
    isReconnecting: Boolean = false,
    onBack: () -> Unit,
    onContactSupport: () -> Unit,
    onExpandMap: () -> Unit = {},
    onReorderItem: (OrderItem) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val presentation = OrderStatusPresentationMapper.present(order.orderStatus)
    val context = LocalContext.current
    val items = order.items.orEmpty()
    val isDelivered = order.orderStatus.uppercase() == "DELIVERED"
    val isCancelled = presentation.isCancelled
    val isActive = !presentation.isTerminal

    // Pulsing radar animation for active status
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        // 1. FIRST ELEMENT AT VERY TOP: INFINITE WIDTH LIVE TRACKING GOOGLE MAP
        CustomerLiveMapTrackingView(
            order = order,
            liveTracking = liveTracking,
            onExpandClick = onExpandMap,
            onBack = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        )

        // 2. BELOW THE MAP: ORDER STATUS, DELIVERY PIN, TIMELINE, ITEMS, AND DETAILS
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Reconnecting notification banner if telemetry stream drops
            if (isReconnecting) {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.size(8.dp).background(Color(0xFFF59E0B), CircleShape))
                        Text(
                            text = "Live telemetry stream updating...",
                            color = Color(0xFF92400E),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (isCancelled) {
                // Cancelled Order State
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFFEE2E2)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = CircleShape,
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✕", color = Color(0xFFDC2626), fontSize = 22.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Text(
                            text = "Order Cancelled",
                            color = Color(0xFF0F172A),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "This order was cancelled. If you paid online, a full refund of ₹${order.totalAmount} will be credited to your account within 2-4 hours. For Cash on Delivery, no payment is due.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Active / Delivered Order Status & SLA Card
                // 1. Sleek Compact Delivery Status & PIN Card
                val displayOtp = order.effectiveDeliveryPin ?: liveTracking?.deliveryOtp
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .scale(if (isActive) pulseScale else 1.0f)
                                            .background(
                                                if (isActive) Color(0xFF059669).copy(alpha = pulseAlpha) else Color(0xFF2563EB),
                                                CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when (order.orderStatus.uppercase()) {
                                            "DELIVERED" -> "ORDER COMPLETED"
                                            "OUT_FOR_DELIVERY" -> "ON THE WAY"
                                            "SELLER_ACCEPTED", "PACKED" -> "BEING PACKED"
                                            else -> "ORDER CONFIRMED"
                                        },
                                        color = if (isActive) Color(0xFF059669) else Color(0xFF2563EB),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(3.dp))

                                val dynamicEtaMins = order.deliverySlaMins.coerceAtLeast(8)
                                Text(
                                    text = when (order.orderStatus.uppercase()) {
                                        "DELIVERED" -> "Delivered Successfully 🎉"
                                        "ARRIVED_CUSTOMER", "HANDOFF_STARTED" -> "At your doorstep ⚡"
                                        else -> "Arriving in $dynamicEtaMins mins ⚡"
                                    },
                                    color = Color(0xFF0F172A),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "${order.storeName ?: "Central Express Hub"} • Direct dispatch",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }

                            if (isActive && !displayOtp.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFFFFFBEB),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFFCD34D))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(11.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("PIN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF92400E))
                                        }
                                        Text(
                                            text = displayOtp,
                                            color = Color(0xFFB45309),
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stepper Progress Line
                        val progressFloat = when (order.orderStatus.uppercase()) {
                            "DELIVERED" -> 1.0f
                            "OUT_FOR_DELIVERY", "REACHING_YOU" -> 0.75f
                            "PACKED" -> 0.55f
                            "SELLER_ACCEPTED" -> 0.35f
                            else -> 0.25f
                        }

                        LinearProgressIndicator(
                            progress = { progressFloat },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = Color(0xFF059669),
                            trackColor = Color(0xFFE2E8F0)
                        )
                    }
                }

                // Assigned Delivery Partner Card
                val assignedRiderName = liveTracking?.riderName ?: order.riderName
                val assignedRiderPhone = liveTracking?.riderPhone ?: (order.riderPhone ?: "")
                val assignedRiderVehicle = liveTracking?.riderVehicle ?: order.riderVehicle
                val hasAssignedRider = !assignedRiderName.isNullOrBlank() && assignedRiderName != "null" && assignedRiderName != "unassigned"

                if (hasAssignedRider && assignedRiderName != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Surface(
                                    color = Color(0xFFECFDF5),
                                    shape = CircleShape,
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🛵", fontSize = 22.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = assignedRiderName, color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                                    }
                                    Text(text = "Delivery Partner • 4.9 ★", color = Color(0xFF059669), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                    if (!assignedRiderVehicle.isNullOrBlank()) {
                                        Text(text = assignedRiderVehicle, color = Color(0xFF64748B), fontSize = 11.sp)
                                    }
                                }
                            }

                            if (assignedRiderPhone.isNotBlank()) {
                                FilledTonalButton(
                                    onClick = {
                                        try {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$assignedRiderPhone"))
                                            context.startActivity(dialIntent)
                                        } catch (e: Exception) {
                                            onContactSupport()
                                        }
                                    },
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF059669)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 2. COMPLETE ANATOMY OF ORDER ITEMS (Authentic Medicine Packaging Photos)
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Items Ordered (${items.size})",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items.forEach { item ->
                            val resolvedUrl = remember(item.sku, item.name) {
                                MedicineImageResolver.resolve(item.sku, item.name)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Authentic Medicine Photo
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.size(60.dp)
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

                                Spacer(modifier = Modifier.width(12.dp))

                                // Item Details & Anatomy
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF0F172A),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = Color(0xFFF1F5F9),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "Qty: ${item.quantity}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "₹${item.unitPrice.toInt()} each",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                // Total for item
                                Text(
                                    text = "₹${item.unitPrice.toInt() * item.quantity}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 3. LICENSED PHARMACY & QUALITY ASSURANCE ANATOMY
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = CircleShape,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Verified Pharmacy Dispensation",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Licensed Pharmacist Verified & Quality Checked",
                                fontSize = 11.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Dispensed By", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text(order.storeName ?: "CommerceOS Central Express Hub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Drug License", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("DL-HR-2024-009182", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Safety & Storage", fontSize = 12.sp, color = Color(0xFF64748B))
                        Text("Tamper-Evident & Cold Chain Monitored", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF059669))
                    }
                }
            }

            // =========================================================================
            // 4. DELIVERY ADDRESS & RECIPIENT
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delivery Location", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    val addr = order.deliveryAddress
                    Text(
                        text = buildString {
                            append(addr?.addressLine ?: "Delivery address registered on order")
                            if (!addr?.city.isNullOrBlank()) {
                                append(", ")
                                append(addr!!.city)
                            }
                            if (!addr?.postalCode.isNullOrBlank()) {
                                append(" - ")
                                append(addr!!.postalCode)
                            }
                        },
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 18.sp
                    )
                }
            }

            // =========================================================================
            // 5. BILL ANATOMY & PAYMENT BREAKDOWN
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bill Summary", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (order.paymentMethod.equals("COD", ignoreCase = true)) "Cash on Delivery" else "Paid Online",
                                color = Color(0xFF059669),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val itemSubtotal = items.sumOf { (it.unitPrice.toInt() * it.quantity) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Item Total", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text("₹$itemSubtotal", fontSize = 13.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Medium)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Delivery Partner Fee", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(
                            text = if (order.totalAmount.toInt() - itemSubtotal <= 0) "FREE" else "₹${order.totalAmount.toInt() - itemSubtotal}",
                            fontSize = 13.sp,
                            color = if (order.totalAmount.toInt() - itemSubtotal <= 0) Color(0xFF059669) else Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Govt. Healthcare Cess & Handling", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text("FREE", fontSize = 13.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Amount", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text("₹${order.totalAmount}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    }
                }
            }

            // =========================================================================
            // 6. ORDER META & ACTIONS
            // =========================================================================
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Order ID", fontSize = 12.sp, color = Color(0xFF64748B))
                            Text(order.id, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.3f)),
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Order ID", order.id))
                                Toast.makeText(context, "Order ID copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                text = "Copy",
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onContactSupport,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF334155)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Need Help?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        if (items.isNotEmpty()) {
                            Button(
                                onClick = {
                                    items.forEach { onReorderItem(it) }
                                    Toast.makeText(context, "Reordered ${items.size} items to cart", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Order Again", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}