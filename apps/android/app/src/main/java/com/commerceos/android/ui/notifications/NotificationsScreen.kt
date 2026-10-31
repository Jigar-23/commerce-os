package com.commerceos.android.ui.notifications

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography

data class PharmacyNotification(
    val id: String,
    val title: String,
    val message: String,
    val category: String, // "Orders", "Prescriptions", "Refills", "Offers"
    val timestamp: String,
    val isRead: Boolean = false,
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color
)

@Composable
fun NotificationsScreen(
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Orders", "Prescriptions", "Refills", "Offers")

    var notificationsList by remember {
        mutableStateOf(emptyList<PharmacyNotification>())
    }

    var orderAlertsEnabled by remember { mutableStateOf(true) }
    var rxAlertsEnabled by remember { mutableStateOf(true) }
    var refillAlertsEnabled by remember { mutableStateOf(true) }

    val filteredList = if (selectedCategory == "All") {
        notificationsList
    } else {
        notificationsList.filter { it.category == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F7))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)
    ) {
        // Top Bar matching other screens
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White,
                shape = CircleShape,
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                shadowElevation = 0.5.dp,
                modifier = Modifier.size(38.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Order updates, prescriptions & health alerts",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }

            TextButton(
                onClick = {
                    notificationsList = notificationsList.map { it.copy(isRead = true) }
                }
            ) {
                Text(
                    text = "Mark all read",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF059669)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    color = if (isSelected) Color(0xFF059669) else Color.White,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF059669) else Color(0xFFE2E8F0)),
                    shadowElevation = if (isSelected) 1.dp else 0.dp,
                    modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                    Text(
                        text = cat,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else Color(0xFF334155),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Notifications List or Clean Empty State
        if (filteredList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = Color.White,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "No notifications yet",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Real-time delivery status, prescription approvals, and medicine refill alerts will appear here.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "DELIVERY & HEALTHCARE ALERTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        NotificationToggleRow(
                            title = "Order Dispatch & Live Rider SLA",
                            subtitle = "Push alerts when rider departs store",
                            checked = orderAlertsEnabled,
                            onCheckedChange = { orderAlertsEnabled = it }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                        NotificationToggleRow(
                            title = "Pharmacist Prescription Review",
                            subtitle = "Immediate status on uploaded Rx slips",
                            checked = rxAlertsEnabled,
                            onCheckedChange = { rxAlertsEnabled = it }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 0.5.dp, modifier = Modifier.padding(vertical = 8.dp))
                        NotificationToggleRow(
                            title = "Refill Reminders",
                            subtitle = "Advance 3-day chronic medicine alert",
                            checked = refillAlertsEnabled,
                            onCheckedChange = { refillAlertsEnabled = it }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredList, key = { it.id }) { notif ->
                    NotificationCard(
                        notification = notif,
                        onClick = {
                            notificationsList = notificationsList.map {
                                if (it.id == notif.id) it.copy(isRead = true) else it
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: PharmacyNotification,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) Color.White else Color(0xFFF8FAFC)
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (notification.isRead) Color(0xFFE2E8F0) else Color(0xFF059669).copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Colored icon container
            Surface(
                color = notification.iconBg,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = notification.icon,
                        contentDescription = null,
                        tint = notification.iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = notification.title,
                        fontSize = 14.sp,
                        fontWeight = if (notification.isRead) FontWeight.SemiBold else FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    if (!notification.isRead) {
                        Surface(
                            color = Color(0xFF059669),
                            shape = CircleShape,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notification.message,
                    fontSize = 12.sp,
                    color = Color(0xFF475569),
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = notification.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = notification.timestamp,
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B))
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF059669),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1)
            ),
            modifier = Modifier.size(width = 44.dp, height = 24.dp)
        )
    }
}
