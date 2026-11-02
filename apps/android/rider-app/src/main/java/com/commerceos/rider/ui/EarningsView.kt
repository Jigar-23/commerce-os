package com.commerceos.rider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.commerceos.rider.theme.RiderColors

@Composable
fun EarningsView(
    profile: RiderProfile?,
    modifier: Modifier = Modifier
) {
    val activeProfile = profile ?: RiderProfile(
        riderId = "rdr_partner",
        name = "Delivery Partner",
        phone = "",
        vehicleNumber = "Electric Scooter",
        rating = 4.9,
        completedToday = 0,
        earningsTodayFormatted = "₹0",
        shiftStatus = "ONLINE_AVAILABLE",
        assignedHub = "Rewari Hub"
    )

    val completedCount = activeProfile.completedToday ?: 0
    val earningsDisplay = activeProfile.earningsTodayFormatted ?: "₹0"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RiderColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Today's Total Earnings Hero Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
            border = BorderStroke(1.dp, RiderColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                            color = RiderColors.TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = earningsDisplay,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = RiderColors.Primary
                        )
                    }

                    Surface(
                        color = RiderColors.PrimarySoft,
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = RiderColors.Primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Deliveries", fontSize = 11.sp, color = RiderColors.TextMuted)
                        Text("$completedCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                    }
                    if (!activeProfile.assignedHub.isNullOrBlank()) {
                        Column {
                            Text("Assigned Hub", fontSize = 11.sp, color = RiderColors.TextMuted)
                            Text(activeProfile.assignedHub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.AccentBlueText)
                        }
                    }
                }
            }
        }

        // Payout Schedule Information
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
            border = BorderStroke(1.dp, RiderColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Settlement Information", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                Text(
                    text = "Daily delivery earnings and tips are automatically settled into your registered bank account every Tuesday and Friday.",
                    fontSize = 12.sp,
                    color = RiderColors.TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }
    }
}
