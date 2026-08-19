package com.commerceos.android.ui.address

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.viewmodel.ServiceabilityState

/**
 * Enterprise Multi-Vertical Delivery Promise Card Component.
 * Guarantees address-identity verification and displays per-vertical fulfillment breakdown
 * even during partial availability states.
 */
@Composable
fun AddressServiceabilityCard(
    serviceabilityState: ServiceabilityState,
    selectedAddressId: String?,
    modifier: Modifier = Modifier
) {
    val isTargetMatched = serviceabilityState.targetAddressId == selectedAddressId && selectedAddressId != null

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                !isTargetMatched || serviceabilityState is ServiceabilityState.Checking -> CommerceColors.SurfaceSubtle
                serviceabilityState is ServiceabilityState.Success -> CommerceColors.SurfaceSubtle
                serviceabilityState is ServiceabilityState.Partial -> CommerceColors.WarningSoft
                else -> CommerceColors.DangerContainer
            }
        ),
        shape = RoundedCornerShape(Radius.md),
        border = BorderStroke(
            1.dp,
            when {
                !isTargetMatched || serviceabilityState is ServiceabilityState.Checking -> CommerceColors.Border
                serviceabilityState is ServiceabilityState.Success -> CommerceColors.Border
                serviceabilityState is ServiceabilityState.Partial -> CommerceColors.Warning
                else -> CommerceColors.Danger
            }
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            when {
                selectedAddressId == null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CommerceColors.TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Select a delivery address to view fulfillment promises",
                            style = CommerceTypography.Caption,
                            fontWeight = FontWeight.SemiBold,
                            color = CommerceColors.TextSecondary
                        )
                    }
                }
                !isTargetMatched || serviceabilityState is ServiceabilityState.Checking -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CommerceColors.Success, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "All items in cart available for rapid dispatch • 10-Min Express SLA Guaranteed",
                            style = CommerceTypography.Caption,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.TextPrimary
                        )
                    }
                }
                serviceabilityState is ServiceabilityState.Success || serviceabilityState is ServiceabilityState.Partial -> {
                    val resp = if (serviceabilityState is ServiceabilityState.Success) serviceabilityState.response else (serviceabilityState as ServiceabilityState.Partial).response
                    val statusSummary = if (resp.eligible) {
                        resp.etaLabel?.let { "All items in cart available for rapid dispatch • $it" } ?: "Selected address is serviceable for delivery"
                    } else {
                        "Selected location is out of delivery range for cart items"
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (serviceabilityState is ServiceabilityState.Success) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (serviceabilityState is ServiceabilityState.Success) CommerceColors.Success else CommerceColors.Warning,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            statusSummary,
                            style = CommerceTypography.Caption,
                            fontWeight = FontWeight.Bold,
                            color = if (serviceabilityState is ServiceabilityState.Success) CommerceColors.TextPrimary else CommerceColors.Warning
                        )
                    }

                    val promises = resp.verticals ?: emptyList()
                    if (promises.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(promises) { apiVert ->
                                VerticalPromiseChip(
                                    verticalId = apiVert.verticalId,
                                    etaLabel = apiVert.eta,
                                    isAvailable = apiVert.status.uppercase() == "AVAILABLE" || apiVert.status.uppercase() == "SERVICEABLE"
                                )
                            }
                        }
                    }
                }
                serviceabilityState is ServiceabilityState.Unavailable -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CommerceColors.Danger, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Delivery unavailable for this address. Please select another location.",
                            style = CommerceTypography.Caption,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.Danger
                        )
                    }
                }
                serviceabilityState is ServiceabilityState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = CommerceColors.Danger, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            serviceabilityState.message,
                            style = CommerceTypography.Caption,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.Danger
                        )
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun VerticalPromiseChip(verticalId: String, etaLabel: String?, isAvailable: Boolean) {
    Surface(
        color = if (isAvailable) CommerceColors.Surface else CommerceColors.Background,
        shape = RoundedCornerShape(Radius.sm),
        border = BorderStroke(1.dp, CommerceColors.Border)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(verticalId.replaceFirstChar { it.uppercase() }, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
            Text(
                if (isAvailable) (etaLabel ?: "Available") else "Unavailable",
                style = CommerceTypography.Caption,
                fontWeight = FontWeight.Bold,
                color = if (isAvailable) CommerceColors.Primary else CommerceColors.TextMuted
            )
        }
    }
}
