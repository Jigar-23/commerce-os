package com.commerceos.android.ui.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CatalogCategory
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.PlusJakartaSans
import com.commerceos.android.viewmodel.CategoryTaxonomyUiState
import com.commerceos.android.viewmodel.CategoryViewModel

data class CategoryPresentation(
    val icon: ImageVector,
    val iconTint: Color,
    val iconBg: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val imageUrl: String,
    val badgeText: String,
    val badgeColor: Color,
    val department: String,
    val highlights: String,
    val subTags: List<String>
)

private fun getPresentation(categoryName: String): CategoryPresentation {
    val lower = categoryName.lowercase()
    return when {
        lower.contains("pain") || lower.contains("fever") -> CategoryPresentation(
            icon = Icons.Default.Healing,
            iconTint = Color(0xFFE11D48),
            iconBg = Color(0xFFFFE4E6),
            cardBg = Color(0xFFFFF7F7),
            cardBorder = Color(0xFFFFE4E6),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
            badgeText = "⚡ 10-MIN SLA",
            badgeColor = Color(0xFFE11D48),
            department = "Medicines",
            highlights = "Paracetamol, Dolo, Balms",
            subTags = listOf("Paracetamol", "Dolo 650", "Pain Balms")
        )
        lower.contains("cold") || lower.contains("cough") -> CategoryPresentation(
            icon = Icons.Default.Thermostat,
            iconTint = Color(0xFF0284C7),
            iconBg = Color(0xFFE0F2FE),
            cardBg = Color(0xFFF8FAFC),
            cardBorder = Color(0xFFBAE6FD),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
            badgeText = "MUST HAVE",
            badgeColor = Color(0xFF0284C7),
            department = "Medicines",
            highlights = "Syrups, Lozenges, Inhalers",
            subTags = listOf("Cough Syrups", "Lozenges", "Inhalers")
        )
        lower.contains("diabetes") -> CategoryPresentation(
            icon = Icons.Default.Bloodtype,
            iconTint = Color(0xFF4F46E5),
            iconBg = Color(0xFFEEF2FF),
            cardBg = Color(0xFFF9FAFD),
            cardBorder = Color(0xFFC7D2FE),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg",
            badgeText = "CHRONIC REFILL",
            badgeColor = Color(0xFF4F46E5),
            department = "Chronic Care",
            highlights = "Glucometers, Strips, Care",
            subTags = listOf("Metformin", "Glucometers", "Test Strips")
        )
        lower.contains("antibiotic") || lower.contains("infect") -> CategoryPresentation(
            icon = Icons.Default.Science,
            iconTint = Color(0xFF0D9488),
            iconBg = Color(0xFFCCFBF1),
            cardBg = Color(0xFFF7FDFD),
            cardBorder = Color(0xFF99F6E4),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg",
            badgeText = "Rx PRESCRIBED",
            badgeColor = Color(0xFF0D9488),
            department = "Medicines",
            highlights = "Amoxiclav, Azithral",
            subTags = listOf("Augmentin", "Azithral", "Ointments")
        )
        lower.contains("vitamin") || lower.contains("nutrition") -> CategoryPresentation(
            icon = Icons.Default.FitnessCenter,
            iconTint = Color(0xFF059669),
            iconBg = Color(0xFFD1FAE5),
            cardBg = Color(0xFFF6FDF9),
            cardBorder = Color(0xFFA7F3D0),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg",
            badgeText = "UP TO 40% OFF",
            badgeColor = Color(0xFF059669),
            department = "Daily Wellness",
            highlights = "Multivitamins, Zinc, Calcium",
            subTags = listOf("Multivitamins", "Zinc & C", "Calcium D3")
        )
        lower.contains("stomach") || lower.contains("digest") || lower.contains("gas") -> CategoryPresentation(
            icon = Icons.Default.LocalPharmacy,
            iconTint = Color(0xFFD97706),
            iconBg = Color(0xFFFEF3C7),
            cardBg = Color(0xFFFFFDF7),
            cardBorder = Color(0xFFFDE68A),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg",
            badgeText = "FAST RELIEF",
            badgeColor = Color(0xFFD97706),
            department = "Medicines",
            highlights = "Antacids, Digene, ORS",
            subTags = listOf("Digene Gel", "Pantoprazole", "Gas Relief")
        )
        lower.contains("heart") || lower.contains("cardiac") || lower.contains("bp") -> CategoryPresentation(
            icon = Icons.Default.Favorite,
            iconTint = Color(0xFFDB2777),
            iconBg = Color(0xFFFCE7F3),
            cardBg = Color(0xFFFFF7FA),
            cardBorder = Color(0xFFFBCFE8),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/163836/telma-40mg-strip-of-30-tablets-front-2-1756884638-non-watermarked.jpg",
            badgeText = "DOCTOR PREFERRED",
            badgeColor = Color(0xFFDB2777),
            department = "Chronic Care",
            highlights = "BP Monitors, Heart Health",
            subTags = listOf("Telmisartan", "BP Monitors", "Cardio Care")
        )
        lower.contains("first aid") || lower.contains("wound") || lower.contains("bandage") -> CategoryPresentation(
            icon = Icons.Default.MedicalServices,
            iconTint = Color(0xFFDC2626),
            iconBg = Color(0xFFFEE2E2),
            cardBg = Color(0xFFFFF8F8),
            cardBorder = Color(0xFFFECACA),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/065110/electral-powder-218g-2-1671741180.jpg",
            badgeText = "EMERGENCY KIT",
            badgeColor = Color(0xFFDC2626),
            department = "Devices & First Aid",
            highlights = "Bandages, Antiseptic, Dettol",
            subTags = listOf("Antiseptics", "Bandages", "ORS Electral")
        )
        lower.contains("skin") || lower.contains("hair") || lower.contains("derma") -> CategoryPresentation(
            icon = Icons.Default.Face,
            iconTint = Color(0xFF9333EA),
            iconBg = Color(0xFFF3E8FF),
            cardBg = Color(0xFFFAF7FD),
            cardBorder = Color(0xFFE9D5FF),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/I40695/cetaphil-gentle-skin-cleanser-125ml-2-1671741005.jpg",
            badgeText = "CLINICAL DERMA",
            badgeColor = Color(0xFF9333EA),
            department = "Personal Care",
            highlights = "Derma Creams, Lotions",
            subTags = listOf("Cleansers", "Sunscreen", "Anti-fungal")
        )
        lower.contains("baby") || lower.contains("mother") -> CategoryPresentation(
            icon = Icons.Default.ChildCare,
            iconTint = Color(0xFF2563EB),
            iconBg = Color(0xFFDBEAFE),
            cardBg = Color(0xFFF6FAFF),
            cardBorder = Color(0xFFBFDBFE),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/159115/sebamed-baby-cleansing-bar-100g-2-1671741113.jpg",
            badgeText = "GENTLE CARE",
            badgeColor = Color(0xFF2563EB),
            department = "Personal Care",
            highlights = "Diapers, Wipes, Gripe Water",
            subTags = listOf("Diapers", "Baby Wipes", "Nutrition")
        )
        lower.contains("ayurved") || lower.contains("herbal") -> CategoryPresentation(
            icon = Icons.Default.Eco,
            iconTint = Color(0xFF16A34A),
            iconBg = Color(0xFFDCFCE7),
            cardBg = Color(0xFFF7FDF8),
            cardBorder = Color(0xFFBBF7D0),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/054720/dabur-chyawanprash-2x-immunity-1kg-2-1671741076.jpg",
            badgeText = "100% NATURAL",
            badgeColor = Color(0xFF16A34A),
            department = "Ayurveda",
            highlights = "Chyawanprash, Immunity",
            subTags = listOf("Chyawanprash", "Ashwagandha", "Giloy")
        )
        else -> CategoryPresentation(
            icon = Icons.Default.Medication,
            iconTint = Color(0xFF059669),
            iconBg = Color(0xFFD1FAE5),
            cardBg = Color(0xFFF9FBFA),
            cardBorder = Color(0xFFA7F3D0),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
            badgeText = "CERTIFIED",
            badgeColor = Color(0xFF059669),
            department = "Medicines",
            highlights = "Certified Medicines",
            subTags = listOf("Essential", "Daily Meds", "Generic")
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
    CatalogCategory(id = "cat_baby_care", slug = "baby-care", name = "Baby & Mother Care", productCount = 11),
    CatalogCategory(id = "cat_ayurveda", slug = "ayurveda-herbal", name = "Ayurveda & Herbal Care", productCount = 19)
)

private val HEALTHCARE_DEPARTMENTS = listOf(
    "All",
    "Medicines",
    "Chronic Care",
    "Daily Wellness",
    "Devices & First Aid",
    "Personal Care",
    "Ayurveda"
)

/**
 * World-class Healthcare Category Directory matching Tata 1mg, PharmEasy, and Truemeds benchmarks.
 * Features therapy-themed pastel cards, real authentic clinical pack imagery, sub-condition tags,
 * department segmenter, promotional badges, and instant search filter.
 */
@Composable
fun CategoriesScreen(
    viewModel: CategoryViewModel,
    onSelectCategory: (CatalogCategory) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadTaxonomy()
    }

    var selectedDepartment by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    val rawCategories = when (val state = viewModel.taxonomy) {
        is CategoryTaxonomyUiState.Content -> {
            if (state.categories.isNotEmpty()) state.categories else DEFAULT_HEALTHCARE_CATEGORIES
        }
        else -> DEFAULT_HEALTHCARE_CATEGORIES
    }

    // Filter by department and search text
    val filteredCategories = remember(rawCategories, selectedDepartment, searchQuery) {
        rawCategories.filter { cat ->
            val pres = getPresentation(cat.name)
            val matchesDept = selectedDepartment == "All" || pres.department.equals(selectedDepartment, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                cat.name.contains(searchQuery, ignoreCase = true) ||
                pres.highlights.contains(searchQuery, ignoreCase = true) ||
                pres.subTags.any { it.contains(searchQuery, ignoreCase = true) }
            matchesDept && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // 1. Top Clinical Header & Search Refinement
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Shop by Category",
                        fontSize = 20.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A),
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "100% Genuine Medicines • 10-Min Delivery",
                        fontSize = 11.5.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF059669)
                    )
                }

                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFA7F3D0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Licensed Rx",
                            fontSize = 10.5.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Inline Search Filter Box
            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search categories, therapies, or symptoms...",
                                fontSize = 12.5.sp,
                                fontFamily = PlusJakartaSans,
                                color = Color(0xFF94A3B8)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Department Horizontal Filter Rail (Netmeds / 1mg benchmark)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(HEALTHCARE_DEPARTMENTS) { dept ->
                val isSelected = selectedDepartment == dept
                Surface(
                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.clickable { selectedDepartment = dept }
                ) {
                    Text(
                        text = dept,
                        fontSize = 11.5.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) Color.White else Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)

        // 3. Main Category Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // Optional: Hero "Shop by Health Concern" Spotlight (PharmEasy benchmark)
            if (selectedDepartment == "All" && searchQuery.isBlank()) {
                item(span = { GridItemSpan(2) }) {
                    HealthConcernSpotlight(
                        onSelectConcern = { concernName ->
                            val matched = rawCategories.firstOrNull { it.name.contains(concernName, ignoreCase = true) }
                            if (matched != null) {
                                onSelectCategory(matched)
                            } else {
                                onSelectCategory(
                                    CatalogCategory(
                                        id = "cat_${concernName.lowercase().replace(" ", "_")}",
                                        slug = concernName.lowercase().replace(" ", "-"),
                                        name = concernName,
                                        productCount = 10
                                    )
                                )
                            }
                        }
                    )
                }
            }

            if (filteredCategories.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = CircleShape,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No categories found",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching with a different health condition",
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            } else {
                items(filteredCategories) { cat ->
                    HealthcareCategoryCard(
                        category = cat,
                        onClick = { onSelectCategory(cat) },
                        onSelectTag = { tag ->
                            onSelectCategory(
                                CatalogCategory(
                                    id = "cat_${tag.lowercase().replace(" ", "_")}",
                                    slug = tag.lowercase().replace(" ", "-"),
                                    name = tag,
                                    productCount = 5
                                )
                            )
                        }
                    )
                }
            }

            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Curated Health Concern Spotlight Carousel (PharmEasy Benchmark)
 */
@Composable
private fun HealthConcernSpotlight(
    onSelectConcern: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "TOP HEALTH CONCERNS",
                fontSize = 11.5.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp
            )
            Text(
                text = "Doctor Curated",
                fontSize = 10.5.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF059669)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                ConcernPillCard(
                    title = "Diabetes",
                    sub = "Blood Sugar Under Control",
                    badge = "CHRONIC REFILL",
                    bgColor = Color(0xFFEEF2FF),
                    borderColor = Color(0xFFC7D2FE),
                    accentColor = Color(0xFF4F46E5),
                    imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg",
                    onClick = { onSelectConcern("Diabetes Care") }
                )
            }
            item {
                ConcernPillCard(
                    title = "Heart & BP",
                    sub = "Cardio Health Essentials",
                    badge = "DAILY TRACKING",
                    bgColor = Color(0xFFFFF1F2),
                    borderColor = Color(0xFFFECDD3),
                    accentColor = Color(0xFFE11D48),
                    imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/163836/telma-40mg-strip-of-30-tablets-front-2-1756884638-non-watermarked.jpg",
                    onClick = { onSelectConcern("Heart & Blood Pressure") }
                )
            }
            item {
                ConcernPillCard(
                    title = "Digestive Care",
                    sub = "Instant Acidity & Gas Relief",
                    badge = "FAST RELIEF",
                    bgColor = Color(0xFFFFFBEB),
                    borderColor = Color(0xFFFDE68A),
                    accentColor = Color(0xFFD97706),
                    imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg",
                    onClick = { onSelectConcern("Stomach & Digestion") }
                )
            }
        }
    }
}

@Composable
private fun ConcernPillCard(
    title: String,
    sub: String,
    badge: String,
    bgColor: Color,
    borderColor: Color,
    accentColor: Color,
    imageUrl: String,
    onClick: () -> Unit
) {
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .width(210.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.size(54.dp)
            ) {
                ProductImage(
                    imageUrl = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = badge,
                        fontSize = 8.5.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sub,
                    fontSize = 10.sp,
                    fontFamily = PlusJakartaSans,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Premium Healthcare Category Card (Tata 1mg & PharmEasy Benchmark)
 */
@Composable
private fun HealthcareCategoryCard(
    category: CatalogCategory,
    onClick: () -> Unit,
    onSelectTag: (String) -> Unit
) {
    val pres = getPresentation(category.name)

    Card(
        colors = CardDefaults.cardColors(containerColor = pres.cardBg),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, pres.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(198.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Promo Badge + Arrow Circle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = pres.iconTint.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = pres.badgeText,
                        fontSize = 9.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = pres.iconTint,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.85f),
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // Middle Row: Title + Product Pack Image
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        text = category.name,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.5.sp,
                        color = Color(0xFF0F172A),
                        minLines = 2,
                        maxLines = 2,
                        lineHeight = 17.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = pres.highlights,
                        fontSize = 10.5.sp,
                        fontFamily = PlusJakartaSans,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Authentic Clinical Product Pack Image
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.size(54.dp)
                ) {
                    ProductImage(
                        imageUrl = pres.imageUrl,
                        contentDescription = category.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                }
            }

            // Sub-tag micro pills (1mg Benchmark)
            if (pres.subTags.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    pres.subTags.take(2).forEach { tag ->
                        Surface(
                            color = Color.White.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, pres.cardBorder),
                            modifier = Modifier.clickable { onSelectTag(tag) }
                        ) {
                            Text(
                                text = tag,
                                fontSize = 9.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Row: Subcondition micro-chips or Verified product count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (category.productCount > 0) "${category.productCount}+ products" else "Express 10m",
                        fontSize = 10.5.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }

                Text(
                    text = "Explore ›",
                    fontSize = 11.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = pres.iconTint
                )
            }
        }
    }
}
