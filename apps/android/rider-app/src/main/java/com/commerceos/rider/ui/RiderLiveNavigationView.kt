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
import androidx.compose.ui.draw.clip
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
    onCancelDelivery: () -> Unit = {},
    enteredOtp: String = "",
    onVerifyOtp: () -> Unit = {}
) {
    val context = LocalContext.current

    val merchantLat = session.merchantLat?.takeIf { it != 0.0 } ?: 28.202224
    val merchantLng = session.merchantLng?.takeIf { it != 0.0 } ?: 76.615418
    val customerLat = session.customerLat?.takeIf { it != 0.0 } ?: 28.202224
    val customerLng = session.customerLng?.takeIf { it != 0.0 } ?: 76.615418

    val isPhase1 = session.state in listOf("ASSIGNED", "ACCEPTED", "EN_ROUTE_PICKUP", "OUT_FOR_PICKUP", "EN_ROUTE_STORE", "ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_AT_STORE", "ARRIVED_MERCHANT")
    val targetLat = if (isPhase1) merchantLat else customerLat
    val targetLng = if (isPhase1) merchantLng else customerLng
    val targetName = if (isPhase1) session.merchantName.ifBlank { "Dark Store Hub" } else session.customerName.ifBlank { "Customer" }
    val targetAddress = if (isPhase1) session.merchantAddress.ifBlank { "Rewari Store Hub" } else session.customerAddress.ifBlank { "Delivery Address" }

    val hasRiderGps = riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0

    var waypoints by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }
    var routeDistanceKm by remember { mutableStateOf<Double?>(null) }
    var routeDurationMins by remember { mutableStateOf<Int?>(null) }
    var isRouteLoading by remember { mutableStateOf(false) }
    var routeUnavailable by remember { mutableStateOf(false) }

    // Fetch authoritative OSRM road route geometry for Phase 1 (Rider -> Store) or Phase 2 (Store/Rider -> Customer)
    LaunchedEffect(riderLat, riderLng, targetLat, targetLng, isPhase1) {
        val originLat = if (hasRiderGps) riderLat!! else (if (isPhase1) (merchantLat - 0.008) else merchantLat)
        val originLng = if (hasRiderGps) riderLng!! else (if (isPhase1) (merchantLng - 0.006) else merchantLng)

        isRouteLoading = true
        val res = repository.fetchRoute(originLat, originLng, targetLat, targetLng)
        res.onSuccess { routeResult ->
            if (routeResult.waypoints.size >= 2) {
                waypoints = routeResult.waypoints
            }
            routeDistanceKm = routeResult.distanceKm
            routeDurationMins = routeResult.durationMins
            routeUnavailable = false
        }.onFailure {
            routeUnavailable = false
        }
        isRouteLoading = false
    }

    val displayDistanceKm = routeDistanceKm ?: session.distanceKm ?: 0.0
    val displayEtaMins = routeDurationMins ?: session.estimatedTimeMins ?: 0

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
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = targetName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
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
                        text = "$displayEtaMins MIN",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isPhase1) Color(0xFF38BDF8) else Color(0xFFF59E0B)
                    )
                    Text(
                        text = "%.1f km".format(displayDistanceKm),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Map Viewport (Hero: 260dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                ZomatoDarkMapView(
                    merchantLat = merchantLat,
                    merchantLng = merchantLng,
                    customerLat = customerLat,
                    customerLng = customerLng,
                    riderLat = riderLat,
                    riderLng = riderLng,
                    riderHeading = riderHeading,
                    waypoints = waypoints,
                    isRouteLoading = isRouteLoading,
                    routeUnavailable = routeUnavailable,
                    isStale = isStale,
                    modifier = Modifier.fillMaxSize()
                )

                if (isRouteLoading) {
                    Surface(
                        color = Color(0xFF0F172A).copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Updating Route", fontSize = 10.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Dynamic Contextual State Action Button
            when (session.state) {
                "ASSIGNED", "ACCEPTED", "EN_ROUTE_PICKUP", "OUT_FOR_PICKUP", "EN_ROUTE_STORE" -> {
                    Button(
                        onClick = onConfirmPickup,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ARRIVED AT STORE", fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 0.5.sp)
                    }
                }
                "ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_AT_STORE", "ARRIVED_MERCHANT" -> {
                    Button(
                        onClick = onConfirmPickup,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        Text("CONFIRM ORDER PICKUP", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Black, letterSpacing = 0.5.sp)
                    }
                }
                "PICKED_UP", "EN_ROUTE_CUSTOMER", "OUT_FOR_DELIVERY" -> {
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
                    if (session.otpVerified) {
                        Button(
                            onClick = onCompleteDelivery,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Text("COMPLETE HANDOFF", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Black, letterSpacing = 0.5.sp)
                        }
                    } else {
                        Button(
                            onClick = onVerifyOtp,
                            enabled = enteredOtp.trim().length in 4..6 && (!session.isCod || session.codReconciled),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Text("VERIFY PIN & COMPLETE", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Color.Black, letterSpacing = 0.5.sp)
                        }
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
