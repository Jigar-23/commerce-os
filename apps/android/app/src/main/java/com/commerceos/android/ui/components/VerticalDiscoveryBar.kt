package com.commerceos.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.engine.CustomVerticalDefinition
import com.commerceos.android.engine.GeneralizedVerticalEngine
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

/**
 * P1 — VERTICAL DISCOVERY BAR
 * Dynamically presents client-configured verticals with custom labels, icons, ordering,
 * active states, serviceability badges, and accessibility semantics.
 */
@Composable
fun VerticalDiscoveryBar(
    activeVerticalId: String = "general",
    onSelectVertical: (String) -> Unit,
    config: ClientConfiguration = LocalClientConfiguration.current,
    modifier: Modifier = Modifier
) {
    val verticals = GeneralizedVerticalEngine.resolveActiveVerticals(config)
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        verticals.forEach { item ->
            val isActive = item.verticalId.equals(activeVerticalId, ignoreCase = true)

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) CommerceColors.Primary else CommerceColors.Surface
                ),
                shape = RoundedCornerShape(Radius.Pill),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 1.dp),
                modifier = Modifier
                    .semantics {
                        contentDescription = "Vertical ${item.verticalName}, ${if (isActive) "selected" else "not selected"}"
                    }
                    .clickable { onSelectVertical(item.verticalId) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.visualSymbol, style = CommerceTypography.Body)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        item.verticalName,
                        style = CommerceTypography.Caption,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) CommerceColors.OnPrimary else CommerceColors.TextPrimary
                    )
                }
            }
        }
    }
}
