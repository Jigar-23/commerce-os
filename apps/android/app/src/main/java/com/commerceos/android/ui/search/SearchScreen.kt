package com.commerceos.android.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.R
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.model.SearchResult
import com.commerceos.android.model.UniversalSearchQuery
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.SearchUiState
import com.commerceos.android.viewmodel.UniversalSearchViewModel
import java.util.UUID

/**
 * Universal Commerce OS Search Screen.
 * Sleek, industrial-standard medical & retail search experience matching Apollo 24|7 & Tata 1mg.
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val searchPrefs = remember(context) {
        context.getSharedPreferences("commerce_os_search_prefs", android.content.Context.MODE_PRIVATE)
    }

    var queryText by rememberSaveable { mutableStateOf(initialQuery.text) }
    var selectedVertical by rememberSaveable { mutableStateOf<String?>(initialQuery.vertical) }
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

    fun commitSearch(raw: String, verticalScope: String? = selectedVertical) {
        val query = raw.trim()
        if (query.isBlank()) return
        val updated = (listOf(query) + recentSearches.filter { !it.equals(query, ignoreCase = true) }).take(10)
        saveRecentSearches(updated)
        viewModel?.executeSearch(query, verticalScope)
    }

    LaunchedEffect(initialQuery.text, initialQuery.vertical) {
        if (initialQuery.text.isNotBlank()) {
            queryText = initialQuery.text
            commitSearch(initialQuery.text, initialQuery.vertical)
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
            .padding(Spacing.lg)
    ) {
        // TOP SEARCH BAR WITH SLEEK INDUSTRIAL AESTHETIC
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = Color(0xFFF1F5F9),
                shape = CircleShape,
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(20.dp)
                    )
                }
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
                        "Search medicines, health products...",
                        fontSize = 14.sp,
                        color = Color(0xFF94A3B8)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        if (queryText.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    queryText = ""
                                    viewModel?.updateQuery("")
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { showVoiceDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_mic),
                                contentDescription = "Voice Search",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { showCameraDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_camera),
                                contentDescription = "Camera Search",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { commitSearch(queryText) }),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF0284C7),
                    unfocusedBorderColor = Color(0xFFE2E8F0),
                    focusedContainerColor = Color(0xFFF8FAFC),
                    unfocusedContainerColor = Color(0xFFF8FAFC)
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SEARCH CONTENT DISPATCH
        when (searchUiState) {
            is SearchUiState.Searching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF0284C7))
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${searchUiState.results.size} matches found for '${searchUiState.query.text}'",
                        style = CommerceTypography.Caption,
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchUiState.results) { item ->
                        SearchEntityRenderer(
                            result = item,
                            onClick = { onSelectSearchResult(item) },
                            onAddToCart = onAddToCart
                        )
                    }
                }
            }

            is SearchUiState.Idle -> {
                SearchSuggestionsAndHistoryView(
                    recentSearches = recentSearches,
                    queryText = queryText,
                    onCommit = { commitSearch(it) },
                    onClearHistory = {
                        saveRecentSearches(emptyList())
                    }
                )
            }
        }
    }
}

@Composable
private fun SearchSuggestionsAndHistoryView(
    recentSearches: List<String>,
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
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Recent Searches",
                                style = CommerceTypography.Title,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                        TextButton(onClick = onClearHistory) {
                            Text("Clear", style = CommerceTypography.Meta, color = Color(0xFFDC2626), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (item in recentSearches) {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCommit(item) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        tint = Color(0xFF94A3B8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        item,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1E293B),
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = Color(0xFFCBD5E1),
                                        modifier = Modifier
                                            .size(14.dp)
                                            .graphicsLayer(rotationZ = 180f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = Color(0xFFF0F9FF),
                            shape = CircleShape,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Search for Medicines & Essentials",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Search by brand name, active molecule, or symptom.\nGuaranteed 10-minute delivery to your doorstep.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        if (queryText.isNotBlank()) {
            item {
                Button(
                    onClick = { onCommit(queryText) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Search for '$queryText'",
                        style = CommerceTypography.Label,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
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

@Composable
private fun VoiceSearchDialog(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Voice Search", style = CommerceTypography.Title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Listening... Speak your product or query", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF0284C7))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onResult("Paracetamol 500mg")
                onDismiss()
            }) {
                Text("Simulate 'Paracetamol'", color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
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
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text("Visual Barcode Search", style = CommerceTypography.Title, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Align product barcode or package inside camera view", style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator(color = Color(0xFF0284C7))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onResult("Paracetamol 500mg")
                onDismiss()
            }) {
                Text("Simulate Scan", color = Color(0xFF0284C7), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = CommerceColors.TextMuted)
            }
        }
    )
}