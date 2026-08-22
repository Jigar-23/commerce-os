package com.commerceos.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.ServerOffer

@Composable
fun RiderOfferCard(
    offer: ServerOffer,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // Top Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NEW DELIVERY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    letterSpacing = 1.sp
                )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "⚡ FIRST-COME, FIRST-SERVED",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dominant Payout Hero
            Text(
                text = "₹${offer.earningsAmount.toInt()}",
                color = Color(0xFF10B981),
                fontSize = 38.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = "${offer.totalDistanceKm} km • ~${offer.estimatedDurationMins} min",
                color = Color(0xFFCBD5E1),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFF1E293B))
            Spacer(modifier = Modifier.height(16.dp))

            // Scannable Route Timeline
            Row(modifier = Modifier.fillMaxWidth()) {
                // Route line indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 4.dp, end = 12.dp)) {
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF38BDF8), CircleShape))
                    Box(modifier = Modifier.width(2.dp).height(44.dp).background(Color(0xFF334155)))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFF59E0B), CircleShape))
                }

                // Locations content
                Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.weight(1f)) {
                    Column {
                        Text("Pickup", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (offer.pickupDistanceKm != null && offer.pickupDistanceKm > 0.0) "${offer.merchantName} (${offer.pickupDistanceKm} km from you)" else "${offer.merchantName} • Pickup distance updating",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column {
                        Text("Drop", color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${offer.customerAddress} (${offer.deliveryDistanceKm} km)",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (offer.isCod) {
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFD97706).copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (offer.codAmount != null) "Cash on Delivery: Collect ₹${offer.codAmount.toInt()} from customer" else "Cash on Delivery (COD)",
                        color = Color(0xFFFBBF24),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { onDecline(offer.offerId) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                ) {
                    Text("Decline", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Button(
                    onClick = { onAccept(offer.offerId) },
                    modifier = Modifier.weight(1.5f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                ) {
                    Text(
                        text = "Accept ₹${offer.earningsAmount.toInt()}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
