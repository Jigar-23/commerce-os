package com.commerceos.android.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

/**
 * Controlled Tenant Suspension Screen.
 * Presented when a tenant license is SUSPENDED or TERMINATED.
 * Prevents unauthorized checkout, order creation, or payment mutations.
 */
@Composable
fun TenantSuspendedScreen(
    reason: String? = null,
    onContactSupport: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CommerceColors.Background)
            .padding(Spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.Card),
            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface)
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Tenant Suspended",
                    tint = CommerceColors.Danger,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    "Service Temporarily Suspended",
                    style = CommerceTypography.Heading,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    reason ?: "Your tenant account access has been paused. Please contact Commerce OS billing support to resolve account status.",
                    style = CommerceTypography.BodySmall,
                    color = CommerceColors.TextMuted,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                Button(
                    onClick = onContactSupport,
                    colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Contact Enterprise Support", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
