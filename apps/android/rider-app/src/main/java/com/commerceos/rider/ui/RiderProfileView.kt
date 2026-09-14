package com.commerceos.rider.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
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
fun RiderProfileView(
    profile: RiderProfile?,
    isOnline: Boolean,
    onToggleShift: (Boolean) -> Unit,
    onLogout: () -> Unit,
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
        shiftStatus = if (isOnline) "ONLINE_AVAILABLE" else "OFFLINE",
        assignedHub = "Koramangala Hub"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D0F14))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
            border = BorderStroke(1.dp, Color(0xFF262933)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color(0xFF10B981).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(44.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = activeProfile.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (!profile?.riderId.isNullOrBlank()) "Rider ID: ${profile?.riderId}" else "—",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )

                if (profile?.rating != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("${profile.rating}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // Shift Status Toggle Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
            border = BorderStroke(1.dp, Color(0xFF262933)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Duty / Shift Status", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = if (isOnline) "ONLINE • Ready for offers" else "OFFLINE • Shift ended",
                        fontSize = 12.sp,
                        color = if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8)
                    )
                }

                Switch(
                    checked = isOnline,
                    onCheckedChange = onToggleShift,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF10B981),
                        uncheckedThumbColor = Color(0xFF94A3B8),
                        uncheckedTrackColor = Color(0xFF262933)
                    )
                )
            }
        }

        // Vehicle & Safety Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16181F)),
            border = BorderStroke(1.dp, Color(0xFF262933)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Vehicle & Details", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Registered Vehicle", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text(profile?.vehicleNumber?.takeIf { it.isNotBlank() } ?: "—", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Registered Phone", fontSize = 12.sp, color = Color(0xFF94A3B8))
                    Text(profile?.phone?.takeIf { it.isNotBlank() } ?: "—", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                if (!profile?.assignedHub.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Assigned Hub", fontSize = 12.sp, color = Color(0xFF94A3B8))
                        Text(profile!!.assignedHub!!, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                    }
                }
            }
        }

        // Logout Action Button
        OutlinedButton(
            onClick = onLogout,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout Rider Account", fontWeight = FontWeight.Bold)
        }
    }
}
