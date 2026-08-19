package com.commerceos.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.commerceos.android.config.ClientConfigProvider
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.config.TenantEnvironment
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTheme
import com.commerceos.android.viewmodel.*

/**
 * Clean Android Activity entry point for Commerce OS.
 * Performs client bootstrap before presenting the first screen.
 */
class MainActivity : ComponentActivity() {

    private val container by lazy { (application as CommerceOSApplication).container }

    private val homeViewModel by lazy { ViewModelProvider(this, container.factory)[HomeViewModel::class.java] }
    private val authViewModel by lazy { ViewModelProvider(this, container.factory)[AuthViewModel::class.java] }
    private val cartViewModel by lazy { ViewModelProvider(this, container.factory)[CartViewModel::class.java] }
    private val checkoutViewModel by lazy { ViewModelProvider(this, container.factory)[CheckoutViewModel::class.java] }
    private val addressViewModel by lazy { ViewModelProvider(this, container.factory)[AddressViewModel::class.java] }
    private val orderViewModel by lazy { ViewModelProvider(this, container.factory)[OrderViewModel::class.java] }
    private val prescriptionViewModel by lazy { ViewModelProvider(this, container.factory)[PrescriptionViewModel::class.java] }
    private val productDetailViewModel by lazy { ViewModelProvider(this, container.factory)[ProductDetailViewModel::class.java] }
    private val catalogViewModel by lazy { ViewModelProvider(this, container.factory)[CatalogViewModel::class.java] }
    private val categoryViewModel by lazy { ViewModelProvider(this, container.factory)[CategoryViewModel::class.java] }
    private val verticalHomeViewModel by lazy { ViewModelProvider(this, container.factory)[VerticalHomeViewModel::class.java] }
    private val universalSearchViewModel by lazy { ViewModelProvider(this, container.factory)[UniversalSearchViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bootstrap client configuration before rendering first screen
        ClientConfigProvider.bootstrap(
            environment = TenantEnvironment.PRODUCTION,
            context = this
        )

        setContent {
            val activeConfig by ClientConfigProvider.currentConfig.collectAsState()
            
            androidx.compose.runtime.CompositionLocalProvider(
                LocalClientConfiguration provides activeConfig
            ) {
                CommerceTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = CommerceColors.Background
                    ) {
                        CommerceOSApp(
                            container = container,
                            homeViewModel = homeViewModel,
                            authViewModel = authViewModel,
                            cartViewModel = cartViewModel,
                            checkoutViewModel = checkoutViewModel,
                            addressViewModel = addressViewModel,
                            orderViewModel = orderViewModel,
                            prescriptionViewModel = prescriptionViewModel,
                            productDetailViewModel = productDetailViewModel,
                            catalogViewModel = catalogViewModel,
                            categoryViewModel = categoryViewModel,
                            verticalHomeViewModel = verticalHomeViewModel,
                            universalSearchViewModel = universalSearchViewModel
                        )
                    }
                }
            }
        }
    }
}
