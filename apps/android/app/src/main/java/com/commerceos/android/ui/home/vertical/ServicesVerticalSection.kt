package com.commerceos.android.ui.home.vertical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.ui.theme.*

@Composable
fun ServicesVerticalSection(onOpenCatalog: (CatalogQuery) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.SurfaceSubtle),
            shape = RoundedCornerShape(Radius.Card),
            border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
            modifier = Modifier.fillMaxWidth().clickable { onOpenCatalog(CatalogQuery(vertical = "services")) }
        ) {
            Column(modifier = Modifier.padding(Spacing.lg)) {
                Text("🔧 Home Services Catalog", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text("Explore Services →", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
            }
        }
    }
}
