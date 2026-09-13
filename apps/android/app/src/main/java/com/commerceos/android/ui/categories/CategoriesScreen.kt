package com.commerceos.android.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CatalogCategory
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.viewmodel.CategoryTaxonomyUiState
import com.commerceos.android.viewmodel.CategoryViewModel

data class CategoryPresentation(
    val emoji: String,
    val iconTint: Color,
    val iconBg: Color,
    val highlights: String
)

private fun getPresentation(categoryName: String): CategoryPresentation {
    val lower = categoryName.lowercase()
    return when {
        lower.contains("pain") || lower.contains("fever") -> CategoryPresentation(
            emoji = "🌡️",
            iconTint = Color(0xFFEF4444),
            iconBg = Color(0xFFFEF2F2),
            highlights = "Paracetamol, Dolo, Balms"
        )
        lower.contains("cold") || lower.contains("cough") -> CategoryPresentation(
            emoji = "🤧",
            iconTint = Color(0xFF0284C7),
            iconBg = Color(0xFFF0F9FF),
            highlights = "Syrups, Lozenges, Inhalers"
        )
        lower.contains("diabetes") -> CategoryPresentation(
            emoji = "🩺",
            iconTint = Color(0xFF4F46E5),
            iconBg = Color(0xFFEEF2FF),
            highlights = "Glucometers, Strips, Care"
        )
        lower.contains("antibiotic") || lower.contains("infect") -> CategoryPresentation(
            emoji = "🧪",
            iconTint = Color(0xFF0D9488),
            iconBg = Color(0xFFF0FDFA),
            highlights = "Amoxiclav, Azithral"
        )
        lower.contains("vitamin") || lower.contains("nutrition") -> CategoryPresentation(
            emoji = "🌿",
            iconTint = Color(0xFF059669),
            iconBg = Color(0xFFECFDF5),
            highlights = "Multivitamins, Zinc, Calcium"
        )
        lower.contains("stomach") || lower.contains("digest") || lower.contains("gas") -> CategoryPresentation(
            emoji = "💊",
            iconTint = Color(0xFFD97706),
            iconBg = Color(0xFFFFFBEB),
            highlights = "Antacids, Digene, ORS"
        )
        lower.contains("heart") || lower.contains("cardiac") || lower.contains("bp") -> CategoryPresentation(
            emoji = "❤️",
            iconTint = Color(0xFFDB2777),
            iconBg = Color(0xFFFDF2F8),
            highlights = "BP Monitors, Heart Health"
        )
        lower.contains("first aid") || lower.contains("wound") || lower.contains("bandage") -> CategoryPresentation(
            emoji = "🩹",
            iconTint = Color(0xFFE11D48),
            iconBg = Color(0xFFFFF1F2),
            highlights = "Bandages, Antiseptic, Dettol"
        )
        lower.contains("skin") || lower.contains("hair") || lower.contains("derma") -> CategoryPresentation(
            emoji = "✨",
            iconTint = Color(0xFF9333EA),
            iconBg = Color(0xFFFAF5FF),
            highlights = "Derma Creams, Lotions"
        )
        lower.contains("baby") || lower.contains("mother") -> CategoryPresentation(
            emoji = "👶",
            iconTint = Color(0xFF2563EB),
            iconBg = Color(0xFFEFF6FF),
            highlights = "Diapers, Wipes, Gripe Water"
        )
        lower.contains("ayurved") || lower.contains("herbal") -> CategoryPresentation(
            emoji = "🍃",
            iconTint = Color(0xFF16A34A),
            iconBg = Color(0xFFF0FDF4),
            highlights = "Chyawanprash, Immunity"
        )
        else -> CategoryPresentation(
            emoji = "💊",
            iconTint = Color(0xFF059669),
            iconBg = Color(0xFFECFDF5),
            highlights = "Certified Medicines"
        )
    }
}

private val DEFAULT_HEALTHCARE_CATEGORIES = listOf(
    CatalogCategory(id = "cat_pain_fever", slug = "pain-fever", name = "Pain & Fever", productCount = 18),
    CatalogCategory(id = "cat_cold_cough", slug = "cold-cough", name = "Cold & Cough", productCount = 14),
    CatalogCategory(id = "cat_diabetes_care", slug = "diabetes-care", name = "Diabetes Care", productCount = 12),
    CatalogCategory(id = "cat_antibiotics", slug = "antibiotics", name = "Antibiotics", productCount = 9),
    CatalogCategory(id = "cat_vitamins", slug = "vitamins-nutrition", name = "Vitamins & Daily Wellness", productCount = 24),
    CatalogCategory(id = "cat_digestive", slug = "digestive-health", name = "Stomach & Digestion", productCount = 16),
    CatalogCategory(id = "cat_cardiac", slug = "cardiac-care", name = "Heart & Blood Pressure", productCount = 8),
    CatalogCategory(id = "cat_first_aid", slug = "first-aid", name = "First Aid & Essentials", productCount = 20),
    CatalogCategory(id = "cat_skin_care", slug = "skin-care", name = "Skin & Dermatology", productCount = 15),
    CatalogCategory(id = "cat_baby_care", slug = "baby-care", name = "Baby & Mother Care", productCount = 11)
)

/**
 * Clean, production-grade Healthcare Categories Directory.
 * Displays curated medical departments with authentic clinical iconography,
 * helpful subcategory highlights, and 10-minute delivery badges.
 */
@Composable
fun CategoriesScreen(
    viewModel: CategoryViewModel,
    onSelectCategory: (CatalogCategory) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadTaxonomy()
    }

    val categories = when (val state = viewModel.taxonomy) {
        is CategoryTaxonomyUiState.Content -> {
            if (state.categories.isNotEmpty()) state.categories else DEFAULT_HEALTHCARE_CATEGORIES
        }
        else -> DEFAULT_HEALTHCARE_CATEGORIES
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F5F7))
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp)
    ) {
        // Top Header matching Home, Orders, and Account
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Shop by Category",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Certified medicines & healthcare essentials",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
            Surface(
                color = Color(0xFFECFDF5),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚡", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "10-Min Delivery",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Categories Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Reassuring Safety Banner
            item(span = { GridItemSpan(2) }) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shadowElevation = 0.5.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color(0xFFECFDF5),
                            shape = CircleShape,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "100% Genuine & Licensed Pharmacy",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Dispensed directly from verified retail pharmacies",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }
            }

            items(categories) { cat ->
                HealthcareCategoryCard(
                    category = cat,
                    onClick = { onSelectCategory(cat) }
                )
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HealthcareCategoryCard(
    category: CatalogCategory,
    onClick: () -> Unit
) {
    val pres = getPresentation(category.name)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Colored Icon Tile
                Surface(
                    color = pres.iconBg,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(pres.emoji, fontSize = 20.sp)
                    }
                }

                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = category.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color(0xFF0F172A),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pres.highlights,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (category.productCount > 0) "${category.productCount}+ products" else "Express delivery",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF059669)
                )
            }
        }
    }
}
