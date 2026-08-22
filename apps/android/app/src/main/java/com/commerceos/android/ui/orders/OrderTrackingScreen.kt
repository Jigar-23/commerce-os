package com.commerceos.android.ui.orders

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CustomerOrderApiResponse
import com.commerceos.android.model.CustomerOrderTrackingDto
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.viewmodel.OrderDetailUiState

@Composable
fun OrderTrackingScreen(
    detail: OrderDetailUiState,
    liveTracking: CustomerOrderTrackingDto? = null,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
    onContactSupport: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C16))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (detail) {
            is OrderDetailUiState.Loading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color(0xFF1E293B), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Loading order...", style = CommerceTypography.BodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(40.dp))
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF10B981))
                }
            }
            is OrderDetailUiState.NotFound -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color(0xFF1E293B), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Order Status", style = CommerceTypography.BodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(40.dp))
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Order not found", style = CommerceTypography.BodySmall, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is OrderDetailUiState.Error -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color(0xFF1E293B), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text("Order Status", style = CommerceTypography.BodySmall, color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.size(40.dp))
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(detail.message, style = CommerceTypography.BodySmall, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                        Button(
                            onClick = onRefresh,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is OrderDetailUiState.Content -> OrderTrackingContent(
                order = detail.order,
                liveTracking = liveTracking,
                onBack = onBack,
                onContactSupport = onContactSupport
            )
        }
    }
}

@Composable
fun OrderTrackingContent(
    order: CustomerOrderApiResponse,
    liveTracking: CustomerOrderTrackingDto? = null,
    onBack: () -> Unit,
    onContactSupport: () -> Unit
) {
    val scrollState = rememberScrollState()
    val presentation = OrderStatusPresentationMapper.present(order.orderStatus)
    val context = LocalContext.current
    var isMapExpanded by remember { mutableStateOf(false) }

    if (isMapExpanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1120))
        ) {
            val merchantLat = liveTracking?.merchantLat ?: 28.2021899
            val merchantLng = liveTracking?.merchantLng ?: 76.6153954
            val customerLat = liveTracking?.customerLat ?: order.deliveryAddress?.latitude ?: 28.1970
            val customerLng = liveTracking?.customerLng ?: order.deliveryAddress?.longitude ?: 76.6190
            val telemetry = liveTracking?.liveRiderTelemetry

            ZomatoDarkMapView(
                merchantLat = merchantLat,
                merchantLng = merchantLng,
                customerLat = customerLat,
                customerLng = customerLng,
                riderLat = telemetry?.latitude,
                riderLng = telemetry?.longitude,
                riderHeading = telemetry?.heading,
                isStale = liveTracking?.isStale ?: (telemetry?.isStale ?: false),
                modifier = Modifier.fillMaxSize()
            )

            // Floating Top Controls with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isMapExpanded = false },
                    modifier = Modifier
                        .background(Color(0xFF0F172A).copy(alpha = 0.95f), CircleShape)
                        .size(44.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Exit Fullscreen",
                        tint = Color.White
                    )
                }

                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.95f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFF10B981), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ORDER #${order.id.takeLast(6).uppercase()} • LIVE MAP",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        return
    }

    // Pulsing radar animation for active status
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
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
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // App Bar & Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color(0xFF1E293B), CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "ORDER #${order.id.takeLast(8).uppercase()}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "₹${order.totalAmount}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        if (presentation.isCancelled) {
            // Cancelled Order State View (Clean, structured, zero active map)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1417)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", color = Color(0xFFEF4444), fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Text(
                        text = "Order Cancelled",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "This order was cancelled. If you made an online payment, a full refund of ₹${order.totalAmount} will be credited to your original payment method within 2-4 hours. For Cash on Delivery, no payment is due.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Cancelled Items Summary
            val items = order.items.orEmpty()
            if (items.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Cancelled Items", color = Color(0xFF94A3B8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))
                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${item.quantity}x ${item.name}", color = Color(0xFFCBD5E1), fontSize = 13.sp, maxLines = 1)
                                Text("₹${item.unitPrice.toInt() * item.quantity}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
            ) {
                Text("Back to Shopping", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
            }
        } else {
            // Active Quick-Commerce "Crystal Page" Tracking View

            // 1. Hero SLA Crystal Card (Blinkit-Grade)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF10B981).copy(alpha = 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            // Live Status Overline with Pulsing Radar Dot
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .scale(pulseScale)
                                        .background(Color(0xFF10B981).copy(alpha = pulseAlpha), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (order.orderStatus.uppercase()) {
                                        "DELIVERED" -> "ORDER COMPLETED"
                                        "OUT_FOR_DELIVERY" -> "ON THE WAY"
                                        "SELLER_ACCEPTED" -> "BEING PACKED"
                                        else -> "LIVE ORDER"
                                    },
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Hero SLA Headline
                            Text(
                                text = when (order.orderStatus.uppercase()) {
                                    "DELIVERED" -> "Delivered Successfully 🎉"
                                    "OUT_FOR_DELIVERY", "REACHING_YOU" -> "Arriving in 6 mins ⚡"
                                    "SELLER_ACCEPTED" -> "Arriving in 10 mins ⚡"
                                    else -> "Arriving in 10 mins ⚡"
                                },
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Rewari Central Master Store • Fast delivery guaranteed",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        // Status Icon Badge
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = when (order.orderStatus.uppercase()) {
                                        "DELIVERED" -> "🎉"
                                        "OUT_FOR_DELIVERY" -> "🛵"
                                        "SELLER_ACCEPTED" -> "📦"
                                        else -> "⚡"
                                    },
                                    fontSize = 20.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Illuminated Stage Progress Bar
                    val progressFloat = when (order.orderStatus.uppercase()) {
                        "DELIVERED" -> 1.0f
                        "OUT_FOR_DELIVERY", "REACHING_YOU" -> 0.75f
                        "PACKED" -> 0.55f
                        "SELLER_ACCEPTED" -> 0.35f
                        else -> 0.15f
                    }

                    LinearProgressIndicator(
                        progress = { progressFloat },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1E293B)
                    )
                }
            }

            // 2. Hero Dark Map Viewport
            CustomerLiveMapTrackingView(
                order = order,
                liveTracking = liveTracking,
                onExpandClick = { isMapExpanded = true }
            )

            // 3. Verified Security PIN Card (Golden Security Standard)
            if (!order.deliveryOtp.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1914)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
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
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Delivery Handover PIN", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "Share with partner only at doorstep",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = order.deliveryOtp,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
            }

            // 4. Assigned Delivery Partner Card
            val riderName = liveTracking?.riderName
            val riderPhone = liveTracking?.riderPhone ?: ""
            val riderVehicle = liveTracking?.riderVehicle
            val hasAssignedRider = !riderName.isNullOrBlank() && riderName != "null" && riderName != "unassigned" && order.orderStatus.uppercase() !in listOf("PLACED", "PENDING", "CONFIRMED", "SELLER_ACCEPTED")

            if (hasAssignedRider) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
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
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
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
                                    Text(riderName!!, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("4.9 (1,240 orders)", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (!riderVehicle.isNullOrBlank()) {
                                    Text(riderVehicle, color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }
                        }

                        if (riderPhone.isNotBlank()) {
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val dialIntent = Intent(
                                            Intent.ACTION_DIAL,
                                            Uri.parse("tel:$riderPhone")
                                        )
                                        context.startActivity(dialIntent)
                                    } catch (e: Exception) {
                                        onContactSupport()
                                    }
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF10B981)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Call", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (order.orderStatus.uppercase() !in listOf("DELIVERED", "CANCELLED")) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
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
                                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                shape = CircleShape,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("⚡", fontSize = 22.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Assigning Delivery Partner", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Looking for partners near the store...", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }

                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "PACKING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // 5. Fulfillment Timeline
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Order Journey", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(14.dp))
                    FulfillmentTimeline(currentStatus = order.orderStatus)
                }
            }

            // 6. Delivery Address Recap
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Delivery Location", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${order.deliveryAddress?.addressLine ?: "Rewari Central"}, ${order.deliveryAddress?.city ?: ""}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // 7. Delivery Instructions (Blinkit deliveryInstructions Snippet)
            DeliveryInstructionsCard()

            // 8. Itemized Bill Summary & Savings Card
            val items = order.items.orEmpty()
            if (items.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Bill Summary", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (order.paymentMethod.equals("COD", ignoreCase = true)) "Cash on Delivery" else "Paid Online",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${item.quantity}x ${item.name}", color = Color(0xFFCBD5E1), fontSize = 13.sp, maxLines = 1)
                                Text("₹${item.unitPrice.toInt() * item.quantity}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color(0xFF1E293B))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Delivery Fee", color = Color(0xFF94A3B8), fontSize = 12.sp)
                            Text("₹2", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Total Amount", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("₹${order.totalAmount}", color = Color(0xFF10B981), fontSize = 16.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FulfillmentTimeline(currentStatus: String) {
    val steps = listOf(
        "PLACED" to ("Order Placed" to "Order confirmed with store"),
        "SELLER_ACCEPTED" to ("Packing Items" to "Rewari Central Hub is packing your order"),
        "OUT_FOR_DELIVERY" to ("Out for Delivery" to "Partner heading to your location"),
        "DELIVERED" to ("Delivered" to "Delivered at your doorstep")
    )

    val currentIndex = when (currentStatus.uppercase()) {
        "PLACED" -> 0
        "SELLER_ACCEPTED", "PACKED" -> 1
        "OUT_FOR_DELIVERY", "REACHING_YOU", "PICKED_UP" -> 2
        "DELIVERED" -> 3
        else -> 1
    }

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        steps.forEachIndexed { index, (_, labelAndSub) ->
            val (label, sub) = labelAndSub
            val isDone = index <= currentIndex
            val isCurrent = index == currentIndex

            Row(modifier = Modifier.fillMaxWidth()) {
                // Column with indicator dot + connecting line
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isDone -> Color(0xFF10B981)
                            else -> Color(0xFF334155)
                        },
                        modifier = Modifier.size(16.dp)
                    ) {
                        if (isDone) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✓", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (index < steps.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(36.dp)
                                .background(if (index < currentIndex) Color(0xFF10B981) else Color(0xFF1E293B))
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.padding(bottom = if (index < steps.lastIndex) 16.dp else 0.dp)) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isDone) Color.White else Color(0xFF64748B)
                    )
                    Text(
                        text = sub,
                        fontSize = 11.sp,
                        color = if (isCurrent) Color(0xFF94A3B8) else Color(0xFF475569)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeliveryInstructionsCard() {
    var selectedInstructions by remember { mutableStateOf(setOf("🔕 Don't ring bell")) }
    val instructions = listOf(
        "🔕 Don't ring bell",
        "🚪 Leave at door",
        "📞 Call before reaching",
        "🐕 Pet at home"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Delivery Instructions", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Surface(
                    color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "LIVE TO RIDER",
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 grid of instruction chips
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                instructions.chunked(2).forEach { rowChips ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowChips.forEach { chip ->
                            val isSelected = selectedInstructions.contains(chip)
                            Surface(
                                color = if (isSelected) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFF1E293B),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF10B981) else Color(0xFF334155)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedInstructions = if (isSelected) {
                                            selectedInstructions - chip
                                        } else {
                                            selectedInstructions + chip
                                        }
                                    }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = chip,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF10B981) else Color(0xFFCBD5E1),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Partner will follow these instructions upon reaching your doorstep.",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}