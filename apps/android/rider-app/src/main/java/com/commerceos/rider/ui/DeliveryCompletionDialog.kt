package com.commerceos.rider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.theme.RiderColors

@Composable
fun DeliveryCompletionDialog(
    session: ServerDeliverySession,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success Badge
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(RiderColors.PrimarySoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = RiderColors.Primary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DELIVERY COMPLETED! 🎉",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = RiderColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val shortId = if (session.orderId.length > 14) session.orderId.take(12) + "…" else session.orderId
                    Text(
                        text = "Order #$shortId handoff confirmed",
                        fontSize = 12.sp,
                        color = RiderColors.TextMuted
                    )
                }

                // Earnings Credit Summary
                Card(
                    colors = CardDefaults.cardColors(containerColor = RiderColors.SurfaceSubtle),
                    border = BorderStroke(1.dp, RiderColors.Border),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Order Value / Mode:", fontSize = 13.sp, color = RiderColors.TextSecondary)
                            val billVal = if (session.isCod) {
                                "₹${(session.codCollectedAmount ?: session.codAmount ?: session.orderTotal ?: 0.0).toInt()} (COD)"
                            } else {
                                "₹${(session.orderTotal ?: 0.0).toInt()} (Zero Cash Due)"
                            }
                            Text(
                                text = billVal,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.Primary
                            )
                        }

                        HorizontalDivider(color = RiderColors.Border, thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Customer:", fontSize = 12.sp, color = RiderColors.TextMuted)
                            Text(session.customerName.ifBlank { "Customer" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.TextPrimary)
                        }

                        if (session.isCod) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("COD Cash Collected:", fontSize = 12.sp, color = RiderColors.TextMuted)
                                Text("₹${(session.codCollectedAmount ?: 0.0).toInt()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RiderColors.Warning)
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("Ready for next offer", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        },
        containerColor = RiderColors.Surface,
        shape = RoundedCornerShape(22.dp)
    )
}
