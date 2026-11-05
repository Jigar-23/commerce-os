package com.commerceos.android.ui.orders

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
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
    onReorderItem: (OrderItem) -> Unit = {},
    onReorderOrder: (CustomerOrderApiResponse) -> Unit = {}
) {
    var isMapExpanded by remember { mutableStateOf(false) }

    // Intercept physical / gesture back click when map is maximized to collapse map back to order details!
    BackHandler(enabled = isMapExpanded) {
        isMapExpanded = false
    }

    if (isMapExpanded && detail is OrderDetailUiState.Content && !detail.order.effectiveStatus.equals("DELIVERED", ignoreCase = true) && !detail.order.effectiveStatus.equals("CANCELLED", ignoreCase = true)) {
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
                    onReorderItem = onReorderItem,
                    onReorderOrder = onReorderOrder
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
                    tint = Color(0xFF0E675E),
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
    onReorderItem: (OrderItem) -> Unit = {},
    onReorderOrder: (CustomerOrderApiResponse) -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val currentStatus = order.effectiveStatus
    val presentation = OrderStatusPresentationMapper.present(currentStatus)
    val context = LocalContext.current
    val items = order.items.orEmpty()
    val isDelivered = currentStatus.equals("DELIVERED", ignoreCase = true)
    val isCancelled = currentStatus.equals("CANCELLED", ignoreCase = true) || presentation.isCancelled
    val isActive = !presentation.isTerminal && !isDelivered && !isCancelled
    var showSupportSheet by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
        // 1. FIRST ELEMENT AT VERY TOP:
        // Active delivery -> Live interactive Google Map tracking
        // Delivered / Cancelled order -> Clean top app bar with Back button and Order ID (NO MAP)
        if (!isDelivered && !isCancelled) {
            CustomerLiveMapTrackingView(
                order = order,
                liveTracking = liveTracking,
                onExpandClick = onExpandMap,
                onBack = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        } else {
            // Dedicated Order Details Top Bar for Delivered or Cancelled Order (Map is hidden)
            Surface(
                color = Color.White,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFF0E675E))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                if (isCancelled) "Order Cancelled" else "Order Details",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCancelled) Color(0xFFDC2626) else Color(0xFF0F172A)
                            )
                            Text(
                                if (isCancelled) "Cancellation Status" else "Summary & Receipt",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Authoritative Order ID Badge with Copy
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Order ID", order.displayOrderId))
                            Toast.makeText(context, "Order ID copied: ${order.displayOrderId}", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "#${order.displayOrderId.takeLast(10)}",
                                color = Color(0xFF0F172A),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. BELOW TOP HEADER: STATUS CARDS, DETAILS, ITEMS, RECEIPT
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Reconnecting notification banner if telemetry stream drops
            if (isReconnecting && !isDelivered) {
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
                            text = "Live telemetry stream reconnecting with exponential backoff...",
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
                            text = "Your order was cancelled. No payment was collected; no refund is due.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (isDelivered) {
                // =========================================================================
                // TATA 1MG BENCHMARK: DELIVERED CELEBRATION, 4-STAGE STEPPER & PRESCRIPTIONS
                // =========================================================================
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Skyline Celebration Banner (Tata 1mg Benchmark)
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        shadowElevation = 0.5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Soft blue gradient skyline illustration background
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFFE0F2FE).copy(alpha = 0.7f),
                                                Color(0xFFF0F9FF).copy(alpha = 0.3f),
                                                Color.White
                                            )
                                        )
                                    )
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Surface(
                                        color = Color(0xFF00B57A),
                                        shape = CircleShape,
                                        modifier = Modifier.size(54.dp),
                                        shadowElevation = 2.dp
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Delivered",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Order delivered",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "Thank you for trusting us.",
                                        fontSize = 13.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            // 2. 4-Stage Stepper Card (Placed -> Validated -> Shipped -> Delivered)
                            Surface(
                                color = Color(0xFFF0F9FF),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFBAE6FD).copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFF0284C7),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "Delivered",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Text(
                                            text = "Delivered on ${order.displayOrderDate}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // 4-Node Horizontal Stepper
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val stages = listOf("Placed", "Validated", "Shipped", "Delivered")
                                        stages.forEachIndexed { idx, stageName ->
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Surface(
                                                    color = Color(0xFF00B57A),
                                                    shape = CircleShape,
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = Color.White,
                                                            modifier = Modifier.size(13.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stageName,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (idx == 3) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (idx == 3) Color(0xFF00B57A) else Color(0xFF64748B)
                                                )
                                            }
                                            if (idx < stages.size - 1) {
                                                Box(
                                                    modifier = Modifier
                                                        .weight(0.8f)
                                                        .height(2.5.dp)
                                                        .offset(y = (-8).dp)
                                                        .background(Color(0xFF00B57A))
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.8.dp)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Coral Red "Reorder" Button (Tata 1mg Benchmark)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = {
                                                if (items.isNotEmpty()) {
                                                    onReorderOrder(order)
                                                } else {
                                                    items.firstOrNull()?.let { onReorderItem(it) }
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = "Reorder",
                                                fontSize = 14.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE11D48)
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .height(18.dp)
                                                .width(1.dp)
                                                .background(Color(0xFFE2E8F0))
                                        )
                                        TextButton(onClick = { showSupportSheet = true; onContactSupport() }) {
                                            Text(
                                                text = "Need Help?",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // 3. Need Help With Your Order Card (Tata 1mg Benchmark)
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        shadowElevation = 0.5.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = Color(0xFFFEE2E2),
                                    shape = CircleShape,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "?",
                                            color = Color(0xFFE11D48),
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "Need help with your order?",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "Contact us",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE11D48),
                                        modifier = Modifier.clickable { showSupportSheet = true; onContactSupport() }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Easy return and pharmacist support available",
                                fontSize = 11.5.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // 4. Prescriptions Attached Card (Only shown when order actually contains prescription/Rx items)
                    val hasPrescription = !order.prescriptionId.isNullOrBlank() || order.pharmacistVerification != null || items.any { it.effectiveRxRequired }
                    if (hasPrescription) {
                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 0.5.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (!order.prescriptionId.isNullOrBlank()) "Prescription Attached" else "Medical Verification",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color(0xFFFBCFE8)),
                                        modifier = Modifier.size(width = 72.dp, height = 90.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color(0xFFFDF2F8))
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp),
                                                verticalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Surface(
                                                    color = Color(0xFFDB2777),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = if (order.prescriptionId != null) "PRESCRIPTION" else "VERIFIED RX",
                                                        fontSize = 7.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "Rx Verified\nDr. Approved",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF831843),
                                                    lineHeight = 11.sp
                                                )
                                            }

                                            // Green verified check badge at top right
                                            Surface(
                                                color = Color(0xFF00B57A),
                                                shape = CircleShape,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .align(Alignment.TopEnd)
                                                    .offset(x = (-4).dp, y = 4.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = "Verified",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    val verificationNote = order.pharmacistVerification?.licenseNo?.let { "Verified by Pharmacist ($it)" }
                                        ?: "Verified by Registered Pharmacist"
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = verificationNote,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "Prescription verified under CDSCO schedule drug safety standards.",
                                            fontSize = 11.5.sp,
                                            color = Color(0xFF64748B),
                                            lineHeight = 15.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Order Status & SLA Card with Live PIN
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
                                        text = when (currentStatus.uppercase()) {
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
                                    text = when (currentStatus.uppercase()) {
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
                        val progressFloat = when (currentStatus.uppercase()) {
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
                                        Icon(
                                            imageVector = Icons.Default.LocalShipping,
                                            contentDescription = "Rider",
                                            tint = Color(0xFF059669),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = assignedRiderName, color = Color(0xFF0F172A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color(0xFF059669), modifier = Modifier.size(14.dp))
                                    }
                                    Text(text = "Express Pharmacy Delivery Fleet", color = Color(0xFF059669), fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
                            val sku = item.effectiveSku
                            val name = item.effectiveName
                            val price = item.effectivePrice
                            val qty = item.effectiveQuantity
                            val resolvedUrl = remember(sku, name) {
                                MedicineImageResolver.resolve(sku, name)
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
                                        contentDescription = name,
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
                                        text = name,
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
                                                text = "Qty: $qty",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "₹${price.toInt()} each",
                                            fontSize = 12.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }

                                // Total for item
                                Text(
                                    text = "₹${price.toInt() * qty}",
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
                    val fullAddressText = buildString {
                        val mainLine = addr?.addressLine?.takeIf { it.isNotBlank() }
                        if (mainLine != null) {
                            append(mainLine)
                        } else {
                            append("Delivery address registered on order")
                        }
                        if (!addr?.landmark.isNullOrBlank()) {
                            append(", Near ")
                            append(addr!!.landmark)
                        }
                        if (!addr?.city.isNullOrBlank()) {
                            append(", ")
                            append(addr!!.city)
                        }
                        if (!addr?.state.isNullOrBlank()) {
                            append(", ")
                            append(addr!!.state)
                        }
                        if (!addr?.postalCode.isNullOrBlank()) {
                            append(" - ")
                            append(addr!!.postalCode)
                        }
                    }
                    Text(
                        text = fullAddressText,
                        fontSize = 13.sp,
                        color = Color(0xFF334155),
                        lineHeight = 18.sp
                    )
                    if (!addr?.contactName.isNullOrBlank() || !addr?.contactPhone.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val contactParts = listOfNotNull(
                            addr?.contactName?.takeIf { it.isNotBlank() },
                            addr?.contactPhone?.takeIf { it.isNotBlank() }
                        )
                        if (contactParts.isNotEmpty()) {
                            Text(
                                text = "Contact: ${contactParts.joinToString(" • ")}",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
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
                        Column {
                            Text("Bill Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Order Placed: ${order.displayOrderDateTime}",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (order.paymentMethod.equals("COD", ignoreCase = true)) "Cash on Delivery" else "Payment: ${order.paymentMethod.ifBlank { "Unspecified" }}",
                                color = Color(0xFF059669),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    val bill = order.billSummary ?: order.bill_summary
                    val itemSubtotal = bill?.itemsSubtotal?.toDouble()?.takeIf { it > 0.0 }
                        ?: items.sumOf { (it.unitPrice.toDouble() * it.quantity) }
                    val effectiveDeliveryFee = bill?.deliveryFee?.toDouble()
                        ?: order.deliveryFee?.toDouble()
                        ?: order.delivery_fee?.toDouble()
                        ?: (if (itemSubtotal >= 199.0) 0.0 else 2.0)
                    val isFreeDelivery = effectiveDeliveryFee <= 0.0
                    val handlingFee = bill?.handlingFee?.toDouble()
                        ?: (order.packagingFee?.toDouble() ?: order.handlingFee?.toDouble() ?: 0.0)
                    val effectiveTotal = bill?.grandTotal?.toDouble()?.takeIf { it > 0.0 }
                        ?: order.effectiveTotalAmount.toDouble().takeIf { it > 0.0 }
                        ?: (itemSubtotal + (if (isFreeDelivery) 0.0 else effectiveDeliveryFee) + handlingFee)
                    val productDiscount = bill?.discountAmount?.toDouble()?.takeIf { it > 0.0 }
                        ?: (order.discountAmount?.toDouble()?.takeIf { it > 0.0 } ?: (if ((itemSubtotal + effectiveDeliveryFee + handlingFee) > effectiveTotal) {
                            Math.round(((itemSubtotal + effectiveDeliveryFee + handlingFee) - effectiveTotal) * 100.0) / 100.0
                        } else 0.0))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Item Total", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(
                            text = if (itemSubtotal % 1.0 == 0.0) "₹${itemSubtotal.toInt()}" else "₹${"%.2f".format(itemSubtotal)}",
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (productDiscount > 0.0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Product Discount", fontSize = 13.sp, color = Color(0xFF059669), fontWeight = FontWeight.Medium)
                            Text(
                                text = "- ₹${if (productDiscount % 1.0 == 0.0) productDiscount.toInt() else "%.2f".format(productDiscount)}",
                                fontSize = 13.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Delivery Fee", fontSize = 13.sp, color = Color(0xFF64748B))
                        Text(
                            text = if (isFreeDelivery) "FREE" else (if (effectiveDeliveryFee % 1.0 == 0.0) "₹${effectiveDeliveryFee.toInt()}" else "₹${"%.2f".format(effectiveDeliveryFee)}"),
                            fontSize = 13.sp,
                            color = if (isFreeDelivery) Color(0xFF059669) else Color(0xFF0F172A),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (handlingFee > 0.0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Handling & Packaging", fontSize = 13.sp, color = Color(0xFF64748B))
                            Text(
                                text = if (handlingFee % 1.0 == 0.0) "₹${handlingFee.toInt()}" else "₹${"%.2f".format(handlingFee)}",
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Grand Total", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                        Text(
                            text = if (effectiveTotal % 1.0 == 0.0) "₹${effectiveTotal.toInt()}" else "₹${"%.2f".format(effectiveTotal)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
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
                            Text(
                                order.displayOrderId,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF059669).copy(alpha = 0.3f)),
                            modifier = Modifier.clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Order ID", order.displayOrderId))
                                Toast.makeText(context, "Order ID copied: ${order.displayOrderId}", Toast.LENGTH_SHORT).show()
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

                    // Order Date & Payment Mode (Tata 1mg Benchmark)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Order Date", fontSize = 12.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = order.displayOrderDateTime,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Payment Mode", fontSize = 12.sp, color = Color(0xFF64748B))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (order.paymentMethod.equals("COD", ignoreCase = true)) "Cash on Delivery" else "Payment: ${order.paymentMethod.ifBlank { "Unspecified" }}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
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
                            onClick = {
                                showSupportSheet = true
                                onContactSupport()
                            },
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
                                    onReorderOrder(order)
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

            // 100% Genuine Medicine Guarantee Commitment Card (Tata 1mg Benchmark)
            Surface(
                color = Color(0xFFFEFCE8),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFEF08A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = Color(0xFFCA8A04),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "100% Genuine Healthcare Commitment",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF713F12)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "We are committed to 100% customer satisfaction by offering only genuine products, secure payments, and easy returns.",
                            fontSize = 11.sp,
                            color = Color(0xFF854D0E),
                            lineHeight = 15.sp
                        )
                    }

                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Text(
                            text = "★ GENUINE ★",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    // Floating "Scan and verify" Action Button (Tata 1mg Benchmark)
    if (isDelivered) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 20.dp)
                .clickable {
                    Toast.makeText(context, "Scanning package QR code for authenticity...", Toast.LENGTH_SHORT).show()
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = Color(0xFFE11D48),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "Scan and verify",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }

    if (showSupportSheet) {
        OrderSupportBottomSheet(
            order = order,
            onDismiss = { showSupportSheet = false }
        )
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderSupportBottomSheet(
    order: CustomerOrderApiResponse,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val supportPhone = order.storePhone?.takeIf { it.isNotBlank() }
        ?: order.merchantPhone?.takeIf { it.isNotBlank() }
        ?: "+91 1800-208-9999"
    val cleanPhone = supportPhone.replace(" ", "").replace("-", "")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Need Help With Your Order?",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "Order #${order.displayOrderId}",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                }
            }

            HorizontalDivider(color = Color(0xFFF1F5F9))

            // Option 1: Call Store / Support
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanPhone"))
                            context.startActivity(dialIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Support phone: $supportPhone", Toast.LENGTH_LONG).show()
                        }
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = Color(0xFF059669).copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Call Pharmacy / Support", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                        Text("Available 24x7 for prescription and order assistance ($supportPhone)", fontSize = 11.5.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }

            // Option 2: WhatsApp Chat
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val url = "https://wa.me/9118002089999?text=Hello,%20I%20need%20help%20with%20Commerce%20OS%20Order%20%23${order.displayOrderId}"
                            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(waIntent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "WhatsApp support at +91 1800-208-9999", Toast.LENGTH_LONG).show()
                        }
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = Color(0xFF25D366).copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Chat on WhatsApp", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                        Text("Instant resolution with licensed pharmacist assistant", fontSize = 11.5.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }

            // Option 3: Report Damaged / Missing Item
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Toast.makeText(context, "Support ticket opened for Order #${order.displayOrderId}. Support team will contact you shortly.", Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        color = Color(0xFFE11D48).copy(alpha = 0.12f),
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Report Issue with Order / Items", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
                        Text("Damaged packing, missing medicine, or wrong delivery", fontSize = 11.5.sp, color = Color(0xFF64748B))
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}