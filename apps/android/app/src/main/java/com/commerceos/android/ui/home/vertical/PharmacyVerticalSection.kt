package com.commerceos.android.ui.home.vertical

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.ui.theme.*

@Composable
fun PharmacyVerticalSection(onOpenCatalog: (CatalogQuery) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.InfoContainer),
            shape = RoundedCornerShape(Radius.Card),
            modifier = Modifier.fillMaxWidth().clickable { onOpenCatalog(CatalogQuery(vertical = "health")) }
        ) {
            Row(modifier = Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Surface(color = CommerceColors.Primary, shape = RoundedCornerShape(Radius.Micro)) {
                        Text("HEALTH & PHARMACY", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("💊 Medicines & Healthcare Catalog", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                }
            }
        }
    }
}
