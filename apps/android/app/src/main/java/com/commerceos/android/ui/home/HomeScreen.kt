package com.commerceos.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.commerceos.android.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.model.ApiMedicine
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.model.CartItem
import com.commerceos.android.model.CommerceEntity
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.HomeContext
import com.commerceos.android.model.HomeDestination
import com.commerceos.android.model.HomeHeroDto
import com.commerceos.android.model.HomeSection
import com.commerceos.android.model.HomeSectionType
import com.commerceos.android.model.HomeVertical
import com.commerceos.android.model.toProductCardModel
import com.commerceos.android.ui.components.CommerceEntityRenderer
import com.commerceos.android.ui.components.CommerceProductCard
import com.commerceos.android.ui.components.ElectronicsProductCard
import com.commerceos.android.ui.components.FashionProductCard
import com.commerceos.android.ui.components.GroceryProductCard
import com.commerceos.android.ui.components.PharmacyProductCard
import com.commerceos.android.ui.components.ProductCardVariant
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.components.SkeletonProductImage
import com.commerceos.android.ui.components.SkeletonText
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.HomeViewModel

/**
 * Commerce OS Home. Renders the SERVER-AUTHORED merchandising home feed verbatim as [HomeSection]s.
 *
 * First viewport answers key consumer intent in order:
 * 1. Delivery location (where am I shopping)
 * 2. Universal Search (what am I looking for)
 * 3. Vertical Discovery (which store/vertical)
 * 4. Personalized Feed & Merchandising (Campaigns, Buy Again, Fast Near You, Deals, Recommendations)
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    customerId: String,
    cartItems: List<CartItem> = emptyList(),
    homeContext: HomeContext? = null,
    selectedAddress: ApiAddress? = null,
    calculatedEtaMinutes: Int = 0,
    locationHeaderLabel: String? = null,
    isLocating: Boolean = false,
    onChangeAddress: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onEntityClick: (CommerceEntity) -> Unit = {},
    onAddToCart: (CommerceProduct) -> Unit = {},
    onUpdateQuantity: (String, Int) -> Unit = { _, _ -> },
    onSearchClick: (String) -> Unit = {},
    onVerticalSelect: (HomeVertical) -> Unit = {},
    onOpenCatalog: () -> Unit = {},
    onUploadPrescription: () -> Unit = {}
) {
    // Feed is keyed on (customer, delivery address): changing location MUST
    // invalidate and refetch inventory/ETA/promotions for the new address.
    LaunchedEffect(customerId, homeContext?.addressId) {
        viewModel.loadHomeData(customerId, homeContext?.addressId)
    }

    val cartQuantityMap = remember(cartItems) {
        cartItems.associate { (if (it.sku.isNotBlank()) it.sku else it.productId) to it.quantity }
    }

    val isLoading = viewModel.isLoading
    val sections = viewModel.sections
    val homeError = viewModel.errorMessage

    val nonHealthSections = remember {
        setOf(
            HomeSectionType.CATEGORY_GRID,
            HomeSectionType.RESTAURANT_SHELF,
            HomeSectionType.SERVICE_SHELF,
            HomeSectionType.DISH_SHELF
        )
    }
    val filteredSections = remember(sections) {
        sections.filter { it.type !in nonHealthSections }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DeliveryAddressWidget(
                    context = homeContext,
                    selectedAddress = selectedAddress,
                    calculatedEtaMinutes = calculatedEtaMinutes,
                    locationHeaderLabel = locationHeaderLabel,
                    isLocating = isLocating,
                    onChangeAddress = onChangeAddress,
                    onProfileClick = onProfileClick
                )
            }

            item {
                UniversalSearchBar(
                    onClick = { onSearchClick("") }
                )
            }

            item {
                PrescriptionQuickStrip(
                    onUploadPrescription = onUploadPrescription
                )
            }

            item {
                MedicineCategoryRail(
                    onSelectCategory = { categoryQuery -> onSearchClick(categoryQuery) }
                )
            }

            if (isLoading && sections.isEmpty()) {
                item {
                    LoadingSkeletonWidget()
                }
            }

            if (homeError != null && !isLoading && sections.isEmpty()) {
                item {
                    ErrorRetryWidget(onRetry = { viewModel.loadHomeData(customerId, homeContext?.addressId, forceRefresh = true) })
                }
            }

            items(filteredSections) { section ->
                HomeSectionRenderer(
                    section = section,
                    cartQuantityMap = cartQuantityMap,
                    onEntityClick = onEntityClick,
                    onAddToCart = onAddToCart,
                    onUpdateQuantity = onUpdateQuantity,
                    onOpenCatalog = onOpenCatalog
                )
            }

            // Bottom space for floating cart
            item {
                Spacer(modifier = Modifier.height(if (cartItems.isNotEmpty()) 80.dp else 24.dp))
            }
        }
    }
}

private data class MedicalCategory(
    val name: String,
    val query: String,
    val imageUrl: String
)

@Composable
private fun MedicineCategoryRail(
    onSelectCategory: (String) -> Unit
) {
    val categories = remember {
        listOf(
            MedicalCategory(
                name = "Pain & Fever",
                query = "Pain & Fever",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg"
            ),
            MedicalCategory(
                name = "Cold & Cough",
                query = "Cold & Cough",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg"
            ),
            MedicalCategory(
                name = "Diabetes",
                query = "Diabetes",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg"
            ),
            MedicalCategory(
                name = "Antibiotics",
                query = "Antibiotics",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg"
            ),
            MedicalCategory(
                name = "Vitamins",
                query = "Vitamins",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg"
            ),
            MedicalCategory(
                name = "Acidity & Gas",
                query = "Acidity & Gas",
                imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg"
            )
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Popular Categories",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827),
            modifier = Modifier.padding(bottom = 10.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(categories) { cat ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(80.dp)
                        .clickable { onSelectCategory(cat.query) }
                ) {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.size(72.dp)
                    ) {
                        ProductImage(
                            imageUrl = cat.imageUrl,
                            contentDescription = cat.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = cat.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 1-Tap Prescription Quick Upload Strip (Tata 1mg / PharmEasy / Truemeds pattern).
 */
@Composable
private fun PrescriptionQuickStrip(
    onUploadPrescription: () -> Unit
) {
    Surface(
        color = Color(0xFFECFDF5),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onUploadPrescription)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6EE7B7)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📄", fontSize = 18.sp)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Order with Prescription",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF065F46)
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "Upload doctor's slip & get 10-min delivery",
                    fontSize = 11.sp,
                    color = Color(0xFF047857)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = Color(0xFF0C831F),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable(onClick = onUploadPrescription)
            ) {
                Text(
                    text = "Upload",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fixed first-viewport chrome (Healthcare-Optimized Header)
// ---------------------------------------------------------------------------

@Composable
private fun DeliveryAddressWidget(
    context: HomeContext?,
    selectedAddress: ApiAddress? = null,
    calculatedEtaMinutes: Int = 0,
    locationHeaderLabel: String? = null,
    isLocating: Boolean = false,
    onChangeAddress: () -> Unit,
    onProfileClick: () -> Unit
) {
    val hasAddress = context?.hasAddress == true || selectedAddress != null || !locationHeaderLabel.isNullOrBlank()

    val displayAddress = if (!locationHeaderLabel.isNullOrBlank() && locationHeaderLabel != "Select Delivery Location") {
        locationHeaderLabel
    } else if (hasAddress) {
        val tag = selectedAddress?.tag?.ifBlank { "Home" } ?: "Home"
        val line = selectedAddress?.addressLine?.takeIf { it.isNotBlank() }
            ?: context?.displayLabel?.takeIf { it.isNotBlank() }
            ?: "Current Location"
        "$tag - $line"
    } else {
        "Current Location"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Location and Delivery ETA
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onChangeAddress)
        ) {
            if (isLocating && selectedAddress == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Detecting location...",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(11.dp),
                        color = Color(0xFF059669),
                        strokeWidth = 1.5.dp
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "Finding closest delivery pinpoint...",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayAddress,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Select Location",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasAddress && calculatedEtaMinutes > 0) {
                        Text(
                            text = "Delivering in ",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = "$calculatedEtaMinutes mins",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    } else if (hasAddress && !context?.formattedEta.isNullOrBlank()) {
                        Text(
                            text = "Delivering in ",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        Text(
                            text = context!!.formattedEta!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF059669)
                        )
                    } else {
                        Text(
                            text = "Tap to choose delivery location & view ETA",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Action: User profile
        Surface(
            color = Color(0xFFF1F5F9),
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .size(38.dp)
                .clickable(onClick = onProfileClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Account",
                    tint = Color(0xFF334155),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun UniversalSearchBar(
    onClick: () -> Unit
) {
    val searchHints = remember {
        listOf(
            "Search medicines, e.g. Dolo 650...",
            "Search for Paracetamol, Crocin...",
            "Search for Augmentin 625 Duo...",
            "Search for Benadryl Cough syrup...",
            "Search for Glycomet, Insulin...",
            "Search for Pan 40, Digene..."
        )
    }
    var hintIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            hintIndex = (hintIndex + 1) % searchHints.size
        }
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = "Search",
                tint = Color(0xFF0C831F),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = hintIndex,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "SearchHintAnimation"
                ) { targetIndex ->
                    Text(
                        text = searchHints[targetIndex],
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = "Voice Search",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_camera),
                contentDescription = "Camera Search",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}



// ---------------------------------------------------------------------------
// Server-authored feed sections
// ---------------------------------------------------------------------------

@Composable
fun HeroCampaignWidget(hero: HomeHeroDto, onCta: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF064E3B),
                        Color(0xFF047857),
                        Color(0xFF0C831F)
                    )
                ),
                RoundedCornerShape(18.dp)
            )
    ) {
        // Editorial Campaign Photography / Backdrop
        if (!hero.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.52f)
                    .align(Alignment.CenterEnd)
            ) {
                ProductImage(
                    imageUrl = hero.imageUrl,
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF064E3B),
                                    Color(0xFF064E3B).copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.70f)
                .padding(18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = hero.badge.ifBlank { "⚡ 10-MIN PHARMACY" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hero.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 22.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = hero.subtitle,
                fontSize = 11.sp,
                color = Color(0xFFD1FAE5),
                maxLines = 2,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = hero.ctaText.ifBlank { "Shop now" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF064E3B)
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Loading / error states
// ---------------------------------------------------------------------------

@Composable
private fun LoadingSkeletonWidget() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        // 1. Shimmering Hero Banner Placeholder
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(130.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SkeletonText(width = 160.dp, height = 16.dp)
            }
        }

        // 2. Row of Category Circular Icons (Blinkit / Zepto pattern)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonProductImage(
                        modifier = Modifier.size(60.dp),
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonText(width = 44.dp, height = 10.dp)
                }
            }
        }

        // 3. Shimmering Product Cards
        repeat(3) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(modifier = Modifier.padding(Spacing.md)) {
                    SkeletonProductImage(
                        modifier = Modifier.size(96.dp),
                        shape = RoundedCornerShape(Radius.ImageTile)
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SkeletonText(width = 150.dp, height = 14.dp)
                        SkeletonText(width = 100.dp, height = 11.dp)
                        SkeletonText(width = 70.dp, height = 11.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorRetryWidget(onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Could not load home",
                style = CommerceTypography.BodySmall,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.Danger
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                "Please check your connection and try again.",
                style = CommerceTypography.Caption,
                color = CommerceColors.TextMuted
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            TextButton(onClick = onRetry) {
                Text("Retry", color = CommerceColors.Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
