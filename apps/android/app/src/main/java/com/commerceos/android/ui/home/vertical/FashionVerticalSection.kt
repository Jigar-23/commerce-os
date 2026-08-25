package com.commerceos.android.ui.home.vertical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.model.VerticalCategory
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.*

@Composable
fun FashionVerticalSection(
    categories: List<VerticalCategory> = emptyList(),
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        if (categories.isNotEmpty()) {
            Text("Style Departments", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
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
                            .clickable { onOpenCatalog(CatalogQuery(categoryId = cat.id, vertical = "fashion")) }
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
            colors = CardDefaults.cardColors(containerColor = CommerceColors.HeroDark),
            shape = RoundedCornerShape(Radius.Card),
            modifier = Modifier.fillMaxWidth().clickable { onOpenCatalog(CatalogQuery(vertical = "fashion")) }
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Surface(color = CommerceColors.Discount, shape = RoundedCornerShape(Radius.Micro)) {
                    Text("SEASON DROPS", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Fashion Apparel Catalog", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text("Shop Catalog →", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
            }
        }
    }
}
