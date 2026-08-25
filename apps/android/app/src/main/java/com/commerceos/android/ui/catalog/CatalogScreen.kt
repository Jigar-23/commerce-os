package com.commerceos.android.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.Destination
import com.commerceos.android.ui.components.CommerceProductCard
import com.commerceos.android.ui.components.ProductCardVariant
import com.commerceos.android.ui.components.SkeletonProductImage
import com.commerceos.android.ui.components.SkeletonText
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.CATALOG_PRICE_BANDS
import com.commerceos.android.viewmodel.CatalogUiState
import com.commerceos.android.viewmodel.CatalogViewModel

/**
 * Unified PLP: free-text search, category browsing and full catalog share this
 * destination. All filters (price band, destination) are applied server-side and
 * the list paginates through limit/offset pages with a real hasMore flag.
 */
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onSelectProduct: (CommerceProduct) -> Unit,
    onAddToCart: (CommerceProduct) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        CatalogSearchBar(viewModel = viewModel)

        PriceBandRow(selected = viewModel.selectedPriceBand, onSelect = viewModel::setPriceBand)

        when (val state = viewModel.uiState) {
            is CatalogUiState.Loading -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    repeat(6) {
                        item {
                            CatalogGridSkeleton()
                        }
                    }
                }
            }
            is CatalogUiState.Empty -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                    shape = RoundedCornerShape(Radius.lg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = CommerceColors.TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text("No products match these filters", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                        Text("Try a different search, category, or price range.", style = CommerceTypography.Caption, color = CommerceColors.TextMuted)
                    }
                }
            }
            is CatalogUiState.Error -> {
                Text(state.message, style = CommerceTypography.BodySmall, color = CommerceColors.Danger, fontWeight = FontWeight.Bold)
            }
            is CatalogUiState.Content -> {
                Text(
                    "${state.title} • ${state.totalElements} items",
                    style = CommerceTypography.Caption,
                    color = CommerceColors.TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    gridItems(state.results) { prod ->
                        CommerceProductCard(
                            product = prod,
                            onSelect = onSelectProduct,
                            onAddToCart = onAddToCart,
                            variant = ProductCardVariant.Grid,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (viewModel.hasMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LoadMoreRow(
                                isLoadingMore = viewModel.isLoadingMore,
                                onLoadMore = viewModel::loadMore
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogSearchBar(viewModel: CatalogViewModel) {
    OutlinedTextField(
        value = viewModel.query,
        onValueChange = viewModel::onQueryChange,
        placeholder = { Text("Search products, brands, or health needs...", style = CommerceTypography.Label) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CommerceColors.Primary) },
        trailingIcon = {
            if (viewModel.query.isNotBlank()) {
                IconButton(onClick = { viewModel.onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = CommerceColors.TextMuted, modifier = Modifier.size(18.dp))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { viewModel.submitQuery() }),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CommerceColors.Primary,
            unfocusedBorderColor = CommerceColors.Border,
            focusedContainerColor = CommerceColors.Surface,
            unfocusedContainerColor = CommerceColors.Surface
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DestinationRow(
    destinations: List<Destination>,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    if (destinations.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("DELIVERY DESTINATION", style = CommerceTypography.Meta, fontWeight = FontWeight.Black, color = CommerceColors.TextMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(destinations) { dest ->
                    val selected = dest.id == selectedId
                    FilterChip(
                        selected = selected,
                        onClick = { onSelect(if (selected) null else dest.id) },
                        label = { Text(dest.name, style = CommerceTypography.Meta, fontWeight = if (selected) FontWeight.Black else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CommerceColors.Primary,
                            selectedLabelColor = CommerceColors.OnPrimary,
                            containerColor = CommerceColors.Surface,
                            labelColor = CommerceColors.NeutralDark
                        ),
                        shape = RoundedCornerShape(Radius.Chip)
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceBandRow(
    selected: com.commerceos.android.model.PriceBand,
    onSelect: (com.commerceos.android.model.PriceBand) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("PRICE", style = CommerceTypography.Meta, fontWeight = FontWeight.Black, color = CommerceColors.TextMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(CATALOG_PRICE_BANDS) { band ->
                val isSelected = band == selected
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(band) },
                    label = { Text(band.label, style = CommerceTypography.Meta, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CommerceColors.Success,
                        selectedLabelColor = CommerceColors.OnPrimary,
                        containerColor = CommerceColors.Surface,
                        labelColor = CommerceColors.NeutralDark
                    ),
                    shape = RoundedCornerShape(Radius.md)
                )
            }
        }
    }
}

@Composable
private fun CatalogGridSkeleton() {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card)
    ) {
        Column {
            SkeletonProductImage(
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SkeletonText(width = 110.dp, height = 13.dp)
                SkeletonText(width = 80.dp, height = 11.dp)
                SkeletonText(width = 64.dp, height = 13.dp)
            }
        }
    }
}

@Composable
private fun LoadMoreRow(isLoadingMore: Boolean, onLoadMore: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm), contentAlignment = Alignment.Center) {
        if (isLoadingMore) {
            CircularProgressIndicator(color = CommerceColors.Primary, modifier = Modifier.size(28.dp))
        } else {
            OutlinedButton(
                onClick = onLoadMore,
                shape = RoundedCornerShape(Radius.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Load More", fontWeight = FontWeight.Bold)
            }
        }
    }
}
