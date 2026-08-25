package com.commerceos.android.tracking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.fulfillment.CarrierTrackingStatus
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

/**
 * Universal Unified Order Tracking Screen.
 * Dynamically resolves and renders tracking presentation based on active TrackingMode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiedOrderTrackingScreen(
    session: UnifiedTrackingSession,
    onBack: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Track Order #${session.orderId.takeLast(6)}", style = CommerceTypography.Title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CommerceColors.Surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CommerceColors.Background)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                TrackingHeaderCard(session)
            }

            when (session.mode) {
                TrackingMode.LIVE_LOCATION -> {
                    item { LiveLocationTrackingWidget(session) }
                }
                TrackingMode.CARRIER_CHECKPOINT -> {
                    item { CarrierHeaderCard(session) }
                    items(session.checkpoints) { checkpoint ->
                        CarrierCheckpointItem(checkpoint)
                    }
                }
                TrackingMode.PICKUP -> {
                    item { PickupPassTrackingWidget(session) }
                }
                TrackingMode.SERVICE_BOOKING -> {
                    item { ServiceBookingTrackingWidget(session) }
                }
            }
        }
    }
}

@Composable
private fun TrackingHeaderCard(session: UnifiedTrackingSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = CommerceColors.PrimarySoft,
                    shape = RoundedCornerShape(Radius.Pill)
                ) {
                    Text(
                        session.mode.name,
                        style = CommerceTypography.Meta,
                        color = CommerceColors.Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("ETA: ${session.estimatedArrivalFormatted}", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(session.statusText, style = CommerceTypography.Heading, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
        }
    }
}

@Composable
private fun LiveLocationTrackingWidget(session: UnifiedTrackingSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            // Live Map View with Stale Signal Detection
            val (badgeText, badgeColor, statusSubtext) = when (session.signalState) {
                CustomerTrackingSignalState.LIVE -> Triple(
                    "● LIVE RIDER GPS STREAM",
                    CommerceColors.Primary,
                    if (session.liveRiderLat != null && session.liveRiderLng != null) "Lat: ${session.liveRiderLat}, Lng: ${session.liveRiderLng}" else "Awaiting live telemetry..."
                )
                CustomerTrackingSignalState.DELAYED -> Triple(
                    "⚠️ RIDER LOCATION SIGNAL PAUSED",
                    CommerceColors.Warning,
                    "Displaying last verified checkpoint • Telemetry signal delayed"
                )
                CustomerTrackingSignalState.LOCATION_UNAVAILABLE -> Triple(
                    "LOCATION UNAVAILABLE",
                    CommerceColors.TextMuted,
                    "Awaiting initial rider GPS signal..."
                )
                CustomerTrackingSignalState.DISCONNECTED -> Triple(
                    "DISCONNECTED",
                    CommerceColors.Danger,
                    "Reconnecting to live stream..."
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(Radius.ImageTile))
                    .background(if (session.signalState == CustomerTrackingSignalState.DELAYED) CommerceColors.WarningSoft else CommerceColors.SurfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = badgeColor,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        badgeText,
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor
                    )
                    Text(
                        statusSubtext,
                        style = CommerceTypography.Meta,
                        color = CommerceColors.TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Rider Contact & Handoff PIN Card
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(CommerceColors.PrimarySoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(session.riderName?.take(1) ?: "R", fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                }
                Spacer(modifier = Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.riderName ?: "Assigned Rider", style = CommerceTypography.Title, fontWeight = FontWeight.Bold)
                    Text(session.riderPhone ?: "+91-XXXXX-XXXXX", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.Call, contentDescription = "Call Rider", tint = CommerceColors.Primary)
                }
            }

            session.handoffOtp?.let { otp ->
                Spacer(modifier = Modifier.height(Spacing.sm))
                Surface(
                    color = CommerceColors.SurfaceSubtle,
                    shape = RoundedCornerShape(Radius.Card),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Share 4-digit Delivery PIN with Rider:", style = CommerceTypography.BodySmall)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(otp, style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CarrierHeaderCard(session: UnifiedTrackingSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(session.carrierName ?: "Carrier Partner", style = CommerceTypography.Title, fontWeight = FontWeight.Bold)
            Text("Consignment No: ${session.consignmentNumber ?: "N/A"}", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
        }
    }
}

@Composable
private fun CarrierCheckpointItem(checkpoint: com.commerceos.android.fulfillment.ShipmentCheckpoint) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (checkpoint.status == CarrierTrackingStatus.DELIVERED) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = if (checkpoint.status == CarrierTrackingStatus.DELIVERED) CommerceColors.Success else CommerceColors.Primary
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(checkpoint.description, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold)
                Text("${checkpoint.facilityName} • ${checkpoint.locationName}", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
            }
        }
    }
}

@Composable
private fun PickupPassTrackingWidget(session: UnifiedTrackingSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("STORE PICKUP PASS", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(session.pickupStoreAddress ?: "Store Outlet Central", style = CommerceTypography.BodySmall)
            Spacer(modifier = Modifier.height(Spacing.md))
            Surface(
                color = CommerceColors.PrimarySoft,
                shape = RoundedCornerShape(Radius.Card)
            ) {
                Text(
                    session.pickupQrPassCode ?: "PASS-99812",
                    style = CommerceTypography.Heading,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.Primary,
                    modifier = Modifier.padding(Spacing.md)
                )
            }
        }
    }
}

@Composable
private fun ServiceBookingTrackingWidget(session: UnifiedTrackingSession) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text("Service Provider: ${session.serviceProviderName ?: "Verified Professional"}", style = CommerceTypography.Title, fontWeight = FontWeight.Bold)
            Text("Slot: ${session.serviceSlotTime ?: "Today, 4:00 PM - 5:00 PM"}", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
        }
    }
}
