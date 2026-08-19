package com.commerceos.android.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.commerceos.android.repository.AppRepository
import com.commerceos.android.repository.FulfillmentRepository
import com.commerceos.android.session.SessionManager
import com.commerceos.android.usecase.AuthUseCase
import com.commerceos.android.viewmodel.AddressViewModel
import com.commerceos.android.viewmodel.AuthViewModel
import com.commerceos.android.viewmodel.CartViewModel
import com.commerceos.android.viewmodel.CheckoutViewModel
import com.commerceos.android.viewmodel.CatalogViewModel
import com.commerceos.android.viewmodel.CategoryViewModel
import com.commerceos.android.viewmodel.HomeViewModel
import com.commerceos.android.viewmodel.OrderViewModel
import com.commerceos.android.viewmodel.PrescriptionViewModel
import com.commerceos.android.viewmodel.ProductDetailViewModel

/**
 * Manual dependency graph (no Hilt): the single owner of app-scoped dependencies.
 * ViewModels are created through [factory] so they get proper Android lifecycle
 * semantics via ViewModelProvider instead of `remember { ... }`.
 */
class AppContainer(context: Context) {
    val sessionManager: SessionManager by lazy { SessionManager(context) }
    val repository: AppRepository by lazy { AppRepository() }
    val fulfillmentRepository: FulfillmentRepository by lazy { FulfillmentRepository(repository) }
    val searchRepository: com.commerceos.android.repository.UniversalSearchRepository by lazy { com.commerceos.android.repository.UniversalSearchRepository(repository) }
    val authUseCase: AuthUseCase by lazy { AuthUseCase(repository) }

    val database: com.commerceos.android.data.local.AppDatabase by lazy {
        com.commerceos.android.data.local.AppDatabase.getInstance(context)
    }
    val cartDao: com.commerceos.android.data.local.CartDao by lazy {
        database.cartDao()
    }

    val factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = repository
            return when {
                modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                    AuthViewModel(authUseCase) as T
                modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                    HomeViewModel(repo) as T
                modelClass.isAssignableFrom(CartViewModel::class.java) ->
                    CartViewModel(repo, cartDao) as T
                modelClass.isAssignableFrom(CheckoutViewModel::class.java) ->
                    CheckoutViewModel(repo) as T
                modelClass.isAssignableFrom(AddressViewModel::class.java) ->
                    AddressViewModel(repo) as T
                modelClass.isAssignableFrom(OrderViewModel::class.java) ->
                    OrderViewModel(repo) as T
                modelClass.isAssignableFrom(PrescriptionViewModel::class.java) ->
                    PrescriptionViewModel(repo) as T
                modelClass.isAssignableFrom(ProductDetailViewModel::class.java) ->
                    ProductDetailViewModel(repo) as T
                modelClass.isAssignableFrom(CatalogViewModel::class.java) ->
                    CatalogViewModel(repo) as T
                modelClass.isAssignableFrom(CategoryViewModel::class.java) ->
                    CategoryViewModel(repo) as T
                modelClass.isAssignableFrom(com.commerceos.android.viewmodel.VerticalHomeViewModel::class.java) ->
                    com.commerceos.android.viewmodel.VerticalHomeViewModel(repo, fulfillmentRepository) as T
                modelClass.isAssignableFrom(com.commerceos.android.viewmodel.UniversalSearchViewModel::class.java) ->
                    com.commerceos.android.viewmodel.UniversalSearchViewModel(repo, searchRepository) as T
                else -> error("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
}
