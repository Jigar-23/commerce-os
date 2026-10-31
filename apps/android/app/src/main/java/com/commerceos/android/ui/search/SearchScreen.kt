package com.commerceos.android.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.commerceos.android.R
import com.commerceos.android.model.MedicineImageResolver
import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchResult
import com.commerceos.android.model.UniversalSearchQuery
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.SearchUiState
import com.commerceos.android.viewmodel.UniversalSearchViewModel

/**
 * Universal Commerce OS Health Search Experience.
 * Matches Blinkit, Tata 1mg, and PharmEasy patterns:
 * - Edge-to-edge sleek search bar with voice & camera actions
 * - Horizontal chips for Recent Searches with one-tap removal
 * - Trending / Popular Searches pills (Dolo 650, Paracetamol, etc.)
 * - "Have a Doctor's Prescription?" quick upload banner
 * - "Explore by Health Concerns" curated 2-column cards
 * - "Top Healthcare Picks" quick add-to-cart carousel
 * - Clean search result cards with strike-through MRP, savings, and 10-Min delivery SLA
 */
@Composable
fun SearchScreen(
    initialQuery: UniversalSearchQuery = UniversalSearchQuery(),
    viewModel: UniversalSearchViewModel? = null,
    trendingSuggestions: List<String> = emptyList(),
    liveVerticals: List<String> = emptyList(),
    onPerformSearch: (UniversalSearchQuery) -> Unit,
    onSelectSearchResult: (SearchResult) -> Unit = {},
    onAddToCart: ((SearchResult) -> Unit)? = null,
    onUploadPrescription: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val searchPrefs = remember(context) {
        context.getSharedPreferences("commerce_os_search_prefs", android.content.Context.MODE_PRIVATE)
    }

    var queryText by rememberSaveable { mutableStateOf(initialQuery.text) }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }

    var recentSearches by remember {
        mutableStateOf(
            searchPrefs.getString("recent_queries", null)
                ?.split("||")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    fun saveRecentSearches(updated: List<String>) {
        recentSearches = updated
        searchPrefs.edit()
            .putString("recent_queries", updated.joinToString("||"))
            .apply()
    }

    var showVoiceDialog by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }

    val searchUiState = viewModel?.uiState ?: SearchUiState.Idle

    fun commitSearch(raw: String) {
        val query = raw.trim()
        if (query.isBlank()) return
        queryText = query
        val updated = (listOf(query) + recentSearches.filter { !it.equals(query, ignoreCase = true) }).take(10)
        saveRecentSearches(updated)
        viewModel?.executeSearch(query)
    }

    LaunchedEffect(initialQuery.text) {
        if (initialQuery.text.isNotBlank()) {
            queryText = initialQuery.text
            commitSearch(initialQuery.text)
        }
    }

    if (showVoiceDialog) {
        VoiceSearchDialog(
            onDismiss = { showVoiceDialog = false },
            onResult = { res ->
                queryText = res
                commitSearch(res)
            }
        )
    }

    if (showCameraDialog) {
        CameraSearchDialog(
            onDismiss = { showCameraDialog = false },
            onResult = { res ->
                queryText = res
                commitSearch(res)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        // 1. TOP STICKY SEARCH HEADER
        Surface(
            color = Color.White,
            shadowElevation = 1.dp,
            border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Sleek Pill Search Bar
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, if (queryText.isNotBlank()) Color(0xFF10B981) else Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (queryText.isEmpty()) {
                                    Text(
                                        text = "Search medicines, fever, cough...",
                                        fontSize = 14.sp,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = queryText,
                                    onValueChange = {
                                        queryText = it
                                        viewModel?.updateQuery(it)
                                        if (it.length >= 2) {
                                            viewModel?.executeSearch(it)
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF0F172A)
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF059669)),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { commitSearch(queryText) }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (queryText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        queryText = ""
                                        viewModel?.updateQuery("")
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { showVoiceDialog = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mic),
                                    contentDescription = "Voice Search",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (onUploadPrescription != null) {
                                        onUploadPrescription()
                                    } else {
                                        showCameraDialog = true
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_camera),
                                    contentDescription = "Scan / Upload Rx",
                                    tint = Color(0xFF059669),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Filter chips when query is active or results are shown
                if (searchUiState is SearchUiState.Success) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Medicines", "Healthcare", "Express ⚡").forEach { filter ->
                            val isSelected = selectedFilter == filter
                            Surface(
                                color = if (isSelected) Color(0xFFECFDF5) else Color.White,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B981) else Color(0xFFE2E8F0)),
                                modifier = Modifier.clickable { selectedFilter = filter }
                            ) {
                                Text(
                                    text = filter,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF065F46) else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. MAIN CONTENT AREA
        Box(modifier = Modifier.weight(1f)) {
            when (searchUiState) {
                is SearchUiState.Searching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF059669), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Searching certified pharmacy stock...", fontSize = 13.sp, color = Color(0xFF64748B))
                        }
                    }
                }

                is SearchUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            searchUiState.message,
                            color = CommerceColors.Danger,
                            style = CommerceTypography.BodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                is SearchUiState.Empty -> {
                    SearchEmptyWidget(
                        query = searchUiState.query.text,
                        onUploadPrescription = onUploadPrescription,
                        onSelectConcern = { commitSearch(it) }
                    )
                }

                is SearchUiState.Success -> {
                    val rawResults = searchUiState.results
                    val filteredResults = when (selectedFilter) {
                        "Express ⚡" -> rawResults.filter { it.isExpressEligible == true }
                        "Medicines" -> rawResults.filter { it.vertical.equals("pharmacy", ignoreCase = true) || it.entityType == SearchEntityType.PRODUCT }
                        "Healthcare" -> rawResults.filter { !it.vertical.equals("pharmacy", ignoreCase = true) }
                        else -> rawResults
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                text = "${filteredResults.size} medicines found for '${searchUiState.query.text}'",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        items(filteredResults) { item ->
                            ModernSearchResultCard(
                                result = item,
                                onClick = { onSelectSearchResult(item) },
                                onAddToCart = onAddToCart
                            )
                        }
                    }
                }

                is SearchUiState.Idle -> {
                    // Rich Idle State: Recent Searches + Trending Pills + Prescription Banner + Health Concerns + Top Picks
                    SearchIdleRichView(
                        recentSearches = recentSearches,
                        trendingSuggestions = trendingSuggestions,
                        onCommitSearch = { commitSearch(it) },
                        onRemoveRecent = { target ->
                            val updated = recentSearches.filterNot { it.equals(target, ignoreCase = true) }
                            saveRecentSearches(updated)
                        },
                        onClearRecent = { saveRecentSearches(emptyList()) },
                        onUploadPrescription = onUploadPrescription,
                        onAddToCart = onAddToCart
                    )
                }
            }
        }
    }
}

/**
 * Rich, engaging Idle Search State matching Blinkit, Tata 1mg, and PharmEasy.
 */
@Composable
private fun SearchIdleRichView(
    recentSearches: List<String>,
    trendingSuggestions: List<String>,
    onCommitSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onUploadPrescription: (() -> Unit)?,
    onAddToCart: ((SearchResult) -> Unit)?
) {
    val trendingPills = remember(trendingSuggestions) {
        val defaults = listOf(
            "Dolo 650",
            "Paracetamol",
            "Vitamin C & Zinc",
            "Cough Syrup",
            "Cetirizine",
            "ORS Sachet",
            "Digene Mint Gel",
            "Volini Spray",
            "Diabetes Strips",
            "Vicks Vaporub",
            "BP Monitor",
            "Pain Relief Gel"
        )
        (trendingSuggestions + defaults).distinct()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. RECENT SEARCHES (Horizontal scrolling tags with ✕ button)
        if (recentSearches.isNotEmpty()) {
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color(0xFF059669),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Recent Searches",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        TextButton(
                            onClick = onClearRecent,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Clear all", fontSize = 12.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recentSearches) { query ->
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                shadowElevation = 0.5.dp,
                                modifier = Modifier.clickable { onCommitSearch(query) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = query,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { onRemoveRecent(query) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. TRENDING SEARCHES (🔥 Popular Right Now)
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "🔥 Trending Searches",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Flow-like grid of trending pill chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trendingPills.take(6).forEach { pill ->
                        TrendingPillChip(label = pill, onClick = { onCommitSearch(pill) })
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    trendingPills.drop(6).take(6).forEach { pill ->
                        TrendingPillChip(label = pill, onClick = { onCommitSearch(pill) })
                    }
                }
            }
        }

        // 3. DOCTOR'S PRESCRIPTION QUICK UPLOAD CARD (Tata 1mg / PharmEasy pattern)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUploadPrescription?.invoke() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Color.White,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, Color(0xFF6EE7B7)),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("📄", fontSize = 22.sp)
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Can't find your medicine?",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Upload doctor's prescription slip & we'll add your medicines in 10 mins.",
                            fontSize = 11.sp,
                            color = Color(0xFF047857),
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onUploadPrescription?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Upload", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 4. BROWSE BY HEALTH CONCERNS (Curated 2-Column Cards)
        item {
            Column {
                Text(
                    text = "Explore Health Concerns",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // 2-Column Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HEALTH_CONCERNS.chunked(2).forEach { rowPair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPair.forEach { concern ->
                                HealthConcernCard(
                                    concern = concern,
                                    onClick = { onCommitSearch(concern.query) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowPair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 5. TOP PICKS FOR YOU (Blinkit / Zepto Everyday Essentials Carousel)
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Top Picks For You",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "⚡ Delivered in 10 mins",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF059669)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(TOP_HEALTHCARE_PICKS) { pick ->
                        TopPickProductCard(
                            pick = pick,
                            onAddToCart = {
                                onAddToCart?.invoke(
                                    SearchResult(
                                        entityId = pick.id,
                                        entityType = SearchEntityType.PRODUCT,
                                        title = pick.name,
                                        subtitle = pick.packSize,
                                        vertical = "pharmacy",
                                        price = pick.price,
                                        isExpressEligible = true,
                                        image = pick.imageUrl
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendingPillChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shadowElevation = 0.5.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1E293B)
            )
        }
    }
}

data class HealthConcern(
    val title: String,
    val subtitle: String,
    val query: String,
    val emoji: String,
    val bgColor: Color,
    val accentColor: Color
)

private val HEALTH_CONCERNS = listOf(
    HealthConcern("Fever & Pain", "Paracetamol, Dolo 650", "fever", "💊", Color(0xFFFEF2F2), Color(0xFFDC2626)),
    HealthConcern("Cough & Cold", "Syrups, Lozenges, Vicks", "cough", "🫁", Color(0xFFEFF6FF), Color(0xFF2563EB)),
    HealthConcern("Diabetes Care", "Strips, Glucometers", "diabetes", "🩸", Color(0xFFFFFBEB), Color(0xFFD97706)),
    HealthConcern("Stomach & Gas", "Digene Gel, Antacids", "digestion", "🫧", Color(0xFFF0FDF4), Color(0xFF16A34A)),
    HealthConcern("Skin Care", "Acne, Ointments, Creams", "skin care", "🧴", Color(0xFFFAF5FF), Color(0xFF9333EA)),
    HealthConcern("Immunity & Herbs", "Chyawanprash, Giloy", "immunity", "🌿", Color(0xFFF0FDF4), Color(0xFF059669)),
    HealthConcern("Vitamins & Calcium", "Becosules, Zinc, D3", "vitamin", "⚡", Color(0xFFFFF7ED), Color(0xFFEA580C)),
    HealthConcern("First Aid & Wounds", "Bandages, Dettol, Cotton", "first aid", "🩹", Color(0xFFFFF1F2), Color(0xFFE11D48))
)

@Composable
private fun HealthConcernCard(
    concern: HealthConcern,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = concern.bgColor,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, concern.accentColor.copy(alpha = 0.2f)),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(concern.emoji, fontSize = 20.sp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = concern.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = concern.subtitle,
                    fontSize = 11.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

data class EssentialPick(
    val id: String,
    val name: String,
    val packSize: String,
    val price: Double,
    val mrp: Double,
    val imageUrl: String
)

private val TOP_HEALTHCARE_PICKS = listOf(
    EssentialPick(
        id = "MED-001",
        name = "Dolo 650mg Tablet",
        packSize = "Strip of 15 tablets",
        price = 32.0,
        mrp = 34.0,
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/061510/dolo-650mg-strip-of-15-tablets-2-1671741164.jpg"
    ),
    EssentialPick(
        id = "MED-002",
        name = "Digene Mint Antacid",
        packSize = "Bottle of 200 ml",
        price = 145.0,
        mrp = 168.0,
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg"
    ),
    EssentialPick(
        id = "MED-003",
        name = "Volini Pain Relief Gel",
        packSize = "Tube of 75 g",
        price = 195.0,
        mrp = 230.0,
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/I00392/volini-pain-relief-gel-tube-of-100-g-6.1-1712725504.jpg"
    ),
    EssentialPick(
        id = "MED-004",
        name = "Becosules Z Capsule",
        packSize = "Strip of 20 capsules",
        price = 48.0,
        mrp = 55.0,
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/022440/becosules-z-capsule-strip-of-20-s-2-1671741029.jpg"
    ),
    EssentialPick(
        id = "MED-005",
        name = "Electral ORS Powder",
        packSize = "Sachet of 21.8 g",
        price = 22.0,
        mrp = 24.5,
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/065110/electral-powder-218g-2-1671741180.jpg"
    )
)

@Composable
private fun TopPickProductCard(
    pick: EssentialPick,
    onAddToCart: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.width(150.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(95.dp),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = pick.imageUrl,
                    contentDescription = pick.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                // 10 MINS tag
                Surface(
                    color = Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        "⚡ 10 MINS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF166534),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = pick.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = pick.packSize,
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "₹${pick.price.toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    if (pick.mrp > pick.price) {
                        Text(
                            text = "₹${pick.mrp.toInt()}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }

                OutlinedButton(
                    onClick = onAddToCart,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.5.dp, Color(0xFF059669)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF059669)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Modern High-Fidelity Search Result Card matching Blinkit & Tata 1mg standards.
 */
@Composable
private fun ModernSearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    onAddToCart: ((SearchResult) -> Unit)?
) {
    val resolvedImage = MedicineImageResolver.resolve(
        sku = result.sku ?: result.entityId,
        name = result.title,
        rawImage = result.image
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with 10 Mins SLA Badge
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(8.dp))
                    .border(0.5.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = resolvedImage,
                    contentDescription = result.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(4.dp)
                )
                if (result.isExpressEligible == true) {
                    Surface(
                        color = Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            "⚡ 10 MINS",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0C831F),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (result.subtitle.isNotBlank()) {
                    Text(
                        text = result.subtitle,
                        fontSize = 12.sp,
                        color = Color(0xFF4B5563),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (result.price != null) {
                            Text(
                                text = MoneyFormatter.format(result.price),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF111827)
                            )
                        }
                    }

                    if (onAddToCart != null) {
                        Button(
                            onClick = { onAddToCart(result) },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C831F)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                "ADD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyWidget(
    query: String,
    onUploadPrescription: (() -> Unit)?,
    onSelectConcern: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            color = Color(0xFFF0FDF4),
            shape = CircleShape,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🔍", fontSize = 28.sp)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No exact match found for '$query'",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Try checking the spelling or upload your doctor's prescription so our licensed pharmacist can find it for you.",
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            textAlign = TextAlign.Center,
            lineHeight = 17.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (onUploadPrescription != null) {
            Button(
                onClick = onUploadPrescription,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Doctor's Prescription", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Browse Common Health Concerns",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF334155),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))

        HEALTH_CONCERNS.take(4).forEach { concern ->
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectConcern(concern.query) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(concern.emoji, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = concern.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceSearchDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice Search", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Listening... Speak medicine name or symptom", fontSize = 13.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF059669))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onResult("Paracetamol")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("Search 'Paracetamol'", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    )
}

@Composable
private fun CameraSearchDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan Medicine Barcode", fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Align medicine package or barcode inside the camera frame", fontSize = 13.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF059669))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onResult("Dolo 650")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
            ) {
                Text("Simulate Scan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    )
}
