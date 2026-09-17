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
            offer.pickupDistanceKm?.takeIf { it > 0.0 } ?: 0.8
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
            offer.deliveryDistanceKm.takeIf { it > 0.0 } ?: 1.5
        }
    }

    val totalTripKm = Math.round((pickupDistKm + dropDistKm) * 10.0) / 10.0
    val totalTripMins = maxOf(6, Math.round(totalTripKm * 3.5 + 4).toInt())

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        border = BorderStroke(1.dp, Color(0xFF2B2F3B)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Scrollable Details Section (Safe on all device screens)
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
                        text = "Delivery Request",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    if (offer.orderId.isNotBlank()) {
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF334155))
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
                                    text = "ORDER #${offer.orderId}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Copy",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }
                }

                // Distinct Side-by-Side Comparison: Rider Payout vs Customer Bill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Rider Earnings Card (What rider pockets)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("💵", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("YOUR EARNING", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF10B981), letterSpacing = 0.5.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₹${offer.earningsAmount.toInt()}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981)
                            )
                            Text(
                                text = "Direct to wallet",
                                fontSize = 11.sp,
                                color = Color(0xFF6EE7B7),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // 2. Customer Order Bill Card (Value of cart package)
                    val cartVal = offer.orderTotal ?: offer.codAmount ?: 0.0
                    val totalItemCount = offer.items.sumOf { it.quantity }.takeIf { it > 0 } ?: 1
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF22252E),
                        border = BorderStroke(1.dp, Color(0xFF2F3340)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🛍️", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ORDER BILL", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF9CA3AF), letterSpacing = 0.5.sp)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (cartVal > 0.0) "₹${if (cartVal % 1.0 == 0.0) cartVal.toInt() else "%.2f".format(cartVal)}" else "₹0",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "$totalItemCount item(s) • ${if (offer.isCod) "Collect COD" else "Paid Online"}",
                                fontSize = 11.sp,
                                color = if (offer.isCod) Color(0xFFFBBF24) else Color(0xFF9CA3AF),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Trip Distance and Duration Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1C1F28),
                    border = BorderStroke(1.dp, Color(0xFF2B2F3B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estimated Trip Distance",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                        Text(
                            text = "${String.format(java.util.Locale.US, "%.1f", totalTripKm)} km • ~$totalTripMins min",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF262933))

                // Clean Route Timeline
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp, end = 12.dp)) {
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), CircleShape))
                        Box(modifier = Modifier.width(2.dp).height(44.dp).background(Color(0xFF333846)))
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFFF59E0B), CircleShape))
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                        Column {
                            Text("PICKUP HUB", color = Color(0xFF9CA3AF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${offer.merchantName} (${String.format(java.util.Locale.US, "%.1f", pickupDistKm)} km away)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Column {
                            Text("CUSTOMER DROP", color = Color(0xFF9CA3AF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${offer.customerAddress} (${String.format(java.util.Locale.US, "%.1f", dropDistKm)} km)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Itemized Package Preview
                if (offer.items.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF22252E),
                        border = BorderStroke(1.dp, Color(0xFF2B2F3B)),
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
                                    color = Color(0xFF9CA3AF),
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Ready for Pickup",
                                    fontSize = 11.sp,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            offer.items.forEach { item ->
                                Text(
                                    text = "• ${item.name} × ${item.quantity}",
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // COD Notice
                val effectiveCod = (if (offer.codAmount != null && offer.codAmount > 0.0) offer.codAmount else offer.orderTotal) ?: 0.0
                if (offer.isCod && effectiveCod > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💵 Cash on Delivery: Collect ₹${effectiveCod.toInt()} from customer",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // PINNED BOTTOM ACTION BAR (Guaranteed Full Width & Prominent on All Screen Sizes)
            Surface(
                color = Color(0xFF1C1F28),
                border = BorderStroke(1.dp, Color(0xFF262933)),
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
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = "ACCEPT DELIVERY • ₹${offer.earningsAmount.toInt()}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
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
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
