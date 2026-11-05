package com.commerceos.rider.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
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
import com.commerceos.rider.theme.RiderColors
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderMainScreen(
    profile: RiderProfile? = null,
    onLogout: () -> Unit = {},
    intentOfferId: String? = null,
    onClearIntentOfferId: () -> Unit = {}
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
    var isOnline by remember { mutableStateOf(sessionManager.isShiftOnline()) }
    var session by remember { mutableStateOf<ServerDeliverySession?>(null) }
    var completedSessionsList by remember { mutableStateOf<List<ServerDeliverySession>>(emptyList()) }
    var activeOffer by remember { mutableStateOf<ServerOffer?>(null) }
    var notificationsList by remember { mutableStateOf<List<RiderNotificationItem>>(emptyList()) }
    var unreadNotifCount by remember { mutableIntStateOf(0) }
    var showOrderDetailDialog by remember { mutableStateOf(false) }
    var selectedOrderForDetail by remember { mutableStateOf<ServerDeliverySession?>(null) }
    var selectedOfferForDetail by remember { mutableStateOf<ServerOffer?>(null) }
    var showCompletionDialog by remember { mutableStateOf(false) }
    var showCancelDeliveryDialog by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var jobAlreadyAssignedDialogMessage by remember { mutableStateOf<String?>(null) }
    var actionLoading by remember { mutableStateOf(false) }

    // Direct Notification Click Deep-Link: Navigate to Order on Home Page if active, otherwise route to Alerts
    LaunchedEffect(intentOfferId) {
        val targetId = intentOfferId ?: sessionManager.getPendingOfferId()
        if (!targetId.isNullOrBlank()) {
            scope.launch {
                sessionManager.savePendingOfferId(null)
                onClearIntentOfferId()

                // 1. Immediately cancel the notification in the status bar for this offer
                com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(context, targetId)

                // 2. Fetch the target offer or check if it's currently available
                var foundOffer: ServerOffer? = null
                val offerRes = repository.fetchOfferById(targetId)
                if (offerRes is com.commerceos.rider.model.ActiveOfferResult.Success &&
                    offerRes.offer.status in listOf("CREATED", "OFFERED", "DISPATCHED", "NOTIFIED", "DISPLAYED")) {
                    foundOffer = offerRes.offer
                }

                // Fallback check against general active offer
                if (foundOffer == null) {
                    val activeRes = repository.fetchActiveOffer()
                    if (activeRes is com.commerceos.rider.model.ActiveOfferResult.Success &&
                        (activeRes.offer.offerId == targetId || activeRes.offer.orderId == targetId)) {
                        foundOffer = activeRes.offer
                    }
                }

                if (foundOffer != null) {
                    // Job is currently on homepage! Reflect it there.
                    activeOffer = foundOffer
                    selectedTab = 0
                    selectedOfferForDetail = null
                    selectedOrderForDetail = null
                } else {
                    // Job is no longer available on homepage -> Put in Alerts!
                    selectedTab = 2
                    Toast.makeText(context, "This job is no longer available. Showing Alerts.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    var enteredOtp by remember { mutableStateOf("") }
    var enteredCodAmount by remember { mutableStateOf("") }
    var resendCooldown by remember { mutableIntStateOf(0) }

    val lastLocation by RiderForegroundLocationService.lastLocation.collectAsState()
    val isStale by RiderForegroundLocationService.isStale.collectAsState()

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "GOOD MORNING"
            in 12..16 -> "GOOD AFTERNOON"
            else -> "GOOD EVENING"
        }
    }

    var isShiftUpdating by remember { mutableStateOf(false) }

    fun executeShiftToggle(nextStatus: Boolean) {
        if (isShiftUpdating) return
        if (nextStatus) {
            val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!fine && !coarse) {
                Toast.makeText(context, "Location permission is required to go online.", Toast.LENGTH_LONG).show()
                return
            }
        }
        isShiftUpdating = true
        scope.launch {
            try {
                val res = repository.updateShiftStatus(nextStatus)
                res.onSuccess {
                    isOnline = nextStatus
                    sessionManager.saveShiftOnline(nextStatus)
                    val authoritativeStoreId = liveProfile?.storeId ?: sessionManager.getStoreId()
                    sessionManager.reconcileStoreTopic(authoritativeStoreId, nextStatus)
                    if (nextStatus) {
                        try {
                            val svcIntent = Intent(context, RiderForegroundLocationService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(svcIntent)
                            } else {
                                context.startService(svcIntent)
                            }
                        } catch (_: Exception) {}
                    }
                    liveProfile = liveProfile?.copy(shiftStatus = if (nextStatus) "ONLINE_AVAILABLE" else "OFFLINE")
                }.onFailure {
                    Toast.makeText(context, "Shift status update failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error updating shift: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isShiftUpdating = false
            }
        }
    }

    // 1. Fetch Authoritative Rider Profile & Register Cached FCM Token
    LaunchedEffect(Unit) {
        if (sessionManager.getAuthToken().isBlank()) {
            onLogout()
            return@LaunchedEffect
        }
        val res = repository.fetchRiderProfile()
        val savedDuty = sessionManager.isShiftOnline()
        res.onSuccess {
            liveProfile = it
            sessionManager.reconcileStoreTopic(it.storeId, savedDuty)
            isOnline = savedDuty
        }.onFailure { err ->
            android.util.Log.e("RiderMainScreen", "fetchRiderProfile failed: ${err.message}", err)
            if (err.message?.contains("401") == true || err.message?.contains("UNAUTHORIZED") == true) {
                // If token is rejected, try refresh or log out cleanly
                val refreshed = sessionManager.refreshAccessToken()
                if (refreshed) {
                    val retry = repository.fetchRiderProfile()
                    retry.onSuccess {
                        liveProfile = it
                        sessionManager.reconcileStoreTopic(it.storeId, savedDuty)
                        isOnline = savedDuty
                    }.onFailure {
                        sessionManager.coordinateLogout(context)
                        onLogout()
                    }
                } else {
                    sessionManager.coordinateLogout(context)
                    onLogout()
                }
            } else if (liveProfile == null) {
                // Local profile fallback for UI continuity so screen is never blank
                val savedRiderId = sessionManager.getRiderId().ifBlank { "rdr_partner" }
                liveProfile = RiderProfile(
                    riderId = savedRiderId,
                    name = "Delivery Partner",
                    phone = "",
                    vehicleNumber = "Electric Scooter",
                    rating = 4.9,
                    completedToday = 0,
                    earningsTodayFormatted = "₹0",
                    shiftStatus = if (savedDuty) "ONLINE_AVAILABLE" else "OFFLINE",
                    assignedHub = "Koramangala Store"
                )
                isOnline = savedDuty
            }
        }

        // Register any pending cached FCM token
        val cachedFcmToken = sessionManager.getCachedDeviceToken()
        if (cachedFcmToken.isNotBlank()) {
            repository.registerDeviceToken(cachedFcmToken)
        }

        // Initial sync of shift status so dispatch engine aligns with rider preference
        if (sessionManager.getAuthToken().isNotBlank()) {
            repository.updateShiftStatus(savedDuty)
        }

        // Initial Trips / Order History Fetch
        val initialTrips = repository.fetchTrips()
        if (initialTrips.isNotEmpty()) {
            completedSessionsList = initialTrips.filter { it.state in listOf("DELIVERED", "COMPLETED", "CANCELLED") }
        }
    }

    // Refresh Trips History whenever Orders tab is opened
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            val trips = repository.fetchTrips()
            if (trips.isNotEmpty()) {
                completedSessionsList = trips.filter { it.state in listOf("DELIVERED", "COMPLETED", "CANCELLED") }
            }
        }
    }

    // 2. Realtime Incoming Offer Pipeline Stream (FCM & SSE Ingestion)
    LaunchedEffect(Unit) {
        com.commerceos.rider.util.RiderOfferEventPipeline.incomingOfferEvents.collect { offer ->
            activeOffer = offer
            com.commerceos.rider.util.RiderAlertNotifier.playNewJobAlert(context, offer.offerId)
        }
    }

    // 3. Ultra-Fast Concurrent Reconciliation Loop (3s active polling)
    LaunchedEffect(isOnline) {
        var profilePollCounter = 0
        while (isOnline) {
            if (sessionManager.getAuthToken().isBlank()) {
                break
            }

            coroutineScope {
                val offerDeferred = async { repository.fetchActiveOffer() }
                val notifsDeferred = async { repository.fetchNotifications() }
                val sessionDeferred = async { repository.fetchActiveSession() }
                val profileDeferred = if (profilePollCounter % 5 == 0) {
                    async { repository.fetchRiderProfile() }
                } else null

                profilePollCounter++

                // 1. Process active delivery session concurrently
                try {
                    val fetched = sessionDeferred.await()
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
                } catch (_: Exception) {}

                // 2. Process active job offer concurrently
                try {
                    when (val offerResult = offerDeferred.await()) {
                        is com.commerceos.rider.model.ActiveOfferResult.Success -> {
                            val prevId = activeOffer?.offerId
                            activeOffer = offerResult.offer
                            // Do NOT post a system tray notification while rider is actively inside the app on RiderMainScreen.
                            // Only play subtle audio alert if it's a genuine new offer that was not just opened from a notification.
                            if (prevId != null && prevId != offerResult.offer.offerId && isOnline) {
                                com.commerceos.rider.util.RiderAlertNotifier.playNewJobAlert(context, offerResult.offer.offerId)
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
                } catch (_: Exception) {}

                // 3. Process notifications concurrently (merging remote SSE/REST + local on-device store)
                try {
                    val notifs = notifsDeferred.await()
                    val localNotifs = com.commerceos.rider.util.RiderLocalNotificationStore.getStoredNotifications(context)
                    val merged = (notifs + localNotifs).distinctBy { it.notificationId.ifBlank { it.offerId ?: it.orderId ?: "" } }
                    notificationsList = merged
                    unreadNotifCount = merged.count { it.readAt == null }
                } catch (_: Exception) {}

                // 4. Process periodic profile & earnings refresh
                try {
                    val profRes = profileDeferred?.await()
                    if (profRes != null && profRes.isSuccess) {
                        profRes.getOrNull()?.let { p ->
                            liveProfile = p
                            sessionManager.reconcileStoreTopic(p.storeId, isOnline)
                        }
                    }
                } catch (_: Exception) {}
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

    // Foreground Location Service Toggle: remain tracking while online OR actively delivering
    val hasActiveDelivery = session != null && session?.deliveryId.isNullOrBlank().not() && session?.state !in listOf("CANCELLED", "DECLINED", "DELIVERED")
    val shouldTrack = isOnline || hasActiveDelivery
    LaunchedEffect(shouldTrack) {
        val intent = Intent(context, RiderForegroundLocationService::class.java)
        if (shouldTrack) {
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
                    when (selectedTab) {
                        0 -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(if (isOnline) RiderColors.PrimarySoft else RiderColors.SurfaceSubtle, CircleShape)
                                        .border(1.5.dp, if (isOnline) RiderColors.Primary else RiderColors.Border, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isOnline) RiderColors.Primary else RiderColors.TextMuted,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    val displayName = liveProfile?.name ?: "Partner 6180"
                                    Text(
                                        text = "$greeting, $displayName",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = RiderColors.TextPrimary,
                                        maxLines = 1
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = if (isOnline) RiderColors.Primary else RiderColors.TextMuted,
                                            shape = CircleShape,
                                            modifier = Modifier.size(6.dp)
                                        ) {}
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isOnline) "On Duty • Rewari Hub" else "Off Duty • Shift Paused",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isOnline) RiderColors.Primary else RiderColors.TextMuted
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            Column {
                                Text(
                                    text = "Trip History",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.TextPrimary
                                )
                                Text(
                                    text = "${completedSessionsList.size} deliveries completed today",
                                    fontSize = 11.sp,
                                    color = RiderColors.TextMuted
                                )
                            }
                        }
                        2 -> {
                            Column {
                                Text(
                                    text = "Dispatch Alerts",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.TextPrimary
                                )
                                Text(
                                    text = if (unreadNotifCount > 0) "$unreadNotifCount unread notification(s)" else "All caught up with dispatches",
                                    fontSize = 11.sp,
                                    color = RiderColors.TextMuted
                                )
                            }
                        }
                        else -> {
                            Column {
                                Text(
                                    text = "Captain Profile",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RiderColors.TextPrimary
                                )
                                Text(
                                    text = "CommerceOS Partner Fleet",
                                    fontSize = 11.sp,
                                    color = RiderColors.TextMuted
                                )
                            }
                        }
                    }
                },
                actions = {
                    when (selectedTab) {
                        0, 3 -> {
                            // Tactile Duty Pill Switch
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isOnline) RiderColors.PrimarySoft else RiderColors.SurfaceSubtle,
                                border = BorderStroke(1.dp, if (isOnline) RiderColors.Primary.copy(alpha = 0.5f) else RiderColors.Border),
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clickable(enabled = !isShiftUpdating) {
                                        executeShiftToggle(!isOnline)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Surface(
                                        color = if (isOnline) RiderColors.Primary else RiderColors.TextMuted,
                                        shape = CircleShape,
                                        modifier = Modifier.size(7.dp)
                                    ) {}
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isShiftUpdating) "Updating…" else if (isOnline) "ON DUTY" else "OFF DUTY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                        color = if (isOnline) RiderColors.PrimarySoftText else RiderColors.TextSecondary
                                    )
                                }
                            }
                        }
                        1 -> {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val trips = repository.fetchTrips()
                                        if (trips.isNotEmpty()) {
                                            completedSessionsList = trips.filter { it.state in listOf("DELIVERED", "COMPLETED", "CANCELLED") }
                                        }
                                        Toast.makeText(context, "Trips refreshed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = RiderColors.Primary)
                            }
                        }
                        2 -> {
                            if (unreadNotifCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = RiderColors.PrimarySoft,
                                    border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .clickable {
                                            notificationsList = notificationsList.map { it.copy(readAt = "read") }
                                            unreadNotifCount = 0
                                            scope.launch { repository.markAllNotificationsRead() }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = RiderColors.Primary, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Mark read", color = RiderColors.PrimarySoftText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RiderColors.Surface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = RiderColors.Surface,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RiderColors.Primary,
                        selectedTextColor = RiderColors.Primary,
                        unselectedIconColor = RiderColors.TextMuted,
                        unselectedTextColor = RiderColors.TextMuted,
                        indicatorColor = RiderColors.PrimarySoft
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Orders") },
                    label = { Text("Orders", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RiderColors.Primary,
                        selectedTextColor = RiderColors.Primary,
                        unselectedIconColor = RiderColors.TextMuted,
                        unselectedTextColor = RiderColors.TextMuted,
                        indicatorColor = RiderColors.PrimarySoft
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (unreadNotifCount > 0) {
                                    Badge(containerColor = RiderColors.Danger) {
                                        Text("$unreadNotifCount", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                        }
                    },
                    label = { Text("Alerts", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RiderColors.Primary,
                        selectedTextColor = RiderColors.Primary,
                        unselectedIconColor = RiderColors.TextMuted,
                        unselectedTextColor = RiderColors.TextMuted,
                        indicatorColor = RiderColors.PrimarySoft
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontWeight = if (selectedTab == 3) FontWeight.Bold else FontWeight.Medium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RiderColors.Primary,
                        selectedTextColor = RiderColors.Primary,
                        unselectedIconColor = RiderColors.TextMuted,
                        unselectedTextColor = RiderColors.TextMuted,
                        indicatorColor = RiderColors.PrimarySoft
                    )
                )
            }
        },
        containerColor = RiderColors.Background
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            var stickyLat by remember { mutableStateOf<Double?>(null) }
            var stickyLng by remember { mutableStateOf<Double?>(null) }
            var stickyHeading by remember { mutableStateOf<Float?>(null) }

            if (lastLocation != null) {
                stickyLat = lastLocation?.latitude
                stickyLng = lastLocation?.longitude
                stickyHeading = lastLocation?.heading
            }

            when (selectedTab) {
                0 -> {
                    val currentSession = session
                    val currentOffer = activeOffer

                    if (currentSession != null && currentSession.state != "DELIVERED") {
                        val effectiveRiderLat = stickyLat
                            ?: currentSession.telemetry?.latitude
                            ?: when (currentSession.state) {
                                in listOf("ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_AT_STORE", "PICKED_UP") -> currentSession.merchantLat
                                in listOf("ARRIVED_CUSTOMER", "HANDOFF_STARTED", "DELIVERED") -> currentSession.customerLat
                                else -> null
                            }
                        val effectiveRiderLng = stickyLng
                            ?: currentSession.telemetry?.longitude
                            ?: when (currentSession.state) {
                                in listOf("ARRIVED_PICKUP", "ARRIVED_STORE", "ARRIVED_AT_STORE", "PICKED_UP") -> currentSession.merchantLng
                                in listOf("ARRIVED_CUSTOMER", "HANDOFF_STARTED", "DELIVERED") -> currentSession.customerLng
                                else -> null
                            }
                        val effectiveRiderHeading = stickyHeading ?: currentSession.telemetry?.heading

                        ActiveDeliveryScreen(
                            session = currentSession,
                            repository = repository,
                            riderLat = effectiveRiderLat,
                            riderLng = effectiveRiderLng,
                            riderHeading = effectiveRiderHeading,
                            isStale = isStale,
                            onArrivedStore = {
                                scope.launch {
                                    actionLoading = true
                                    errorMessage = null
                                    val res = repository.arriveMerchant(currentSession.deliveryId)
                                    res.onSuccess {
                                        session = it
                                    }.onFailure {
                                        val msg = it.message ?: "Failed to record arrival at store"
                                        errorMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                    actionLoading = false
                                }
                            },
                            onConfirmPickup = {
                                scope.launch {
                                    actionLoading = true
                                    errorMessage = null
                                    val res = repository.pickupFromMerchant(currentSession.deliveryId)
                                    res.onSuccess {
                                        session = it
                                    }.onFailure {
                                        val msg = it.message ?: "Failed to confirm pickup"
                                        errorMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                    actionLoading = false
                                }
                            },
                            onArrivedCustomer = {
                                scope.launch {
                                    actionLoading = true
                                    errorMessage = null
                                    val res = repository.arriveCustomer(currentSession.deliveryId)
                                    res.onSuccess {
                                        session = it
                                    }.onFailure {
                                        val msg = it.message ?: "Failed to record arrival at customer"
                                        errorMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                    actionLoading = false
                                }
                            },
                            onCompleteDelivery = {
                                if (!currentSession.otpVerified) {
                                    Toast.makeText(context, "Customer delivery PIN verification is required", Toast.LENGTH_LONG).show()
                                    return@ActiveDeliveryScreen
                                }
                                scope.launch {
                                    actionLoading = true
                                    errorMessage = null
                                    val res = repository.completeDelivery(currentSession.deliveryId)
                                    res.onSuccess {
                                        session = it
                                        completedSessionsList = listOf(it) + completedSessionsList
                                        showCompletionDialog = true
                                    }.onFailure {
                                        val msg = it.message ?: "Failed to complete delivery"
                                        errorMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                    actionLoading = false
                                }
                            },
                            onViewOrderDetails = {
                                selectedOrderForDetail = currentSession
                            },
                            enteredOtp = enteredOtp,
                            onOtpChange = { enteredOtp = it },
                            onVerifyOtp = {
                                if (enteredOtp.isBlank() || enteredOtp.trim().length < 4) {
                                    Toast.makeText(context, "Please enter 4-6 digit customer delivery PIN", Toast.LENGTH_SHORT).show()
                                    return@ActiveDeliveryScreen
                                }
                                scope.launch {
                                    actionLoading = true
                                    errorMessage = null
                                    val codAmt = enteredCodAmount.toDoubleOrNull() ?: currentSession.codAmount ?: currentSession.orderTotal
                                    val res = repository.verifyOtp(
                                        currentSession.deliveryId,
                                        enteredOtp.trim(),
                                        codCollected = true,
                                        collectedAmount = codAmt
                                    )
                                    if (res.isSuccess) {
                                        val compRes = repository.completeDelivery(currentSession.deliveryId)
                                        compRes.onSuccess {
                                            session = it
                                            completedSessionsList = listOf(it) + completedSessionsList
                                            showCompletionDialog = true
                                        }.onFailure {
                                            val msg = it.message ?: "Failed to complete delivery"
                                            errorMessage = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        }
                                    } else {
                                        val err = res.exceptionOrNull()?.message ?: "Incorrect Delivery PIN"
                                        errorMessage = err
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
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
                                    val amount = enteredCodAmount.toDoubleOrNull() ?: currentSession.codAmount ?: currentSession.orderTotal ?: 0.0
                                    actionLoading = true
                                    errorMessage = null
                                    val res = repository.reconcileCod(currentSession.deliveryId, amount)
                                    res.onSuccess {
                                        session = session?.copy(codReconciled = true, codCollectedAmount = amount)
                                    }.onFailure {
                                        val msg = it.message ?: "Failed to reconcile COD"
                                        errorMessage = msg
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
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
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            RiderOfferCard(
                                offer = currentOffer,
                                riderLat = stickyLat ?: lastLocation?.latitude,
                                riderLng = stickyLng ?: lastLocation?.longitude,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 620.dp),
                                onAccept = { offId ->
                                    scope.launch {
                                        actionLoading = true
                                        com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(context, offId)
                                        val res = repository.acceptOffer(offId, activeOffer)
                                        res.onSuccess {
                                            activeOffer = null
                                            session = it
                                        }.onFailure { err ->
                                            val msg = err.message ?: "Failed to accept job"
                                            if (msg.contains("already", ignoreCase = true) || msg.contains("CLAIMED", ignoreCase = true) || msg.contains("409") || msg.contains("taken", ignoreCase = true)) {
                                                jobAlreadyAssignedDialogMessage = "This delivery job has already been accepted by another rider."
                                            } else {
                                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                            }
                                            activeOffer = null
                                            notificationsList = notificationsList.map {
                                                if (it.offerId == offId) it.copy(isAvailable = false, offerStatus = "CLAIMED_BY_OTHER") else it
                                            }
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
                        // Quick-Commerce Rider Home Dashboard (Swiggy / Zomato / Blinkit Style)
                        val infiniteTransition = rememberInfiniteTransition(label = "RadarSonar")
                        val radarScale1 by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 2.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "RadarScale1"
                        )
                        val radarAlpha1 by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "RadarAlpha1"
                        )
                        val radarScale2 by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 2.1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2200, delayMillis = 800, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "RadarScale2"
                        )
                        val radarAlpha2 by infiniteTransition.animateFloat(
                            initialValue = 0.5f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2200, delayMillis = 800, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "RadarAlpha2"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 1. Shift & Hub Priority Banner
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                                border = BorderStroke(1.dp, RiderColors.Border),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Surface(
                                            color = if (isOnline) RiderColors.PrimarySoft else RiderColors.SurfaceSubtle,
                                            shape = CircleShape,
                                            modifier = Modifier.size(46.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Place,
                                                    contentDescription = null,
                                                    tint = if (isOnline) RiderColors.Primary else RiderColors.TextMuted,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            val assignedHub = liveProfile?.assignedHub?.ifBlank { "Rewari Central Hub" } ?: "Rewari Central Hub"
                                            val cleanHubDisplay = if (assignedHub.contains("(")) assignedHub.substringBefore("(").trim() else assignedHub
                                            val cleanStoreId = if (assignedHub.contains("(")) assignedHub.substringAfter("(").substringBefore(")") else "STORE_REWARI_01"
                                            Text(
                                                text = cleanHubDisplay,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = RiderColors.TextPrimary
                                            )
                                            Text(
                                                text = if (isOnline) "🟢 $cleanStoreId • Priority Area" else "⚪ Offline • Shift Paused",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isOnline) RiderColors.Primary else RiderColors.TextMuted
                                            )
                                        }
                                    }

                                    Surface(
                                        color = RiderColors.PrimarySoft,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, RiderColors.Primary.copy(alpha = 0.3f))
                                    ) {
                                        Text(
                                            text = "⚡ 10-Min SLA",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RiderColors.PrimarySoftText,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                            }

                            // 2. Today's Performance Cockpit (Order Value & Deliveries)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Today's Order Value Fulfilled Tile
                                val todayOrderValSum = completedSessionsList.sumOf {
                                    it.orderTotal ?: it.items.sumOf { item -> item.price * item.quantity }.takeIf { it > 0.0 } ?: 32.0
                                }
                                val orderValueText = if (todayOrderValSum > 0.0) "₹${todayOrderValSum.toInt()}" else "₹0"

                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                                    border = BorderStroke(1.dp, RiderColors.Border),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "TODAY'S ORDER VALUE",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RiderColors.TextMuted,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = orderValueText,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = RiderColors.Primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Fulfilled Store Volume",
                                            fontSize = 11.sp,
                                            color = RiderColors.TextLight
                                        )
                                    }
                                }

                                // Today's Trips Tile
                                Card(
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                                    border = BorderStroke(1.dp, RiderColors.Border),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "DELIVERIES",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RiderColors.TextMuted,
                                            letterSpacing = 0.5.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        val tripCount = liveProfile?.completedToday ?: completedSessionsList.size
                                        Text(
                                            text = "$tripCount",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black,
                                            color = RiderColors.TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Orders fulfilled",
                                            fontSize = 11.sp,
                                            color = RiderColors.TextLight
                                        )
                                    }
                                }
                            }

                            // 3. Hero Animated Dispatch Radar Container
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
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Animated Sonar Wave Circle
                                    Box(
                                        modifier = Modifier.size(110.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isOnline) {
                                            // Radar Pulse 1
                                            Box(
                                                modifier = Modifier
                                                    .size(76.dp)
                                                    .scale(radarScale1)
                                                    .background(RiderColors.Primary.copy(alpha = radarAlpha1), CircleShape)
                                            )
                                            // Radar Pulse 2
                                            Box(
                                                modifier = Modifier
                                                    .size(76.dp)
                                                    .scale(radarScale2)
                                                    .background(RiderColors.Primary.copy(alpha = radarAlpha2), CircleShape)
                                            )
                                        }

                                        // Central Core
                                        Surface(
                                            color = if (isOnline) RiderColors.DarkHeaderBg else RiderColors.SurfaceSubtle,
                                            shape = CircleShape,
                                            border = BorderStroke(2.dp, if (isOnline) RiderColors.Primary else RiderColors.Border),
                                            modifier = Modifier.size(74.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Place,
                                                    contentDescription = null,
                                                    tint = if (isOnline) RiderColors.Primary else RiderColors.TextMuted,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = if (isOnline) "Scanning Express Dispatches" else "You Are Offline",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = RiderColors.TextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = if (isOnline) "Connected to Central Dispatch Engine near Rewari Central Hub.\nKeep your app open to receive instant order dispatches." else "Turn on duty to start receiving assigned dispatches for Rewari Central Hub.",
                                            fontSize = 13.sp,
                                            color = RiderColors.TextSecondary,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                            lineHeight = 19.sp
                                        )
                                    }

                                    if (!isOnline) {
                                        Button(
                                            onClick = { executeShiftToggle(true) },
                                            enabled = !isShiftUpdating,
                                            colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth().height(48.dp)
                                        ) {
                                            Text(if (isShiftUpdating) "UPDATING..." else "GO ON DUTY", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                        }
                                    }

                                    HorizontalDivider(color = RiderColors.Divider, thickness = 1.dp)

                                    // Realtime Status Chips Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                color = if (isOnline) RiderColors.Primary else RiderColors.TextMuted,
                                                shape = CircleShape,
                                                modifier = Modifier.size(8.dp)
                                            ) {}
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isOnline) "GPS Active" else "GPS Idle",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = RiderColors.TextSecondary
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = RiderColors.Primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Express Fleet",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = RiderColors.TextSecondary
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Place,
                                                contentDescription = null,
                                                tint = RiderColors.AccentBlue,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Rewari Hub",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = RiderColors.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Quick Shortcut to Order History
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = RiderColors.Surface),
                                border = BorderStroke(1.dp, RiderColors.Border),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                onClick = { selectedTab = 1 },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            color = RiderColors.PrimarySoft,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.ShoppingCart,
                                                    contentDescription = null,
                                                    tint = RiderColors.Primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Trip & Order History",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = RiderColors.TextPrimary
                                            )
                                            Text(
                                                text = "View all past completed deliveries and billings",
                                                fontSize = 11.sp,
                                                color = RiderColors.TextMuted
                                            )
                                        }
                                    }

                                    Icon(
                                        Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = RiderColors.TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> OrdersHistoryView(
                    activeSession = session,
                    completedSessions = completedSessionsList,
                    onSelectActiveOrder = { selectedTab = 0 },
                    onViewOrderDetail = { orderSession ->
                        selectedOrderForDetail = orderSession
                    }
                )
                2 -> {
                    var selectedNotifCategory by remember { mutableStateOf("ALL") }
                    RiderNotificationCenterScreen(
                        notifications = notificationsList,
                        unreadCount = unreadNotifCount,
                        selectedCategory = selectedNotifCategory,
                        onCategorySelected = { selectedNotifCategory = it },
                        onNotificationClick = { notif ->
                            android.util.Log.e("RiderMainScreen", ">>> onNotificationClick clicked: id=${notif.notificationId}, title=${notif.title}, orderId=${notif.orderId}, offerId=${notif.offerId}")
                            scope.launch {
                                repository.markNotificationRead(notif.notificationId)
                                notificationsList = notificationsList.map {
                                    if (it.notificationId == notif.notificationId) it.copy(readAt = "read") else it
                                }
                                unreadNotifCount = notificationsList.count { it.readAt == null }

                                // 0. Check if strictly claimed by another rider
                                if (notif.offerStatus == "CLAIMED_BY_OTHER") {
                                    jobAlreadyAssignedDialogMessage = "This delivery job has already been taken by another rider."
                                    return@launch
                                }

                                // 1. Check if active/historical order delivery session -> show ActiveDeliveryDetailDialog pop-up
                                val notifOrderId = notif.orderId ?: notif.deliveryId
                                if (!notifOrderId.isNullOrBlank()) {
                                    if (session?.orderId == notifOrderId || session?.deliveryId == notifOrderId) {
                                        selectedOrderForDetail = session
                                        return@launch
                                    }

                                    val matchingCompleted = completedSessionsList.find { it.orderId == notifOrderId || it.deliveryId == notifOrderId }
                                    if (matchingCompleted != null) {
                                        selectedOrderForDetail = matchingCompleted
                                        return@launch
                                    }

                                    val fetched = repository.fetchSession(notifOrderId)
                                    if (fetched != null) {
                                        selectedOrderForDetail = fetched
                                        return@launch
                                    }
                                }

                                // 2. Check if active offer notification -> navigate directly to Home page Order Acceptance Card
                                val targetOfferId = notif.offerId
                                if (!targetOfferId.isNullOrBlank()) {
                                    if (activeOffer?.offerId == targetOfferId) {
                                        selectedOfferForDetail = null
                                        selectedOrderForDetail = null
                                        selectedTab = 0
                                        Toast.makeText(context, "Review & accept order offer", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    val offerRes = repository.fetchOfferById(targetOfferId)
                                    if (offerRes is com.commerceos.rider.model.ActiveOfferResult.Success) {
                                        if (offerRes.offer.status in listOf("CLAIMED_BY_OTHER")) {
                                            jobAlreadyAssignedDialogMessage = "This delivery job has already been taken by another rider."
                                            notificationsList = notificationsList.map {
                                                if (it.notificationId == notif.notificationId) it.copy(isAvailable = false, offerStatus = "CLAIMED_BY_OTHER") else it
                                            }
                                            return@launch
                                        }
                                        activeOffer = offerRes.offer
                                        selectedOfferForDetail = null
                                        selectedOrderForDetail = null
                                        selectedTab = 0
                                        Toast.makeText(context, "Review & accept order offer", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                }

                                // 3. Fallback check for order/job notification: display authoritative trip if found, otherwise alert that details are unavailable
                                if (notif.category == "ORDER" || notif.category == "ORDERS" || notif.type.contains("ORDER", ignoreCase = true) || !notif.orderId.isNullOrBlank() || !notif.deliveryId.isNullOrBlank()) {
                                    val orderIdFromTitle = Regex("""#([A-Za-z0-9_-]+)""").find(notif.title)?.groupValues?.get(1)
                                    val cleanOrderId = notif.orderId ?: notif.deliveryId ?: orderIdFromTitle?.let { "ord_$it" } ?: ""

                                    // Check if existing session in trips matches cleanOrderId or orderIdFromTitle
                                    val existingSession = completedSessionsList.find { it.orderId.contains(cleanOrderId, ignoreCase = true) || (orderIdFromTitle != null && it.orderId.contains(orderIdFromTitle, ignoreCase = true)) }
                                        ?: (if (cleanOrderId.isNotBlank() && session?.orderId?.contains(cleanOrderId, ignoreCase = true) == true) session else null)

                                    if (existingSession != null) {
                                        selectedOrderForDetail = existingSession
                                        return@launch
                                    }

                                    Toast.makeText(context, "Order details currently unavailable. Please check your network connection.", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }

                                Toast.makeText(context, notif.title, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDismissNotification = { notifItem ->
                            notificationsList = notificationsList.filter { it.notificationId != notifItem.notificationId }
                            unreadNotifCount = notificationsList.count { it.readAt == null }
                        },
                        onMarkAllRead = {
                            notificationsList = notificationsList.map { it.copy(readAt = "read") }
                            unreadNotifCount = 0
                            scope.launch { repository.markAllNotificationsRead() }
                        },
                        onBack = { selectedTab = 0 }
                    )
                }
                3 -> RiderProfileView(
                    profile = liveProfile,
                    isOnline = isOnline,
                    isShiftUpdating = isShiftUpdating,
                    onToggleShift = { newStatus ->
                        executeShiftToggle(newStatus)
                    },
                    onLogout = {
                        scope.launch {
                            try { repository.logoutDeviceToken() } catch (_: Exception) {}
                            sessionManager.coordinateLogout(context)
                            isOnline = false
                            onLogout()
                        }
                    }
                )
            }
        }
    }

    // Modals
    if (selectedOfferForDetail != null) {
        val currentSelectedOffer = selectedOfferForDetail!!
        RiderOrderDetailDialog(
            offer = currentSelectedOffer,
            onAccept = {
                val off = currentSelectedOffer
                selectedOfferForDetail = null
                scope.launch {
                    actionLoading = true
                    com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(context, off.offerId)
                    val res = repository.acceptOffer(off.offerId, off)
                    res.onSuccess {
                        activeOffer = null
                        session = it
                        selectedTab = 0
                    }.onFailure { err ->
                        val msg = err.message ?: "Failed to accept job"
                        if (msg.contains("already", ignoreCase = true) || msg.contains("CLAIMED", ignoreCase = true) || msg.contains("409") || msg.contains("taken", ignoreCase = true)) {
                            jobAlreadyAssignedDialogMessage = "This delivery job has already been accepted by another rider."
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                        activeOffer = null
                        notificationsList = notificationsList.map {
                            if (it.offerId == off.offerId) it.copy(isAvailable = false, offerStatus = "CLAIMED_BY_OTHER") else it
                        }
                    }
                    actionLoading = false
                }
            },
            onDecline = {
                val off = currentSelectedOffer
                selectedOfferForDetail = null
                scope.launch {
                    com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(context, off.offerId)
                    repository.declineOffer(off.offerId)
                    if (activeOffer?.offerId == off.offerId) {
                        activeOffer = null
                    }
                }
            },
            onDismiss = { selectedOfferForDetail = null }
        )
    }

    if (selectedOrderForDetail != null) {
        android.util.Log.e("RiderMainScreen", ">>> Showing ActiveDeliveryDetailDialog for ${selectedOrderForDetail?.orderId}")
        ActiveDeliveryDetailDialog(
            session = selectedOrderForDetail!!,
            onDismiss = { selectedOrderForDetail = null }
        )
    } else if (showOrderDetailDialog && session != null) {
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
                    showCancelDeliveryDialog = false
                    val res = repository.cancelDelivery(currentSession.deliveryId, reason, note)
                    res.onSuccess {
                        session = null
                        activeOffer = null
                        RiderForegroundLocationService.clearDeliverySession(currentSession.deliveryId)
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

    if (jobAlreadyAssignedDialogMessage != null) {
        AlertDialog(
            onDismissRequest = { jobAlreadyAssignedDialogMessage = null },
            title = {
                Text(
                    text = "Delivery Notice",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = RiderColors.TextPrimary
                )
            },
            text = {
                Text(
                    text = jobAlreadyAssignedDialogMessage ?: "",
                    fontSize = 14.sp,
                    color = RiderColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { jobAlreadyAssignedDialogMessage = null },
                    colors = ButtonDefaults.buttonColors(containerColor = RiderColors.Primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = RiderColors.Surface,
            shape = RoundedCornerShape(16.dp)
        )
    }
}
