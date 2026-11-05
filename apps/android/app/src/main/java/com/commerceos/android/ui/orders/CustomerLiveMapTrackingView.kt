package com.commerceos.android.ui.orders

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CustomerOrderApiResponse
import com.commerceos.android.model.CustomerOrderTrackingDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun CustomerLiveMapTrackingView(
    order: CustomerOrderApiResponse,
    liveTracking: CustomerOrderTrackingDto?,
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onExpandClick: (() -> Unit)? = null,
    onExitFullscreen: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    val merchantLat = liveTracking?.merchantLat?.takeIf { it != 0.0 } ?: 0.0
    val merchantLng = liveTracking?.merchantLng?.takeIf { it != 0.0 } ?: 0.0
    val customerLat = liveTracking?.customerLat?.takeIf { it != 0.0 }
        ?: order.deliveryAddress?.latitude?.takeIf { it != 0.0 }
        ?: 0.0
    val customerLng = liveTracking?.customerLng?.takeIf { it != 0.0 }
        ?: order.deliveryAddress?.longitude?.takeIf { it != 0.0 }
        ?: 0.0

    val telemetry = liveTracking?.liveRiderTelemetry
    val realRiderLat = telemetry?.latitude?.takeIf { it != 0.0 }
    val realRiderLng = telemetry?.longitude?.takeIf { it != 0.0 }
    val heading = telemetry?.heading
    val isStale = liveTracking?.isStale ?: (telemetry?.isStale ?: false)

    val hasLocations = merchantLat != 0.0 && merchantLng != 0.0 && customerLat != 0.0 && customerLng != 0.0
    val hasGpsData = realRiderLat != null && realRiderLng != null

    var dynamicRoadPoints by remember(order.id) { mutableStateOf<List<MapRoutePoint>>(emptyList()) }
    var cachedRiderLat by remember(order.id) { mutableStateOf<Double?>(null) }
    var cachedRiderLng by remember(order.id) { mutableStateOf<Double?>(null) }
    var lastRoutedOriginLat by remember(order.id) { mutableStateOf<Double?>(null) }
    var lastRoutedOriginLng by remember(order.id) { mutableStateOf<Double?>(null) }
    var lastRoutedStage by remember(order.id) { mutableStateOf<String?>(null) }
    val routesProvider = remember { com.commerceos.android.location.GoogleRoutesProvider() }
    var routeUnavailable by remember(order.id) { mutableStateOf(false) }

    if (realRiderLat != null && realRiderLng != null) {
        cachedRiderLat = realRiderLat
        cachedRiderLng = realRiderLng
    }

    val currentStatus = order.effectiveStatus
    val activeStage = liveTracking?.stage ?: when (currentStatus.uppercase()) {
        "CANCELLED" -> "CANCELLED"
        "DELIVERED" -> "DELIVERED"
        "ARRIVED_CUSTOMER", "HANDOFF_STARTED" -> "AT_DOORSTEP"
        "PICKED_UP", "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU" -> "OUT_FOR_DELIVERY"
        "ARRIVED_PICKUP", "EN_ROUTE_PICKUP", "AT_STORE" -> "AT_STORE"
        "SELLER_ACCEPTED", "ACCEPTED", "EN_ROUTE_STORE", "ASSIGNED" -> "HEADING_TO_STORE"
        else -> "ASSIGNING_PARTNER"
    }

    val isHeadingToCustomer = activeStage in listOf(
        "PICKED_UP", "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU",
        "NEARBY", "ARRIVED_CUSTOMER", "AT_DOORSTEP", "HANDOFF_STARTED"
    )
    val isHeadingToStore = activeStage in listOf(
        "HEADING_TO_STORE", "ACCEPTED", "EN_ROUTE_PICKUP", "EN_ROUTE_STORE",
        "ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_MERCHANT", "AT_STORE", "PACKED",
        "SELLER_ACCEPTED", "ASSIGNED", "DISPATCHED", "RIDER_ASSIGNED", "RIDER_ACCEPTED"
    )
    val isOrderPlaced = !isHeadingToCustomer && !isHeadingToStore

    val effectiveRiderLat = if (isOrderPlaced) null else (realRiderLat ?: cachedRiderLat ?: if (isHeadingToCustomer) merchantLat else null)
    val effectiveRiderLng = if (isOrderPlaced) null else (realRiderLng ?: cachedRiderLng ?: if (isHeadingToCustomer) merchantLng else null)

    // Prevent 500ms route flash: check if cached/server waypoints terminate at the store when heading to customer
    val isServerWaypointsStaleForCurrentStage = remember(liveTracking?.waypoints, isHeadingToCustomer, isHeadingToStore, merchantLat, merchantLng, customerLat, customerLng) {
        val serverWaypoints = liveTracking?.waypoints
        if (serverWaypoints.isNullOrEmpty() || serverWaypoints.size < 2) {
            false
        } else {
            val lastPt = serverWaypoints.last()
            if (isHeadingToCustomer && customerLat != 0.0 && merchantLat != 0.0) {
                val distArrM = FloatArray(1)
                val distArrC = FloatArray(1)
                android.location.Location.distanceBetween(lastPt.lat, lastPt.lng, merchantLat, merchantLng, distArrM)
                android.location.Location.distanceBetween(lastPt.lat, lastPt.lng, customerLat, customerLng, distArrC)
                distArrM[0] < 150.0f && distArrC[0] > 200.0f
            } else false
        }
    }

    LaunchedEffect(liveTracking?.waypoints, isServerWaypointsStaleForCurrentStage, merchantLat, merchantLng, customerLat, customerLng, realRiderLat, realRiderLng, activeStage) {
        if (!liveTracking?.waypoints.isNullOrEmpty() && (liveTracking?.waypoints?.size ?: 0) >= 2 && !isServerWaypointsStaleForCurrentStage) {
            dynamicRoadPoints = emptyList()
            return@LaunchedEffect
        }
        val effectiveRiderLat = realRiderLat ?: cachedRiderLat
        val effectiveRiderLng = realRiderLng ?: cachedRiderLng

        // If stage changed, immediately clear dynamicRoadPoints so stale store waypoints never flash into customer delivery!
        if (activeStage != lastRoutedStage) {
            dynamicRoadPoints = emptyList()
            lastRoutedOriginLat = null
            lastRoutedOriginLng = null
            lastRoutedStage = activeStage
        }

        // If order is placed / preparing (no committed rider yet), NO route is fetched or drawn
        if (isOrderPlaced || effectiveRiderLat == null || effectiveRiderLng == null) {
            dynamicRoadPoints = emptyList()
            lastRoutedOriginLat = null
            lastRoutedOriginLng = null
            lastRoutedStage = activeStage
            return@LaunchedEffect
        }

        // Origin & Destination depending on active delivery stage:
        // 1. Rider Assigned / Heading to Store: Rider -> Store Hub
        // 2. Rider Out for Delivery: Rider -> Customer Home
        val (originLat, originLng, destLat, destLng) = when {
            isHeadingToStore -> {
                listOf(effectiveRiderLat, effectiveRiderLng, merchantLat, merchantLng)
            }
            isHeadingToCustomer -> {
                listOf(effectiveRiderLat, effectiveRiderLng, customerLat, customerLng)
            }
            else -> {
                listOf(0.0, 0.0, 0.0, 0.0)
            }
        }

        if (originLat == 0.0 || destLat == 0.0) {
            return@LaunchedEffect
        }

        // Throttle route queries if position displacement is minimal (< 40m)
        val prevLat = lastRoutedOriginLat
        val prevLng = lastRoutedOriginLng
        if (dynamicRoadPoints.size >= 2 && activeStage == lastRoutedStage && prevLat != null && prevLng != null) {
            val distArr = FloatArray(1)
            android.location.Location.distanceBetween(prevLat, prevLng, originLat, originLng, distArr)
            if (distArr[0] < 40.0f) {
                return@LaunchedEffect
            }
        }

        if (originLat != destLat || originLng != destLng) {
            val result = routesProvider.getDrivingRoute(originLat, originLng, destLat, destLng)
            if (result is com.commerceos.android.network.ApiResult.Success && result.data.waypoints.size >= 2) {
                dynamicRoadPoints = result.data.waypoints.map { MapRoutePoint(it.latitude, it.longitude) }
                lastRoutedOriginLat = originLat
                lastRoutedOriginLng = originLng
                lastRoutedStage = activeStage
                routeUnavailable = false
            } else {
                dynamicRoadPoints = emptyList()
                routeUnavailable = true
            }
        }
    }

    val routePoints = remember(liveTracking?.waypoints, isServerWaypointsStaleForCurrentStage, dynamicRoadPoints, merchantLat, merchantLng, customerLat, customerLng, effectiveRiderLat, effectiveRiderLng, isHeadingToStore, isHeadingToCustomer, activeStage, lastRoutedStage) {
        if (!isHeadingToStore && !isHeadingToCustomer) {
            emptyList()
        } else if (effectiveRiderLat == null || effectiveRiderLng == null) {
            emptyList()
        } else if (!liveTracking?.waypoints.isNullOrEmpty() && (liveTracking?.waypoints?.size ?: 0) >= 2 && !isServerWaypointsStaleForCurrentStage) {
            liveTracking!!.waypoints.map { MapRoutePoint(it.lat, it.lng) }
        } else if (dynamicRoadPoints.size >= 2 && lastRoutedStage == activeStage) {
            dynamicRoadPoints
        } else {
            emptyList()
        }
    }

    val traversedPoints = remember(liveTracking?.traversedWaypoints) {
        liveTracking?.traversedWaypoints?.map { MapRoutePoint(it.lat, it.lng) } ?: emptyList()
    }
    val remainingPoints = remember(liveTracking?.remainingWaypoints, isServerWaypointsStaleForCurrentStage, routePoints) {
        if (!liveTracking?.remainingWaypoints.isNullOrEmpty() && !isServerWaypointsStaleForCurrentStage) {
            liveTracking!!.remainingWaypoints.map { MapRoutePoint(it.lat, it.lng) }
        } else {
            routePoints
        }
    }

    // Pulsing beacon for live partner status
    val infiniteTransition = rememberInfiniteTransition(label = "MapBeacon")
    val beaconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeaconAlpha"
    )
    val beaconScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeaconScale"
    )

    if (isFullscreen) {
        Box(modifier = modifier.fillMaxSize().background(Color(0xFFF1F5F9))) {
            if (hasLocations) {
                NativeGoogleOrderTrackingMap(
                    merchantLat = merchantLat,
                    merchantLng = merchantLng,
                    customerLat = customerLat,
                    customerLng = customerLng,
                    riderLat = effectiveRiderLat,
                    riderLng = effectiveRiderLng,
                    riderHeading = heading,
                    speedKmh = telemetry?.speedKmh,
                    waypoints = routePoints,
                    activeStage = activeStage,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFF0E675E),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Live Route Syncing",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Waiting for store dispatch coordinates",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Top Floating Header for Fullscreen (Clean white themed floating controls)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { onExitFullscreen?.invoke() },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Fullscreen",
                            tint = Color(0xFF0E675E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    color = Color.White.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .scale(if (hasGpsData && !isStale) beaconScale else 1.0f)
                                .background(
                                    when {
                                        hasGpsData && !isStale -> Color(0xFF10B981).copy(alpha = beaconAlpha)
                                        hasGpsData && isStale -> Color(0xFFF59E0B)
                                        else -> Color(0xFF0E675E)
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "LIVE MAP TRACKING",
                            color = Color(0xFF0F172A),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFFF1F5F9))
        ) {
            if (hasLocations) {
                NativeGoogleOrderTrackingMap(
                    merchantLat = merchantLat,
                    merchantLng = merchantLng,
                    customerLat = customerLat,
                    customerLng = customerLng,
                    riderLat = effectiveRiderLat,
                    riderLng = effectiveRiderLng,
                    riderHeading = heading,
                    speedKmh = telemetry?.speedKmh,
                    waypoints = routePoints,
                    activeStage = activeStage,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFF0E675E),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Live Route Syncing",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Waiting for store dispatch coordinates",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            // Top Floating Navigation Bar (Back button only - black order ID tile removed as requested)
            if (onBack != null) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.96f),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .padding(14.dp)
                        .size(40.dp)
                        .align(Alignment.TopStart)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0E675E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Bottom Floating Controls (White elevated Live Status Pill + Brand Fullscreen Button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleek Floating Status Pill (White card with emerald beacon and dark slate text)
                Surface(
                    color = Color.White.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .scale(if (hasGpsData && !isStale) beaconScale else 1.0f)
                                .background(
                                    when {
                                        currentStatus.equals("CANCELLED", ignoreCase = true) -> Color(0xFFEF4444)
                                        hasGpsData && !isStale -> Color(0xFF10B981).copy(alpha = beaconAlpha)
                                        hasGpsData && isStale -> Color(0xFFF59E0B)
                                        currentStatus == "SELLER_ACCEPTED" -> Color(0xFF0E675E)
                                        else -> Color(0xFF0E675E)
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                currentStatus.equals("CANCELLED", ignoreCase = true) -> "Order Cancelled"
                                currentStatus == "DELIVERED" -> "Order Delivered"
                                currentStatus == "HANDOFF_STARTED" -> "Partner at your door"
                                currentStatus == "ARRIVED_CUSTOMER" -> "Partner has arrived"
                                routeUnavailable && routePoints.isEmpty() && (isHeadingToStore || isHeadingToCustomer) -> "Route unavailable • Live tracking"
                                currentStatus == "OUT_FOR_DELIVERY" || currentStatus == "EN_ROUTE_CUSTOMER" -> "Partner on the way"
                                currentStatus == "PICKED_UP" -> "Order packed & picked up"
                                currentStatus in listOf("ARRIVED_PICKUP", "EN_ROUTE_PICKUP") -> "Partner picking up"
                                currentStatus == "SELLER_ACCEPTED" -> "Order accepted & packing"
                                hasGpsData -> "Partner is on the way"
                                else -> "Assigning delivery partner..."
                            },
                            color = Color(0xFF0F172A),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Clean White Fullscreen Icon Button (Theme-aligned with brand teal symbol)
                if (onExpandClick != null) {
                    Surface(
                        onClick = onExpandClick,
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.96f),
                        shadowElevation = 4.dp,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = "⛶",
                                color = Color(0xFF0E675E),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
