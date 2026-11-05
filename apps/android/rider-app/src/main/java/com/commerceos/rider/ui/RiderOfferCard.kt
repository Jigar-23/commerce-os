package com.commerceos.rider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.commerceos.rider.model.ServerOffer
import com.commerceos.rider.theme.RiderColors

@Composable
fun RiderOfferCard(
    offer: ServerOffer,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    riderLat: Double? = null,
    riderLng: Double? = null,
    modifier: Modifier = Modifier
) {
    // Precise GPS Road Distance Calculations
    val pickupDistKm: Double = remember(riderLat, riderLng, offer.merchantLat, offer.merchantLng) {
        if (riderLat != null && riderLng != null && riderLat != 0.0 && riderLng != 0.0 && offer.merchantLat != 0.0 && offer.merchantLng != 0.0) {
            val distResults = FloatArray(1)
            android.location.Location.distanceBetween(riderLat, riderLng, offer.merchantLat, offer.merchantLng, distResults)
            val roadMeters = distResults[0] * 1.25f
            val km = (roadMeters / 1000.0)
            (Math.round(km * 10.0) / 10.0).coerceAtLeast(0.3)
        } else {
            offer.pickupDistanceKm?.takeIf { it > 0.0 } ?: 0.0
        }
    }

    val dropDistKm: Double = remember(offer.merchantLat, offer.merchantLng, offer.customerLat, offer.customerLng) {
        if (offer.merchantLat != 0.0 && offer.merchantLng != 0.0 && offer.customerLat != 0.0 && offer.customerLng != 0.0) {
            val distResults = FloatArray(1)
            android.location.Location.distanceBetween(offer.merchantLat, offer.merchantLng, offer.customerLat, offer.customerLng, distResults)
            val roadMeters = distResults[0] * 1.3f
            val km = (roadMeters / 1000.0)
            (Math.round(km * 10.0) / 10.0).coerceAtLeast(0.5)
        } else {
            offer.deliveryDistanceKm.takeIf { it > 0.0 } ?: 0.0
        }
    }

    val hasPickupLeg = pickupDistKm > 0.0
    val hasDropLeg = dropDistKm > 0.0
    val totalTripKm = if (offer.totalDistanceKm > 0.0) offer.totalDistanceKm else Math.round((pickupDistKm + dropDistKm) * 10.0) / 10.0
    val totalTripMins = if (offer.estimatedDurationMins > 0) offer.estimatedDurationMins else if (totalTripKm > 0.0) maxOf(6, Math.round(totalTripKm * 3.5 + 4).toInt()) else 0
    val isApproximateRoute = !hasPickupLeg || !hasDropLeg || offer.totalDistanceKm <= 0.0

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, RiderColors.Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Scrollable Details Section
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val context = LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Delivery Offer",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = RiderColors.TextPrimary
                    )
                    if (offer.orderId.isNotBlank()) {
                        val shortId = if (offer.orderId.length > 14) offer.orderId.take(12) + "…" else offer.orderId
                        Surface(
                            color = RiderColors.SurfaceSubtle,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, RiderColors.Border)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Order ID", offer.orderId))
                                        Toast.makeText(context, "Copied Order ID: #${offer.orderId}", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "ORDER #$shortId",
                                    color = RiderColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Copy",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.Primary
                                )
                            }
                        }
                    }
                }

                // Customer Order Bill Card
                val cartVal = offer.orderTotal ?: offer.codAmount ?: 0.0
                val totalItemCount = if (offer.isAccepted && offer.items.isNotEmpty()) {
                    offer.items.sumOf { it.quantity }
                } else {
                    offer.packageCount
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RiderColors.SurfaceSubtle,
                    border = BorderStroke(1.dp, RiderColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ORDER VALUE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.TextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$totalItemCount item(s) • Collect COD",
                                fontSize = 12.sp,
                                color = RiderColors.WarningText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = if (cartVal > 0.0) {
                                "₹${if (cartVal % 1.0 == 0.0) cartVal.toInt() else "%.2f".format(cartVal)}"
                            } else {
                                "Amount pending"
                            },
                            fontSize = if (cartVal <= 0.0) 18.sp else 28.sp,
                            fontWeight = FontWeight.Black,
                            color = RiderColors.TextPrimary
                        )
                    }
                }

                // Trip Distance and Duration Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RiderColors.Surface,
                    border = BorderStroke(1.dp, RiderColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isApproximateRoute) "Approximate Route" else "Estimated Trip Route",
                            fontSize = 12.sp,
                            color = RiderColors.TextMuted
                        )
                        Text(
                            text = if (totalTripKm > 0.0 && totalTripMins > 0) {
                                "${if (isApproximateRoute) "~" else ""}${String.format(java.util.Locale.US, "%.1f", totalTripKm)} km • ~$totalTripMins min"
                            } else if (totalTripKm > 0.0) {
                                "~${String.format(java.util.Locale.US, "%.1f", totalTripKm)} km"
                            } else {
                                "Route pending dispatch"
                            },
                            color = RiderColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                // Clean Route Timeline
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp, end = 12.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(RiderColors.AccentBlue, CircleShape))
                        Box(modifier = Modifier.width(2.dp).height(44.dp).background(RiderColors.Border))
                        Box(modifier = Modifier.size(10.dp).background(RiderColors.Primary, CircleShape))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        Column {
                            Text("PICKUP HUB", color = RiderColors.AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (hasPickupLeg && pickupDistKm > 0.0) {
                                    "${offer.merchantName} (${String.format(java.util.Locale.US, "%.1f", pickupDistKm)} km away)"
                                } else {
                                    offer.merchantName
                                },
                                color = RiderColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column {
                            Text("CUSTOMER DROP", color = RiderColors.Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (hasDropLeg && dropDistKm > 0.0) {
                                    "${offer.customerAddress} (${String.format(java.util.Locale.US, "%.1f", dropDistKm)} km)"
                                } else {
                                    offer.customerAddress
                                },
                                color = RiderColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Itemized Package Preview: reveal item names only after acceptance for privacy
                if (offer.isAccepted && offer.items.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RiderColors.SurfaceSubtle,
                        border = BorderStroke(1.dp, RiderColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PACKAGE CONTENTS (${offer.items.sumOf { it.quantity }})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.TextMuted,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Ready for Pickup",
                                    fontSize = 11.sp,
                                    color = RiderColors.Primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            offer.items.forEach { item ->
                                Text(
                                    text = "• ${item.name} × ${item.quantity}",
                                    fontSize = 13.sp,
                                    color = RiderColors.TextPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else if (!offer.isAccepted) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RiderColors.SurfaceSubtle,
                        border = BorderStroke(1.dp, RiderColors.Border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "PACKAGE CONTENTS ($totalItemCount)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.TextMuted,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Details sealed until acceptance",
                                fontSize = 11.sp,
                                color = RiderColors.TextSecondary,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }

                // COD Notice
                val effectiveCod = (if (offer.codAmount != null && offer.codAmount > 0.0) offer.codAmount else offer.orderTotal) ?: 0.0
                if (offer.isCod) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = RiderColors.WarningSoft,
                        border = BorderStroke(1.dp, RiderColors.Warning.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (effectiveCod > 0.0) {
                                "💵 Cash on Delivery: Collect ₹${effectiveCod.toInt()} from customer"
                            } else {
                                "💵 Cash on Delivery: Collect order total from customer"
                            },
                            color = RiderColors.WarningText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // PINNED BOTTOM ACTION BAR
            Surface(
                color = RiderColors.Surface,
                border = BorderStroke(1.dp, RiderColors.Border),
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = { onAccept(offer.offerId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary)
                    ) {
                        Text(
                            text = "ACCEPT DELIVERY",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    TextButton(
                        onClick = { onDecline(offer.offerId) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                    ) {
                        Text(
                            text = "Decline Offer",
                            color = RiderColors.TextMuted,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
