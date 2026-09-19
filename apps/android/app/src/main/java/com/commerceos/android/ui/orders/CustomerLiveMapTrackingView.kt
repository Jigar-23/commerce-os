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
    val merchantLat = liveTracking?.merchantLat?.takeIf { it != 0.0 } ?: 28.202224
    val merchantLng = liveTracking?.merchantLng?.takeIf { it != 0.0 } ?: 76.615418
    val customerLat = liveTracking?.customerLat?.takeIf { it != 0.0 }
        ?: order.deliveryAddress?.latitude?.takeIf { it != 0.0 }
        ?: 28.202224
    val customerLng = liveTracking?.customerLng?.takeIf { it != 0.0 }
        ?: order.deliveryAddress?.longitude?.takeIf { it != 0.0 }
        ?: 76.615418

    val telemetry = liveTracking?.liveRiderTelemetry
    val realRiderLat = telemetry?.latitude?.takeIf { it != 0.0 }
    val realRiderLng = telemetry?.longitude?.takeIf { it != 0.0 }
    val heading = telemetry?.heading
    val isStale = liveTracking?.isStale ?: (telemetry?.isStale ?: false)

    val hasLocations = merchantLat != 0.0 && merchantLng != 0.0 && customerLat != 0.0 && customerLng != 0.0
    val hasGpsData = realRiderLat != null && realRiderLng != null

    var dynamicRoadPoints by remember { mutableStateOf<List<MapRoutePoint>>(emptyList()) }
    var cachedRiderLat by remember { mutableStateOf<Double?>(null) }
    var cachedRiderLng by remember { mutableStateOf<Double?>(null) }
    var lastRoutedOriginLat by remember { mutableStateOf<Double?>(null) }
    var lastRoutedOriginLng by remember { mutableStateOf<Double?>(null) }
    var lastRoutedStage by remember { mutableStateOf<String?>(null) }

    if (realRiderLat != null && realRiderLng != null) {
        cachedRiderLat = realRiderLat
        cachedRiderLng = realRiderLng
    }

    val activeStage = liveTracking?.stage ?: when (order.orderStatus.uppercase()) {
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

    val effectiveRiderLat = realRiderLat ?: cachedRiderLat ?: if (isHeadingToCustomer) merchantLat else null
    val effectiveRiderLng = realRiderLng ?: cachedRiderLng ?: if (isHeadingToCustomer) merchantLng else null

    LaunchedEffect(liveTracking?.waypoints, merchantLat, merchantLng, customerLat, customerLng, realRiderLat, realRiderLng, activeStage) {
        if (!liveTracking?.waypoints.isNullOrEmpty() && (liveTracking?.waypoints?.size ?: 0) >= 2) {
            dynamicRoadPoints = emptyList()
            return@LaunchedEffect
        }
        val effectiveRiderLat = realRiderLat ?: cachedRiderLat
        val effectiveRiderLng = realRiderLng ?: cachedRiderLng

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
            withContext(Dispatchers.IO) {
                var routeFetched = false
                // 1. Authoritative Backend Routing (Host Google Directions + OSRM)
                try {
                    val backendBase = com.commerceos.android.network.NetworkClient.baseUrl.trimEnd('/')
                    val backendUrl = "$backendBase/api/v1/delivery/route?originLat=$originLat&originLng=$originLng&destLat=$destLat&destLng=$destLng"
                    val url = URL(backendUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 3500
                        readTimeout = 3500
                        setRequestProperty("User-Agent", "CommerceOS-Customer/2.0")
                    }
                    if (conn.responseCode == 200) {
                        val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(jsonStr)
                        if (json.optBoolean("ok", false) && json.has("waypoints")) {
                            val arr = json.getJSONArray("waypoints")
                            val pts = mutableListOf<MapRoutePoint>()
                            for (i in 0 until arr.length()) {
                                val pt = arr.getJSONObject(i)
                                pts.add(MapRoutePoint(lat = pt.getDouble("lat"), lng = pt.getDouble("lng")))
                            }
                            if (pts.size >= 2) {
                                withContext(Dispatchers.Main) {
                                    dynamicRoadPoints = pts
                                    lastRoutedOriginLat = originLat
                                    lastRoutedOriginLng = originLng
                                    lastRoutedStage = activeStage
                                }
                                routeFetched = true
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (!routeFetched) {
                    // 2. Direct OSRM Fallback
                    try {
                        val url = URL("https://router.project-osrm.org/route/v1/driving/$originLng,$originLat;$destLng,$destLat?overview=full&geometries=geojson")
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 4000
                            readTimeout = 4000
                            setRequestProperty("User-Agent", "CommerceOS-Customer/2.0")
                        }
                        if (conn.responseCode == 200) {
                            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(jsonStr)
                            val routes = json.getJSONArray("routes")
                            if (routes.length() > 0) {
                                val geom = routes.getJSONObject(0).getJSONObject("geometry")
                                val coords = geom.getJSONArray("coordinates")
                                val pts = mutableListOf<MapRoutePoint>()
                                for (i in 0 until coords.length()) {
                                    val c = coords.getJSONArray(i)
                                    pts.add(MapRoutePoint(lat = c.getDouble(1), lng = c.getDouble(0)))
                                }
                                if (pts.size >= 2) {
                                    withContext(Dispatchers.Main) {
                                        dynamicRoadPoints = pts
                                        lastRoutedOriginLat = originLat
                                        lastRoutedOriginLng = originLng
                                        lastRoutedStage = activeStage
                                    }
                                    routeFetched = true
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 3. Unbreakable geometric fallback corridor
                if (!routeFetched && dynamicRoadPoints.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        dynamicRoadPoints = listOf(
                            MapRoutePoint(originLat, originLng),
                            MapRoutePoint(destLat, destLng)
                        )
                        lastRoutedOriginLat = originLat
                        lastRoutedOriginLng = originLng
                        lastRoutedStage = activeStage
                    }
                }
            }
        }
    }

    val routePoints = remember(liveTracking?.waypoints, dynamicRoadPoints, merchantLat, merchantLng, customerLat, customerLng, effectiveRiderLat, effectiveRiderLng, isHeadingToStore, isHeadingToCustomer) {
        if (!isHeadingToStore && !isHeadingToCustomer) {
            emptyList()
        } else if (effectiveRiderLat == null || effectiveRiderLng == null) {
            emptyList()
        } else if (!liveTracking?.waypoints.isNullOrEmpty() && (liveTracking?.waypoints?.size ?: 0) >= 2) {
            liveTracking!!.waypoints.map { MapRoutePoint(it.lat, it.lng) }
        } else if (dynamicRoadPoints.size >= 2) {
            dynamicRoadPoints
        } else if (isHeadingToStore) {
            listOf(MapRoutePoint(effectiveRiderLat, effectiveRiderLng), MapRoutePoint(merchantLat, merchantLng))
        } else if (isHeadingToCustomer) {
            listOf(MapRoutePoint(effectiveRiderLat, effectiveRiderLng), MapRoutePoint(customerLat, customerLng))
        } else {
            emptyList()
        }
    }

    val traversedPoints = remember(liveTracking?.traversedWaypoints) {
        liveTracking?.traversedWaypoints?.map { MapRoutePoint(it.lat, it.lng) } ?: emptyList()
    }
    val remainingPoints = remember(liveTracking?.remainingWaypoints, routePoints) {
        if (!liveTracking?.remainingWaypoints.isNullOrEmpty()) {
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
        Box(modifier = modifier.fillMaxSize().background(Color(0xFF080C16))) {
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
                    modifier = Modifier.fillMaxSize().background(Color(0xFF080C16)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Live Route Syncing",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Waiting for dark store dispatch coordinates",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Top Floating Header for Fullscreen
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { onExitFullscreen?.invoke() },
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
                                .scale(if (hasGpsData && !isStale) beaconScale else 1.0f)
                                .background(
                                    when {
                                        hasGpsData && !isStale -> Color(0xFF10B981).copy(alpha = beaconAlpha)
                                        hasGpsData && isStale -> Color(0xFFF59E0B)
                                        else -> Color(0xFF38BDF8)
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ORDER #${order.id} • FULLSCREEN MAP",
                            color = Color.White,
                            fontSize = 11.sp,
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
                .background(Color(0xFF0B1120))
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
                    modifier = Modifier.fillMaxSize().background(Color(0xFF0B1120)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Live Route Syncing",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Waiting for dark store dispatch coordinates",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // Top Floating Navigation Bar (Back button + Order ID chip)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .background(Color(0xFF0F172A).copy(alpha = 0.88f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(40.dp))
                }

                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.88f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Text(
                        text = "ORDER #${order.id}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            // Bottom Floating Controls (Live Status Text + Pulsing Beacon on Left, ⛶ Fullscreen Icon Button on Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sleek Floating Status Pill (NOT bulky white card!)
                Surface(
                    color = Color(0xFF0F172A).copy(alpha = 0.90f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(0xFF1E293B).copy(alpha = 0.8f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .scale(if (hasGpsData && !isStale) beaconScale else 1.0f)
                                .background(
                                    when {
                                        hasGpsData && !isStale -> Color(0xFF10B981).copy(alpha = beaconAlpha)
                                        hasGpsData && isStale -> Color(0xFFF59E0B)
                                        order.orderStatus == "SELLER_ACCEPTED" -> Color(0xFF38BDF8)
                                        else -> Color(0xFF38BDF8)
                                    },
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                order.orderStatus == "DELIVERED" -> "Order Delivered"
                                order.orderStatus == "HANDOFF_STARTED" -> "Partner at your door"
                                order.orderStatus == "ARRIVED_CUSTOMER" -> "Partner has arrived"
                                order.orderStatus == "OUT_FOR_DELIVERY" || order.orderStatus == "EN_ROUTE_CUSTOMER" -> "Partner on the way"
                                order.orderStatus == "PICKED_UP" -> "Order packed & picked up"
                                order.orderStatus in listOf("ARRIVED_PICKUP", "EN_ROUTE_PICKUP") -> "Partner picking up"
                                order.orderStatus == "SELLER_ACCEPTED" -> "Order accepted & packing"
                                hasGpsData -> "Partner is on the way"
                                else -> "Assigning delivery partner..."
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                // Square bracket symbol/icon button (NO text saying "Fullscreen"!)
                if (onExpandClick != null) {
                    IconButton(
                        onClick = onExpandClick,
                        modifier = Modifier
                            .background(Color(0xFF0F172A).copy(alpha = 0.90f), CircleShape)
                            .size(38.dp)
                    ) {
                        Text(
                            text = "⛶",
                            color = Color(0xFF38BDF8),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
