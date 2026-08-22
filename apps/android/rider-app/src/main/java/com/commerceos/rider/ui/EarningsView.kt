package com.commerceos.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.RiderProfile

@Composable
fun EarningsView(
    profile: RiderProfile?,
    modifier: Modifier = Modifier
) {
    val activeProfile = profile ?: RiderProfile(
        riderId = "rdr_rewari_01",
        name = "Delivery Partner",
        phone = "+919876543210",
        vehicleNumber = "HR-26-AB-1234",
        rating = 4.9,
        completedToday = 0,
        earningsTodayFormatted = "₹0",
        shiftStatus = "ONLINE_AVAILABLE",
        assignedHub = "Rewari Central Hub",
        tier = "Diamond"
    )

    val completedCount = activeProfile.completedToday ?: 0
    val earningsDisplay = activeProfile.earningsTodayFormatted ?: "₹0"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Today's Total Earnings Hero Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today's Earnings",
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = earningsDisplay,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF10B981)
                        )
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF1E293B))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Deliveries", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Text("$completedCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    if (!activeProfile.assignedHub.isNullOrBlank()) {
                        Column {
                            Text("Assigned Hub", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Text(activeProfile.assignedHub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF38BDF8))
                        }
                    }
                }
            }
        }

        // Payout Schedule Information
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Settlement Information", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(
                    text = "Daily delivery earnings and tips are automatically settled into your registered bank account every Tuesday and Friday.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8),
                    lineHeight = 18.sp
                )
            }
        }
    }
}
