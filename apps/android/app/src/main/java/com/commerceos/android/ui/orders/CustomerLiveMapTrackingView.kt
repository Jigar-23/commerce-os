package com.commerceos.android.ui.orders

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    onExpandClick: (() -> Unit)? = null
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

    val activeStage = liveTracking?.stage ?: when (order.orderStatus.uppercase()) {
        "DELIVERED" -> "DELIVERED"
        "ARRIVED_CUSTOMER", "HANDOFF_STARTED" -> "AT_DOORSTEP"
        "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU" -> "OUT_FOR_DELIVERY"
        "PICKED_UP", "ARRIVED_PICKUP", "EN_ROUTE_PICKUP" -> "AT_STORE"
        "SELLER_ACCEPTED" -> "HEADING_TO_STORE"
        else -> "ASSIGNING_PARTNER"
    }

    LaunchedEffect(liveTracking?.waypoints, merchantLat, merchantLng, customerLat, customerLng, realRiderLat, realRiderLng, activeStage) {
        if (!liveTracking?.waypoints.isNullOrEmpty() && (liveTracking?.waypoints?.size ?: 0) >= 2) {
            dynamicRoadPoints = emptyList()
            return@LaunchedEffect
        }
        val isPhase1 = activeStage in listOf("HEADING_TO_STORE", "ASSIGNING_PARTNER", "AT_STORE")
        val originLat = if (isPhase1) (realRiderLat ?: (merchantLat - 0.008)) else merchantLat
        val originLng = if (isPhase1) (realRiderLng ?: (merchantLng - 0.006)) else merchantLng
        val destLat = if (isPhase1) merchantLat else customerLat
        val destLng = if (isPhase1) merchantLng else customerLng

        if (originLat != destLat || originLng != destLng) {
            withContext(Dispatchers.IO) {
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
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    val routePoints = remember(liveTracking?.waypoints, dynamicRoadPoints, merchantLat, merchantLng, customerLat, customerLng) {
        if (!liveTracking?.waypoints.isNullOrEmpty() && (liveTracking?.waypoints?.size ?: 0) >= 2) {
            liveTracking!!.waypoints.map { MapRoutePoint(it.lat, it.lng) }
        } else if (dynamicRoadPoints.isNotEmpty()) {
            dynamicRoadPoints
        } else if (hasLocations) {
            listOf(MapRoutePoint(merchantLat, merchantLng), MapRoutePoint(customerLat, customerLng))
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

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1120)),
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier.fillMaxWidth().height(290.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasLocations) {
                // Interactive MapLibre Dark Map V2
                ZomatoDarkMapView(
                    merchantLat = merchantLat,
                    merchantLng = merchantLng,
                    customerLat = customerLat,
                    customerLng = customerLng,
                    riderLat = realRiderLat,
                    riderLng = realRiderLng,
                    riderHeading = heading,
                    speedKmh = telemetry?.speedKmh,
                    routeProgressPct = liveTracking?.routeProgressPct ?: telemetry?.routeProgressPct,
                    snappedSegmentIndex = liveTracking?.snappedSegmentIndex,
                    waypoints = routePoints,
                    traversedWaypoints = traversedPoints,
                    remainingWaypoints = remainingPoints,
                    stage = activeStage,
                    isStale = isStale,
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

            // Top Floating Live Status Glass Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .background(Color(0xFF0F172A).copy(alpha = 0.94f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
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
                            order.orderStatus == "EN_ROUTE_CUSTOMER" -> "Partner on the way"
                            order.orderStatus == "PICKED_UP" -> "Order packed & picked up"
                            order.orderStatus in listOf("ARRIVED_PICKUP", "EN_ROUTE_PICKUP") -> "Partner picking up"
                            order.orderStatus == "SELLER_ACCEPTED" -> "Order accepted & packing"
                            hasGpsData -> "Partner is on the way"
                            else -> "Assigning delivery partner..."
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (onExpandClick != null) {
                    Surface(
                        onClick = onExpandClick,
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        Text(
                            text = "⛶ Fullscreen",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
