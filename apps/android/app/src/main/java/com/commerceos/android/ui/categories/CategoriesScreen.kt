package com.commerceos.android.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.model.CatalogCategory
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.CategoryTaxonomyUiState
import com.commerceos.android.viewmodel.CategoryViewModel

/**
 * Universal Categories Directory. Driven by server & client configuration taxonomy.
 * Displays a 2-column visual grid for quick catalog discovery.
 */
@Composable
fun CategoriesScreen(
    viewModel: CategoryViewModel,
    onSelectCategory: (CatalogCategory) -> Unit
) {
    val clientConfig = LocalClientConfiguration.current
    val categoriesLabel: String = "Categories"

    LaunchedEffect(Unit) {
        viewModel.loadTaxonomy()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            text = categoriesLabel,
            style = CommerceTypography.Title,
            fontWeight = FontWeight.Bold,
            color = CommerceColors.TextPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Browse ${clientConfig.identity.clientName} catalog taxonomy",
            style = CommerceTypography.Caption,
            color = CommerceColors.TextMuted
        )
        Spacer(modifier = Modifier.height(Spacing.lg))

        when (val state = viewModel.taxonomy) {
            is CategoryTaxonomyUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CommerceColors.Primary)
                }
            }
            is CategoryTaxonomyUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.message, color = CommerceColors.Danger, fontWeight = FontWeight.Bold)
                }
            }
            is CategoryTaxonomyUiState.Content -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.categories) { cat ->
                        CategoryCard(category = cat, onClick = { onSelectCategory(cat) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: CatalogCategory, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.lg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = CommerceColors.SurfaceSubtle,
                    shape = RoundedCornerShape(Radius.Chip)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, tint = CommerceColors.Primary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(category.slug.uppercase(), style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = CommerceColors.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = category.name,
                    fontWeight = FontWeight.Bold,
                    style = CommerceTypography.BodySmall,
                    color = CommerceColors.TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (category.productCount == 1) "1 item available" else "${category.productCount} items available",
                    style = CommerceTypography.Meta,
                    color = CommerceColors.TextMuted
                )
            }
        }
    }
}
