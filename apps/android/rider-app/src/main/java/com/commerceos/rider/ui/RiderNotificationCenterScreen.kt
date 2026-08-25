package com.commerceos.rider.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.commerceos.rider.model.RiderNotificationItem
import com.commerceos.rider.theme.RiderColors

@Composable
fun RiderNotificationCenterScreen(
    notifications: List<RiderNotificationItem>,
    unreadCount: Int,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    onNotificationClick: (RiderNotificationItem) -> Unit,
    onMarkAllRead: () -> Unit,
    onBack: () -> Unit
) {
    val categories = listOf("ALL", "ORDERS", "EARNINGS", "INCENTIVES", "OPERATIONS", "SYSTEM")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Notification Center",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                if (unreadCount > 0) {
                    Text(
                        text = "$unreadCount unread alerts",
                        color = Color(0xFF10B981),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (unreadCount > 0) {
                TextButton(onClick = onMarkAllRead) {
                    Text("Mark all read", color = Color(0xFF38BDF8), fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Category Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.take(4).forEach { cat ->
                val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) Color(0xFF10B981) else Color(0xFF1E293B),
                    modifier = Modifier.clickable { onCategorySelected(cat) }
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No notifications in this category.",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().weight(1f)
            ) {
                items(notifications) { item ->
                    NotificationCardItem(item = item, onClick = { onNotificationClick(item) })
                }
            }
        }
    }
}

@Composable
private fun NotificationCardItem(
    item: RiderNotificationItem,
    onClick: () -> Unit
) {
    val isUnread = item.readAt == null
    val containerBg = if (isUnread) Color(0xFF1E293B) else Color(0xFF0F172A)

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 10.dp)
                        .size(8.dp)
                        .background(Color(0xFF10B981), CircleShape)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = item.category,
                        color = Color(0xFF38BDF8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = item.body,
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )

                if (item.deepLink != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap to view details →",
                        color = Color(0xFF10B981),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
