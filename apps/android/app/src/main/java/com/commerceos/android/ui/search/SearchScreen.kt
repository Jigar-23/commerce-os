package com.commerceos.android.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.util.Locale
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
import com.commerceos.android.ui.theme.PlusJakartaSans
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.SearchUiState
import com.commerceos.android.viewmodel.UniversalSearchViewModel

/**
 * Enterprise Healthcare Universal Search Experience.
 * Benchmarked directly against Tata 1mg, PharmEasy, and Truemeds:
 * - Elevated floating search header with back, voice, camera/Rx upload & live cart indicator
 * - Truemeds-style promotional discount voucher banner (FLAT 27% OFF)
 * - Horizontal recent searches with 1-tap delete & clear-all
 * - PharmEasy-style "Popular Categories" horizontal carousel with real blister pack images
 * - PharmEasy-style "Top Medicine Searches" vertical list with authentic packaging & salt microcopy
 * - Real-time Typeahead / Autocomplete dropdown with quick-chip extensions & instant preview
 * - Result cards with active salt composition tag, pack size, manufacturer, SLA ⚡ 10 MINS,
 *   selling price, strikethrough MRP, discount % tag, per-unit price pill (₹X.XX/tablet),
 *   and real cart quantity stepper [ - qty + ]
 * - PharmEasy-signature "Certified Generic Alternative Available" banner & comparison card
 * - "Frequently Needed Together" recommendation shelf so search is never a barren void
 */
@Composable
fun SearchScreen(
    initialQuery: UniversalSearchQuery = UniversalSearchQuery(),
    viewModel: UniversalSearchViewModel? = null,
    trendingSuggestions: List<String> = emptyList(),
    liveVerticals: List<String> = emptyList(),
    cartQuantityMap: Map<String, Int> = emptyMap(),
    cartItemCount: Int = 0,
    onPerformSearch: (UniversalSearchQuery) -> Unit,
    onSelectSearchResult: (SearchResult) -> Unit = {},
    onAddToCart: ((SearchResult) -> Unit)? = null,
    onUpdateQuantity: ((String, Int) -> Unit)? = null,
    onViewCart: (() -> Unit)? = null,
    onUploadPrescription: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val searchPrefs = remember(context) {
        context.getSharedPreferences("commerce_os_search_prefs", android.content.Context.MODE_PRIVATE)
    }

    var queryText by rememberSaveable { mutableStateOf(initialQuery.text) }
    var isSearchSubmitted by rememberSaveable { mutableStateOf(initialQuery.text.isNotBlank()) }
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

    val searchUiState = viewModel?.uiState ?: SearchUiState.Idle

    fun commitSearch(raw: String) {
        val query = raw.trim()
        if (query.isBlank()) return
        queryText = query
        isSearchSubmitted = true
        val updated = (listOf(query) + recentSearches.filter { !it.equals(query, ignoreCase = true) }).take(10)
        saveRecentSearches(updated)
        viewModel?.executeSearch(query)
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedText = spokenMatches?.firstOrNull()?.trim()
            if (!recognizedText.isNullOrBlank()) {
                queryText = recognizedText
                commitSearch(recognizedText)
            }
        }
    }

    var showCameraDialog by remember { mutableStateOf(false) }

    LaunchedEffect(initialQuery.text) {
        if (initialQuery.text.isNotBlank()) {
            queryText = initialQuery.text
            commitSearch(initialQuery.text)
        } else {
            queryText = ""
            isSearchSubmitted = false
            viewModel?.reset()
        }
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
        // 1. TOP STICKY ELEVATED SEARCH HEADER
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
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
                        onClick = {
                            if (isSearchSubmitted && queryText.isNotBlank()) {
                                queryText = ""
                                isSearchSubmitted = false
                                viewModel?.reset()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Sleek Pill Search Bar (PharmEasy & Tata 1mg standard)
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(22.dp),
                        border = BorderStroke(1.2.dp, if (queryText.isNotBlank()) Color(0xFF0C831F) else Color(0xFFE2E8F0)),
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
                                tint = Color(0xFF0C831F),
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(modifier = Modifier.weight(1f)) {
                                if (queryText.isEmpty()) {
                                    Text(
                                        text = "Search medicines, symptoms, salts...",
                                        fontSize = 14.sp,
                                        fontFamily = PlusJakartaSans,
                                        color = Color(0xFF94A3B8),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = queryText,
                                    onValueChange = {
                                        queryText = it
                                        isSearchSubmitted = false
                                        if (it.isBlank()) {
                                            viewModel?.reset()
                                        } else {
                                            viewModel?.updateQuery(it)
                                        }
                                    },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        fontFamily = PlusJakartaSans,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827)
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF0C831F)),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                    keyboardActions = KeyboardActions(onSearch = { commitSearch(queryText) }),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (queryText.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        queryText = ""
                                        isSearchSubmitted = false
                                        viewModel?.updateQuery("")
                                        viewModel?.reset()
                                    },
                                    modifier = Modifier.size(26.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Hairline vertical divider
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .width(1.dp)
                                    .height(20.dp)
                                    .background(Color(0xFFCBD5E1))
                            )

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
                                    tint = Color(0xFF0C831F),
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Search medicines, salts & health products...")
                                    }
                                    try {
                                        speechRecognizerLauncher.launch(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Voice search is not available on this device", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_mic),
                                    contentDescription = "Voice Search",
                                    tint = Color(0xFF0C831F),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    // Floating Cart Action Button if cart has items (Truemeds / 1mg style)
                    if (cartItemCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            onClick = { onViewCart?.invoke() },
                            shape = CircleShape,
                            color = Color(0xFFE8F5E9),
                            border = BorderStroke(1.2.dp, Color(0xFF0C831F)),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = "View Cart",
                                    tint = Color(0xFF0C831F),
                                    modifier = Modifier.size(18.dp)
                                )
                                Surface(
                                    color = Color(0xFF0C831F),
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 2.dp, end = 2.dp)
                                        .size(16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "$cartItemCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Filter pills when query results are active
                if (isSearchSubmitted && searchUiState is SearchUiState.Success) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Medicines", "Branded Generics", "Express ⚡ 10 Mins").forEach { filter ->
                            val isSelected = selectedFilter == filter
                            Surface(
                                color = if (isSelected) Color(0xFFE8F5E9) else Color.White,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF0C831F) else Color(0xFFE2E8F0)),
                                modifier = Modifier.clickable { selectedFilter = filter }
                            ) {
                                Text(
                                    text = filter,
                                    fontSize = 12.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color(0xFF0C831F) else Color(0xFF475569),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. MAIN BODY AREA
        Box(modifier = Modifier.weight(1f)) {
            when {
                // ACTIVE TYPING STATE: Live Typeahead & Autocomplete Dropdown
                queryText.isNotBlank() && !isSearchSubmitted -> {
                    SearchTypeaheadView(
                        queryText = queryText,
                        suggestions = viewModel?.suggestions ?: emptyList(),
                        onSelectSuggestion = { suggestion ->
                            commitSearch(suggestion)
                        },
                        onAddToCart = onAddToCart,
                        cartQuantityMap = cartQuantityMap,
                        onUpdateQuantity = onUpdateQuantity
                    )
                }

                // COMMITTED SEARCH: SEARCHING IN PROGRESS
                isSearchSubmitted && searchUiState is SearchUiState.Searching -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF0C831F), modifier = Modifier.size(38.dp), strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                "Searching certified pharmacy stock...",
                                fontSize = 13.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                }

                // COMMITTED SEARCH: ERROR STATE
                isSearchSubmitted && searchUiState is SearchUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            searchUiState.message,
                            color = CommerceColors.Danger,
                            style = CommerceTypography.BodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // COMMITTED SEARCH: EMPTY STATE
                isSearchSubmitted && searchUiState is SearchUiState.Empty -> {
                    SearchEmptyWidget(
                        query = searchUiState.query.text,
                        onUploadPrescription = onUploadPrescription,
                        onSelectConcern = { commitSearch(it) }
                    )
                }

                // COMMITTED SEARCH: SUCCESS RESULTS
                isSearchSubmitted && searchUiState is SearchUiState.Success -> {
                    val rawResults = searchUiState.results
                    val filteredResults = when (selectedFilter) {
                        "Express ⚡ 10 Mins" -> rawResults.filter { it.isExpressEligible == true }
                        "Medicines" -> rawResults.filter { it.vertical.equals("pharmacy", ignoreCase = true) || it.entityType == SearchEntityType.PRODUCT }
                        "Branded Generics" -> rawResults.filter { it.title.contains("Paracip", ignoreCase = true) || it.title.contains("generic", ignoreCase = true) || it.title.contains("Moxikind", ignoreCase = true) }
                        else -> rawResults
                    }

                    SearchResultsView(
                        queryText = queryText,
                        results = if (filteredResults.isEmpty() && selectedFilter == "Branded Generics") rawResults else filteredResults,
                        cartQuantityMap = cartQuantityMap,
                        onSelectSearchResult = onSelectSearchResult,
                        onAddToCart = onAddToCart,
                        onUpdateQuantity = onUpdateQuantity,
                        onUploadPrescription = onUploadPrescription
                    )
                }

                // DEFAULT IDLE STATE: Rich Discovery Hub
                else -> {
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
                        onAddToCart = onAddToCart,
                        cartQuantityMap = cartQuantityMap,
                        onUpdateQuantity = onUpdateQuantity
                    )
                }
            }
        }
    }
}

/**
 * Rich Typeahead & Autocomplete Dropdown while the user is typing.
 * Matches Blinkit, Tata 1mg, and PharmEasy typeahead patterns.
 */
@Composable
private fun SearchTypeaheadView(
    queryText: String,
    suggestions: List<com.commerceos.android.model.SearchSuggestion>,
    onSelectSuggestion: (String) -> Unit,
    onAddToCart: ((SearchResult) -> Unit)?,
    cartQuantityMap: Map<String, Int>,
    onUpdateQuantity: ((String, Int) -> Unit)?
) {
    val quickPills = remember(queryText) {
        listOf(
            "$queryText Tablet",
            "$queryText 650mg",
            "$queryText Syrup",
            "$queryText Generic"
        )
    }

    val dynamicSuggestions: List<String> = remember(queryText, suggestions) {
        if (suggestions.isNotEmpty()) {
            suggestions.map { it.title }
        } else {
            val q = queryText.lowercase()
            val matched = TOP_MEDICINE_CATALOG
                .filter { it.name.lowercase().contains(q) || it.saltComposition.lowercase().contains(q) }
                .map { it.name }
                .take(6)
            if (matched.isNotEmpty()) {
                matched
            } else {
                listOf(
                    "$queryText 650mg Tablet",
                    "$queryText Drop 15ml",
                    "$queryText Suspension 60ml",
                    "$queryText Cold Relief"
                )
            }
        }
    }

    val matchingProducts = remember(queryText) {
        val q = queryText.lowercase()
        TOP_MEDICINE_CATALOG
            .filter { it.name.lowercase().contains(q) || it.saltComposition.lowercase().contains(q) }
            .take(2)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Query Extension Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPills.forEach { pill ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.clickable { onSelectSuggestion(pill) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF0C831F), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pill,
                                fontSize = 12.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
        }

        // Fast-match Instant Product Previews (Tata 1mg signature feature)
        if (matchingProducts.isNotEmpty()) {
            item {
                Text(
                    text = "MATCHING MEDICINES",
                    fontSize = 11.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )
            }

            items(matchingProducts) { med ->
                TopMedicineRowItem(
                    medicine = med,
                    onClick = { onSelectSuggestion(med.name) },
                    onAddToCart = {
                        onAddToCart?.invoke(
                            SearchResult(
                                entityId = med.id,
                                sku = med.sku,
                                entityType = SearchEntityType.PRODUCT,
                                title = med.name,
                                subtitle = "${med.packSize} • ${med.manufacturer}",
                                vertical = "pharmacy",
                                price = med.price,
                                isExpressEligible = true,
                                image = med.imageUrl
                            )
                        )
                    },
                    cartQuantity = cartQuantityMap[med.sku] ?: 0,
                    onUpdateQuantity = onUpdateQuantity
                )
            }
        }

        // Suggestions Dropdown List
        item {
            Text(
                text = "SUGGESTED SEARCHES",
                fontSize = 11.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF64748B),
                letterSpacing = 0.5.sp
            )
        }

        items(dynamicSuggestions) { item ->
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSuggestion(item) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF0C831F),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item,
                        fontSize = 14.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Rich Search Results List with Generic Alternative comparison & recommendations.
 */
@Composable
private fun SearchResultsView(
    queryText: String,
    results: List<SearchResult>,
    cartQuantityMap: Map<String, Int>,
    onSelectSearchResult: (SearchResult) -> Unit,
    onAddToCart: ((SearchResult) -> Unit)?,
    onUpdateQuantity: ((String, Int) -> Unit)?,
    onUploadPrescription: (() -> Unit)?
) {
    // Check if first result has an active certified generic substitute
    val firstMed = results.firstOrNull()
    val enrichedMeta = remember(firstMed) {
        firstMed?.let { MedicineDetailEnricher.resolve(it.sku ?: it.entityId, it.title, it.subtitle, it.price ?: 30.0) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Results Count Header
        item {
            val countLabel = "${results.size} ${if (results.size == 1) "medicine" else "medicines"}"
            Text(
                text = "$countLabel found for '$queryText'",
                fontSize = 13.sp,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
        }

        // PharmEasy Signature: Certified Generic Substitute Savings Banner & Comparison Card
        if (enrichedMeta?.genericSubstitute != null) {
            item {
                GenericSubstituteBanner(
                    originalName = firstMed?.title ?: "Prescribed Brand",
                    originalPrice = firstMed?.price ?: 30.0,
                    substitute = enrichedMeta.genericSubstitute,
                    onAddSubstitute = {
                        onAddToCart?.invoke(
                            SearchResult(
                                entityId = "GEN-${firstMed?.entityId ?: "SUB"}",
                                sku = "SKU-GEN-SUB",
                                entityType = SearchEntityType.PRODUCT,
                                title = enrichedMeta.genericSubstitute.brandName,
                                subtitle = "${enrichedMeta.genericSubstitute.packSize} • ${enrichedMeta.genericSubstitute.manufacturer}",
                                vertical = "pharmacy",
                                price = enrichedMeta.genericSubstitute.price,
                                isExpressEligible = true,
                                image = enrichedMeta.genericSubstitute.imageUrl
                            )
                        )
                    }
                )
            }
        }

        // Primary Search Result Cards
        items(results) { item ->
            val meta = MedicineDetailEnricher.resolve(item.sku ?: item.entityId, item.title, item.subtitle, item.price ?: 30.0)
            val currentQty = cartQuantityMap[item.sku ?: item.entityId] ?: 0

            ModernSearchResultCard(
                result = item,
                meta = meta,
                currentQuantity = currentQty,
                onClick = { onSelectSearchResult(item) },
                onAddToCart = onAddToCart,
                onUpdateQuantity = onUpdateQuantity
            )
        }

        // Recommendations Carousel if results are few
        if (results.size <= 2) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frequently Needed Together",
                        fontSize = 14.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827)
                    )
                    Text(
                        text = "⚡ 10-Min Delivery",
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0C831F)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(TOP_HEALTHCARE_PICKS) { pick ->
                        val pickQty = cartQuantityMap[pick.id] ?: 0
                        TopPickProductCard(
                            pick = pick,
                            currentQuantity = pickQty,
                            onAddToCart = {
                                onAddToCart?.invoke(
                                    SearchResult(
                                        entityId = pick.id,
                                        sku = pick.id,
                                        entityType = SearchEntityType.PRODUCT,
                                        title = pick.name,
                                        subtitle = pick.packSize,
                                        vertical = "pharmacy",
                                        price = pick.price,
                                        isExpressEligible = true,
                                        image = pick.imageUrl
                                    )
                                )
                            },
                            onUpdateQuantity = onUpdateQuantity
                        )
                    }
                }
            }
        }
    }
}

/**
 * PharmEasy-Signature Smart Generic Substitute Comparison Card.
 * Compares prescribed medicine vs certified equivalent generic and highlights % savings.
 */
@Composable
private fun GenericSubstituteBanner(
    originalName: String,
    originalPrice: Double,
    substitute: GenericSubstituteInfo,
    onAddSubstitute: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, Color(0xFFA7F3D0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = Color(0xFF0C831F),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        "SAVE UP TO ${substitute.savingsPercent}%",
                        fontSize = 9.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    "Certified Generic Equivalent Available",
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF065F46)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SubcomposeAsyncImage(
                    model = substitute.imageUrl,
                    contentDescription = substitute.brandName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(6.dp))
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = substitute.brandName,
                        fontSize = 13.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${substitute.packSize} • ${substitute.manufacturer}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = MoneyFormatter.format(substitute.price),
                            fontSize = 13.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0C831F)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = MoneyFormatter.format(substitute.mrp),
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            textDecoration = TextDecoration.LineThrough
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = substitute.perUnitPrice,
                                fontSize = 9.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF475569),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onAddSubstitute,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C831F)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        "SWITCH",
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Rich, engaging Idle Search Discovery View matching Tata 1mg, Truemeds, and PharmEasy.
 */
@Composable
private fun SearchIdleRichView(
    recentSearches: List<String>,
    trendingSuggestions: List<String>,
    onCommitSearch: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    onUploadPrescription: (() -> Unit)?,
    onAddToCart: ((SearchResult) -> Unit)?,
    cartQuantityMap: Map<String, Int>,
    onUpdateQuantity: ((String, Int) -> Unit)?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. TRUEMEDS-STYLE PROMOTIONAL DISCOUNT TICKET
        item {
            Surface(
                color = Color(0xFFE8F5E9),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color(0xFF0C831F), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FLAT 27% OFF on first medicine order",
                            fontSize = 12.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                        Text(
                            text = "10-Minute guaranteed delivery by licensed pharmacist",
                            fontSize = 10.sp,
                            color = Color(0xFF047857)
                        )
                    }
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFF6EE7B7))
                    ) {
                        Text(
                            text = "FIRST27",
                            fontSize = 10.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0C831F),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // 2. RECENT SEARCHES (Horizontal scrolling tags with ✕ button)
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
                                tint = Color(0xFF0C831F),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Recent Searches",
                                fontSize = 14.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        TextButton(
                            onClick = onClearRecent,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "Clear all",
                                fontSize = 12.sp,
                                fontFamily = PlusJakartaSans,
                                color = Color(0xFFDC2626),
                                fontWeight = FontWeight.SemiBold
                            )
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
                                        fontFamily = PlusJakartaSans,
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

        // 3. POPULAR CATEGORIES CAROUSEL (PharmEasy Benchmark)
        item {
            Column {
                Text(
                    text = "POPULAR CATEGORIES",
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(POPULAR_MED_CATEGORIES) { cat ->
                        Surface(
                            onClick = { onCommitSearch(cat.query) },
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            shadowElevation = 0.5.dp,
                            modifier = Modifier.width(86.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        SubcomposeAsyncImage(
                                            model = cat.imageUrl,
                                            contentDescription = cat.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(42.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = cat.name,
                                    fontSize = 11.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. TOP MEDICINE SEARCHES WITH BLISTER PACKAGING (PharmEasy's #1 Layout!)
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOP MEDICINE SEARCHES",
                        fontSize = 12.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF64748B),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = Color(0xFFE2E8F0),
                        thickness = 0.5.dp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TOP_MEDICINE_CATALOG.forEach { med ->
                        val qty = cartQuantityMap[med.sku] ?: 0
                        TopMedicineRowItem(
                            medicine = med,
                            onClick = { onCommitSearch(med.name) },
                            onAddToCart = {
                                onAddToCart?.invoke(
                                    SearchResult(
                                        entityId = med.id,
                                        sku = med.sku,
                                        entityType = SearchEntityType.PRODUCT,
                                        title = med.name,
                                        subtitle = "${med.packSize} • ${med.manufacturer}",
                                        vertical = "pharmacy",
                                        price = med.price,
                                        isExpressEligible = true,
                                        image = med.imageUrl
                                    )
                                )
                            },
                            cartQuantity = qty,
                            onUpdateQuantity = onUpdateQuantity
                        )
                    }
                }
            }
        }

        // 5. DOCTOR'S PRESCRIPTION QUICK UPLOAD CARD (Tata 1mg / PharmEasy)
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
                            Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(24.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Can't find your medicine?",
                            fontSize = 14.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Upload doctor's prescription slip & licensed pharmacist will add it in 10 mins.",
                            fontSize = 11.sp,
                            color = Color(0xFF047857),
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { onUploadPrescription?.invoke() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C831F)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Upload", fontSize = 12.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 6. BROWSE BY HEALTH CONCERNS (Curated 2-Column Cards)
        item {
            Column {
                Text(
                    text = "EXPLORE HEALTH CONCERNS",
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF64748B),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

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

        // 7. TOP PICKS FOR YOU (Everyday Essentials Carousel)
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Everyday Healthcare Essentials",
                        fontSize = 14.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "⚡ 10 mins",
                        fontSize = 11.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0C831F)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(TOP_HEALTHCARE_PICKS) { pick ->
                        val pickQty = cartQuantityMap[pick.id] ?: 0
                        TopPickProductCard(
                            pick = pick,
                            currentQuantity = pickQty,
                            onAddToCart = {
                                onAddToCart?.invoke(
                                    SearchResult(
                                        entityId = pick.id,
                                        sku = pick.id,
                                        entityType = SearchEntityType.PRODUCT,
                                        title = pick.name,
                                        subtitle = pick.packSize,
                                        vertical = "pharmacy",
                                        price = pick.price,
                                        isExpressEligible = true,
                                        image = pick.imageUrl
                                    )
                                )
                            },
                            onUpdateQuantity = onUpdateQuantity
                        )
                    }
                }
            }
        }
    }
}

/**
 * PharmEasy-Style Top Medicine Row Item with authentic blister thumbnail, salt microcopy & chevron/add.
 */
@Composable
private fun TopMedicineRowItem(
    medicine: TopMedicineItem,
    onClick: () -> Unit,
    onAddToCart: () -> Unit,
    cartQuantity: Int = 0,
    onUpdateQuantity: ((String, Int) -> Unit)? = null
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Blister pack thumbnail
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SubcomposeAsyncImage(
                        model = medicine.imageUrl,
                        contentDescription = medicine.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medicine.name,
                    fontSize = 13.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = medicine.saltComposition,
                    fontSize = 10.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF0C831F),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "₹${medicine.price.toInt()}",
                        fontSize = 12.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF111827)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "(${medicine.perUnitLabel})",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (cartQuantity > 0) {
                // Stepper [ - qty + ]
                Surface(
                    color = Color(0xFF0C831F),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onUpdateQuantity?.invoke(medicine.sku, cartQuantity - 1) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text(
                            text = "$cartQuantity",
                            fontSize = 11.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        IconButton(
                            onClick = { onUpdateQuantity?.invoke(medicine.sku, cartQuantity + 1) },
                            modifier = Modifier.size(22.dp)
                        ) {
                            Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onAddToCart,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.2.dp, Color(0xFF0C831F)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0C831F)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("ADD", fontSize = 10.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/**
 * Modern High-Fidelity Search Result Card matching Tata 1mg & PharmEasy.
 */
@Composable
private fun ModernSearchResultCard(
    result: SearchResult,
    meta: MedicineMetadata,
    currentQuantity: Int,
    onClick: () -> Unit,
    onAddToCart: ((SearchResult) -> Unit)?,
    onUpdateQuantity: ((String, Int) -> Unit)?
) {
    val resolvedImage = MedicineImageResolver.resolve(
        sku = result.sku ?: result.entityId,
        name = result.title,
        rawImage = result.image
    )

    val price = result.price ?: 30.0
    val mrp = (price * 1.15).coerceAtLeast(price)
    val hasDiscount = mrp > price
    val discountPercent = if (mrp > 0) (((mrp - price) / mrp) * 100).toInt() else 0

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Authentic Packaging Thumbnail with 10 Mins SLA Badge
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = resolvedImage,
                    contentDescription = result.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                )
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        "⚡ 10 MINS",
                        fontSize = 8.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0C831F),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title
                val formattedTitle = com.commerceos.android.ui.components.formatHealthcareTitle(result.title)
                val formattedSalt = com.commerceos.android.ui.components.formatHealthcareSalt(result.title, meta.saltComposition)
                val formattedBrand = com.commerceos.android.ui.components.formatHealthcareBrand(meta.manufacturer, result.title)
                val formattedPack = com.commerceos.android.ui.components.formatPackSize(meta.packDescription)

                Text(
                    text = formattedTitle,
                    fontSize = 14.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Active Salt Composition Badge
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Text(
                        text = formattedSalt,
                        fontSize = 10.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF065F46),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Pack size & Manufacturer subtitle
                Text(
                    text = "$formattedPack • $formattedBrand",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = MoneyFormatter.format(price),
                                fontSize = 15.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF111827)
                            )
                            if (hasDiscount) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = MoneyFormatter.format(mrp),
                                    fontSize = 11.sp,
                                    color = Color(0xFF9CA3AF),
                                    textDecoration = TextDecoration.LineThrough
                                )
                            }
                            if (discountPercent > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$discountPercent% OFF",
                                    fontSize = 10.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0C831F)
                                )
                            }
                        }

                        // Per-Unit Price Pill
                        Text(
                            text = meta.perUnitPrice,
                            fontSize = 10.sp,
                            fontFamily = PlusJakartaSans,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    // Cart Action: [ ADD + ] or [ - qty + ]
                    if (currentQuantity > 0) {
                        Surface(
                            color = Color(0xFF0C831F),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { onUpdateQuantity?.invoke(result.sku ?: result.entityId, currentQuantity - 1) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("-", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text(
                                    text = "$currentQuantity",
                                    fontSize = 12.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = { onUpdateQuantity?.invoke(result.sku ?: result.entityId, currentQuantity + 1) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    } else if (onAddToCart != null) {
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.2.dp, Color(0xFF0C831F)),
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { onAddToCart(result) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "ADD",
                                    fontSize = 12.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0C831F)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color(0xFF0C831F),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
                    Icon(
                        imageVector = concern.icon,
                        contentDescription = null,
                        tint = concern.accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = concern.title,
                    fontSize = 13.sp,
                    fontFamily = PlusJakartaSans,
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

@Composable
private fun TopPickProductCard(
    pick: EssentialPick,
    currentQuantity: Int,
    onAddToCart: () -> Unit,
    onUpdateQuantity: ((String, Int) -> Unit)?
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
                Surface(
                    color = Color(0xFFE8F5E9),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        "⚡ 10 MINS",
                        fontSize = 9.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0C831F),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = pick.name,
                fontSize = 13.sp,
                fontFamily = PlusJakartaSans,
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
                        fontFamily = PlusJakartaSans,
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

                if (currentQuantity > 0) {
                    Surface(
                        color = Color(0xFF0C831F),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onUpdateQuantity?.invoke(pick.id, currentQuantity - 1) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("-", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Text(
                                text = "$currentQuantity",
                                fontSize = 11.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            IconButton(
                                onClick = { onUpdateQuantity?.invoke(pick.id, currentQuantity + 1) },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Text("+", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = onAddToCart,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.2.dp, Color(0xFF0C831F)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0C831F)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("ADD", fontSize = 11.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold)
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
            color = Color(0xFFE8F5E9),
            shape = CircleShape,
            modifier = Modifier.size(68.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(32.dp))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No exact match found for '$query'",
            fontSize = 16.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Try checking the spelling or upload your doctor's prescription so our licensed pharmacist can add it in 10 mins.",
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C831F))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Upload Doctor's Prescription", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Browse Common Health Concerns",
            fontSize = 13.sp,
            fontFamily = PlusJakartaSans,
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
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = concern.bgColor,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = concern.icon,
                                contentDescription = null,
                                tint = concern.accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = concern.title,
                        fontSize = 13.sp,
                        fontFamily = PlusJakartaSans,
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
        title = { Text("Voice Search", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Listening... Speak medicine name or symptom", fontSize = 13.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF0C831F))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onResult("Paracetamol")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C831F))
            ) {
                Text("Search 'Paracetamol'", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold)
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
        title = { Text("Scan Medicine Barcode", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Align medicine package or barcode inside the camera frame", fontSize = 13.sp, color = Color(0xFF64748B))
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF0C831F))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onResult("Dolo 650")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0C831F))
            ) {
                Text("Simulate Scan", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    )
}

// -------------------------------------------------------------
// DOMAIN MODELS & REPOSITORIES FOR BENCHMARKED HEALTHCARE UX
// -------------------------------------------------------------

data class TopMedicineItem(
    val id: String,
    val sku: String,
    val name: String,
    val saltComposition: String,
    val manufacturer: String,
    val packSize: String,
    val price: Double,
    val mrp: Double,
    val perUnitLabel: String,
    val imageUrl: String
)

data class PopularCategoryItem(
    val name: String,
    val query: String,
    val imageUrl: String
)

data class HealthConcern(
    val title: String,
    val subtitle: String,
    val query: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val bgColor: Color,
    val accentColor: Color
)

data class EssentialPick(
    val id: String,
    val name: String,
    val packSize: String,
    val price: Double,
    val mrp: Double,
    val imageUrl: String
)

data class MedicineMetadata(
    val saltComposition: String,
    val manufacturer: String,
    val packDescription: String,
    val perUnitPrice: String,
    val genericSubstitute: GenericSubstituteInfo? = null
)

data class GenericSubstituteInfo(
    val brandName: String,
    val manufacturer: String,
    val packSize: String,
    val price: Double,
    val mrp: Double,
    val perUnitPrice: String,
    val savingsPercent: Int,
    val imageUrl: String
)

object MedicineDetailEnricher {
    fun resolve(sku: String?, name: String?, subtitle: String?, price: Double): MedicineMetadata {
        val s = (sku ?: "").lowercase()
        val n = (name ?: "").lowercase()
        val sub = (subtitle ?: "")

        return when {
            n.contains("dolo") || s.contains("dolo") || n.contains("para") || s.contains("pcm") -> MedicineMetadata(
                saltComposition = "Paracetamol (650mg)",
                manufacturer = "Micro Labs Ltd",
                packDescription = "Strip of 15 tablets • Micro Labs Ltd",
                perUnitPrice = "₹${String.format("%.2f", price / 15)} / tablet",
                genericSubstitute = GenericSubstituteInfo(
                    brandName = "Paracip 650mg Tablet",
                    manufacturer = "Cipla Limited",
                    packSize = "Strip of 10 tablets",
                    price = 18.0,
                    mrp = 22.0,
                    perUnitPrice = "₹1.80 / tablet",
                    savingsPercent = 40,
                    imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/266954/paracip-500mg-strip-of-10-tablets-box-front-1-1756826302-non-watermarked.jpg"
                )
            )

            n.contains("crocin") || s.contains("croc") -> MedicineMetadata(
                saltComposition = "Paracetamol (650mg) Fast Release",
                manufacturer = "GSK Consumer Healthcare",
                packDescription = "Strip of 15 tablets • GSK Pharma",
                perUnitPrice = "₹${String.format("%.2f", price / 15)} / tablet",
                genericSubstitute = GenericSubstituteInfo(
                    brandName = "Dolo 650mg Tablet",
                    manufacturer = "Micro Labs Ltd",
                    packSize = "Strip of 15 tablets",
                    price = 30.0,
                    mrp = 34.50,
                    perUnitPrice = "₹2.00 / tablet",
                    savingsPercent = 15,
                    imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg"
                )
            )

            n.contains("augmentin") || s.contains("aug") || n.contains("amox") -> MedicineMetadata(
                saltComposition = "Amoxycillin (500mg) + Clavulanic Acid (125mg)",
                manufacturer = "GlaxoSmithKline",
                packDescription = "Strip of 10 tablets • GSK Pharma",
                perUnitPrice = "₹${String.format("%.2f", price / 10)} / tablet",
                genericSubstitute = GenericSubstituteInfo(
                    brandName = "Moxikind-CV 625 Tablet",
                    manufacturer = "Mankind Pharma",
                    packSize = "Strip of 10 tablets",
                    price = 115.0,
                    mrp = 145.0,
                    perUnitPrice = "₹11.50 / tablet",
                    savingsPercent = 38,
                    imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg"
                )
            )

            n.contains("pan d") || n.contains("pan-d") -> MedicineMetadata(
                saltComposition = "Pantoprazole (40mg) + Domperidone (30mg)",
                manufacturer = "Alkem Laboratories",
                packDescription = "Strip of 15 capsules • Alkem Labs",
                perUnitPrice = "₹${String.format("%.2f", price / 15)} / capsule",
                genericSubstitute = GenericSubstituteInfo(
                    brandName = "Pantocid-DSR Capsule",
                    manufacturer = "Sun Pharmaceutical",
                    packSize = "Strip of 15 capsules",
                    price = 115.0,
                    mrp = 140.0,
                    perUnitPrice = "₹7.66 / capsule",
                    savingsPercent = 26,
                    imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/130198/pan-d-capsule-strip-of-15-capsules-front-2-1756897184-non-watermarked.jpg"
                )
            )

            n.contains("becosules") -> MedicineMetadata(
                saltComposition = "Vitamin B-Complex + Vitamin C + Zinc",
                manufacturer = "Pfizer Ltd",
                packDescription = "Strip of 20 capsules • Pfizer",
                perUnitPrice = "₹${String.format("%.2f", price / 20)} / capsule",
                genericSubstitute = null
            )

            n.contains("digene") -> MedicineMetadata(
                saltComposition = "Magnesium Hydroxide + Aluminium Hydroxide + Simethicone",
                manufacturer = "Abbott Healthcare",
                packDescription = "Bottle of 200 ml • Abbott",
                perUnitPrice = "₹0.73 / ml",
                genericSubstitute = null
            )

            n.contains("volini") -> MedicineMetadata(
                saltComposition = "Diclofenac Diethylamine + Linseed Oil + Menthol",
                manufacturer = "Sun Pharmaceutical",
                packDescription = "Tube of 75 g • Sun Pharma",
                perUnitPrice = "₹2.60 / g",
                genericSubstitute = null
            )

            n.contains("electral") -> MedicineMetadata(
                saltComposition = "WHO Recommended Oral Rehydration Salts",
                manufacturer = "FDC Limited",
                packDescription = "Sachet of 21.8 g • FDC Ltd",
                perUnitPrice = "₹22.00 / sachet",
                genericSubstitute = null
            )

            n.contains("telma") -> MedicineMetadata(
                saltComposition = "Telmisartan (40mg)",
                manufacturer = "Glenmark Pharmaceuticals",
                packDescription = "Strip of 30 tablets • Glenmark",
                perUnitPrice = "₹${String.format("%.2f", price / 30)} / tablet",
                genericSubstitute = null
            )

            n.contains("ecosprin") -> MedicineMetadata(
                saltComposition = "Aspirin (75mg)",
                manufacturer = "USV Private Limited",
                packDescription = "Strip of 14 tablets • USV Ltd",
                perUnitPrice = "₹0.43 / tablet",
                genericSubstitute = null
            )

            n.contains("shelcal") -> MedicineMetadata(
                saltComposition = "Calcium (500mg) + Vitamin D3 (250IU)",
                manufacturer = "Torrent Pharmaceuticals",
                packDescription = "Bottle of 30 tablets • Torrent",
                perUnitPrice = "₹3.93 / tablet",
                genericSubstitute = null
            )

            else -> MedicineMetadata(
                saltComposition = "Certified Pharmaceutical Formulation",
                manufacturer = if (sub.contains("•")) sub.substringAfter("•").trim() else "WHO-GMP Certified",
                packDescription = if (sub.isNotBlank()) sub else "Strip of 10 units",
                perUnitPrice = "₹${String.format("%.2f", (price / 10).coerceAtLeast(1.0))} / unit",
                genericSubstitute = null
            )
        }
    }
}

private val POPULAR_MED_CATEGORIES = listOf(
    PopularCategoryItem(
        name = "Pain & Fever",
        query = "Pain & Fever",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg"
    ),
    PopularCategoryItem(
        name = "Acidity & Gas",
        query = "Acidity & Gas",
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg"
    ),
    PopularCategoryItem(
        name = "Antibiotics",
        query = "Antibiotics",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg"
    ),
    PopularCategoryItem(
        name = "Vitamins & Zinc",
        query = "Vitamins",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg"
    ),
    PopularCategoryItem(
        name = "Heart & BP",
        query = "Telma",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/163836/telma-40mg-strip-of-30-tablets-front-2-1756884638-non-watermarked.jpg"
    ),
    PopularCategoryItem(
        name = "Diabetes Care",
        query = "Diabetes",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg"
    ),
    PopularCategoryItem(
        name = "Cough & Cold",
        query = "Cold & Cough",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg"
    ),
    PopularCategoryItem(
        name = "First Aid & ORS",
        query = "First Aid",
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/065110/electral-powder-218g-2-1671741180.jpg"
    )
)

private val TOP_MEDICINE_CATALOG = listOf(
    TopMedicineItem(
        id = "MED-DOLO-650",
        sku = "SKU-DOLO-650",
        name = "Dolo 650mg Tablet",
        saltComposition = "Paracetamol (650mg)",
        manufacturer = "Micro Labs Ltd",
        packSize = "Strip of 15 tablets",
        price = 30.0,
        mrp = 34.50,
        perUnitLabel = "₹2.00/tab",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg"
    ),
    TopMedicineItem(
        id = "MED-AUG-625",
        sku = "SKU-AUG-625",
        name = "Augmentin 625 Duo Tablet",
        saltComposition = "Amoxycillin (500mg) + Clavulanic Acid (125mg)",
        manufacturer = "GlaxoSmithKline",
        packSize = "Strip of 10 tablets",
        price = 185.0,
        mrp = 220.0,
        perUnitLabel = "₹18.50/tab",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg"
    ),
    TopMedicineItem(
        id = "MED-PAN-D",
        sku = "SKU-PAN-D",
        name = "Pan-D Capsule",
        saltComposition = "Pantoprazole (40mg) + Domperidone (30mg)",
        manufacturer = "Alkem Laboratories",
        packSize = "Strip of 15 capsules",
        price = 155.0,
        mrp = 199.0,
        perUnitLabel = "₹10.33/cap",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/130198/pan-d-capsule-strip-of-15-capsules-front-2-1756897184-non-watermarked.jpg"
    ),
    TopMedicineItem(
        id = "MED-BECO-Z",
        sku = "SKU-BECO-Z",
        name = "Becosules Z Capsule",
        saltComposition = "Vitamin B-Complex + Vitamin C + Zinc",
        manufacturer = "Pfizer Ltd",
        packSize = "Strip of 20 capsules",
        price = 48.0,
        mrp = 55.0,
        perUnitLabel = "₹2.40/cap",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg"
    ),
    TopMedicineItem(
        id = "MED-DIG-200",
        sku = "SKU-DIG-200",
        name = "Digene Mint Antacid Gel",
        saltComposition = "Magnesium Hydroxide + Aluminium Hydroxide + Simethicone",
        manufacturer = "Abbott Healthcare",
        packSize = "Bottle of 200 ml",
        price = 145.0,
        mrp = 168.0,
        perUnitLabel = "₹0.73/ml",
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg"
    ),
    TopMedicineItem(
        id = "MED-VOL-75",
        sku = "SKU-VOL-75",
        name = "Volini Pain Relief Gel",
        saltComposition = "Diclofenac Diethylamine + Linseed Oil + Menthol",
        manufacturer = "Sun Pharmaceutical",
        packSize = "Tube of 75 g",
        price = 195.0,
        mrp = 230.0,
        perUnitLabel = "₹2.60/g",
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/I00392/volini-pain-relief-gel-tube-of-100-g-6.1-1712725504.jpg"
    ),
    TopMedicineItem(
        id = "MED-ELEC-21",
        sku = "SKU-ELEC-21",
        name = "Electral ORS Powder",
        saltComposition = "WHO Recommended Oral Rehydration Salts",
        manufacturer = "FDC Limited",
        packSize = "Sachet of 21.8 g",
        price = 22.0,
        mrp = 24.5,
        perUnitLabel = "₹22.00/sachet",
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/065110/electral-powder-218g-2-1671741180.jpg"
    ),
    TopMedicineItem(
        id = "MED-NEURO-30",
        sku = "SKU-NEURO-30",
        name = "Neurobion Forte Tablet",
        saltComposition = "Vitamin B1 + B6 + B12 (Nerve Health)",
        manufacturer = "Procter & Gamble",
        packSize = "Strip of 30 tablets",
        price = 38.0,
        mrp = 44.0,
        perUnitLabel = "₹1.27/tab",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/119934/neurobion-forte-tablet-30s-front-2-1756897143-non-watermarked.jpg"
    ),
    TopMedicineItem(
        id = "MED-TEL-40",
        sku = "SKU-TEL-40",
        name = "Telma 40mg Tablet",
        saltComposition = "Telmisartan (40mg)",
        manufacturer = "Glenmark Pharmaceuticals",
        packSize = "Strip of 30 tablets",
        price = 112.0,
        mrp = 138.0,
        perUnitLabel = "₹3.73/tab",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/163836/telma-40mg-strip-of-30-tablets-front-2-1756884638-non-watermarked.jpg"
    ),
    TopMedicineItem(
        id = "MED-ECO-75",
        sku = "SKU-ECO-75",
        name = "Ecosprin 75mg Tablet",
        saltComposition = "Aspirin (75mg)",
        manufacturer = "USV Private Limited",
        packSize = "Strip of 14 tablets",
        price = 6.0,
        mrp = 7.2,
        perUnitLabel = "₹0.43/tab",
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/066348/ecosprin-75mg-strip-of-14-tablets-front-2-1756897534-non-watermarked.jpg"
    ),
    TopMedicineItem(
        id = "MED-SHEL-500",
        sku = "SKU-SHEL-500",
        name = "Shelcal 500 Tablet",
        saltComposition = "Elemental Calcium (500mg) + Vitamin D3 (250IU)",
        manufacturer = "Torrent Pharmaceuticals",
        packSize = "Bottle of 30 tablets",
        price = 118.0,
        mrp = 136.0,
        perUnitLabel = "₹3.93/tab",
        imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/K78299/shelcal-500mg-bottle-of-30-tablets-6.1-1787223246.jpg"
    )
)

private val HEALTH_CONCERNS = listOf(
    HealthConcern("Fever & Pain", "Paracetamol, Dolo 650", "fever", Icons.Default.Healing, Color(0xFFFEF2F2), Color(0xFFDC2626)),
    HealthConcern("Cough & Cold", "Syrups, Lozenges, Vicks", "cough", Icons.Default.Thermostat, Color(0xFFEFF6FF), Color(0xFF2563EB)),
    HealthConcern("Diabetes Care", "Strips, Glucometers", "diabetes", Icons.Default.Bloodtype, Color(0xFFFFFBEB), Color(0xFFD97706)),
    HealthConcern("Stomach & Gas", "Digene Gel, Antacids", "digestion", Icons.Default.LocalPharmacy, Color(0xFFF0FDF4), Color(0xFF16A34A)),
    HealthConcern("Skin Care", "Acne, Ointments, Creams", "skin care", Icons.Default.Face, Color(0xFFFAF5FF), Color(0xFF9333EA)),
    HealthConcern("Immunity & Herbs", "Chyawanprash, Giloy", "immunity", Icons.Default.Eco, Color(0xFFF0FDF4), Color(0xFF0C831F)),
    HealthConcern("Vitamins & Calcium", "Becosules, Zinc, D3", "vitamin", Icons.Default.FitnessCenter, Color(0xFFFFF7ED), Color(0xFFEA580C)),
    HealthConcern("First Aid & Wounds", "Bandages, Dettol, Cotton", "first aid", Icons.Default.MedicalServices, Color(0xFFFFF1F2), Color(0xFFE11D48))
)

private val TOP_HEALTHCARE_PICKS = listOf(
    EssentialPick(
        id = "MED-001",
        name = "Dolo 650mg Tablet",
        packSize = "Strip of 15 tablets",
        price = 30.0,
        mrp = 34.50,
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg"
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
        imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg"
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
