package com.commerceos.android.ui.categories

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.commerceos.android.model.CatalogCategory
import com.commerceos.android.model.HomeContext
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.home.HeaderCanopy
import com.commerceos.android.ui.theme.PlusJakartaSans
import com.commerceos.android.viewmodel.CategoryTaxonomyUiState
import com.commerceos.android.viewmodel.CategoryViewModel

data class HealthcareCategoryPresentation(
    val icon: ImageVector,
    val iconTint: Color,
    val cardGradientStart: Color,
    val cardGradientEnd: Color,
    val cardBorder: Color,
    val imageUrl: String,
    val badgeText: String,
    val badgeBg: Color,
    val department: String,
    val highlights: String,
    val subTags: List<String>
)

private fun getHealthcarePresentation(categoryName: String): HealthcareCategoryPresentation {
    val lower = categoryName.lowercase()
    return when {
        lower.contains("pain") || lower.contains("fever") -> HealthcareCategoryPresentation(
            icon = Icons.Default.Healing,
            iconTint = Color(0xFFE11D48),
            cardGradientStart = Color(0xFFFFF1F2),
            cardGradientEnd = Color(0xFFFFE4E6),
            cardBorder = Color(0xFFFECDD3),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
            badgeText = "⚡ 10-MIN SLA",
            badgeBg = Color(0xFFFFE4E6),
            department = "Medicines",
            highlights = "Paracetamol, Dolo, Balms",
            subTags = listOf("Dolo 650", "Paracetamol", "Pain Balms", "Volini")
        )
        lower.contains("cold") || lower.contains("cough") -> HealthcareCategoryPresentation(
            icon = Icons.Default.Thermostat,
            iconTint = Color(0xFF0284C7),
            cardGradientStart = Color(0xFFF0FDF4),
            cardGradientEnd = Color(0xFFE0F2FE),
            cardBorder = Color(0xFFBAE6FD),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
            badgeText = "MUST HAVE",
            badgeBg = Color(0xFFE0F2FE),
            department = "Medicines",
            highlights = "Syrups, Lozenges, Inhalers",
            subTags = listOf("Benadryl", "Cough Syrups", "Lozenges", "Steam Inhaler")
        )
        lower.contains("diabetes") -> HealthcareCategoryPresentation(
            icon = Icons.Default.Bloodtype,
            iconTint = Color(0xFF4F46E5),
            cardGradientStart = Color(0xFFEEF2FF),
            cardGradientEnd = Color(0xFFE0E7FF),
            cardBorder = Color(0xFFC7D2FE),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg",
            badgeText = "CHRONIC REFILL",
            badgeBg = Color(0xFFEEF2FF),
            department = "Chronic Care",
            highlights = "Glucometers, Strips, Care",
            subTags = listOf("Glycomet", "Metformin", "Glucometer", "Sugar Free")
        )
        lower.contains("antibiotic") || lower.contains("infect") -> HealthcareCategoryPresentation(
            icon = Icons.Default.Science,
            iconTint = Color(0xFF0D9488),
            cardGradientStart = Color(0xFFF0FDFA),
            cardGradientEnd = Color(0xFFCCFBF1),
            cardBorder = Color(0xFF99F6E4),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg",
            badgeText = "Rx PRESCRIBED",
            badgeBg = Color(0xFFCCFBF1),
            department = "Medicines",
            highlights = "Amoxiclav, Azithral",
            subTags = listOf("Augmentin 625", "Azithral", "Ointments", "Antiseptic")
        )
        lower.contains("vitamin") || lower.contains("nutrition") -> HealthcareCategoryPresentation(
            icon = Icons.Default.FitnessCenter,
            iconTint = Color(0xFFD97706),
            cardGradientStart = Color(0xFFFFFBEB),
            cardGradientEnd = Color(0xFFFEF3C7),
            cardBorder = Color(0xFFFDE68A),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg",
            badgeText = "UP TO 40% OFF",
            badgeBg = Color(0xFFFEF3C7),
            department = "Daily Wellness",
            highlights = "Multivitamins, Zinc, Calcium",
            subTags = listOf("Becosules", "Vitamin C", "Zincovit", "Calcium D3")
        )
        lower.contains("stomach") || lower.contains("digest") || lower.contains("gas") -> HealthcareCategoryPresentation(
            icon = Icons.Default.LocalPharmacy,
            iconTint = Color(0xFF059669),
            cardGradientStart = Color(0xFFF0FDF4),
            cardGradientEnd = Color(0xFFDCFCE7),
            cardBorder = Color(0xFFBBF7D0),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg",
            badgeText = "FAST RELIEF",
            badgeBg = Color(0xFFDCFCE7),
            department = "Medicines",
            highlights = "Digene, Antacids, ORS",
            subTags = listOf("Digene", "Pan-D", "Eno", "Probiotics")
        )
        lower.contains("heart") || lower.contains("cardiac") || lower.contains("bp") -> HealthcareCategoryPresentation(
            icon = Icons.Default.Favorite,
            iconTint = Color(0xFFDB2777),
            cardGradientStart = Color(0xFFFDF2F8),
            cardGradientEnd = Color(0xFFFCE7F3),
            cardBorder = Color(0xFFFBCFE8),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/163836/telma-40mg-strip-of-30-tablets-front-2-1756884638-non-watermarked.jpg",
            badgeText = "DOCTOR PICKS",
            badgeBg = Color(0xFFFCE7F3),
            department = "Chronic Care",
            highlights = "BP Monitors, Heart Health",
            subTags = listOf("Telma 40", "BP Monitor", "Ecosprin 75", "Omega 3")
        )
        lower.contains("first aid") || lower.contains("wound") || lower.contains("bandage") -> HealthcareCategoryPresentation(
            icon = Icons.Default.MedicalServices,
            iconTint = Color(0xFFDC2626),
            cardGradientStart = Color(0xFFFEF2F2),
            cardGradientEnd = Color(0xFFFEE2E2),
            cardBorder = Color(0xFFFECACA),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/065110/electral-powder-218g-2-1671741180.jpg",
            badgeText = "EMERGENCY KIT",
            badgeBg = Color(0xFFFEE2E2),
            department = "Devices & First Aid",
            highlights = "Bandages, Dettol, ORS",
            subTags = listOf("Electral ORS", "Dettol", "Bandages", "Thermometer")
        )
        lower.contains("skin") || lower.contains("hair") || lower.contains("derma") -> HealthcareCategoryPresentation(
            icon = Icons.Default.Face,
            iconTint = Color(0xFF9333EA),
            cardGradientStart = Color(0xFFFAF5FF),
            cardGradientEnd = Color(0xFFF3E8FF),
            cardBorder = Color(0xFFE9D5FF),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/I40695/cetaphil-gentle-skin-cleanser-125ml-2-1671741005.jpg",
            badgeText = "CLINICAL DERMA",
            badgeBg = Color(0xFFF3E8FF),
            department = "Personal Care",
            highlights = "Derma Cleansers, Sunscreen",
            subTags = listOf("Cetaphil", "Sunscreen", "Anti-Fungal", "Moisturizer")
        )
        lower.contains("baby") || lower.contains("mother") -> HealthcareCategoryPresentation(
            icon = Icons.Default.ChildCare,
            iconTint = Color(0xFF2563EB),
            cardGradientStart = Color(0xFFF0F9FF),
            cardGradientEnd = Color(0xFFE0F2FE),
            cardBorder = Color(0xFFBAE6FD),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/159115/sebamed-baby-cleansing-bar-100g-2-1671741113.jpg",
            badgeText = "GENTLE CARE",
            badgeBg = Color(0xFFE0F2FE),
            department = "Personal Care",
            highlights = "Diapers, Wipes, Gripe Water",
            subTags = listOf("Sebamed", "Diapers", "Baby Wipes", "Gripe Water")
        )
        lower.contains("ayurved") || lower.contains("herbal") -> HealthcareCategoryPresentation(
            icon = Icons.Default.Eco,
            iconTint = Color(0xFF16A34A),
            cardGradientStart = Color(0xFFF2FBF5),
            cardGradientEnd = Color(0xFFDCFCE7),
            cardBorder = Color(0xFFB8ECCD),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/054720/dabur-chyawanprash-2x-immunity-1kg-2-1671741076.jpg",
            badgeText = "100% NATURAL",
            badgeBg = Color(0xFFDCFCE7),
            department = "Ayurveda",
            highlights = "Chyawanprash, Immunity Herbs",
            subTags = listOf("Chyawanprash", "Ashwagandha", "Giloy", "Pure Honey")
        )
        lower.contains("women") || lower.contains("fem") -> HealthcareCategoryPresentation(
            icon = Icons.Default.Spa,
            iconTint = Color(0xFFE11D48),
            cardGradientStart = Color(0xFFFFF1F2),
            cardGradientEnd = Color(0xFFFFE4E6),
            cardBorder = Color(0xFFFECDD3),
            imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/K78299/shelcal-500mg-bottle-of-30-tablets-6.1-1787223246.jpg",
            badgeText = "WELLNESS CARE",
            badgeBg = Color(0xFFFFE4E6),
            department = "Personal Care",
            highlights = "Calcium, Iron, Intimate Care",
            subTags = listOf("Shelcal", "Sanitary Pads", "Iron Folic", "Intimate Wash")
        )
        else -> HealthcareCategoryPresentation(
            icon = Icons.Default.LocalPharmacy,
            iconTint = Color(0xFF007E65),
            cardGradientStart = Color(0xFFF0FDF4),
            cardGradientEnd = Color(0xFFDCFCE7),
            cardBorder = Color(0xFFA7F3D0),
            imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
            badgeText = "CERTIFIED",
            badgeBg = Color(0xFFDCFCE7),
            department = "Medicines",
            highlights = "Certified Essential Care",
            subTags = listOf("Essential", "Daily Meds", "Generic")
        )
    }
}

private val DEFAULT_HEALTHCARE_CATEGORIES = listOf(
    CatalogCategory(id = "cat_pain_fever", slug = "pain-fever", name = "Pain & Fever", productCount = 18),
    CatalogCategory(id = "cat_cold_cough", slug = "cold-cough", name = "Cold & Cough", productCount = 14),
    CatalogCategory(id = "cat_diabetes_care", slug = "diabetes-care", name = "Diabetes Care", productCount = 12),
    CatalogCategory(id = "cat_vitamins", slug = "vitamins-nutrition", name = "Vitamins & Daily Wellness", productCount = 24),
    CatalogCategory(id = "cat_digestive", slug = "digestive-health", name = "Stomach & Digestion", productCount = 16),
    CatalogCategory(id = "cat_cardiac", slug = "cardiac-care", name = "Heart & Blood Pressure", productCount = 8),
    CatalogCategory(id = "cat_antibiotics", slug = "antibiotics", name = "Antibiotics", productCount = 9),
    CatalogCategory(id = "cat_skin_care", slug = "skin-care", name = "Skin & Dermatology", productCount = 15),
    CatalogCategory(id = "cat_ayurveda", slug = "ayurveda-herbal", name = "Ayurveda & Herbal Care", productCount = 19),
    CatalogCategory(id = "cat_women_care", slug = "women-care", name = "Women's Health & Care", productCount = 13),
    CatalogCategory(id = "cat_first_aid", slug = "first-aid", name = "First Aid & Essentials", productCount = 20),
    CatalogCategory(id = "cat_baby_care", slug = "baby-care", name = "Baby & Mother Care", productCount = 11)
)

private val HEALTHCARE_DEPARTMENTS = listOf(
    "All Categories",
    "Medicines",
    "Chronic Care",
    "Daily Wellness",
    "Ayurveda",
    "Personal Care",
    "Devices & First Aid"
)

private data class QuickPromoItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector,
    val iconTint: Color,
    val bgStart: Color,
    val bgEnd: Color,
    val borderColor: Color,
    val query: String
)

private val QUICK_PROMO_ITEMS = listOf(
    QuickPromoItem(
        title = "Most Bought",
        subtitle = "Daily Essentials",
        badge = "⚡ 10m SLA",
        icon = Icons.Default.ShoppingBag,
        iconTint = Color(0xFF007E65),
        bgStart = Color(0xFFE6F7F0),
        bgEnd = Color(0xFFD1F2E2),
        borderColor = Color(0xFFB5EAD7),
        query = "Dolo 650"
    ),
    QuickPromoItem(
        title = "Deals Of The Day",
        subtitle = "Up to 60% OFF",
        badge = "MEGA SALE",
        icon = Icons.Default.Percent,
        iconTint = Color(0xFF0284C7),
        bgStart = Color(0xFFE5F6FA),
        bgEnd = Color(0xFFCEEFF9),
        borderColor = Color(0xFFB2E5F3),
        query = "Vitamins"
    ),
    QuickPromoItem(
        title = "Health Checkups",
        subtitle = "Free Home Pickup",
        badge = "NABL LABS",
        icon = Icons.Default.Biotech,
        iconTint = Color(0xFF4F46E5),
        bgStart = Color(0xFFEEF2FF),
        bgEnd = Color(0xFFE0E7FF),
        borderColor = Color(0xFFC7D2FE),
        query = "Full Body Checkup"
    ),
    QuickPromoItem(
        title = "Generic Switch",
        subtitle = "Save up to 40%",
        badge = "SAME SALT",
        icon = Icons.Default.SwapHoriz,
        iconTint = Color(0xFFD97706),
        bgStart = Color(0xFFFEF3C7),
        bgEnd = Color(0xFFFDE68A),
        borderColor = Color(0xFFFCD34D),
        query = "Paracip"
    )
)

private data class FeaturedBrand(
    val name: String,
    val tagline: String,
    val badge: String,
    val accentColor: Color,
    val query: String
)

private val FEATURED_BRANDS = listOf(
    FeaturedBrand("Cipla", "Caring for Life", "Up to 30% OFF", Color(0xFF0284C7), "Cipla"),
    FeaturedBrand("Abbott", "A Promise for Life", "Best Deals", Color(0xFF0D9488), "Abbott"),
    FeaturedBrand("Dabur", "100% Ayurvedic", "Immunity Care", Color(0xFF16A34A), "Dabur"),
    FeaturedBrand("Sun Pharma", "Global Health", "Doctor Preferred", Color(0xFFE11D48), "Sun Pharma"),
    FeaturedBrand("Dr. Reddy's", "Good Health Can't Wait", "Up to 35% OFF", Color(0xFF4F46E5), "Dr. Reddy"),
    FeaturedBrand("Himalaya", "Herbal Wellness", "Pure Herbs", Color(0xFF059669), "Himalaya"),
    FeaturedBrand("Micro Labs", "Dolo 650 Makers", "Fast Relief", Color(0xFFD97706), "Micro Labs")
)

data class DepartmentColorPalette(
    val topGradient: Color,
    val bottomGradient: Color,
    val selectedChipColor: Color
)

fun getDepartmentColors(department: String): DepartmentColorPalette {
    return when (department.lowercase().trim()) {
        "chronic care" -> DepartmentColorPalette(
            topGradient = Color(0xFF1E3A8A), // Deep Royal Navy Blue
            bottomGradient = Color(0xFF1D4ED8),
            selectedChipColor = Color(0xFF1E3A8A)
        )
        "daily wellness" -> DepartmentColorPalette(
            topGradient = Color(0xFF0F766E), // Deep Teal
            bottomGradient = Color(0xFF0D9488),
            selectedChipColor = Color(0xFF0F766E)
        )
        "ayurveda" -> DepartmentColorPalette(
            topGradient = Color(0xFF14532D), // Deep Herbal Forest Green
            bottomGradient = Color(0xFF166534),
            selectedChipColor = Color(0xFF14532D)
        )
        "personal care" -> DepartmentColorPalette(
            topGradient = Color(0xFF831843), // Rich Magenta / Plum
            bottomGradient = Color(0xFF9D174D),
            selectedChipColor = Color(0xFF831843)
        )
        else -> DepartmentColorPalette( // "All Categories", "Medicines", etc.
            topGradient = Color(0xFF005844), // Classic PharmEasy Emerald
            bottomGradient = Color(0xFF067A5E),
            selectedChipColor = Color(0xFF005844)
        )
    }
}

/**
 * Flagship Healthcare Category Hub matching and exceeding PharmEasy & Tata 1mg benchmarks.
 * Features:
 * - PharmEasy Deep Emerald Header with live address locator & camera prescription quick trigger.
 * - "SHOP SMARTER, SAVE BIGGER" 3D-styled interactive healthcare tiles.
 * - Fast-Track Order Strip ("OR YOU CAN ORDER VIA" WhatsApp, Prescription, Call).
 * - "SHOP BY HEALTH CONCERN" dual-column pastel gradient cards with authentic high-res medicine packs.
 * - Instant symptom lookup pills and department filter segmenter.
 * - Truemeds / PharmEasy "Generic Switch & Save up to 40%" trust card.
 * - Trusted Indian pharmaceutical brand stores.
 */
@Composable
fun CategoriesScreen(
    viewModel: CategoryViewModel,
    homeContext: HomeContext? = null,
    selectedAddress: ApiAddress? = null,
    calculatedEtaMinutes: Int = 0,
    locationHeaderLabel: String? = null,
    isLocating: Boolean = false,
    onChangeAddress: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onSearchClick: (String) -> Unit = {},
    onUploadPrescription: () -> Unit = {},
    onSelectCategory: (CatalogCategory) -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadTaxonomy()
    }

    var selectedDepartment by remember { mutableStateOf("All Categories") }
    var searchQuery by remember { mutableStateOf("") }

    val currentPalette = remember(selectedDepartment) { getDepartmentColors(selectedDepartment) }

    val rawCategories = when (val state = viewModel.taxonomy) {
        is CategoryTaxonomyUiState.Content -> {
            if (state.categories.isNotEmpty()) state.categories else DEFAULT_HEALTHCARE_CATEGORIES
        }
        else -> DEFAULT_HEALTHCARE_CATEGORIES
    }

    // Filter categories by department and search query
    val filteredCategories = remember(rawCategories, selectedDepartment, searchQuery) {
        rawCategories.filter { cat ->
            val pres = getHealthcarePresentation(cat.name)
            val matchesDept = selectedDepartment == "All Categories" ||
                pres.department.equals(selectedDepartment, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                cat.name.contains(searchQuery, ignoreCase = true) ||
                pres.highlights.contains(searchQuery, ignoreCase = true) ||
                pres.subTags.any { it.contains(searchQuery, ignoreCase = true) }
            matchesDept && matchesSearch
        }
    }

    fun handleTrigger(queryText: String) {
        if (queryText.equals("Upload Prescription", ignoreCase = true)) {
            onUploadPrescription()
            return
        }
        val matched = rawCategories.firstOrNull { it.name.contains(queryText, ignoreCase = true) }
        if (matched != null) {
            onSelectCategory(matched)
        } else {
            onSelectCategory(
                CatalogCategory(
                    id = "cat_${queryText.lowercase().replace(" ", "_")}",
                    slug = queryText.lowercase().replace(" ", "-"),
                    name = queryText,
                    productCount = 10
                )
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8))
    ) {
        // ====================================================================
        // 1. UNIFIED HEALTHCARE EMERALD HEADER (100% shared with Home Screen)
        // ====================================================================
        HeaderCanopy(
            homeContext = homeContext,
            selectedAddress = selectedAddress,
            calculatedEtaMinutes = calculatedEtaMinutes,
            locationHeaderLabel = locationHeaderLabel,
            isLocating = isLocating,
            onChangeAddress = onChangeAddress,
            onProfileClick = onProfileClick,
            onNotificationsClick = onNotificationsClick,
            onSearchClick = onSearchClick,
            onUploadPrescription = onUploadPrescription
        )

        // ====================================================================
        // 2. SCROLLABLE CONTENT BODY
        // ====================================================================
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            // Big Deals, Small Bills Ribbon Banner (PharmEasy Benchmark - moved below Address Bar)
            item {
                Surface(
                    color = Color(0xFFFEF3C7),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = Color(0xFFD97706),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "BIG DEALS",
                                fontSize = 9.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "UPTO 26% OFF ON MEDICINES • Extra ₹500 credits",
                            fontSize = 11.5.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            // Section A: "SHOP SMARTER, SAVE BIGGER" (PharmEasy Benchmark)
            if (searchQuery.isBlank() && selectedDepartment == "All Categories") {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(top = 14.dp, bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SHOP SMARTER, SAVE BIGGER",
                                fontSize = 12.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF475569),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "100% Genuine",
                                fontSize = 11.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF007E65)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(QUICK_PROMO_ITEMS) { item ->
                                Surface(
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(18.dp),
                                    border = BorderStroke(1.dp, item.borderColor),
                                    modifier = Modifier
                                        .width(135.dp)
                                        .clickable { handleTrigger(item.query) }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(item.bgStart, item.bgEnd)
                                                )
                                            )
                                            .padding(10.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = Color.White,
                                                shape = CircleShape,
                                                shadowElevation = 1.dp,
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        item.icon,
                                                        contentDescription = null,
                                                        tint = item.iconTint,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = item.iconTint.copy(alpha = 0.12f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = item.badge,
                                                    fontSize = 8.sp,
                                                    fontFamily = PlusJakartaSans,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = item.iconTint,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = item.title,
                                            fontSize = 12.5.sp,
                                            fontFamily = PlusJakartaSans,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            fontSize = 10.sp,
                                            fontFamily = PlusJakartaSans,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF64748B),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section B: "OR YOU CAN ORDER VIA" FAST-TRACK STRIP
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                            Text(
                                text = "OR YOU CAN ORDER VIA",
                                fontSize = 10.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8),
                                letterSpacing = 0.5.sp
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFE2E8F0))
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // WhatsApp
                            Surface(
                                color = Color(0xFFF0FDF4),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { handleTrigger("WhatsApp Order") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Chat,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "WhatsApp",
                                        fontSize = 11.sp,
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534)
                                    )
                                }
                            }

                            // Prescription
                            Surface(
                                color = Color(0xFFFFF7ED),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFFED7AA)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { handleTrigger("Upload Prescription") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = null,
                                        tint = Color(0xFFEA580C),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Upload Rx",
                                        fontSize = 11.sp,
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF9A3412)
                                    )
                                }
                            }

                            // Call
                            Surface(
                                color = Color(0xFFEFF6FF),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { handleTrigger("Doctor Consultation") }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = null,
                                        tint = Color(0xFF2563EB),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Call Order",
                                        fontSize = 11.sp,
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF1E40AF)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Section C: Department Horizontal Filter Rail
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(vertical = 8.dp)
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(HEALTHCARE_DEPARTMENTS) { dept ->
                            val isSelected = selectedDepartment == dept
                            val deptPal = getDepartmentColors(dept)
                            Surface(
                                color = if (isSelected) deptPal.selectedChipColor else Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) deptPal.selectedChipColor else Color(0xFFE2E8F0)
                                ),
                                modifier = Modifier.clickable { selectedDepartment = dept }
                            ) {
                                Text(
                                    text = dept,
                                    fontSize = 12.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) Color.White else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
            }

            // Section D: "SHOP BY HEALTH CONCERN" Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (selectedDepartment == "All Categories") "Shop by Health Concern" else selectedDepartment,
                            fontSize = 17.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "${filteredCategories.size} health categories available",
                            fontSize = 11.sp,
                            fontFamily = PlusJakartaSans,
                            color = Color(0xFF64748B)
                        )
                    }

                    Surface(
                        color = Color(0xFFECFDF5),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFA7F3D0))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Doctor Curated",
                                fontSize = 10.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF065F46)
                            )
                        }
                    }
                }
            }

            // Section E: 2-Column Category Grid
            if (filteredCategories.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = CircleShape,
                            modifier = Modifier.size(60.dp)
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
                            text = "No health categories found",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try searching with a symptom like 'fever', 'cough' or 'diabetes'",
                            fontFamily = PlusJakartaSans,
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                searchQuery = ""
                                selectedDepartment = "All Categories"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = currentPalette.selectedChipColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Reset Filters",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                // Chunk into pairs of 2 for grid layout inside LazyColumn
                val pairs = filteredCategories.chunked(2)
                items(pairs) { rowItems ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (cat in rowItems) {
                            Box(modifier = Modifier.weight(1f)) {
                                PharmEasyCategoryCard(
                                    category = cat,
                                    onClick = { onSelectCategory(cat) },
                                    onSelectTag = { tag -> handleTrigger(tag) }
                                )
                            }
                        }
                        // If odd number, put empty spacer
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Section F: Generic Substitutes Value Proposition (Truemeds & PharmEasy Signature)
            if (searchQuery.isBlank() && selectedDepartment == "All Categories") {
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(0xFFF0FDF4),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .clickable { handleTrigger("Generic Equivalents") }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = Color(0xFF007E65),
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Save Up to 40% on Medicines",
                                        fontSize = 13.5.sp,
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Surface(
                                        color = Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "WHO-GMP",
                                            fontSize = 8.5.sp,
                                            fontFamily = PlusJakartaSans,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF166534),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Switch to certified generic equivalents with identical salt composition (e.g. Paracip instead of Dolo).",
                                    fontSize = 11.sp,
                                    fontFamily = PlusJakartaSans,
                                    color = Color(0xFF475569),
                                    lineHeight = 15.sp
                                )
                            }

                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFF007E65),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Section G: Featured Pharmaceutical Brand Stores
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOP PHARMACEUTICAL BRANDS",
                                fontSize = 12.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF475569),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Verified Stores",
                                fontSize = 11.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF007E65)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(FEATURED_BRANDS) { brand ->
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier
                                        .width(150.dp)
                                        .clickable { handleTrigger(brand.query) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                color = brand.accentColor.copy(alpha = 0.12f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = brand.name.take(1),
                                                        fontSize = 14.sp,
                                                        fontFamily = PlusJakartaSans,
                                                        fontWeight = FontWeight.Black,
                                                        color = brand.accentColor
                                                    )
                                                }
                                            }

                                            Surface(
                                                color = Color(0xFFECFDF5),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = brand.badge,
                                                    fontSize = 8.5.sp,
                                                    fontFamily = PlusJakartaSans,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF059669),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = brand.name,
                                            fontSize = 13.5.sp,
                                            fontFamily = PlusJakartaSans,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = brand.tagline,
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
                    }
                }

                // Section H: Healthcare Quality Assurance Banner
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFF007E65),
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "100% Genuine Medicines Guarantee",
                                    fontSize = 12.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Sourced directly from licensed distributors • Temperature controlled cold-chain delivery.",
                                    fontSize = 10.5.sp,
                                    fontFamily = PlusJakartaSans,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Flagship Healthcare Category Card directly matching PharmEasy's Soft Pastel Medical Aesthetic.
 * Features:
 * - Dual-tone soft pastel healthcare gradient (Mint, Ice Sky, Citrus, Coral, Rose, Lavender).
 * - High-definition authentic clinical pack imagery (Dolo 650, Digene, Glycomet, Benadryl, etc.).
 * - Prominent 15.5sp ExtraBold therapy title.
 * - SLA and promotional pill badges (⚡ 10-MIN SLA, CHRONIC REFILL, UP TO 40% OFF).
 * - Interactive micro-tags for instant symptom filtering.
 */
@Composable
private fun PharmEasyCategoryCard(
    category: CatalogCategory,
    onClick: () -> Unit,
    onSelectTag: (String) -> Unit
) {
    val pres = getHealthcarePresentation(category.name)

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Prominent category visual in soft pastel container
            Surface(
                color = pres.cardGradientStart,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, pres.cardBorder.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    ProductImage(
                        imageUrl = pres.imageUrl,
                        contentDescription = category.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = category.name,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = pres.highlights,
                fontSize = 11.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = pres.badgeBg,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = pres.badgeText,
                    fontSize = 9.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = pres.iconTint,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                )
            }
        }
    }
}
