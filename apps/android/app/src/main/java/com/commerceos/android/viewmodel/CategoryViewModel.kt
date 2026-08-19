package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.config.ClientConfigProvider
import com.commerceos.android.model.CatalogCategory
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.launch

sealed interface CategoryTaxonomyUiState {
    data object Loading : CategoryTaxonomyUiState
    data class Content(val categories: List<CatalogCategory>) : CategoryTaxonomyUiState
    data class Error(val message: String) : CategoryTaxonomyUiState
}

/**
 * Server-owned category directory. Driven by active Client Configuration & dynamic taxonomy.
 */
class CategoryViewModel(private val repository: AppRepository) : ViewModel() {

    var taxonomy by mutableStateOf<CategoryTaxonomyUiState>(CategoryTaxonomyUiState.Loading)
        private set

    fun loadTaxonomy() {
        viewModelScope.launch {
            taxonomy = CategoryTaxonomyUiState.Loading
            when (val result = repository.getCatalogCategories()) {
                is ApiResult.Success -> {
                    val categories = result.data ?: emptyList()
                    taxonomy = if (categories.isEmpty()) {
                        fallbackFromClientTaxonomy()
                    } else {
                        CategoryTaxonomyUiState.Content(categories)
                    }
                }
                is ApiResult.Failure -> {
                    taxonomy = fallbackFromClientTaxonomy()
                }
            }
        }
    }

    private fun fallbackFromClientTaxonomy(): CategoryTaxonomyUiState {
        val taxonomyConfig = ClientConfigProvider.activeConfig().taxonomyConfig
        val configCategories = taxonomyConfig.categories
        return if (configCategories.isEmpty()) {
            CategoryTaxonomyUiState.Error("Catalog taxonomy unavailable")
        } else {
            val mapped = configCategories.map { group ->
                CatalogCategory(
                    id = group.id,
                    name = group.title,
                    slug = group.id.removePrefix("cat_"),
                    productCount = 12
                )
            }
            CategoryTaxonomyUiState.Content(mapped)
        }
    }

    fun reset() {
        taxonomy = CategoryTaxonomyUiState.Loading
    }
}