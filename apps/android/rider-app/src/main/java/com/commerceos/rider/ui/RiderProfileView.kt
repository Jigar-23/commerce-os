package com.commerceos.rider.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.RiderProfile
import com.commerceos.rider.theme.RiderColors

@Composable
fun RiderProfileView(
    profile: RiderProfile?,
    isOnline: Boolean,
    isShiftUpdating: Boolean = false,
    onToggleShift: (Boolean) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }

    val activeProfile = profile ?: RiderProfile(
        riderId = "",
        name = "Loading profile...",
        phone = "",
        vehicleNumber = "",
        rating = 5.0,
        completedToday = 0,
        earningsTodayFormatted = "₹0",
        shiftStatus = if (isOnline) "ONLINE_AVAILABLE" else "OFFLINE",
        assignedHub = ""
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(RiderColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero Captain Identity Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
            border = BorderStroke(1.dp, RiderColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(RiderColors.PrimarySoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = RiderColors.Primary,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = activeProfile.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = RiderColors.TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))
                val riderIdText = if (!activeProfile.riderId.isNullOrBlank()) "Rider ID: ${activeProfile.riderId}" else "Partner ID: #6180"
                Text(
                    text = riderIdText,
                    fontSize = 12.sp,
                    color = RiderColors.TextMuted,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Fleet Pill
                    Surface(
                        color = RiderColors.AccentBlueSoft,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RiderColors.AccentBlue.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Rewari Hub",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.AccentBlueText
                            )
                        }
                    }

                    // Verified Badge Pill
                    Surface(
                        color = RiderColors.PrimarySoft,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "In-House Fleet",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = RiderColors.PrimarySoftText
                            )
                        }
                    }
                }
            }
        }

        // 2. Performance Metric Grid (4-up stats)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Deliveries Today
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                border = BorderStroke(1.dp, RiderColors.Border),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("TRIPS TODAY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextMuted, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${activeProfile.completedToday}", fontSize = 20.sp, fontWeight = FontWeight.Black, color = RiderColors.TextPrimary)
                    Text("Completed", fontSize = 11.sp, color = RiderColors.TextLight)
                }
            }

            // Today's Delivered Order Volume
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                border = BorderStroke(1.dp, RiderColors.Border),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("TODAY'S VOLUME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextMuted, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    val earningsText = activeProfile.earningsTodayFormatted?.ifBlank { "₹0" } ?: "₹0"
                    Text(earningsText, fontSize = 20.sp, fontWeight = FontWeight.Black, color = RiderColors.Primary)
                    Text("Fulfilled Orders", fontSize = 11.sp, color = RiderColors.TextLight)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // SLA
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                border = BorderStroke(1.dp, RiderColors.Border),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("ON-TIME SLA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextMuted, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("99.4%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = RiderColors.TextPrimary)
                    Text("Top 5% Tier", fontSize = 11.sp, color = RiderColors.Success)
                }
            }

            // Acceptance
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                border = BorderStroke(1.dp, RiderColors.Border),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("ACCEPTANCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextMuted, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("100%", fontSize = 20.sp, fontWeight = FontWeight.Black, color = RiderColors.TextPrimary)
                    Text("Auto-dispatch", fontSize = 11.sp, color = RiderColors.TextLight)
                }
            }
        }

        // 3. Duty / Shift Status Toggle Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
            border = BorderStroke(1.dp, RiderColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Duty & Shift Status", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isOnline) "🟢 ONLINE • Receiving express dispatches" else "⚪ OFFLINE • Shift paused",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isOnline) RiderColors.Primary else RiderColors.TextMuted
                    )
                }

                Switch(
                    checked = isOnline,
                    onCheckedChange = onToggleShift,
                    enabled = !isShiftUpdating,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RiderColors.Primary,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = RiderColors.Border
                    )
                )
            }
        }

        // 4. Vehicle & Fleet Hub Details Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
            border = BorderStroke(1.dp, RiderColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Vehicle & Fleet Equipment", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)

                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Registered Vehicle", fontSize = 13.sp, color = RiderColors.TextSecondary)
                    Text(activeProfile.vehicleNumber.ifBlank { "HR-26-AB-1234 (EV)" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Registered Phone", fontSize = 13.sp, color = RiderColors.TextSecondary)
                    Text(activeProfile.phone.ifBlank { "+91 98179 16180" }, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                }

                val hubName = activeProfile.assignedHub?.ifBlank { "Rewari Central Hub" } ?: "Rewari Central Hub"
                val cleanHubDisplay = if (hubName.contains("(")) hubName.substringBefore("(").trim() else hubName
                val cleanStoreId = if (hubName.contains("(")) hubName.substringAfter("(").substringBefore(")") else "STORE_REWARI_01"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text("Assigned Base Hub", fontSize = 13.sp, color = RiderColors.TextSecondary, modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(cleanHubDisplay, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.AccentBlueText)
                        Text(cleanStoreId, fontSize = 11.sp, color = RiderColors.TextMuted)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cold-Chain Delivery Bag", fontSize = 13.sp, color = RiderColors.TextSecondary)
                    Text("✓ Inspected & Certified", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RiderColors.Success)
                }
            }
        }

        // 5. Store Employment & Shift Details Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
            border = BorderStroke(1.dp, RiderColors.Border),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Store Employment & Shift", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                    Surface(
                        color = RiderColors.PrimarySoft,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Monthly Staff",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = RiderColors.PrimarySoftText,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Employment Type", fontSize = 13.sp, color = RiderColors.TextSecondary)
                    Text("Salaried In-House Fleet", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Assigned Store Hub", fontSize = 13.sp, color = RiderColors.TextSecondary)
                    Text("Rewari Central Hub", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Daily Shift Schedule", fontSize = 13.sp, color = RiderColors.TextSecondary)
                    Text("9:00 AM – 9:00 PM", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RiderColors.Primary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("COD Remittance", fontSize = 13.sp, color = RiderColors.TextSecondary)
                    Text("Direct Handover to Cashier", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = RiderColors.Success)
                }
            }
        }

        // 6. Hotline & Support Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:18001234567"))
                    context.startActivity(intent)
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, RiderColors.Border),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, tint = RiderColors.AccentBlue, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Hub Hotline", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RiderColors.TextPrimary)
            }

            OutlinedButton(
                onClick = {
                    Toast.makeText(context, "Emergency SOS triggered: Hub Dispatch alerted", Toast.LENGTH_LONG).show()
                },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, RiderColors.Danger.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = RiderColors.DangerSoft),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = RiderColors.Danger, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Captain SOS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RiderColors.DangerText)
            }
        }

        // 7. Logout Action Button
        OutlinedButton(
            onClick = { showLogoutDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = RiderColors.Danger),
            border = BorderStroke(1.dp, RiderColors.Danger.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout Rider Account", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Logout Confirmation", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to end your shift and log out of CommerceOS Partner Fleet?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Danger)
                ) {
                    Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = RiderColors.TextSecondary)
                }
            },
            containerColor = RiderColors.Surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
