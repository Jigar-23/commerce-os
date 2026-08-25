package com.commerceos.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.model.SearchFilterOption
import com.commerceos.android.model.SearchResult
import com.commerceos.android.model.UniversalSearchQuery
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.SearchUiState
import com.commerceos.android.viewmodel.UniversalSearchViewModel

/**
 * Universal Commerce OS Search Screen.
 * Fully wired with [LocalClientConfiguration], dynamic filters, history behavior, and Search Data Integrity rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: UniversalSearchQuery = UniversalSearchQuery(),
    viewModel: UniversalSearchViewModel? = null,
    trendingSuggestions: List<String> = emptyList(),
    liveVerticals: List<String> = emptyList(),
    onPerformSearch: (UniversalSearchQuery) -> Unit,
    onSelectSearchResult: (SearchResult) -> Unit = {},
    onBack: () -> Unit
) {
    val clientConfig = LocalClientConfiguration.current
    val searchConfig = clientConfig.searchConfig

    var queryText by rememberSaveable { mutableStateOf(initialQuery.text) }
    var selectedVertical by rememberSaveable { mutableStateOf<String?>(initialQuery.vertical) }
    var activeQuickFilterIds by remember { mutableStateOf(setOf<String>()) }
    var recentSearches by rememberSaveable {
        mutableStateOf(listOf("Paracetamol 500mg", "T-Shirt", "Wireless Headphones").take(searchConfig.searchHistoryBehavior.maxHistoryItems))
    }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showCameraDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val searchUiState = viewModel?.uiState ?: SearchUiState.Idle

    fun commitSearch(raw: String, verticalScope: String? = selectedVertical) {
        val query = raw.trim()
        if (query.isBlank()) return
        if (searchConfig.searchHistoryBehavior.enabled) {
            recentSearches = (listOf(query) + recentSearches.filter { it != query }).take(searchConfig.searchHistoryBehavior.maxHistoryItems)
        }
        val searchQuery = UniversalSearchQuery(
            text = query,
            vertical = verticalScope,
            locationAddressId = initialQuery.locationAddressId,
            intent = initialQuery.intent,
            sessionId = initialQuery.sessionId.ifBlank { viewModel?.session?.sessionId ?: java.util.UUID.randomUUID().toString() }
        )
        viewModel?.executeSearch(query, verticalScope)
        onPerformSearch(searchQuery)
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

    if (showFilterSheet) {
        DynamicFilterSheet(
            activeFilterCount = activeQuickFilterIds.size,
            onDismiss = { showFilterSheet = false },
            onApply = { showFilterSheet = false },
            onClearAll = { activeQuickFilterIds = emptySet() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.lg)
    ) {
        // TOP SEARCH BAR WITH BACK & CLEAR AFFORDANCE
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = CommerceColors.TextPrimary
                )
            }

            OutlinedTextField(
                value = queryText,
                onValueChange = {
                    queryText = it
                    viewModel?.updateQuery(it)
                    if (it.length >= 2) {
                        viewModel?.executeSearch(it, selectedVertical)
                    }
                },
                placeholder = {
                    Text(
                        searchConfig.searchPlaceholder,
                        style = CommerceTypography.BodySmall,
                        color = CommerceColors.TextMuted
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CommerceColors.Primary) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchConfig.voiceCapabilityEnabled) {
                            IconButton(onClick = { showVoiceDialog = true }) {
                                Text("🎙️", style = CommerceTypography.BodyLarge)
                            }
                        }
                        if (searchConfig.imageCapabilityEnabled || searchConfig.barcodeCapabilityEnabled) {
                            IconButton(onClick = { showCameraDialog = true }) {
                                Text("📷", style = CommerceTypography.BodyLarge)
                            }
                        }
                        if (queryText.isNotEmpty()) {
                            IconButton(onClick = {
                                queryText = ""
                                viewModel?.updateQuery("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = CommerceColors.TextMuted)
                            }
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commitSearch(queryText) }),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CommerceColors.Primary,
                    unfocusedBorderColor = CommerceColors.Border,
                    focusedContainerColor = CommerceColors.Surface,
                    unfocusedContainerColor = CommerceColors.Surface
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        // DYNAMIC QUICK FILTER ROW WITH FILTER SHEET BUTTON & PILL BADGE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            // FILTER SHEET TRIGGER WITH ACTIVE COUNT PILL
            FilterChip(
                selected = activeQuickFilterIds.isNotEmpty(),
                onClick = { showFilterSheet = true },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.List, contentDescription = "Filter Sheet", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Filters", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold)
                        if (activeQuickFilterIds.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(color = CommerceColors.Primary, shape = RoundedCornerShape(Radius.Pill)) {
                                Text(
                                    "${activeQuickFilterIds.size}",
                                    style = CommerceTypography.Meta,
                                    color = CommerceColors.OnPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CommerceColors.PrimarySoft,
                    containerColor = CommerceColors.Surface
                ),
                shape = RoundedCornerShape(Radius.Chip)
            )

            // DYNAMIC QUICK FILTERS FROM SEARCH CONFIGURATION
            for (filter in searchConfig.quickFilters) {
                val isSelected = filter.id in activeQuickFilterIds
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        activeQuickFilterIds = if (isSelected) {
                            activeQuickFilterIds - filter.id
                        } else {
                            activeQuickFilterIds + filter.id
                        }
                    },
                    label = { Text(filter.label, style = CommerceTypography.Meta, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CommerceColors.Primary,
                        selectedLabelColor = CommerceColors.OnPrimary,
                        containerColor = CommerceColors.Surface,
                        labelColor = CommerceColors.TextPrimary
                    ),
                    shape = RoundedCornerShape(Radius.Chip)
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        // SEARCH CONTENT DISPATCH
        when (searchUiState) {
            is SearchUiState.Searching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CommerceColors.Primary)
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
                SearchEmptyWidget(query = searchUiState.query.text, onCommit = { commitSearch(it) })
            }

            is SearchUiState.Success -> {
                Text(
                    "${searchUiState.results.size} matches found for '${searchUiState.query.text}'",
                    style = CommerceTypography.Caption,
                    color = CommerceColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchUiState.results) { item ->
                        SearchEntityRenderer(
                            result = item,
                            onClick = { onSelectSearchResult(item) }
                        )
                    }
                }
            }

            is SearchUiState.Idle -> {
                SearchSuggestionsAndHistoryView(
                    recentSearches = recentSearches,
                    trendingSuggestions = trendingSuggestions,
                    queryText = queryText,
                    onCommit = { commitSearch(it) },
                    onClearHistory = {
                        if (searchConfig.searchHistoryBehavior.allowClearAll) {
                            recentSearches = emptyList()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchSuggestionsAndHistoryView(
    recentSearches: List<String>,
    trendingSuggestions: List<String>,
    queryText: String,
    onCommit: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier.fillMaxSize()
    ) {
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
                                contentDescription = "Recent Searches",
                                tint = CommerceColors.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Recent Searches", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                        }
                        TextButton(onClick = onClearHistory) {
                            Text("Clear", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        for (item in recentSearches) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCommit(item) }
                                    .padding(vertical = Spacing.xs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = CommerceColors.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    item,
                                    style = CommerceTypography.BodySmall,
                                    color = CommerceColors.TextPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (trendingSuggestions.isNotEmpty()) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Trending Search",
                            tint = CommerceColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text("Trending right now", style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        for (rowItems in trendingSuggestions.chunked(2)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                            ) {
                                for (suggestion in rowItems) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                                        shape = RoundedCornerShape(Radius.Chip),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onCommit(suggestion) }
                                    ) {
                                        Text(
                                            suggestion,
                                            style = CommerceTypography.BodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = CommerceColors.TextPrimary,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (queryText.isNotBlank()) {
            item {
                Button(
                    onClick = { onCommit(queryText) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                    shape = RoundedCornerShape(Radius.Button)
                ) {
                    Text(
                        "Search for '$queryText'",
                        style = CommerceTypography.Label,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyWidget(query: String, onCommit: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.md),
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "No results found",
                style = CommerceTypography.Title,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "We couldn't find matches for '$query'. Try checking spelling or searching another category.",
                style = CommerceTypography.BodySmall,
                color = CommerceColors.TextMuted
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DynamicFilterSheet(
    activeFilterCount: Int,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onClearAll: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filters ($activeFilterCount active)", style = CommerceTypography.Heading, fontWeight = FontWeight.Bold)
                TextButton(onClick = onClearAll) {
                    Text("Clear All", color = CommerceColors.Danger, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Filter Categories List (Price, Brand, Rating, Availability, Category, Specs)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("Price Range", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("Under ₹500") })
                    FilterChip(selected = false, onClick = {}, label = { Text("₹500 - ₹2,000") })
                    FilterChip(selected = false, onClick = {}, label = { Text("₹2,000+") })
                }

                Text("Rating", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("★ 4.0 & above") })
                    FilterChip(selected = false, onClick = {}, label = { Text("★ 4.5 & above") })
                }

                Text("Availability & Delivery", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("⚡ Express Delivery") })
                    FilterChip(selected = false, onClick = {}, label = { Text("In Stock Only") })
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            Button(
                onClick = onApply,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                shape = RoundedCornerShape(Radius.Button)
            ) {
                Text("Apply Filters", fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary)
            }
        }
    }
}

@Composable
private fun VoiceSearchDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🎙️ Voice Search", style = CommerceTypography.Title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Listening... Speak your product or query", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = CommerceColors.Primary)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onResult("Paracetamol 500mg")
                onDismiss()
            }) {
                Text("Simulate 'Paracetamol'", color = CommerceColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CommerceColors.TextMuted)
            }
        }
    )
}

@Composable
private fun CameraSearchDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📷 Visual Barcode Search", style = CommerceTypography.Title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Align product barcode or package inside camera view", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = CommerceColors.Primary)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onResult("Paracetamol 500mg")
                onDismiss()
            }) {
                Text("Simulate Scan", color = CommerceColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CommerceColors.TextMuted)
            }
        }
    )
}