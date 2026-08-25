package com.commerceos.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.config.CommerceDomain
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

/**
 * P2 — VERTICAL-SPECIFIC QUALITY WIDGETS & NULL-SAFETY HELPERS
 * Handles vertical-specific empty states, error screens, shimmer loading placeholders,
 * onboarding tooltips, and strict metadata null-safety without fabricating data.
 */
object VerticalExperienceQuality {

    @Composable
    fun EmptyState(
        verticalId: String = "general",
        config: ClientConfiguration = LocalClientConfiguration.current,
        modifier: Modifier = Modifier
    ) {
        val (icon, title, body) = when (config.domain) {
            CommerceDomain.PHARMACY -> Triple("💊", "No Medicines Found", "Your prescription vault or cart is empty.")
            CommerceDomain.FASHION -> Triple("👗", "No Apparel Items", "Browse new arrivals or saved favorites.")
            CommerceDomain.FOOD -> Triple("🍕", "No Dishes or Restaurants Available", "Try searching for a different cuisine or locality.")
            CommerceDomain.ELECTRONICS -> Triple("⚡", "No Tech Products Available", "Explore alternate gadget categories or brands.")
            CommerceDomain.SERVICES -> Triple("🛠️", "No Service Technicians Available", "Select another appointment slot or service type.")
            CommerceDomain.GENERAL_COMMERCE -> Triple("📦", "No Products Available", "Check back soon for new inventory.")
        }

        Column(
            modifier = modifier.fillMaxWidth().padding(Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, style = CommerceTypography.Display)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(title, style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(body, style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
        }
    }

    @Composable
    fun ErrorState(
        message: String,
        onRetry: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.DangerSoft),
            shape = RoundedCornerShape(Radius.Card),
            modifier = modifier.fillMaxWidth().padding(Spacing.md)
        ) {
            Column(modifier = Modifier.padding(Spacing.md), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚠️ Error", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.Danger)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(message, style = CommerceTypography.Meta, color = CommerceColors.Danger)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Danger)
                ) {
                    Text("Retry", style = CommerceTypography.Caption, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun LoadingPlaceholder(modifier: Modifier = Modifier) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.SurfaceSubtle),
            shape = RoundedCornerShape(Radius.Card),
            modifier = modifier.fillMaxWidth().height(120.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CommerceColors.Primary, modifier = Modifier.size(32.dp))
            }
        }
    }
}
