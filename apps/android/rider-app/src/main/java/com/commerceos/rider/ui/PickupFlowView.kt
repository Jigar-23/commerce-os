package com.commerceos.rider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.theme.RiderColors

@Composable
fun PickupFlowView(
    session: ServerDeliverySession,
    onConfirmPickup: () -> Unit,
    onReportIssue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
        border = BorderStroke(1.dp, RiderColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = RiderColors.AccentBlueSoft,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("STORE PICKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = RiderColors.AccentBlue, letterSpacing = 0.8.sp)
                    Text("Order is ready for pickup", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                }
            }

            Text(
                text = "${session.merchantName} • ${session.merchantAddress}",
                fontSize = 13.sp,
                color = RiderColors.TextSecondary
            )

            HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

            // Verification Checklist
            Text("Check package & order details", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Order items packed and sealed in tamper-proof bag", fontSize = 12.sp, color = RiderColors.TextSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val itemsSummary = if (session.items.isNotEmpty()) {
                    session.items.joinToString(", ") { "${it.name} (x${it.quantity})" }
                } else {
                    "Order Items Verified"
                }
                Text("Contents: $itemsSummary", fontSize = 12.sp, color = RiderColors.AccentBlueText, fontWeight = FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val cartVal = session.orderTotal ?: session.codAmount ?: 0.0
                val billText = if (cartVal > 0.0) " • Bill Total ₹${if (cartVal % 1.0 == 0.0) cartVal.toInt() else "%.2f".format(cartVal)}" else ""
                val shortId = if (session.orderId.length > 14) session.orderId.take(12) + "…" else session.orderId
                Text("Order #$shortId$billText", fontSize = 12.sp, color = RiderColors.TextSecondary)
            }

            Spacer(modifier = Modifier.height(4.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            val storePhone = session.merchantPhone.ifBlank { "1800123456" }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        com.commerceos.rider.util.RiderNavigationUtils.dialPhoneNumber(context, storePhone)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RiderColors.AccentBlueText),
                    border = BorderStroke(1.dp, RiderColors.Border),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Icon(Icons.Default.Place, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call Hub", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onReportIssue,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RiderColors.Danger),
                    border = BorderStroke(1.dp, RiderColors.Danger.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Report issue", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = onConfirmPickup,
                    colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.4f).height(48.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text("Confirm pickup", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }
        }
    }
}
