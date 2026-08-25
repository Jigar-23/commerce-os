package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.SearchResult
import com.commerceos.android.model.UniversalSearchQuery
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import com.commerceos.android.repository.UniversalSearchRepository
import kotlinx.coroutines.launch
import java.util.UUID

/** Search Session context preserving query, vertical, intent, location & filters. */
data class SearchSession(
    val queryText: String = "",
    val vertical: String? = null,
    val locationAddressId: String? = null,
    val intent: String? = null,
    val filters: Map<String, String> = emptyMap(),
    val sessionId: String = UUID.randomUUID().toString()
)

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Success(
        val query: UniversalSearchQuery,
        val results: List<SearchResult>
    ) : SearchUiState
    data class Empty(val query: UniversalSearchQuery) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/** Domain ViewModel owning Universal Search pipeline, execution & session tracking. */
class UniversalSearchViewModel(
    private val repository: AppRepository,
    private val searchRepository: UniversalSearchRepository = UniversalSearchRepository(repository)
) : ViewModel() {

    var session by mutableStateOf(SearchSession())
        private set

    var uiState by mutableStateOf<SearchUiState>(SearchUiState.Idle)
        private set

    var suggestions by mutableStateOf<List<com.commerceos.android.model.SearchSuggestion>>(emptyList())
        private set

    private var activeQueryRequestId: String = ""
    private var debounceJob: kotlinx.coroutines.Job? = null
    private var searchJob: kotlinx.coroutines.Job? = null
    private var autocompleteJob: kotlinx.coroutines.Job? = null

    fun initSession(query: UniversalSearchQuery) {
        session = SearchSession(
            queryText = query.text,
            vertical = query.vertical,
            locationAddressId = query.locationAddressId,
            intent = query.intent,
            filters = query.filters,
            sessionId = query.sessionId.ifBlank { UUID.randomUUID().toString() }
        )
        if (query.text.isNotBlank()) {
            executeSearch(query.text, query.vertical)
        }
    }

    fun updateQuery(text: String, debounceMs: Long = 300L) {
        session = session.copy(queryText = text)
        executeDebouncedSearch(text, session.vertical, debounceMs)
        loadAutocomplete(text)
    }

    fun updateVertical(vertical: String?) {
        session = session.copy(vertical = vertical)
        if (session.queryText.isNotBlank()) {
            executeSearch(session.queryText, vertical)
        }
    }

    fun executeDebouncedSearch(text: String = session.queryText, verticalScope: String? = session.vertical, debounceMs: Long = 300L) {
        debounceJob?.cancel()
        val trimmed = text.trim()
        if (trimmed.length < 2 && verticalScope.isNullOrBlank()) {
            uiState = SearchUiState.Idle
            return
        }
        debounceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(debounceMs)
            executeSearch(trimmed, verticalScope)
        }
    }

    fun loadAutocomplete(query: String) {
        autocompleteJob?.cancel()
        if (query.isBlank()) {
            suggestions = emptyList()
            return
        }
        autocompleteJob = viewModelScope.launch {
            kotlinx.coroutines.delay(150L)
            when (val res = searchRepository.autocomplete(query, session.vertical)) {
                is ApiResult.Success -> suggestions = res.data
                is ApiResult.Failure -> suggestions = emptyList()
            }
        }
    }

    fun executeSearch(text: String = session.queryText, verticalScope: String? = session.vertical) {
        val trimmed = text.trim()
        if (trimmed.isBlank() && verticalScope.isNullOrBlank()) {
            searchJob?.cancel()
            uiState = SearchUiState.Idle
            return
        }

        searchJob?.cancel()
        val requestId = UUID.randomUUID().toString()
        activeQueryRequestId = requestId
        uiState = SearchUiState.Searching

        searchJob = viewModelScope.launch {
            val searchQuery = UniversalSearchQuery(
                text = trimmed,
                vertical = verticalScope,
                locationAddressId = session.locationAddressId,
                intent = session.intent,
                filters = session.filters,
                sessionId = session.sessionId
            )

            val result = searchRepository.executeSearch(searchQuery)

            // Latest-query-wins protection: discard older responses
            if (activeQueryRequestId == requestId) {
                when (result) {
                    is ApiResult.Success -> {
                        val items = result.data
                        uiState = if (items.isEmpty()) {
                            SearchUiState.Empty(searchQuery)
                        } else {
                            SearchUiState.Success(searchQuery, items)
                        }
                    }
                    is ApiResult.Failure -> {
                        uiState = SearchUiState.Error(result.error.message)
                    }
                }
            }
        }
    }

    fun buildQuery(): UniversalSearchQuery {
        return UniversalSearchQuery(
            text = session.queryText,
            vertical = session.vertical,
            intent = session.intent,
            locationAddressId = session.locationAddressId,
            filters = session.filters,
            sessionId = session.sessionId
        )
    }

    fun reset() {
        session = SearchSession()
        uiState = SearchUiState.Idle
        activeQueryRequestId = ""
    }
}
