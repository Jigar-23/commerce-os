package com.commerceos.rider.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.RoutePoint
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.repository.RiderDeliveryRepository
import com.commerceos.rider.theme.RiderColors
import com.commerceos.rider.util.RiderNavigationUtils

/**
 * World-Class Quick-Commerce Delivery Partner Active Delivery Dashboard.
 * Designed after Zomato / Swiggy Instamart / Blinkit Partner apps.
 * Unified viewport: Prominent Navigation Map (Top), Stage Details (Center), Docked Action Bar (Bottom).
 * Zero duplicate buttons, zero visual clutter.
 */
@Composable
fun ActiveDeliveryScreen(
    session: ServerDeliverySession,
    repository: RiderDeliveryRepository,
    riderLat: Double?,
    riderLng: Double?,
    riderHeading: Float?,
    isStale: Boolean,
    onArrivedStore: () -> Unit,
    onConfirmPickup: () -> Unit,
    onArrivedCustomer: () -> Unit,
    onCompleteDelivery: () -> Unit,
    onViewOrderDetails: () -> Unit,
    enteredOtp: String,
    onOtpChange: (String) -> Unit,
    onVerifyOtp: () -> Unit,
    onResendOtp: () -> Unit,
    resendCooldown: Int,
    enteredCodAmount: String,
    onCodAmountChange: (String) -> Unit,
    onReconcileCod: () -> Unit,
    onReportIssue: () -> Unit,
    onCancelDelivery: () -> Unit = {},
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showManualArrivalConfirmDialog by remember { mutableStateOf(false) }

    val merchantLat = session.merchantLat?.takeIf { it != 0.0 } ?: 0.0
    val merchantLng = session.merchantLng?.takeIf { it != 0.0 } ?: 0.0
    val customerLat = session.customerLat?.takeIf { it != 0.0 } ?: 0.0
    val customerLng = session.customerLng?.takeIf { it != 0.0 } ?: 0.0

    val isPhase1 = session.state in listOf(
        "ASSIGNED", "ACCEPTED", "EN_ROUTE_PICKUP", "OUT_FOR_PICKUP",
        "EN_ROUTE_STORE", "ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_AT_STORE", "ARRIVED_MERCHANT"
    )
    val isAtStore = session.state in listOf("ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_AT_STORE", "ARRIVED_MERCHANT")
    val isEnRouteCustomer = session.state in listOf("PICKED_UP", "EN_ROUTE_CUSTOMER", "OUT_FOR_DELIVERY")
    val isAtCustomer = session.state in listOf("ARRIVED_CUSTOMER", "HANDOFF_STARTED")

    val targetLat = if (isPhase1) merchantLat else customerLat
    val targetLng = if (isPhase1) merchantLng else customerLng
    val targetName = if (isPhase1) {
        session.merchantName.takeIf { it.isNotBlank() && it != "null" } ?: "Store Hub"
    } else {
        session.customerName.takeIf { it.isNotBlank() && it != "null" } ?: "Customer"
    }
    val targetAddress = if (isPhase1) {
        session.merchantAddress.takeIf { it.isNotBlank() && it != "null" } ?: "Store Partner Hub"
    } else {
        session.customerAddress.takeIf { it.isNotBlank() && it != "null" } ?: "Customer Delivery Address"
    }

    val contactPhone = if (isPhase1) {
        session.merchantPhone.ifBlank { "1800123456" }
    } else {
        session.customerPhone.ifBlank { session.maskedCustomerPhone }
    }

    val hasRiderGps = riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0

    var waypoints by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var routeDistanceKm by remember { mutableStateOf<Double?>(null) }
    var routeDurationMins by remember { mutableStateOf<Int?>(null) }
    var isRouteLoading by remember { mutableStateOf(false) }

    var lastRoutedOriginLat by remember { mutableStateOf<Double?>(null) }
    var lastRoutedOriginLng by remember { mutableStateOf<Double?>(null) }
    var lastRoutedPhase by remember { mutableStateOf(isPhase1) }
    var cachedRiderLat by remember { mutableStateOf<Double?>(null) }
    var cachedRiderLng by remember { mutableStateOf<Double?>(null) }

    if (hasRiderGps) {
        cachedRiderLat = riderLat
        cachedRiderLng = riderLng
    }

    // Authoritative OSRM road route fetching
    LaunchedEffect(riderLat, riderLng, targetLat, targetLng, isPhase1) {
        if (!com.commerceos.rider.model.OfferPayloadValidator.isValidLat(targetLat) ||
            !com.commerceos.rider.model.OfferPayloadValidator.isValidLng(targetLng)) {
            return@LaunchedEffect
        }
        val effectiveLat = riderLat ?: cachedRiderLat
        val effectiveLng = riderLng ?: cachedRiderLng
        val originLat = effectiveLat ?: (if (isPhase1) (merchantLat - 0.008) else merchantLat)
        val originLng = effectiveLng ?: (if (isPhase1) (merchantLng - 0.006) else merchantLng)

        val prevLat = lastRoutedOriginLat
        val prevLng = lastRoutedOriginLng
        if (waypoints.size >= 2 && isPhase1 == lastRoutedPhase && prevLat != null && prevLng != null) {
            val distArr = FloatArray(1)
            android.location.Location.distanceBetween(prevLat, prevLng, originLat, originLng, distArr)
            if (distArr[0] < 40.0f) {
                return@LaunchedEffect
            }
        }

        isRouteLoading = true
        val res = repository.fetchRoute(originLat, originLng, targetLat, targetLng)
        res.onSuccess { routeResult ->
            if (routeResult.waypoints.size >= 2) {
                waypoints = routeResult.waypoints
                lastRoutedOriginLat = originLat
                lastRoutedOriginLng = originLng
                lastRoutedPhase = isPhase1
            }
            routeDistanceKm = routeResult.distanceKm
            routeDurationMins = routeResult.durationMins
        }
        isRouteLoading = false
    }

    val distanceToStoreMeters = remember(riderLat, riderLng, merchantLat, merchantLng) {
        if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(riderLat, riderLng, merchantLat, merchantLng, results)
            results[0].toDouble()
        } else {
            null
        }
    }
    val isWithinStoreArrivalRadius = distanceToStoreMeters != null && distanceToStoreMeters <= 200.0

    val displayDistanceKm = routeDistanceKm ?: session.distanceKm ?: 0.0
    val displayEtaMins = routeDurationMins ?: session.estimatedTimeMins ?: 0

    val stageTitle = when {
        isAtCustomer -> "AT DOORSTEP"
        isEnRouteCustomer -> "HEADING TO CUSTOMER"
        isAtStore -> "AT STORE HUB"
        else -> "HEADING TO STORE"
    }

    val stageBadgeColor = when {
        isAtCustomer -> RiderColors.Primary
        isEnRouteCustomer -> RiderColors.Warning
        isAtStore -> RiderColors.Primary
        else -> RiderColors.AccentBlue
    }

    val stageBadgeBg = when {
        isAtCustomer -> RiderColors.PrimarySoft
        isEnRouteCustomer -> RiderColors.WarningSoft
        isAtStore -> RiderColors.PrimarySoft
        else -> RiderColors.AccentBlueSoft
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(RiderColors.Background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // -------------------------------------------------------------
            // SECTION 1: MAP CANVAS & FLOATING GLASS HUD (~42% HEIGHT)
            // -------------------------------------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
            ) {
                // Native Map
                NativeGoogleRiderNavMap(
                    merchantLat = merchantLat,
                    merchantLng = merchantLng,
                    customerLat = customerLat,
                    customerLng = customerLng,
                    riderLat = riderLat,
                    riderLng = riderLng,
                    riderHeading = riderHeading,
                    waypoints = waypoints,
                    isPhase1 = isPhase1,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Floating Glass HUD Card
                Surface(
                    color = RiderColors.Surface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, RiderColors.Border.copy(alpha = 0.8f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = stageBadgeBg,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = stageTitle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        color = stageBadgeColor,
                                        letterSpacing = 0.6.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                val shortId = if (session.orderId.length > 12) session.orderId.take(10) + "…" else session.orderId
                                Text(
                                    text = "#$shortId",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = targetName,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // ETA & Distance Cluster
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "$displayEtaMins MIN",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = stageBadgeColor
                            )
                            Text(
                                text = "%.1f km".format(displayDistanceKm),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RiderColors.TextMuted
                            )
                        }
                    }
                }

                // Floating Action Pills (Bottom of Map Area)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Navigate external button
                    Surface(
                        color = RiderColors.Surface.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shadowElevation = 3.dp,
                        modifier = Modifier.clickable {
                            RiderNavigationUtils.launchExternalMaps(context, targetLat, targetLng, targetName)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Maps ↗", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RiderColors.AccentBlueText)
                        }
                    }

                    // Direct Call button
                    Surface(
                        color = RiderColors.Surface.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, RiderColors.Border),
                        shadowElevation = 3.dp,
                        modifier = Modifier.clickable {
                            RiderNavigationUtils.dialPhoneNumber(context, contactPhone)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isPhase1) "Hub" else "Customer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RiderColors.PrimarySoftText)
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // SECTION 2: DEDICATED STAGE CONTENT (SCROLLABLE, NO DUPLICATE BUTTONS)
            // -------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f)
                    .background(RiderColors.Surface)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    // STAGE 1A: EN ROUTE TO STORE
                    !isAtStore && isPhase1 -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = RiderColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, RiderColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Place, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("PICKUP DESTINATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.AccentBlue, letterSpacing = 0.5.sp)
                                }
                                Text(targetName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                                Text(targetAddress, fontSize = 12.sp, color = RiderColors.TextSecondary, lineHeight = 17.sp)

                                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val itemCount = session.items.size
                                    Text("Package: $itemCount ${if (itemCount == 1) "item" else "items"} ready", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.TextPrimary)
                                    Text(
                                        text = if (isWithinStoreArrivalRadius) "● Near store hub" else distanceToStoreMeters?.let { "📍 ~%.0fm away".format(it) } ?: "● Tracking GPS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isWithinStoreArrivalRadius) RiderColors.Primary else RiderColors.WarningText
                                    )
                                }
                            }
                        }
                    }

                    // STAGE 1B: AT STORE (PICKUP VERIFICATION)
                    isAtStore -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = RiderColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, RiderColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("STORE PACKAGE VERIFICATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.Primary, letterSpacing = 0.5.sp)
                                }
                                Text("Collect sealed package from billing desk", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bag is sealed with security tamper-proof tape", fontSize = 12.sp, color = RiderColors.TextSecondary)
                                    }
                                    if (session.items.isNotEmpty()) {
                                        Row(verticalAlignment = Alignment.Top) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(15.dp).padding(top = 2.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            val itemsText = session.items.joinToString(", ") { "${it.name} (x${it.quantity})" }
                                            Text("Contents: $itemsText", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.AccentBlueText)
                                        }
                                    }
                                }

                                val totalVal = session.orderTotal ?: session.codAmount ?: 0.0
                                if (totalVal > 0.0) {
                                    HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Store Bill Total", fontSize = 12.sp, color = RiderColors.TextSecondary)
                                        Text("₹%.2f".format(totalVal), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                                    }
                                }
                            }
                        }
                    }

                    // STAGE 2A: EN ROUTE TO CUSTOMER
                    isEnRouteCustomer -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = RiderColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, RiderColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = RiderColors.Warning, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("CUSTOMER DELIVERY DESTINATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.Warning, letterSpacing = 0.5.sp)
                                }
                                Text(targetName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                                Text(targetAddress, fontSize = 12.sp, color = RiderColors.TextSecondary, lineHeight = 17.sp)

                                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                                // Payment status banner
                                if (session.isCod) {
                                    Surface(
                                        color = RiderColors.WarningSoft,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("💵 Cash on Delivery: Collect ₹${(session.codAmount ?: session.orderTotal ?: 0.0).toInt()} at doorstep", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RiderColors.WarningText)
                                        }
                                    }
                                } else {
                                    Surface(
                                        color = RiderColors.PrimarySoft,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("✓ Paid Online: Do NOT collect cash from customer", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RiderColors.PrimarySoftText)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // STAGE 2B: AT CUSTOMER DOORSTEP (HANDOFF)
                    isAtCustomer -> {
                        // COD Cash Collection Card (if applicable)
                        if (session.isCod) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (session.codReconciled) RiderColors.PrimarySoft else RiderColors.WarningSoft,
                                border = BorderStroke(1.dp, if (session.codReconciled) RiderColors.Primary else RiderColors.Warning.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (session.codReconciled) "CASH RECEIVED" else "CASH TO COLLECT",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (session.codReconciled) RiderColors.PrimarySoftText else RiderColors.WarningText,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = "₹${(session.codAmount ?: session.orderTotal ?: 0.0).toInt()}",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (session.codReconciled) RiderColors.PrimarySoftText else RiderColors.WarningText
                                        )
                                    }

                                    if (!session.codReconciled) {
                                        Button(
                                            onClick = onReconcileCod,
                                            colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Warning),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Confirm Cash", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    } else {
                                        Surface(color = RiderColors.Primary, shape = CircleShape, modifier = Modifier.size(26.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Delivery PIN Card
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = RiderColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, RiderColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Lock, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(17.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("CUSTOMER DELIVERY PIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.Primary, letterSpacing = 0.5.sp)
                                    }

                                    if (!session.otpVerified) {
                                        if (resendCooldown > 0) {
                                            Text("${resendCooldown}s", fontSize = 11.sp, color = RiderColors.TextMuted, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text(
                                                text = "Resend PIN",
                                                fontSize = 11.sp,
                                                color = RiderColors.AccentBlueText,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable { onResendOtp() }
                                            )
                                        }
                                    }
                                }

                                if (session.otpVerified) {
                                    Surface(
                                        color = RiderColors.PrimarySoft,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Delivery PIN Verified by Customer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.PrimarySoftText)
                                        }
                                    }
                                } else {
                                    Text("Ask customer for the 4-digit PIN shown on their order screen:", fontSize = 12.sp, color = RiderColors.TextSecondary)

                                    OutlinedTextField(
                                        value = enteredOtp,
                                        onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) onOtpChange(it) },
                                        placeholder = { Text("• • • •", fontSize = 22.sp, color = RiderColors.Border, textAlign = TextAlign.Center) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            textAlign = TextAlign.Center,
                                            letterSpacing = 10.sp,
                                            color = RiderColors.TextPrimary
                                        ),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RiderColors.Primary,
                                            unfocusedBorderColor = RiderColors.Border,
                                            cursorColor = RiderColors.Primary,
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                // Error message banner (if any)
                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = RiderColors.DangerSoft,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ $errorMessage",
                            color = RiderColors.DangerText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Secondary row: Details & Report
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "View Order Details & Items",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = RiderColors.AccentBlueText,
                        modifier = Modifier.clickable { onViewOrderDetails() }
                    )
                    Text(
                        text = "Need Help?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = RiderColors.TextMuted,
                        modifier = Modifier.clickable { onReportIssue() }
                    )
                }

                Spacer(modifier = Modifier.height(70.dp)) // Space for docked button
            }
        }

        // -------------------------------------------------------------
        // SECTION 3: DOCKED SINGLE PRIMARY ACTION BUTTON (STICKY AT BOTTOM)
        // -------------------------------------------------------------
        Surface(
            color = RiderColors.Surface,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, RiderColors.Border),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when {
                    // STAGE 1A ACTION: ARRIVED AT STORE
                    !isAtStore && isPhase1 -> {
                        Button(
                            onClick = {
                                if (isWithinStoreArrivalRadius) {
                                    onArrivedStore()
                                } else {
                                    showManualArrivalConfirmDialog = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWithinStoreArrivalRadius) RiderColors.Primary else RiderColors.PrimarySoft
                            ),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = if (isWithinStoreArrivalRadius) Color.White else RiderColors.PrimarySoftText,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isWithinStoreArrivalRadius) "ARRIVED AT STORE" else "ARRIVED AT STORE (CONFIRM)",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    letterSpacing = 0.5.sp,
                                    color = if (isWithinStoreArrivalRadius) Color.White else RiderColors.PrimarySoftText
                                )
                            }
                        }
                    }

                    // STAGE 1B ACTION: CONFIRM ORDER PICKUP
                    isAtStore -> {
                        Button(
                            onClick = onConfirmPickup,
                            colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CONFIRM ORDER PICKUP", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White, letterSpacing = 0.5.sp)
                            }
                        }
                    }

                    // STAGE 2A ACTION: ARRIVED AT CUSTOMER
                    isEnRouteCustomer -> {
                        Button(
                            onClick = onArrivedCustomer,
                            colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Warning),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            } else {
                                Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ARRIVED AT CUSTOMER", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.White, letterSpacing = 0.5.sp)
                            }
                        }
                    }

                    // STAGE 2B ACTION: VERIFY PIN & COMPLETE
                    isAtCustomer -> {
                        val isPinValid = session.otpVerified || enteredOtp.trim().length in 4..6
                        val isCodReady = !session.isCod || session.codReconciled

                        Button(
                            onClick = {
                                if (session.otpVerified) {
                                    onCompleteDelivery()
                                } else {
                                    onVerifyOtp()
                                }
                            },
                            enabled = !isLoading && isPinValid && isCodReady,
                            colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp))
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (session.otpVerified) "COMPLETE DELIVERY" else "VERIFY PIN & COMPLETE DELIVERY",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Manual Arrival Confirmation Dialog (for GPS drift / indoor store)
    if (showManualArrivalConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showManualArrivalConfirmDialog = false },
            title = {
                Text("Confirm Store Arrival", fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary, fontSize = 17.sp)
            },
            text = {
                val distText = if (distanceToStoreMeters != null) "GPS estimates you are ~%.0fm from the hub.".format(distanceToStoreMeters) else "GPS is acquiring your precise location."
                Text(
                    text = "$distText\n\nConfirm that you have arrived at the store hub to inspect and pickup the order?",
                    color = RiderColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showManualArrivalConfirmDialog = false
                        onArrivedStore()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Confirm Arrival", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showManualArrivalConfirmDialog = false },
                    border = BorderStroke(1.dp, RiderColors.Border),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel", color = RiderColors.TextSecondary)
                }
            },
            containerColor = RiderColors.Surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
