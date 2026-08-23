package com.commerceos.rider.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.RoutePoint
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.repository.RiderDeliveryRepository
import kotlinx.coroutines.launch

@Composable
fun RiderLiveNavigationView(
    session: ServerDeliverySession,
    repository: RiderDeliveryRepository,
    riderLat: Double?,
    riderLng: Double?,
    riderHeading: Float? = null,
    isStale: Boolean = false,
    onArrivedStore: () -> Unit,
    onConfirmPickup: () -> Unit,
    onArrivedCustomer: () -> Unit,
    onCompleteDelivery: () -> Unit,
    onViewOrderDetails: () -> Unit,
    onCancelDelivery: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isPhase1 = session.state in listOf("ASSIGNED", "ACCEPTED", "EN_ROUTE_PICKUP", "ARRIVED_PICKUP")
    val targetLat = if (isPhase1) session.merchantLat else session.customerLat
    val targetLng = if (isPhase1) session.merchantLng else session.customerLng
    val targetName = if (isPhase1) session.merchantName else session.customerName
    val targetAddress = if (isPhase1) session.merchantAddress else session.customerAddress

    var waypoints by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var routeDistanceKm by remember { mutableStateOf<Double?>(null) }
    var routeDurationMins by remember { mutableStateOf<Int?>(null) }
    var isRouteLoading by remember { mutableStateOf(false) }
    var routeUnavailable by remember { mutableStateOf(false) }

    val hasRiderGps = riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0

    // Fetch authoritative OSRM road route geometry strictly from valid live rider GPS
    LaunchedEffect(riderLat, riderLng, targetLat, targetLng) {
        if (!hasRiderGps) {
            waypoints = emptyList()
            routeDistanceKm = null
            routeDurationMins = null
            routeUnavailable = false
            return@LaunchedEffect
        }

        if (targetLat != null && targetLng != null && targetLat != 0.0 && targetLng != 0.0) {
            isRouteLoading = true
            val res = repository.fetchRoute(riderLat!!, riderLng!!, targetLat, targetLng)
            res.onSuccess { routeResult ->
                waypoints = routeResult.waypoints
                routeDistanceKm = routeResult.distanceKm
                routeDurationMins = routeResult.durationMins
                routeUnavailable = false
            }.onFailure {
                routeUnavailable = true
            }
            isRouteLoading = false
        }
    }

    val displayDistanceKm = routeDistanceKm ?: session.distanceKm
    val displayEtaMins = routeDurationMins ?: session.estimatedTimeMins

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewOrderDetails)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // HUD Header: Phase Status & Authoritative Route ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = if (isPhase1) Color(0xFF0284C7) else Color(0xFFD97706),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = if (isPhase1) "Going to pickup" else "On the way to customer",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = targetName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = targetAddress,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        maxLines = 1
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (displayDistanceKm != null) "${String.format("%.1f", displayDistanceKm)} km" else "-- km",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF10B981)
                    )
                    Text(
                        text = if (displayEtaMins != null) "~$displayEtaMins min ETA" else "ETA updating…",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Embedded Real Geographic Map Navigation Viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(Color(0xFF0B1120), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                ZomatoDarkMapView(
                    merchantLat = session.merchantLat ?: 28.4595,
                    merchantLng = session.merchantLng ?: 77.0266,
                    customerLat = session.customerLat ?: 28.4595,
                    customerLng = session.customerLng ?: 77.0266,
                    riderLat = riderLat,
                    riderLng = riderLng,
                    riderHeading = riderHeading,
                    waypoints = waypoints,
                    isRouteLoading = isRouteLoading,
                    routeUnavailable = routeUnavailable,
                    isStale = isStale,
                    modifier = Modifier.fillMaxSize()
                )

                // Navigation Turn-by-Turn Action
                Button(
                    onClick = {
                        launchExternalMaps(context, targetLat, targetLng, targetName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Navigation", modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Turn-by-turn", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Glove/Sunlight-Friendly Workflow Action
            when (session.state) {
                "ASSIGNED", "ACCEPTED", "EN_ROUTE_PICKUP", "ARRIVED_PICKUP" -> {
                    val storeLat = session.merchantLat
                    val storeLng = session.merchantLng
                    val distanceToStoreMeters: Float? = if (riderLat != null && riderLng != null && riderLat != 0.0 && storeLat != null && storeLng != null && storeLat != 0.0) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(riderLat, riderLng, storeLat, storeLng, results)
                        results[0]
                    } else null

                    val isWithin50m = distanceToStoreMeters != null && distanceToStoreMeters <= 50.0f

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = {
                                if (isWithin50m || distanceToStoreMeters == null) {
                                    onConfirmPickup()
                                } else {
                                    Toast.makeText(context, "You are ${distanceToStoreMeters.toInt()}m away from store. Must be within 50m to fetch order.", Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isWithin50m) Color(0xFF10B981) else Color(0xFF334155),
                                disabledContainerColor = Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Text(
                                text = if (isWithin50m) "ORDER FETCHED ✓" else if (distanceToStoreMeters != null) "ORDER FETCHED (${distanceToStoreMeters.toInt()}m away)" else "ORDER FETCHED",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = if (isWithin50m) Color.Black else Color(0xFF94A3B8),
                                letterSpacing = 0.5.sp
                            )
                        }
                        if (!isWithin50m && distanceToStoreMeters != null) {
                            Text(
                                text = "⚠️ Reach within 50m of store to fetch order (${distanceToStoreMeters.toInt()}m away)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF59E0B),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                "PICKED_UP", "EN_ROUTE_CUSTOMER" -> {
                    Button(
                        onClick = onArrivedCustomer,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Text("ARRIVED AT CUSTOMER", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 0.5.sp)
                    }
                }
                "ARRIVED_CUSTOMER", "HANDOFF_STARTED" -> {
                    Button(
                        onClick = onCompleteDelivery,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Text("COMPLETE HANDOFF", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Black, letterSpacing = 0.5.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewOrderDetails,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Order Details", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onCancelDelivery,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(42.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("Cancel Order", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                }
            }
        }
    }
}

private fun launchExternalMaps(context: Context, lat: Double?, lng: Double?, label: String) {
    if (lat == null || lng == null || lat == 0.0 || lng == 0.0) {
        Toast.makeText(context, "Location coordinates unavailable for this destination", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val uri = Uri.parse("google.navigation:q=$lat,$lng&mode=d")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallbackUri = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
            context.startActivity(Intent(Intent.ACTION_VIEW, fallbackUri))
        } catch (ex: Exception) {
            Toast.makeText(context, "No navigation app found on device", Toast.LENGTH_SHORT).show()
        }
    }
}
