package com.commerceos.android.ui.home.vertical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.model.VerticalCategory
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.*

@Composable
fun ElectronicsVerticalSection(
    categories: List<VerticalCategory> = emptyList(),
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (categories.isNotEmpty()) {
            Text("Tech Categories & Devices", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                for (cat in categories) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                        shape = RoundedCornerShape(Radius.Card),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier
                            .width(110.dp)
                            .clickable { onOpenCatalog(CatalogQuery(categoryId = cat.id, vertical = "electronics")) }
                    ) {
                        Column {
                            if (!cat.image.isNullOrBlank()) {
                                ProductImage(
                                    imageUrl = cat.image,
                                    contentDescription = cat.name,
                                    contentScale = ContentScale.Crop,
                                    shape = RoundedCornerShape(topStart = Radius.Card, topEnd = Radius.Card),
                                    modifier = Modifier.fillMaxWidth().height(70.dp)
                                )
                            }
                            Text(
                                cat.name,
                                style = CommerceTypography.Meta,
                                fontWeight = FontWeight.Bold,
                                color = CommerceColors.TextPrimary,
                                maxLines = 1,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.SurfaceSubtle),
            shape = RoundedCornerShape(Radius.Card),
            border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
            modifier = Modifier.fillMaxWidth().clickable { onOpenCatalog(CatalogQuery(vertical = "electronics")) }
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text("📱 Electronics & Tech Catalog", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text("Browse Devices →", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
            }
        }
    }
}
