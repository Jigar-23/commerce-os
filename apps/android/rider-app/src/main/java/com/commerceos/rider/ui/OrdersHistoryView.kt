package com.commerceos.rider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.commerceos.rider.model.ServerDeliverySession

@Composable
fun OrdersHistoryView(
    activeSession: ServerDeliverySession?,
    completedSessions: List<ServerDeliverySession> = emptyList(),
    onSelectActiveOrder: () -> Unit,
    onViewOrderDetail: ((ServerDeliverySession) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Delivery Card
        if (activeSession != null && activeSession.state != "DELIVERED") {
            item {
                Text(
                    text = "CURRENT ACTIVE DELIVERY",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
                    border = BorderStroke(1.5.dp, Color(0xFF10B981)),
                    onClick = {
                        onViewOrderDetail?.invoke(activeSession) ?: onSelectActiveOrder()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val context = LocalContext.current
                        val shortId = activeSession.orderId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ORDER #$shortId",
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Order ID", activeSession.orderId))
                                        Toast.makeText(context, "Copied Order ID: #$shortId", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "Copy",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = activeSession.state.replace("_", " "),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        val merchantText = activeSession.merchantName.ifBlank { "Rewari Central Hub" }
                        val customerText = activeSession.customerName.ifBlank { "Customer" }
                        Text(
                            text = "$merchantText → $customerText",
                            fontSize = 13.sp,
                            color = Color(0xFFE2E8F0),
                            fontWeight = FontWeight.Medium
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val itemCount = activeSession.items.sumOf { it.quantity }.takeIf { it > 0 } ?: 1
                            Text(
                                text = "📦 $itemCount item(s) • Tap for details",
                                fontSize = 12.sp,
                                color = Color(0xFF9CA3AF)
                            )
                            Text(
                                text = activeSession.payoutFormatted ?: "₹35",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        item {
            Text(
                text = "TODAY'S COMPLETED DELIVERIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF9CA3AF),
                letterSpacing = 1.sp
            )
        }

        if (completedSessions.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
                    border = BorderStroke(1.dp, Color(0xFF262933)),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = Color(0xFF22252E),
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(28.dp))
                            }
                        }
                        Text("No completed deliveries yet", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                        Text(
                            "Completed deliveries and payout settlements will appear here.\nTap on any delivery to inspect complete item and customer details.",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else {
            items(completedSessions) { itemSession ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
                    border = BorderStroke(1.dp, Color(0xFF262933)),
                    onClick = {
                        onViewOrderDetail?.invoke(itemSession)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val cContext = LocalContext.current
                            val cShortId = itemSession.orderId
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("ORDER #$cShortId", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                    modifier = Modifier.clickable {
                                        val cm = cContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Order ID", itemSession.orderId))
                                        Toast.makeText(cContext, "Copied Order ID: #$cShortId", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(
                                        text = "Copy",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF38BDF8),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = itemSession.payoutFormatted ?: "₹${((itemSession.distanceKm ?: 2.0) * 15.0).toInt().coerceAtLeast(35)}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF10B981)
                            )
                        }

                        Text(
                            text = "${itemSession.merchantName} → ${itemSession.customerAddress}",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF),
                            maxLines = 1
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val qty = itemSession.items.sumOf { it.quantity }.takeIf { it > 0 } ?: 1
                            Text(
                                text = "📦 $qty item(s) • ${if (itemSession.isCod) "COD" else "Prepaid"}",
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = "View Details →",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
