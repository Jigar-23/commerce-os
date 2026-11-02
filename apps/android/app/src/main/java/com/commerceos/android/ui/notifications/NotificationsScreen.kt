package com.commerceos.android.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.commerceos.android.notifications.CustomerNotificationManager
import com.commerceos.android.notifications.InAppNotificationItem
import com.commerceos.android.notifications.LocalNotificationStorage
import com.commerceos.android.notifications.NotificationPreferenceManager

private val EmeraldPrimary = Color(0xFF0E675E)
private val EmeraldDark = Color(0xFF094640)
private val EmeraldSurface = Color(0xFFF2F9F8)
private val EmeraldBorder = Color(0xFFBBE5DF)

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToOrders: () -> Unit = {},
    onNavigateToPrescriptions: () -> Unit = {},
    onNavigateToCart: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Orders", "Prescriptions", "Refills")

    // In-App Notification Center History (Local Device Storage)
    var notificationsList by remember {
        mutableStateOf(LocalNotificationStorage.getNotifications(context))
    }

    // User Preference Toggles (Local SharedPreferences via NotificationPreferenceManager)
    var orderAlertsEnabled by remember {
        mutableStateOf(NotificationPreferenceManager.isOrderAlertsEnabled(context))
    }
    var rxAlertsEnabled by remember {
        mutableStateOf(NotificationPreferenceManager.isRxAlertsEnabled(context))
    }
    var refillAlertsEnabled by remember {
        mutableStateOf(NotificationPreferenceManager.isRefillAlertsEnabled(context))
    }

    // Android 13+ Runtime Permission Request for POST_NOTIFICATIONS
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val filteredList = if (selectedCategory == "All") {
        notificationsList
    } else {
        notificationsList.filter { it.category.equals(selectedCategory, ignoreCase = true) }
    }

    val unreadTotalCount = notificationsList.count { !it.isRead }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // 1. Signature Deep Emerald Header Canopy
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(EmeraldDark, EmeraldPrimary)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Notifications",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                            if (unreadTotalCount > 0) {
                                Surface(
                                    color = Color(0xFFFEF08A),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "$unreadTotalCount NEW",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF854D0E),
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Live order status, Rx & refills",
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    if (notificationsList.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Mark all read button
                            Surface(
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.clickable {
                                    LocalNotificationStorage.markAllAsRead(context)
                                    notificationsList = LocalNotificationStorage.getNotifications(context)
                                }
                            ) {
                                Text(
                                    text = "Mark read",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                            // Clear all button
                            Surface(
                                color = Color.White.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.clickable {
                                    LocalNotificationStorage.clearAll(context)
                                    notificationsList = emptyList()
                                }
                            ) {
                                Text(
                                    text = "Clear",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Category Filter Pills
        Surface(
            color = Color.White,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    val categoryCount = if (cat == "All") {
                        notificationsList.size
                    } else {
                        notificationsList.count { it.category.equals(cat, ignoreCase = true) }
                    }
                    val categoryUnread = if (cat == "All") {
                        notificationsList.count { !it.isRead }
                    } else {
                        notificationsList.count { it.category.equals(cat, ignoreCase = true) && !it.isRead }
                    }

                    Surface(
                        color = if (isSelected) EmeraldPrimary else Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) EmeraldPrimary else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier.clickable { selectedCategory = cat }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF334155)
                            )
                            if (categoryUnread > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isSelected) Color.White else EmeraldPrimary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(6.dp)
                                ) {}
                            } else if (categoryCount > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$categoryCount",
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFF94A3B8),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Notification Stream or Clean Industry Empty State
        if (filteredList.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = EmeraldSurface,
                    shape = CircleShape,
                    border = BorderStroke(1.5.dp, EmeraldBorder),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.NotificationsNone,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (selectedCategory == "All") "You're all caught up!" else "No $selectedCategory notifications",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Live order status, rider GPS tracking, and chronic medicine refills will appear here.",
                    fontSize = 12.5.sp,
                    color = Color(0xFF64748B),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Instant Test Notification Trigger (Helpful for quick verification)
                Button(
                    onClick = {
                        val randomOrderId = "ORD" + (1000..9999).random()
                        CustomerNotificationManager.postOrderPlacedNotification(
                            context = context,
                            orderId = randomOrderId,
                            itemCount = 3,
                            totalAmount = "685",
                            etaMins = 12
                        )
                        notificationsList = LocalNotificationStorage.getNotifications(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Simulate Order Notification",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // On-Device Notification Channel Preferences Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = EmeraldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SYSTEM ALERT PREFERENCES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF64748B),
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        NotificationToggleRow(
                            icon = Icons.Default.LocalShipping,
                            iconTint = Color(0xFF2563EB),
                            title = "Order Dispatch & Live Rider SLA",
                            subtitle = "Heads-up alert when dark store packs order & rider departs",
                            checked = orderAlertsEnabled,
                            onCheckedChange = {
                                orderAlertsEnabled = it
                                NotificationPreferenceManager.setOrderAlertsEnabled(context, it)
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                        NotificationToggleRow(
                            icon = Icons.Default.MedicalServices,
                            iconTint = EmeraldPrimary,
                            title = "Pharmacist Prescription Review",
                            subtitle = "Real-time verification on uploaded prescription doctor slips",
                            checked = rxAlertsEnabled,
                            onCheckedChange = {
                                rxAlertsEnabled = it
                                NotificationPreferenceManager.setRxAlertsEnabled(context, it)
                            }
                        )
                        HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                        NotificationToggleRow(
                            icon = Icons.Default.EventRepeat,
                            iconTint = Color(0xFFD97706),
                            title = "Chronic Refill Reminders",
                            subtitle = "3-day advance on-device alert for chronic daily medications",
                            checked = refillAlertsEnabled,
                            onCheckedChange = {
                                refillAlertsEnabled = it
                                NotificationPreferenceManager.setRefillAlertsEnabled(context, it)
                            }
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredList, key = { it.id }) { notif ->
                    NotificationCard(
                        notification = notif,
                        onClick = {
                            LocalNotificationStorage.markAsRead(context, notif.id)
                            notificationsList = LocalNotificationStorage.getNotifications(context)

                            // Deep Link Execution
                            val action = notif.deepLinkAction?.uppercase()?.trim() ?: ""
                            when {
                                action.startsWith("TRACK_ORDER") || action == "ORDERS" -> onNavigateToOrders()
                                action.startsWith("REFILL_CART") || action == "CART" -> onNavigateToCart()
                                action.startsWith("PRESCRIPTIONS") || action == "RX" -> onNavigateToPrescriptions()
                                else -> {
                                    if (notif.category.equals("Orders", ignoreCase = true)) {
                                        onNavigateToOrders()
                                    } else if (notif.category.equals("Prescriptions", ignoreCase = true)) {
                                        onNavigateToPrescriptions()
                                    } else if (notif.category.equals("Refills", ignoreCase = true)) {
                                        onNavigateToCart()
                                    }
                                }
                            }
                        },
                        onDelete = {
                            LocalNotificationStorage.deleteNotification(context, notif.id)
                            notificationsList = LocalNotificationStorage.getNotifications(context)
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "NOTIFICATION CHANNELS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF64748B),
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            NotificationToggleRow(
                                icon = Icons.Default.LocalShipping,
                                iconTint = Color(0xFF2563EB),
                                title = "Order Dispatch & Live Rider SLA",
                                subtitle = "Push alerts when rider departs store",
                                checked = orderAlertsEnabled,
                                onCheckedChange = {
                                    orderAlertsEnabled = it
                                    NotificationPreferenceManager.setOrderAlertsEnabled(context, it)
                                }
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                            NotificationToggleRow(
                                icon = Icons.Default.MedicalServices,
                                iconTint = EmeraldPrimary,
                                title = "Pharmacist Prescription Review",
                                subtitle = "Immediate status on uploaded Rx slips",
                                checked = rxAlertsEnabled,
                                onCheckedChange = {
                                    rxAlertsEnabled = it
                                    NotificationPreferenceManager.setRxAlertsEnabled(context, it)
                                }
                            )
                            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp, modifier = Modifier.padding(vertical = 10.dp))
                            NotificationToggleRow(
                                icon = Icons.Default.EventRepeat,
                                iconTint = Color(0xFFD97706),
                                title = "Chronic Refill Reminders",
                                subtitle = "Advance 3-day chronic medicine alert",
                                checked = refillAlertsEnabled,
                                onCheckedChange = {
                                    refillAlertsEnabled = it
                                    NotificationPreferenceManager.setRefillAlertsEnabled(context, it)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: InAppNotificationItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, iconTint, iconBg) = when (notification.category.lowercase().trim()) {
        "orders", "order" -> Triple(Icons.Default.LocalShipping, Color(0xFF2563EB), Color(0xFFEFF6FF))
        "prescriptions", "prescription", "rx" -> Triple(Icons.Default.MedicalServices, EmeraldPrimary, EmeraldSurface)
        "refills", "refill" -> Triple(Icons.Default.EventRepeat, Color(0xFFD97706), Color(0xFFFFFBEB))
        else -> Triple(Icons.Default.Notifications, Color(0xFF7C3AED), Color(0xFFF5F3FF))
    }

    val actionLabel = when (notification.category.lowercase().trim()) {
        "orders", "order" -> "Track Order"
        "prescriptions", "prescription", "rx" -> "View Rx Vault"
        "refills", "refill" -> "Reorder"
        else -> "View"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) Color.White else Color(0xFFFAFDFA)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (notification.isRead) Color(0xFFE2E8F0) else EmeraldPrimary.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (notification.isRead) 0.5.dp else 1.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Category Icon Badge
            Surface(
                color = iconBg,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, iconTint.copy(alpha = 0.2f)),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!notification.isRead) {
                            Surface(
                                color = EmeraldPrimary,
                                shape = CircleShape,
                                modifier = Modifier.size(8.dp)
                            ) {}
                        }
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable(onClick = onDelete)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = notification.message,
                    fontSize = 12.5.sp,
                    color = Color(0xFF475569),
                    lineHeight = 17.5.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = notification.category,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }
                        Text(
                            text = notification.formattedTime,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    // Direct Action Label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(onClick = onClick)
                    ) {
                        Text(
                            text = actionLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldPrimary
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
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
        Surface(
            color = iconTint.copy(alpha = 0.1f),
            shape = CircleShape,
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B), lineHeight = 14.sp)
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = EmeraldPrimary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFCBD5E1)
            ),
            modifier = Modifier.size(width = 44.dp, height = 24.dp)
        )
    }
}
