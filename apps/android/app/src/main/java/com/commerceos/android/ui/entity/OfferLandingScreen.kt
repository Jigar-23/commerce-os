package com.commerceos.android.ui.entity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

/** Dedicated Offer Landing screen for typed offer destinations. */
@Composable
fun OfferLandingScreen(
    offerId: String,
    onBack: () -> Unit,
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Special Offer #$offerId", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Text("Exclusive Platform Savings", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
            shape = RoundedCornerShape(Radius.Card),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = CommerceColors.InfoContainer, shape = RoundedCornerShape(Radius.Chip)) {
                    Text("Verified Offer", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Icon(Icons.Default.Star, contentDescription = null, tint = CommerceColors.Primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(Spacing.md))
                Text("Offer #$offerId", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text("Eligible products, categories, and collections for this promotion.", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
                Spacer(modifier = Modifier.height(Spacing.xl))
                Button(
                    onClick = { onOpenCatalog(CatalogQuery(offerId = offerId)) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text("Shop Eligible Products", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
