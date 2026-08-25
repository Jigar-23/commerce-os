package com.commerceos.rider.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.model.ServerOffer

@Composable
fun ActiveDeliveryDetailDialog(
    session: ServerDeliverySession,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val earningsText = session.payoutFormatted ?: "Earnings calculating…"

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ORDER #${session.orderId.takeLast(8)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "ACTIVE DELIVERY • ${session.state}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = earningsText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Locations Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📍 STORE PICKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        Text(
                            text = "${session.merchantName}\n${session.merchantAddress}",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )

                        HorizontalDivider(color = Color(0xFF334155))

                        Text("🏠 CUSTOMER DROP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        Text(
                            text = "${session.customerName} (${session.maskedCustomerPhone})\n${session.customerAddress}",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )

                        HorizontalDivider(color = Color(0xFF334155))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Authoritative Road Distance:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = if (session.distanceKm != null) "${String.format("%.1f", session.distanceKm)} km" else "Updating distance…",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                // COD Card
                if (session.isCod) {
                    Surface(
                        color = if (session.codReconciled) Color(0xFF065F46) else Color(0xFF78350F),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (session.codReconciled) "✓ Cash Collected: ₹${(session.codCollectedAmount ?: 0.0).toInt()}" else "💵 Cash on Delivery: Collect ₹${(session.codAmount ?: 0.0).toInt()} from customer",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val isPickupPhase = session.state in listOf("ASSIGNED", "ACCEPTED", "EN_ROUTE_PICKUP", "ARRIVED_PICKUP")
                            val targetLat = if (isPickupPhase) session.merchantLat else session.customerLat
                            val targetLng = if (isPickupPhase) session.merchantLng else session.customerLng
                            val targetName = if (isPickupPhase) session.merchantName else session.customerName
                            if (targetLat != null && targetLng != null && targetLat != 0.0 && targetLng != 0.0) {
                                launchExternalMaps(context, targetLat, targetLng, targetName)
                            } else {
                                Toast.makeText(context, "Location coordinates unavailable", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                        modifier = Modifier.weight(1.5f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Open in Maps", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun RiderCancelDeliveryDialog(
    deliveryId: String,
    onConfirmCancel: (reason: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val reasons = listOf(
        "CUSTOMER_UNREACHABLE" to "📞 Customer unreachable / not answering",
        "CUSTOMER_REFUSED" to "🚫 Customer refused delivery",
        "STORE_CLOSED_STOCK_ISSUE" to "🏪 Store closed / item not available",
        "VEHICLE_BREAKDOWN" to "🛵 Vehicle breakdown / flat tyre",
        "RIDER_EMERGENCY" to "⚠️ Personal emergency / unable to deliver",
        "WRONG_ADDRESS" to "📍 Wrong customer address"
    )

    var selectedReason by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(reasons[0].first) }
    var additionalNote by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var isSubmitting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cancel Delivery",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFEF4444)
                    )
                    Surface(
                        color = Color(0xFFEF4444).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Rider Cancellation",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }

                Text(
                    text = "Please select a verified reason for cancelling this active delivery job:",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                HorizontalDivider(color = Color(0xFF1E293B))

                reasons.forEach { (key, label) ->
                    val isSelected = selectedReason == key
                    Surface(
                        color = if (isSelected) Color(0xFFEF4444).copy(alpha = 0.15f) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFEF4444)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReason = key }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedReason = key },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFEF4444))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = additionalNote,
                    onValueChange = { additionalNote = it },
                    placeholder = { Text("Optional note (e.g. called customer twice)", fontSize = 12.sp, color = Color(0xFF64748B)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFEF4444),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 2
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("Keep Delivery")
                    }

                    Button(
                        onClick = {
                            if (!isSubmitting) {
                                isSubmitting = true
                                onConfirmCancel(selectedReason, additionalNote)
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Confirm Cancel", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
fun RiderOrderDetailDialog(
    offer: ServerOffer,
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ORDER #${offer.orderId.takeLast(8)}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "DISPATCH OFFER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF38BDF8)
                        )
                    }

                    Surface(
                        color = Color(0xFF10B981),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "₹${offer.earningsAmount.toInt()}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF334155))

                // Locations Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📍 PICKUP LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                        Text(
                            text = "${offer.merchantName}\n${offer.merchantAddress}",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )

                        HorizontalDivider(color = Color(0xFF334155))

                        Text("🏠 DELIVERY LOCATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        Text(
                            text = "${offer.customerName}\n${offer.customerAddress}",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )

                        HorizontalDivider(color = Color(0xFF334155))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Trip Route:", fontSize = 12.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = "${offer.totalDistanceKm} km (~${offer.estimatedDurationMins} min)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }

                // COD Card
                if (offer.isCod) {
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "💵 CASH ON DELIVERY: Collect ₹${(offer.codAmount ?: 0.0).toInt()} from customer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDecline,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Decline", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.weight(1.5f).height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Accept offer", fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                }
            }
        },
        containerColor = Color(0xFF0F172A),
        shape = RoundedCornerShape(20.dp)
    )
}

private fun launchExternalMaps(context: Context, lat: Double, lng: Double, label: String) {
    if (lat == 0.0 || lng == 0.0) {
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
