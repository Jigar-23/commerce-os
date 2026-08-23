package com.commerceos.android.ui.orders

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun CustomerLiveMapTrackingView(
    order: CustomerOrderApiResponse,
    liveTracking: CustomerOrderTrackingDto?,
    modifier: Modifier = Modifier,
    onExpandClick: (() -> Unit)? = null
) {
    val merchantLat = liveTracking?.merchantLat?.takeIf { it != 0.0 } ?: 28.202218
    val merchantLng = liveTracking?.merchantLng?.takeIf { it != 0.0 } ?: 76.615403
    val customerLat = liveTracking?.customerLat?.takeIf { it != 0.0 } ?: (order.deliveryAddress?.latitude ?: 28.1970)
    val customerLng = liveTracking?.customerLng?.takeIf { it != 0.0 } ?: (order.deliveryAddress?.longitude ?: 76.6190)

    val telemetry = liveTracking?.liveRiderTelemetry
    val realRiderLat = telemetry?.latitude
    val realRiderLng = telemetry?.longitude
    val heading = telemetry?.heading
    val isStale = liveTracking?.isStale ?: (telemetry?.isStale ?: false)

    val hasGpsData = realRiderLat != null && realRiderLng != null && realRiderLat != 0.0 && realRiderLng != 0.0

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
            val routePoints = remember(liveTracking?.waypoints) {
                liveTracking?.waypoints?.map { MapRoutePoint(it.lat, it.lng) } ?: emptyList()
            }
            val activeStage = liveTracking?.stage ?: when (order.orderStatus.uppercase()) {
                "DELIVERED" -> "DELIVERED"
                "ARRIVED_CUSTOMER", "HANDOFF_STARTED" -> "AT_DOORSTEP"
                "OUT_FOR_DELIVERY", "EN_ROUTE_CUSTOMER", "REACHING_YOU" -> "OUT_FOR_DELIVERY"
                "PICKED_UP", "ARRIVED_PICKUP", "EN_ROUTE_PICKUP" -> "AT_STORE"
                "SELLER_ACCEPTED" -> "HEADING_TO_STORE"
                else -> "ASSIGNING_PARTNER"
            }

            // Interactive MapLibre Dark Map V2
            ZomatoDarkMapView(
                merchantLat = merchantLat,
                merchantLng = merchantLng,
                customerLat = customerLat,
                customerLng = customerLng,
                riderLat = realRiderLat,
                riderLng = realRiderLng,
                riderHeading = heading,
                waypoints = routePoints,
                stage = activeStage,
                isStale = isStale,
                modifier = Modifier.fillMaxSize()
            )

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
