package com.commerceos.rider.ui

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.rider.model.RiderNotificationItem
import com.commerceos.rider.model.RiderProfile
import com.commerceos.rider.model.ServerDeliverySession
import com.commerceos.rider.model.ServerOffer
import com.commerceos.rider.repository.RiderDeliveryRepository
import com.commerceos.rider.service.RiderForegroundLocationService
import com.commerceos.rider.session.RiderSessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderMainScreen(
    profile: RiderProfile? = null,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionManager = remember(context) { RiderSessionManager.getInstance(context) }
    val repository = remember(sessionManager) {
        RiderDeliveryRepository(
            baseUrlProvider = { sessionManager.getBaseUrl() },
            authTokenProvider = { sessionManager.getAuthToken() }
        )
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var liveProfile by remember { mutableStateOf<RiderProfile?>(profile) }
    var isOnline by remember { mutableStateOf(true) }
    var session by remember { mutableStateOf<ServerDeliverySession?>(null) }
    var completedSessionsList by remember { mutableStateOf<List<ServerDeliverySession>>(emptyList()) }
    var activeOffer by remember { mutableStateOf<ServerOffer?>(null) }
    var notificationsList by remember { mutableStateOf<List<RiderNotificationItem>>(emptyList()) }
    var unreadNotifCount by remember { mutableIntStateOf(0) }
    var showOrderDetailDialog by remember { mutableStateOf(false) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var showCancelDeliveryDialog by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var actionLoading by remember { mutableStateOf(false) }
    var enteredOtp by remember { mutableStateOf("") }
    var enteredCodAmount by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableIntStateOf(0) }

    val lastLocation by RiderForegroundLocationService.lastLocation.collectAsState()
    val isStale by RiderForegroundLocationService.isStale.collectAsState()
    val isNetworkAvailable by RiderForegroundLocationService.isNetworkAvailable.collectAsState()

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }
    }

    var isShiftUpdating by remember { mutableStateOf(false) }

    // 1. Fetch Authoritative Rider Profile & Register Cached FCM Token
    LaunchedEffect(Unit) {
        if (sessionManager.getAuthToken().isBlank()) {
            onLogout()
            return@LaunchedEffect
        }
        val res = repository.fetchRiderProfile()
        res.onSuccess {
            liveProfile = it
            isOnline = true
        }

        // Register any pending cached FCM token
        val cachedFcmToken = sessionManager.getCachedDeviceToken()
        if (cachedFcmToken.isNotBlank()) {
            repository.registerDeviceToken(cachedFcmToken)
        }
    }

    // 2. Realtime Incoming Offer Pipeline Stream (FCM & SSE Ingestion)
    LaunchedEffect(Unit) {
        com.commerceos.rider.util.RiderOfferEventPipeline.incomingOfferEvents.collect { offer ->
            val prevId = activeOffer?.offerId
            activeOffer = offer
            if (prevId != offer.offerId) {
                com.commerceos.rider.util.RiderAlertNotifier.playNewJobAlert(context, offer.offerId)
            }
        }
    }

    // 3. Event-Driven Background Reconciliation Loop (3s active polling)
    LaunchedEffect(isOnline) {
        while (isOnline) {
            if (sessionManager.getAuthToken().isBlank()) {
                val loginRes = repository.loginAsRider("rdr_rewari_01", "+919876543210")
                loginRes.onSuccess { token ->
                    sessionManager.saveAuthToken(token)
                }
            }

            when (val offerResult = repository.fetchActiveOffer()) {
                is com.commerceos.rider.model.ActiveOfferResult.Success -> {
                    val prevId = activeOffer?.offerId
                    activeOffer = offerResult.offer
                    if (prevId != offerResult.offer.offerId) {
                        com.commerceos.rider.util.RiderAlertNotifier.playNewJobAlert(context, offerResult.offer.offerId)
                        // Post persistent notification in Android notification tray
                        com.commerceos.rider.util.RiderNotificationManager.postDirectOfferNotification(context, offerResult.offer)
                    }
                }
                is com.commerceos.rider.model.ActiveOfferResult.None -> {
                    if (activeOffer != null) {
                        com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(context, activeOffer?.offerId)
                    }
                    activeOffer = null
                }
                is com.commerceos.rider.model.ActiveOfferResult.Error -> {
                    // Do not discard active offer on transient network failure
                }
            }

            val notifs = repository.fetchNotifications()
            notificationsList = notifs
            unreadNotifCount = notifs.count { it.readAt == null }

            val fetched = repository.fetchActiveSession()
            if (fetched != null && fetched.state !in listOf("CANCELLED", "DECLINED", "DELIVERED")) {
                session = fetched
                RiderForegroundLocationService.updateDeliverySession(
                    deliveryId = fetched.deliveryId,
                    riderId = fetched.riderId,
                    baseUrl = sessionManager.getBaseUrl(),
                    token = sessionManager.getAuthToken()
                )
            } else if (session != null && (session?.state == "DELIVERED" || showCompletionDialog)) {
                // Keep delivered session for modal
            } else {
                session = null
                RiderForegroundLocationService.clearDeliverySession()
            }
            delay(3000L)
        }
    }

    // Cooldown Ticker
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000L)
            resendCooldown--
        }
    }

    // Foreground Location Service Toggle
    val currentOnlineState = isOnline
    LaunchedEffect(currentOnlineState) {
        val intent = Intent(context, RiderForegroundLocationService::class.java)
        if (currentOnlineState) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        val displayName = liveProfile?.name
                        Text(
                            text = if (displayName != null) "$greeting, $displayName 👋" else if (isOnline) "Welcome 👋" else "Offline",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (isOnline) Color(0xFF10B981) else Color(0xFF64748B),
                                shape = CircleShape,
                                modifier = Modifier.size(7.dp)
                            ) {}
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isShiftUpdating) "Updating…" else if (isOnline) "Online" else "Offline",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOnline) Color(0xFF10B981) else Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                actions = {
                    Switch(
                        checked = isOnline,
                        enabled = !isShiftUpdating,
                        onCheckedChange = { newStatus ->
                            isShiftUpdating = true
                            scope.launch {
                                val res = repository.updateShiftStatus(newStatus)
                                res.onSuccess {
                                    isOnline = newStatus
                                    liveProfile = liveProfile?.copy(shiftStatus = if (newStatus) "ONLINE_AVAILABLE" else "OFFLINE")
                                }.onFailure {
                                    Toast.makeText(context, "Shift status update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                                isShiftUpdating = false
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0F172A)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Orders") },
                    label = { Text("Orders") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Earnings") },
                    label = { Text("Earnings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (unreadNotifCount > 0) {
                                    Badge(containerColor = Color(0xFFEF4444)) {
                                        Text("$unreadNotifCount", color = Color.White, fontSize = 10.sp)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                        }
                    },
                    label = { Text("Alerts") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF10B981),
                        selectedTextColor = Color(0xFF10B981),
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = Color(0xFF1E293B)
                    )
                )
            }
        },
        containerColor = Color(0xFF0B1120)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> {
                    val currentSession = session
                    val currentOffer = activeOffer

                    if (currentSession != null && currentSession.state != "DELIVERED") {
                        ActiveDeliveryScreen(
                            session = currentSession,
                            repository = repository,
                            riderLat = lastLocation?.latitude,
                            riderLng = lastLocation?.longitude,
                            riderHeading = lastLocation?.heading,
                            isStale = isStale,
                            onArrivedStore = {
                                scope.launch {
                                    actionLoading = true
                                    val res = repository.arriveMerchant(currentSession.deliveryId)
                                    res.onSuccess { session = it }
                                    actionLoading = false
                                }
                            },
                            onConfirmPickup = {
                                scope.launch {
                                    actionLoading = true
                                    val res = repository.pickupFromMerchant(currentSession.deliveryId)
                                    res.onSuccess { session = it }
                                    actionLoading = false
                                }
                            },
                            onArrivedCustomer = {
                                scope.launch {
                                    actionLoading = true
                                    val res = repository.arriveCustomer(currentSession.deliveryId)
                                    res.onSuccess { session = it }
                                    actionLoading = false
                                }
                            },
                            onCompleteDelivery = {
                                scope.launch {
                                    actionLoading = true
                                    val res = repository.completeDelivery(currentSession.deliveryId)
                                    res.onSuccess {
                                        session = it
                                        completedSessionsList = listOf(it) + completedSessionsList
                                        showCompletionDialog = true
                                    }
                                    actionLoading = false
                                }
                            },
                            onViewOrderDetails = {
                                showOrderDetailDialog = true
                            },
                            enteredOtp = enteredOtp,
                            onOtpChange = { enteredOtp = it },
                            onVerifyOtp = {
                                scope.launch {
                                    actionLoading = true
                                    val res = repository.verifyOtp(currentSession.deliveryId, enteredOtp)
                                    if (res.isSuccess) {
                                        val compRes = repository.completeDelivery(currentSession.deliveryId)
                                        compRes.onSuccess {
                                            session = it
                                            completedSessionsList = listOf(it) + completedSessionsList
                                            showCompletionDialog = true
                                        }
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message ?: "Incorrect OTP PIN"
                                    }
                                    actionLoading = false
                                }
                            },
                            onResendOtp = {
                                scope.launch {
                                    resendCooldown = 30
                                    repository.resendOtp(currentSession.deliveryId)
                                }
                            },
                            resendCooldown = resendCooldown,
                            enteredCodAmount = enteredCodAmount,
                            onCodAmountChange = { enteredCodAmount = it },
                            onReconcileCod = {
                                scope.launch {
                                    val amount = enteredCodAmount.toDoubleOrNull() ?: 0.0
                                    actionLoading = true
                                    val res = repository.reconcileCod(currentSession.deliveryId, amount)
                                    res.onSuccess {
                                        session = session?.copy(codReconciled = true, codCollectedAmount = amount)
                                    }
                                    actionLoading = false
                                }
                            },
                            onReportIssue = {
                                scope.launch {
                                    repository.reportIssue(currentSession.deliveryId, "DAMAGED_ITEM", "Merchant item issue")
                                }
                            },
                            onCancelDelivery = {
                                showCancelDeliveryDialog = true
                            },
                            isLoading = actionLoading,
                            errorMessage = errorMessage
                        )
                    } else if (currentOffer != null) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            RiderOfferCard(
                                offer = currentOffer,
                                onAccept = { offId ->
                                    scope.launch {
                                        actionLoading = true
                                        com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(context, offId)
                                        val res = repository.acceptOffer(offId)
                                        res.onSuccess {
                                            activeOffer = null
                                            session = it
                                        }.onFailure { err ->
                                            Toast.makeText(context, err.message, Toast.LENGTH_LONG).show()
                                            activeOffer = null
                                        }
                                        actionLoading = false
                                    }
                                },
                                onDecline = { offId ->
                                    scope.launch {
                                        com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(context, offId)
                                        repository.declineOffer(offId)
                                        activeOffer = null
                                    }
                                }
                            )
                        }
                    } else {
                        // Quick-Commerce Rider Home Dashboard
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Hero Earnings Summary Card
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
                                            Text("Today's Earnings", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                                            Text(
                                                text = liveProfile?.earningsTodayFormatted ?: "Loading…",
                                                fontSize = 32.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color(0xFF10B981)
                                            )
                                        }

                                        val currentProfile = liveProfile
                                        if (currentProfile != null) {
                                            Surface(
                                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(
                                                    text = "${currentProfile.completedToday ?: 0} deliveries",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981),
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(12.dp))

                                    val hubProf = liveProfile
                                    if (hubProf != null && !hubProf.assignedHub.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(hubProf.assignedHub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCBD5E1))
                                        }
                                    }
                                }
                            }

                            // Waiting State Container
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            ) {
                                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Surface(
                                            color = Color(0xFF10B981).copy(alpha = 0.15f),
                                            shape = CircleShape,
                                            modifier = Modifier.size(64.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Place,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(32.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (isOnline) "You're Ready" else "You Are Offline",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (isOnline) "Waiting for your next delivery.\nWe'll alert you immediately as soon as a parcel is assigned." else "Go online in the top bar to start receiving delivery offers.",
                                            fontSize = 13.sp,
                                            color = Color(0xFF94A3B8),
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> OrdersHistoryView(
                    activeSession = session,
                    completedSessions = completedSessionsList,
                    onSelectActiveOrder = { selectedTab = 0 }
                )
                2 -> EarningsView(profile = liveProfile)
                3 -> {
                    var selectedNotifCategory by remember { mutableStateOf("ALL") }
                    RiderNotificationCenterScreen(
                        notifications = notificationsList,
                        unreadCount = unreadNotifCount,
                        selectedCategory = selectedNotifCategory,
                        onCategorySelected = { selectedNotifCategory = it },
                        onNotificationClick = { notif ->
                            scope.launch {
                                repository.markNotificationRead(notif.notificationId)
                                val offerRes = repository.fetchActiveOffer()
                                if (offerRes is com.commerceos.rider.model.ActiveOfferResult.Success) {
                                    activeOffer = offerRes.offer
                                    selectedTab = 0
                                } else if (!notif.offerId.isNullOrBlank() || notif.type == "ORDER_OFFER" || notif.category == "ORDERS") {
                                    selectedTab = 0
                                }
                            }
                        },
                        onMarkAllRead = { scope.launch { repository.markAllNotificationsRead() } },
                        onBack = { selectedTab = 0 }
                    )
                }
                4 -> RiderProfileView(
                    profile = liveProfile,
                    isOnline = isOnline,
                    onToggleShift = { newStatus ->
                        isOnline = newStatus
                        scope.launch { repository.updateShiftStatus(newStatus) }
                    },
                    onLogout = {
                        scope.launch {
                            repository.logoutDeviceToken()
                            sessionManager.clearSession()
                            isOnline = false
                            onLogout()
                        }
                    }
                )
            }
        }
    }

    // Modals
    if (showOrderDetailDialog && session != null) {
        ActiveDeliveryDetailDialog(
            session = session!!,
            onDismiss = { showOrderDetailDialog = false }
        )
    }

    if (showCompletionDialog && session != null) {
        DeliveryCompletionDialog(
            session = session!!,
            onDismiss = {
                showCompletionDialog = false
                session = null
                enteredOtp = ""
                enteredCodAmount = ""
            }
        )
    }

    if (showCancelDeliveryDialog && session != null) {
        val currentSession = session!!
        RiderCancelDeliveryDialog(
            deliveryId = currentSession.deliveryId,
            onConfirmCancel = { reason, note ->
                scope.launch {
                    actionLoading = true
                    val res = repository.cancelDelivery(currentSession.deliveryId, reason, note)
                    res.onSuccess {
                        showCancelDeliveryDialog = false
                        session = null
                        activeOffer = null
                        RiderForegroundLocationService.clearDeliverySession()
                        Toast.makeText(context, "Delivery cancelled", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, err.message ?: "Cancellation failed", Toast.LENGTH_LONG).show()
                    }
                    actionLoading = false
                }
            },
            onDismiss = { showCancelDeliveryDialog = false }
        )
    }
}
