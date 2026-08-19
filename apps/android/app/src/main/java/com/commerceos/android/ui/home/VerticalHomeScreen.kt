package com.commerceos.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.registry.ClientPresentationRegistry
import com.commerceos.android.registry.VerticalPresentation
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceElevation
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.VerticalHomeUiState
import com.commerceos.android.viewmodel.VerticalHomeViewModel

/**
 * Dedicated Vertical Home Hub (Health, Food, Grocery, Fashion, Electronics, Services).
 * Driven strictly by Registry & Active Client Configuration without hardcoded when(verticalId) or when(domain) logic.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerticalHomeScreen(
    verticalId: String,
    viewModel: VerticalHomeViewModel,
    addressId: String? = null,
    onBack: () -> Unit,
    onOpenCatalog: (CatalogQuery) -> Unit,
    onSelectProduct: (CommerceProduct) -> Unit = {}
) {
    val clientConfig = LocalClientConfiguration.current
    val presentation = ClientPresentationRegistry.resolvePresentation(verticalId, clientConfig)

    LaunchedEffect(verticalId, addressId) {
        viewModel.loadVertical(verticalId, addressId)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg),
        contentPadding = PaddingValues(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        item {
            // TOP HEADER WITH BACK BUTTON
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CommerceColors.TextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    val headerTitle = (viewModel.uiState as? VerticalHomeUiState.Content)?.title ?: presentation.catalogHeader
                    Text(
                        "${presentation.visualSymbol} $headerTitle",
                        style = CommerceTypography.Title,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextPrimary
                    )
                }
            }
        }

        when (val state = viewModel.uiState) {
            is VerticalHomeUiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CommerceColors.Primary)
                    }
                }
            }

            is VerticalHomeUiState.Error -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                        shape = RoundedCornerShape(Radius.md),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Could Not Load Storefront",
                                style = CommerceTypography.Title,
                                fontWeight = FontWeight.Bold,
                                color = CommerceColors.Danger
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                state.message,
                                style = CommerceTypography.BodySmall,
                                color = CommerceColors.TextMuted
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Button(
                                onClick = { viewModel.loadVertical(verticalId, addressId) },
                                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary)
                            ) {
                                Text("Retry Loading")
                            }
                        }
                    }
                }
            }

            is VerticalHomeUiState.Content -> {
                item {
                    // HERO EDITORIAL BANNER RESOLVED FROM REGISTRY & CONFIG
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CommerceColors.Primary),
                        shape = RoundedCornerShape(Radius.Hero),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(Spacing.lg)) {
                            Surface(color = CommerceColors.Surface.copy(alpha = 0.2f), shape = RoundedCornerShape(Radius.Chip)) {
                                Text(
                                    state.fulfillment.etaLabel?.let { "Delivery $it" } ?: "Delivery time unavailable",
                                    style = CommerceTypography.Meta,
                                    fontWeight = FontWeight.Bold,
                                    color = CommerceColors.OnPrimary,
                                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(Spacing.md))
                            Text(
                                state.title.ifBlank { presentation.heroTitle },
                                style = CommerceTypography.HeroTitle,
                                fontWeight = FontWeight.Bold,
                                color = CommerceColors.OnPrimary
                            )
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                state.subtitle.ifBlank { presentation.heroSubtitle },
                                style = CommerceTypography.BodySmall,
                                color = CommerceColors.OnPrimary.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(Spacing.md))
                            val ctaText = state.ctaText ?: presentation.defaultCta
                            Button(
                                onClick = { onOpenCatalog(CatalogQuery(vertical = verticalId)) },
                                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Surface),
                                shape = RoundedCornerShape(Radius.md)
                            ) {
                                Text(ctaText, color = CommerceColors.Primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (state.categories.isNotEmpty()) {
                    item {
                        Text(
                            presentation.catalogHeader,
                            style = CommerceTypography.Title,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.TextPrimary
                        )
                    }
                    items(state.categories.chunked(2)) { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                            for (cat in pair) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                                    shape = RoundedCornerShape(Radius.Card),
                                    elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onOpenCatalog(CatalogQuery(categoryId = cat.id, vertical = verticalId)) }
                                ) {
                                    Column {
                                        if (!cat.image.isNullOrBlank()) {
                                            ProductImage(
                                                imageUrl = cat.image,
                                                contentDescription = cat.name,
                                                contentScale = ContentScale.Crop,
                                                shape = RoundedCornerShape(topStart = Radius.Card, topEnd = Radius.Card),
                                                modifier = Modifier.fillMaxWidth().height(100.dp)
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(80.dp)
                                                    .background(CommerceColors.InfoContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = CommerceColors.Primary, modifier = Modifier.size(28.dp))
                                            }
                                        }
                                        Column(modifier = Modifier.padding(Spacing.sm)) {
                                            Text(cat.name, style = CommerceTypography.Label, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Vertical feature banner resolved dynamically via ClientPresentationRegistry
                item {
                    ConfiguredVerticalFeatureBanner(presentation = presentation, verticalId = verticalId, onOpenCatalog = onOpenCatalog)
                }
            }
        }
    }
}

@Composable
private fun ConfiguredVerticalFeatureBanner(
    presentation: VerticalPresentation,
    verticalId: String,
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.SurfaceSubtle),
        shape = RoundedCornerShape(Radius.Card),
        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
        modifier = Modifier.fillMaxWidth().clickable { onOpenCatalog(CatalogQuery(vertical = verticalId)) }
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text("${presentation.visualSymbol} ${presentation.catalogHeader}", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text("${presentation.defaultCta} →", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
        }
    }
}
