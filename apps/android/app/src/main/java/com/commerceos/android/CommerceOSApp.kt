package com.commerceos.android

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.di.AppContainer
import kotlinx.coroutines.launch
import com.commerceos.android.model.*
import com.commerceos.android.navigation.AppDestinationRouter
import com.commerceos.android.ui.AuthScreen
import com.commerceos.android.ui.account.AccountScreen
import com.commerceos.android.ui.address.AddressScreen
import com.commerceos.android.ui.address.AddressSelectionBottomSheet
import com.commerceos.android.ui.cart.CartScreen
import com.commerceos.android.ui.catalog.CatalogScreen
import com.commerceos.android.ui.categories.CategoriesScreen
import com.commerceos.android.ui.dialogs.CancelOrderDialog
import com.commerceos.android.ui.dialogs.RxUploadDialog
import com.commerceos.android.ui.entity.*
import com.commerceos.android.ui.home.HomeScreen
import com.commerceos.android.ui.home.VerticalHomeScreen
import com.commerceos.android.ui.navigation.Screen
import com.commerceos.android.ui.orders.OrderHistoryScreen
import com.commerceos.android.ui.orders.OrderTrackingScreen
import com.commerceos.android.ui.payment.PaymentScreen
import com.commerceos.android.ui.prescriptions.PrescriptionVaultScreen
import com.commerceos.android.ui.product.ProductDetailScreen
import com.commerceos.android.ui.root.GlobalCartBar
import com.commerceos.android.ui.search.SearchScreen
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommerceOSApp(
    container: AppContainer,
    homeViewModel: HomeViewModel,
    authViewModel: AuthViewModel,
    cartViewModel: CartViewModel,
    checkoutViewModel: CheckoutViewModel,
    addressViewModel: AddressViewModel,
    orderViewModel: OrderViewModel,
    prescriptionViewModel: PrescriptionViewModel,
    productDetailViewModel: ProductDetailViewModel,
    catalogViewModel: CatalogViewModel,
    categoryViewModel: CategoryViewModel,
    verticalHomeViewModel: VerticalHomeViewModel,
    universalSearchViewModel: UniversalSearchViewModel
) {
    val router = remember { AppDestinationRouter() }
    val clientConfig = LocalClientConfiguration.current
    val terminology = clientConfig.terminology
    val features = clientConfig.features

    val session by container.sessionManager.session.collectAsState()
    val authenticatedCustomerId = session.authenticatedCustomerId

    var currentScreen by remember { mutableStateOf<Screen>(if (session.isAuthenticated) Screen.Home else Screen.Auth) }
    val backStack = remember { mutableStateListOf<Screen>() }

    fun navigate(screen: Screen) {
        if (currentScreen != screen) {
            backStack.add(currentScreen)
            currentScreen = screen
        }
    }

    fun navigateRoot(screen: Screen) {
        backStack.clear()
        currentScreen = screen
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            currentScreen = backStack.removeAt(backStack.size - 1)
        } else if (session.isAuthenticated) {
            currentScreen = Screen.Home
        }
    }

    BackHandler(enabled = currentScreen !is Screen.Auth) { goBack() }

    fun openUniversalSearch(query: UniversalSearchQuery) {
        val targetScreen = router.resolveSearch(query)
        if (targetScreen is Screen.Catalog) {
            catalogViewModel.open(targetScreen.query)
        }
        navigate(targetScreen)
    }

    fun resolveShortcutDestination(destination: HomeDestination) {
        val targetScreen = router.resolve(destination, clientConfig)
        when (targetScreen) {
            is Screen.OrderHistory -> {
                navigateRoot(Screen.OrderHistory)
                orderViewModel.loadHistory(authenticatedCustomerId)
            }
            is Screen.Cart -> {
                navigateRoot(Screen.Cart)
                cartViewModel.loadCart()
            }
            is Screen.Prescriptions -> {
                prescriptionViewModel.load(authenticatedCustomerId)
                navigate(Screen.Prescriptions)
            }
            is Screen.Categories -> {
                categoryViewModel.loadTaxonomy()
                navigateRoot(Screen.Categories)
            }
            is Screen.Catalog -> {
                catalogViewModel.open(targetScreen.query)
                navigate(targetScreen)
            }
            is Screen.ProductDetail -> {
                productDetailViewModel.load(targetScreen.productId)
                navigate(targetScreen)
            }
            is Screen.VerticalHome -> {
                verticalHomeViewModel.loadVertical(targetScreen.verticalId, addressViewModel.selectedAddress?.id)
                navigate(targetScreen)
            }
            else -> navigate(targetScreen)
        }
    }

    var showRxUploadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(authenticatedCustomerId, session.isAuthenticated) {
        if (session.isAuthenticated) {
            if (currentScreen !is Screen.Home) {
                backStack.clear()
                currentScreen = Screen.Home
            }
            homeViewModel.loadHomeData(authenticatedCustomerId)
            cartViewModel.init(authenticatedCustomerId)
            addressViewModel.init(authenticatedCustomerId)
            if (features.enablePrescriptionUpload) {
                prescriptionViewModel.load(authenticatedCustomerId)
            }
        } else {
            if (currentScreen !is Screen.Auth) {
                backStack.clear()
                currentScreen = Screen.Auth
            }
        }
    }

    val context = LocalContext.current

    LaunchedEffect(context) {
        addressViewModel.attachContext(context)
    }

    LaunchedEffect(Unit) {
        cartViewModel.addEvents.collect { event ->
            when (event) {
                is CartAddEvent.Success -> {
                    Toast.makeText(context, "Added ${event.name} to cart", Toast.LENGTH_SHORT).show()
                }
                is CartAddEvent.Failure -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkoutViewModel.events.collect { event ->
            when (event) {
                is CheckoutEvent.OrderPlaced -> {
                    cartViewModel.clear()
                    cartViewModel.loadCart()
                    orderViewModel.loadDetail(event.orderId)
                    navigateRoot(Screen.OrderTracking(event.orderId))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.events.collect { event ->
            when (event) {
                is AuthEvent.Authenticated -> {
                    container.sessionManager.login(
                        customerId = event.customerId,
                        phone = event.phone,
                        accessToken = event.accessToken,
                        refreshToken = event.refreshToken
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        orderViewModel.events.collect { event ->
            when (event) {
                is CancellationEvent.Success -> {}
                is CancellationEvent.Failure -> {}
            }
        }
    }

    if (currentScreen is Screen.Auth) {
        AuthScreen(viewModel = authViewModel)
        return
    }

    val isPrimary = currentScreen is Screen.Home ||
        currentScreen is Screen.Categories ||
        currentScreen is Screen.OrderHistory ||
        currentScreen is Screen.Cart ||
        currentScreen is Screen.Account

    val headerTitle = when (val screen = currentScreen) {
        is Screen.Home -> clientConfig.identity.appName
        is Screen.Categories -> "Categories"
        is Screen.OrderHistory -> terminology.orderLabel
        is Screen.Cart -> terminology.cartLabel
        is Screen.Account -> "My Account"
        is Screen.Catalog -> "Catalog"
        is Screen.ProductDetail -> "Product"
        is Screen.AddressSelection -> if (screen.fromProfile) "Saved Addresses" else "Deliver to"
        is Screen.PaymentGateway -> terminology.checkoutLabel
        is Screen.OrderTracking -> "Order Status"
        is Screen.Prescriptions -> terminology.prescriptionLabel
        is Screen.FeatureDisabled -> "Feature Disabled"
        else -> clientConfig.identity.appName
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAddressBottomSheet by remember { mutableStateOf(false) }
    val bottomNavColors = NavigationBarItemDefaults.colors(
        selectedIconColor = CommerceColors.PrimaryDark,
        selectedTextColor = CommerceColors.PrimaryDark,
        indicatorColor = CommerceColors.SuccessSoft,
        unselectedIconColor = CommerceColors.TextMuted,
        unselectedTextColor = CommerceColors.TextMuted
    )

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val hideTopBar = currentScreen is Screen.Home ||
                (currentScreen is Screen.AddressSelection && addressViewModel.platformUiState.isFlowActive)

            if (!hideTopBar) {
                TopAppBar(
                    title = {
                        Text(headerTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = CommerceColors.TextPrimary)
                    },
                    navigationIcon = {
                        IconButton(onClick = { goBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = CommerceColors.TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { navigateRoot(Screen.Cart); cartViewModel.loadCart() }) {
                            BadgedBox(badge = {
                                val count = cartViewModel.itemCount
                                if (count > 0) {
                                    Badge(
                                        containerColor = CommerceColors.PrimaryDark,
                                        contentColor = CommerceColors.OnPrimary
                                    ) {
                                        Text(if (count > 99) "99+" else "$count")
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Default.ShoppingCart,
                                    contentDescription = terminology.cartLabel,
                                    tint = CommerceColors.TextPrimary
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CommerceColors.Surface,
                        titleContentColor = CommerceColors.TextPrimary,
                        navigationIconContentColor = CommerceColors.TextPrimary,
                        actionIconContentColor = CommerceColors.TextPrimary
                    )
                )
            }
        },
        bottomBar = {
            Column {
                if (cartViewModel.itemCount > 0 && currentScreen !is Screen.Cart && currentScreen !is Screen.PaymentGateway) {
                    GlobalCartBar(
                        itemCount = cartViewModel.itemCount,
                        subtotal = cartViewModel.effectiveGrandTotal,
                        etaLabel = (checkoutViewModel.uiState.serviceability as? ServiceabilityState.Success)?.response?.etaLabel,
                        onClick = { navigate(Screen.Cart) }
                    )
                }
                if (isPrimary) {
                    NavigationBar(containerColor = CommerceColors.Surface, tonalElevation = 0.dp) {
                        NavigationBarItem(
                            selected = currentScreen is Screen.Home,
                            onClick = { navigateRoot(Screen.Home) },
                            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                            label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = bottomNavColors
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Categories,
                            onClick = { navigateRoot(Screen.Categories) },
                            icon = { Icon(Icons.Default.Menu, contentDescription = "Categories") },
                            label = { Text("Categories", fontSize = 11.sp) },
                            colors = bottomNavColors
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.OrderHistory,
                            onClick = { navigateRoot(Screen.OrderHistory); orderViewModel.loadHistory(authenticatedCustomerId) },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = terminology.orderLabel) },
                            label = { Text(terminology.orderLabel, fontSize = 11.sp) },
                            colors = bottomNavColors
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Cart,
                            onClick = { navigateRoot(Screen.Cart); cartViewModel.loadCart() },
                            icon = {
                                BadgedBox(badge = {
                                    val count = cartViewModel.itemCount
                                    if (count > 0) {
                                        Badge(
                                            containerColor = CommerceColors.PrimaryDark,
                                            contentColor = CommerceColors.OnPrimary
                                        ) {
                                            Text(if (count > 99) "99+" else "$count")
                                        }
                                    }
                                }) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = terminology.cartLabel)
                                }
                            },
                            label = { Text(terminology.cartLabel, fontSize = 11.sp) },
                            colors = bottomNavColors
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val screen = currentScreen) {
                is Screen.Home -> {
                    val activeAddr = addressViewModel.selectedAddress ?: addressViewModel.addresses.firstOrNull()
                    HomeScreen(
                        viewModel = homeViewModel,
                        customerId = authenticatedCustomerId,
                        cartItems = cartViewModel.cartItems,
                        selectedAddress = activeAddr,
                        calculatedEtaMinutes = addressViewModel.calculatedEtaMinutes,
                        locationHeaderLabel = addressViewModel.activeLocationHeaderLabel,
                        homeContext = activeAddr?.let { addr ->
                            val serviceability = (checkoutViewModel.uiState.serviceability as? ServiceabilityState.Success)
                            val status = when {
                                serviceability == null -> FulfillmentStatus.UNKNOWN
                                serviceability.addressId != addr.id -> FulfillmentStatus.UNKNOWN
                                serviceability.response.eligible -> FulfillmentStatus.SERVICEABLE
                                else -> FulfillmentStatus.UNSERVICEABLE
                            }
                            val fulfillment = FulfillmentContext(
                                addressId = addr.id,
                                status = status,
                                etaLabel = serviceability?.response?.etaLabel,
                                requestId = "home-ctx-${addr.id}"
                            )
                            HomeContext(
                                addressId = addr.id,
                                tag = addr.tag,
                                addressLine = addr.addressLine,
                                cityZip = "${addr.city} ${addr.postalCode}".trim(),
                                geoPoint = "${addr.latitude},${addr.longitude}",
                                fulfillment = fulfillment,
                                sequenceId = fulfillment.generatedAt
                            )
                        },
                        onChangeAddress = {
                            addressViewModel.init(authenticatedCustomerId)
                            showAddressBottomSheet = true
                        },
                        onProfileClick = {
                            navigateRoot(Screen.Account)
                        },
                    onEntityClick = { entity ->
                        when (entity) {
                            is CommerceEntity.ProductItem -> {
                                productDetailViewModel.load(entity.product.id)
                                navigate(Screen.ProductDetail(entity.product.id))
                            }
                            is CommerceEntity.CategoryItem -> {
                                resolveShortcutDestination(
                                    HomeDestination.Category(categoryId = entity.group.id, vertical = entity.vertical)
                                )
                            }
                            is CommerceEntity.Brand -> {
                                resolveShortcutDestination(
                                    HomeDestination.Brand(brandId = entity.item.id, vertical = entity.vertical)
                                )
                            }
                            is CommerceEntity.Shortcut -> resolveShortcutDestination(entity.destination)
                            is CommerceEntity.RestaurantItem -> {
                                resolveShortcutDestination(HomeDestination.Restaurant(restaurantId = entity.id))
                            }
                            is CommerceEntity.ServiceItem -> {
                                resolveShortcutDestination(HomeDestination.Service(serviceId = entity.id))
                            }
                            is CommerceEntity.DishItem -> resolveShortcutDestination(HomeDestination.Product(entity.id))
                            is CommerceEntity.StoreItem -> resolveShortcutDestination(HomeDestination.Store(entity.id))
                            is CommerceEntity.CollectionItem -> resolveShortcutDestination(HomeDestination.Collection(entity.id))
                            is CommerceEntity.CampaignItem -> resolveShortcutDestination(HomeDestination.Campaign(entity.id))
                            is CommerceEntity.OfferItem -> resolveShortcutDestination(HomeDestination.Offer(entity.id))
                            else -> {}
                        }
                    },
                    onAddToCart = { cartViewModel.addItem(it) },
                    onUpdateQuantity = { sku, qty ->
                        if (qty <= 0) cartViewModel.removeItem(sku)
                        else cartViewModel.updateQuantity(sku, qty)
                    },
                    onSearchClick = { query ->
                        openUniversalSearch(UniversalSearchQuery(text = query))
                    },
                    onVerticalSelect = { vertical ->
                        if (vertical.isLive && (vertical.status?.isServiceable ?: true)) {
                            resolveShortcutDestination(HomeDestination.Vertical(verticalId = vertical.id))
                        } else if (!vertical.isLive) {
                            scope.launch {
                                snackbarHostState.showSnackbar("${vertical.label} store is coming soon to your region.")
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("${vertical.label} is currently unserviceable at your selected delivery address.")
                            }
                        }
                    },
                    onOpenCatalog = {
                        val catalogQuery = CatalogQuery()
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                }
                is Screen.Catalog -> CatalogScreen(
                    viewModel = catalogViewModel,
                    onSelectProduct = { product ->
                        productDetailViewModel.load(product.id)
                        navigate(Screen.ProductDetail(product.id))
                    },
                    onAddToCart = { cartViewModel.addItem(it) }
                )
                is Screen.Categories -> CategoriesScreen(
                    viewModel = categoryViewModel,
                    onSelectCategory = { category ->
                        val catalogQuery = CatalogQuery(categoryId = category.id, categoryName = category.name)
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.VerticalHome -> VerticalHomeScreen(
                    verticalId = screen.verticalId,
                    viewModel = verticalHomeViewModel,
                    addressId = addressViewModel.selectedAddress?.id,
                    onBack = { goBack() },
                    onOpenCatalog = { catalogQuery ->
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.Store -> StoreScreen(
                    storeId = screen.storeId,
                    onBack = { goBack() },
                    onOpenCatalog = { catalogQuery ->
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.Restaurant -> RestaurantScreen(
                    restaurantId = screen.restaurantId,
                    onBack = { goBack() },
                    onOpenCatalog = { catalogQuery ->
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.Service -> ServiceScreen(
                    serviceId = screen.serviceId,
                    onBack = { goBack() },
                    onOpenCatalog = { catalogQuery ->
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.Campaign -> CampaignLandingScreen(
                    campaignId = screen.campaignId,
                    onBack = { goBack() },
                    onOpenCatalog = { catalogQuery ->
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.Brand -> BrandScreen(
                    brandId = screen.brandId,
                    onBack = { goBack() },
                    onOpenCatalog = { catalogQuery ->
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.Collection -> CollectionScreen(
                    collectionId = screen.collectionId,
                    onBack = { goBack() },
                    onOpenCatalog = { catalogQuery ->
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.Offer -> OfferLandingScreen(
                    offerId = screen.offerId,
                    onBack = { goBack() },
                    onOpenCatalog = { catalogQuery ->
                        catalogViewModel.open(catalogQuery)
                        navigate(Screen.Catalog(catalogQuery))
                    }
                )
                is Screen.ProductDetail -> ProductDetailScreen(
                    uiState = productDetailViewModel.uiState,
                    cartItems = cartViewModel.cartItems,
                    onAddToCart = { cartViewModel.addItem(it) },
                    onQuantityChange = { sku, quantity -> cartViewModel.updateQuantity(sku, quantity) },
                    onRemoveItem = { cartViewModel.removeItem(it) },
                    onRetry = { productDetailViewModel.load(screen.productId) }
                )
                is Screen.Cart -> CartScreen(
                    cartItems = cartViewModel.cartItems,
                    cartSubtotal = cartViewModel.effectiveSubtotal,
                    freeDeliveryThreshold = cartViewModel.freeDeliveryThreshold,
                    freeDeliveryEligible = cartViewModel.freeDeliveryEligible,
                    remainingForFreeDelivery = cartViewModel.remainingForFreeDelivery,
                    itemsSubtotal = MoneyFormatter.format(cartViewModel.effectiveSubtotal),
                    totalSavings = MoneyFormatter.format(cartViewModel.totalSavings),
                    expressFee = MoneyFormatter.format(cartViewModel.effectiveExpressFee),
                    coldChainFee = if (cartViewModel.containsColdChain) MoneyFormatter.format(cartViewModel.effectiveColdChainFee) else null,
                    grandTotal = MoneyFormatter.format(cartViewModel.effectiveGrandTotal),
                    prescriptions = prescriptionViewModel.prescriptions,
                    attachedPrescriptionId = checkoutViewModel.uiState.prescriptionId,
                    onQuantityChange = { sku, quantity -> cartViewModel.updateQuantity(sku, quantity) },
                    onRemoveItem = { cartViewModel.removeItem(it) },
                    onUploadPrescription = { showRxUploadDialog = true },
                    onAttachPrescription = { checkoutViewModel.attachPrescription(it) },
                    onProceedToAddress = {
                        val currentSelected = addressViewModel.selectedAddress
                            ?: addressViewModel.addresses.firstOrNull { it.isDefault }
                            ?: addressViewModel.addresses.firstOrNull()

                        checkoutViewModel.start(authenticatedCustomerId, cartViewModel.cartItems)
                        if (currentSelected != null) {
                            checkoutViewModel.selectAddress(currentSelected)
                            navigate(Screen.PaymentGateway)
                        } else {
                            addressViewModel.init(authenticatedCustomerId, force = true)
                            navigate(Screen.AddressSelection(fromCheckout = true))
                        }
                    },
                    onStartShopping = { navigateRoot(Screen.Catalog(CatalogQuery())) }
                )
                is Screen.AddressSelection -> {
                    val isFromCheckout = screen.fromCheckout
                    val isFromProfile = screen.fromProfile
                    AddressScreen(
                        viewModel = addressViewModel,
                        serviceability = checkoutViewModel.uiState.serviceability,
                        onSelectAddress = { address ->
                            if (isFromCheckout) {
                                checkoutViewModel.selectAddress(address)
                            } else if (isFromProfile) {
                                addressViewModel.select(address)
                            } else {
                                addressViewModel.select(address)
                                goBack()
                            }
                        },
                        onProceedToPayment = if (isFromCheckout) {
                            {
                                val addr = addressViewModel.selectedAddress ?: addressViewModel.addresses.firstOrNull()
                                if (addr != null) {
                                    checkoutViewModel.selectAddress(addr)
                                }
                                navigate(Screen.PaymentGateway)
                            }
                        } else null,
                        onSavedSuccessfully = {
                            if (isFromCheckout) {
                                val addr = addressViewModel.selectedAddress ?: addressViewModel.addresses.firstOrNull()
                                if (addr != null) checkoutViewModel.selectAddress(addr)
                                navigate(Screen.PaymentGateway)
                            } else if (isFromProfile) {
                                addressViewModel.loadAddresses()
                            } else {
                                goBack()
                            }
                        }
                    )
                }
                is Screen.PaymentGateway -> PaymentScreen(
                    checkoutUiState = checkoutViewModel.uiState,
                    onAuthorize = { method ->
                        checkoutViewModel.executeCheckout(method)
                    },
                    onChangeAddress = {
                        addressViewModel.init(authenticatedCustomerId, force = true)
                        navigate(Screen.AddressSelection(fromCheckout = true))
                    }
                )
                is Screen.OrderTracking -> OrderTrackingScreen(
                    detail = orderViewModel.detail,
                    liveTracking = orderViewModel.liveTracking,
                    onRefresh = { orderViewModel.loadDetail(screen.orderId) },
                    onBack = { goBack() }
                )
                is Screen.OrderHistory -> OrderHistoryScreen(
                    history = orderViewModel.history,
                    onRefresh = { orderViewModel.loadHistory(authenticatedCustomerId) },
                    onCancelOrder = { order -> orderViewModel.openCancelDialog(order) },
                    onReorderItem = { item -> cartViewModel.addReorderItem(item) },
                    onTrackOrder = { order ->
                        orderViewModel.loadDetail(order.id)
                        navigate(Screen.OrderTracking(order.id))
                    }
                )
                is Screen.Account -> AccountScreen(
                    profile = addressViewModel.profile,
                    fallbackPhone = session.phone,
                    orderCount = (orderViewModel.history as? OrderHistoryUiState.Content)?.orders?.size ?: 0,
                    addressCount = addressViewModel.addresses.size,
                    isLoadingAddresses = addressViewModel.isLoading && addressViewModel.addresses.isEmpty(),
                    prescriptionCount = prescriptionViewModel.prescriptions.size,
                    onOrders = {
                        navigateRoot(Screen.OrderHistory)
                        orderViewModel.loadHistory(authenticatedCustomerId)
                    },
                    onPrescriptions = {
                        if (features.enablePrescriptionUpload) {
                            prescriptionViewModel.load(authenticatedCustomerId)
                            navigate(Screen.Prescriptions)
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("Prescription feature is disabled for this store.")
                            }
                        }
                    },
                    onAddresses = {
                        addressViewModel.init(authenticatedCustomerId)
                        navigate(Screen.AddressSelection(fromProfile = true))
                    },
                    onLogout = {
                        container.sessionManager.logout()
                        homeViewModel.reset()
                        cartViewModel.reset()
                        checkoutViewModel.reset()
                        addressViewModel.reset()
                        orderViewModel.reset()
                        prescriptionViewModel.reset()
                        productDetailViewModel.reset()
                        catalogViewModel.reset()
                        categoryViewModel.reset()
                        verticalHomeViewModel.reset()
                        universalSearchViewModel.reset()
                    }
                )
                is Screen.Prescriptions -> PrescriptionVaultScreen(
                    prescriptions = prescriptionViewModel.prescriptions,
                    isLoading = prescriptionViewModel.isLoading,
                    errorMessage = prescriptionViewModel.errorMessage,
                    onRefresh = { prescriptionViewModel.load(authenticatedCustomerId) },
                    onUpload = { showRxUploadDialog = true }
                )
                is Screen.Search -> SearchScreen(
                    initialQuery = screen.query,
                    viewModel = universalSearchViewModel,
                    trendingSuggestions = homeViewModel.storefrontSuggestions,
                    liveVerticals = homeViewModel.verticals.filter { it.isLive }.map { it.label },
                    onPerformSearch = { searchQuery ->
                        openUniversalSearch(searchQuery)
                    },
                    onSelectSearchResult = { res ->
                        navigate(router.resolve(res.toDestination(), clientConfig))
                    },
                    onBack = { goBack() }
                )
                is Screen.FeatureDisabled -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = CommerceColors.Danger,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(Spacing.md))
                        Text(
                            screen.featureName,
                            style = CommerceTypography.Heading,
                            color = CommerceColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            screen.message,
                            style = CommerceTypography.Body,
                            color = CommerceColors.TextMuted
                        )
                        Spacer(modifier = Modifier.height(Spacing.xl))
                        Button(onClick = { navigateRoot(Screen.Home) }) {
                            Text("Back to Home")
                        }
                    }
                }
                is Screen.Auth -> {
                    AuthScreen(viewModel = authViewModel)
                }
                is Screen.RiderPartnerApp -> {
                    com.commerceos.android.rider.RiderAppScreen(onBack = { goBack() })
                }
                else -> {}
            }

            if (showRxUploadDialog) {
                RxUploadDialog(
                    onDismiss = { showRxUploadDialog = false },
                    onUpload = { patientName, attachments ->
                        prescriptionViewModel.upload(authenticatedCustomerId, patientName, attachments)
                        showRxUploadDialog = false
                    }
                )
            }

            val cancelState = orderViewModel.cancellation
            if (cancelState.isVisible) {
                CancelOrderDialog(
                    orderId = cancelState.orderToCancel!!.id,
                    reasons = cancelState.reasons,
                    selectedReasonCode = cancelState.selectedReasonCode,
                    reasonNote = cancelState.reasonNote,
                    submitting = cancelState.isSubmitting,
                    onSelectReason = { orderViewModel.selectReason(it) },
                    onReasonNoteChange = { orderViewModel.setReasonNote(it) },
                    onConfirm = { orderViewModel.confirmCancel() },
                    onDismiss = { orderViewModel.dismissCancelDialog() }
                )
            }

            if (showAddressBottomSheet) {
                AddressSelectionBottomSheet(
                    addresses = addressViewModel.addresses,
                    selectedAddressId = addressViewModel.selectedAddress?.id,
                    onDismiss = { showAddressBottomSheet = false },
                    onSelectAddress = { addr ->
                        addressViewModel.select(addr)
                        showAddressBottomSheet = false
                    },
                    onAddNewAddress = {
                        showAddressBottomSheet = false
                        addressViewModel.startAddAddressFlow()
                        navigate(Screen.AddressSelection())
                    },
                    onUseCurrentLocation = {
                        addressViewModel.useCurrentGpsLocationAndMatchSaved {
                            showAddressBottomSheet = false
                        }
                    },
                    onEditAddress = { addr ->
                        showAddressBottomSheet = false
                        addressViewModel.startEditAddressFlow(addr)
                        navigate(Screen.AddressSelection())
                    },
                    onDeleteAddress = { id -> addressViewModel.deleteAddress(id) },
                    onSetDefaultAddress = { id -> addressViewModel.setDefaultAddress(id) }
                )
            }
        }
    }
}
