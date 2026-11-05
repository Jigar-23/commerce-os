package com.commerceos.android

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.config.LocalClientConfiguration
import com.commerceos.android.di.AppContainer
import kotlinx.coroutines.launch
import com.commerceos.android.model.*
import com.commerceos.android.location.cleanAddressSegments
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
import com.commerceos.android.ui.splash.SplashScreen
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
    universalSearchViewModel: UniversalSearchViewModel,
    pendingNotificationAction: String? = null,
    pendingNotificationTargetId: String? = null,
    onConsumePendingNotification: () -> Unit = {}
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

    var isSplashVisible by rememberSaveable { mutableStateOf(true) }
    var isLocationLockinVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = !isSplashVisible && !isLocationLockinVisible && currentScreen !is Screen.Auth) { goBack() }

    fun openUniversalSearch(query: UniversalSearchQuery) {
        val targetScreen = router.resolveSearch(query)
        if (targetScreen is Screen.Catalog) {
            catalogViewModel.open(targetScreen.query)
        }
        if (query.text.isNotBlank()) {
            universalSearchViewModel.executeSearch(query.text)
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
                prescriptionViewModel.load(authenticatedCustomerId)
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

    LaunchedEffect(pendingNotificationAction, pendingNotificationTargetId, session.isAuthenticated) {
        val action = pendingNotificationAction ?: return@LaunchedEffect
        if (!session.isAuthenticated) return@LaunchedEffect

        when (action) {
            "TRACK_ORDER" -> {
                val orderId = pendingNotificationTargetId
                if (!orderId.isNullOrBlank()) {
                    orderViewModel.loadDetail(orderId)
                    navigateRoot(Screen.OrderTracking(orderId))
                } else {
                    navigateRoot(Screen.OrderHistory)
                    orderViewModel.loadHistory(authenticatedCustomerId)
                }
            }
            "REFILL_CART", "CART" -> {
                navigateRoot(Screen.Cart)
                cartViewModel.loadCart()
            }
            "REORDER", "ORDERS" -> {
                navigateRoot(Screen.OrderHistory)
                orderViewModel.loadHistory(authenticatedCustomerId)
            }
            "NOTIFICATIONS" -> {
                navigate(Screen.Notifications)
            }
        }
        onConsumePendingNotification()
    }

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

    LaunchedEffect(cartViewModel, checkoutViewModel) {
        checkoutViewModel.setMutationGate(cartViewModel)
        cartViewModel.registerNetworkMonitoring()
    }

    LaunchedEffect(Unit) {
        checkoutViewModel.events.collect { event ->
            when (event) {
                is CheckoutEvent.OrderPlaced -> {
                    checkoutViewModel.reset()
                    cartViewModel.unlockMutations()
                    cartViewModel.clear()
                    cartViewModel.loadCart()
                    val pin = event.order?.effectiveDeliveryPin
                    if (!pin.isNullOrBlank()) {
                        orderViewModel.setKnownOrderPin(event.orderId, pin)
                    }
                    val itemCount = event.order?.items?.sumOf { it.quantity } ?: 1
                    val totalAmount = event.order?.totalAmount?.let { com.commerceos.android.util.MoneyFormatter.format(it) } ?: "540"
                    com.commerceos.android.notifications.CustomerNotificationManager.postOrderPlacedNotification(
                        context = context,
                        orderId = event.orderId,
                        itemCount = itemCount,
                        totalAmount = totalAmount,
                        etaMins = 12
                    )
                    event.order?.items?.forEach { item ->
                        if (com.commerceos.android.notifications.ChronicRefillReminderManager.isChronicMedication(item.name, item.sku)) {
                            com.commerceos.android.notifications.ChronicRefillReminderManager.scheduleRefillReminder(
                                context,
                                medicineName = item.name,
                                sku = item.sku,
                                totalSupplyDays = 30
                            )
                        }
                    }
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
        currentScreen is Screen.Account

    val headerTitle = when (val screen = currentScreen) {
        is Screen.Home -> clientConfig.identity.appName
        is Screen.Categories -> "Categories"
        is Screen.OrderHistory -> terminology.orderLabel
        is Screen.Cart -> terminology.cartLabel
        is Screen.Account -> "My Account"
        is Screen.Catalog -> "Catalog"
        is Screen.ProductDetail -> "Medicine Details"
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
        indicatorColor = Color.Transparent,
        unselectedIconColor = Color(0xFF64748B),
        unselectedTextColor = Color(0xFF64748B)
    )

    if (isSplashVisible) {
        SplashScreen(
            onDismiss = {
                isSplashVisible = false
                isLocationLockinVisible = true
            }
        )
    } else if (isLocationLockinVisible) {
        val confirmedAddr = addressViewModel.selectedAddress ?: addressViewModel.addresses.firstOrNull { it.isDefault }
        val isConfirmed = confirmedAddr != null &&
            confirmedAddr.id.isNotBlank() &&
            confirmedAddr.city.isNotBlank() &&
            confirmedAddr.addressLine.isNotBlank() &&
            (confirmedAddr.latitude ?: 0.0) != 0.0 &&
            (confirmedAddr.longitude ?: 0.0) != 0.0
        val locality = if (isConfirmed) {
            addressViewModel.activeLocationHeaderLabel
                ?: confirmedAddr?.city?.takeIf { it.isNotBlank() }
                ?: confirmedAddr?.tag?.takeIf { it.isNotBlank() }
                ?: "Delivery Location"
        } else {
            "Select Delivery Location"
        }
        val serverEta = (checkoutViewModel.uiState.serviceability as? ServiceabilityState.Success)
            ?.takeIf { it.addressId == confirmedAddr?.id }
            ?.response?.etaLabel
        com.commerceos.android.ui.splash.LocationLockinScreen(
            locality = locality,
            isConfirmed = isConfirmed,
            etaLabel = serverEta,
            onDismiss = { isLocationLockinVisible = false },
            onSelectLocation = {
                isLocationLockinVisible = false
                currentScreen = Screen.AddressSelection()
            }
        )
    } else {
        val isCategoryScreen = currentScreen is Screen.Categories
        val isHomeScreen = currentScreen is Screen.Home
        val isNotificationScreen = currentScreen is Screen.Notifications
        Scaffold(
            containerColor = CommerceColors.Background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            val hideTopBar = currentScreen is Screen.Home ||
                currentScreen is Screen.OrderHistory ||
                currentScreen is Screen.OrderTracking ||
                currentScreen is Screen.Cart ||
                currentScreen is Screen.PaymentGateway ||
                currentScreen is Screen.Account ||
                currentScreen is Screen.Prescriptions ||
                currentScreen is Screen.AddressSelection ||
                currentScreen is Screen.Notifications ||
                currentScreen is Screen.Categories ||
                currentScreen is Screen.Search

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
                        if (currentScreen !is Screen.PaymentGateway) {
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
            Column(
                modifier = Modifier.background(CommerceColors.Background)
            ) {
                if (cartViewModel.itemCount > 0 && currentScreen !is Screen.Cart && currentScreen !is Screen.PaymentGateway && currentScreen !is Screen.OrderTracking && currentScreen !is Screen.Account && currentScreen !is Screen.ProductDetail) {
                    GlobalCartBar(
                        itemCount = cartViewModel.itemCount,
                        subtotal = cartViewModel.effectiveGrandTotal,
                        etaLabel = (checkoutViewModel.uiState.serviceability as? ServiceabilityState.Success)?.response?.etaLabel,
                        onClick = { navigate(Screen.Cart) }
                    )
                }
                if (isPrimary) {
                    HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 0.5.dp)
                    Surface(
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CommerceBottomNavItem(
                                selected = currentScreen is Screen.Home,
                                icon = Icons.Default.Home,
                                label = "Home",
                                onClick = { navigateRoot(Screen.Home) }
                            )
                            CommerceBottomNavItem(
                                selected = currentScreen is Screen.Categories,
                                icon = Icons.Default.GridView,
                                label = "Categories",
                                onClick = { navigateRoot(Screen.Categories) }
                            )
                            CommerceBottomNavItem(
                                selected = currentScreen is Screen.OrderHistory,
                                icon = Icons.Default.ShoppingBag,
                                label = terminology.orderLabel,
                                onClick = {
                                    navigateRoot(Screen.OrderHistory)
                                    orderViewModel.loadHistory(authenticatedCustomerId)
                                }
                            )
                            CommerceBottomNavItem(
                                selected = currentScreen is Screen.Account,
                                icon = Icons.Default.Person,
                                label = "Account",
                                onClick = { navigateRoot(Screen.Account) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        val topPadding = if (isCategoryScreen || isHomeScreen || isNotificationScreen) 0.dp else padding.calculateTopPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = topPadding,
                    bottom = padding.calculateBottomPadding()
                )
        ) {
            val activeAddr = addressViewModel.selectedAddress ?: addressViewModel.addresses.firstOrNull { it.isDefault }
            val currentHomeContext = activeAddr?.let { addr ->
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
            }

            when (val screen = currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        customerId = authenticatedCustomerId,
                        cartItems = cartViewModel.cartItems,
                        selectedAddress = activeAddr,
                        calculatedEtaMinutes = addressViewModel.calculatedEtaMinutes,
                        locationHeaderLabel = addressViewModel.activeLocationHeaderLabel,
                        isLocating = addressViewModel.isAcquiringLiveGps,
                        homeContext = currentHomeContext,
                        onChangeAddress = {
                            addressViewModel.init(authenticatedCustomerId)
                            showAddressBottomSheet = true
                        },
                        onProfileClick = {
                            navigateRoot(Screen.Account)
                        },
                        onNotificationsClick = {
                            navigate(Screen.Notifications)
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
                        if (query.isBlank()) {
                            universalSearchViewModel.reset()
                        }
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
                        universalSearchViewModel.reset()
                        openUniversalSearch(UniversalSearchQuery(text = ""))
                    },
                    onUploadPrescription = {
                        showRxUploadDialog = true
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
                    homeContext = currentHomeContext,
                    selectedAddress = activeAddr,
                    calculatedEtaMinutes = addressViewModel.calculatedEtaMinutes,
                    locationHeaderLabel = addressViewModel.activeLocationHeaderLabel,
                    isLocating = addressViewModel.isAcquiringLiveGps,
                    onChangeAddress = {
                        addressViewModel.init(authenticatedCustomerId)
                        showAddressBottomSheet = true
                    },
                    onProfileClick = {
                        navigateRoot(Screen.Account)
                    },
                    onNotificationsClick = {
                        navigate(Screen.Notifications)
                    },
                    onSearchClick = { query ->
                        if (query.isBlank()) {
                            universalSearchViewModel.reset()
                        }
                        openUniversalSearch(UniversalSearchQuery(text = query))
                    },
                    onUploadPrescription = {
                        showRxUploadDialog = true
                    },
                    onSelectCategory = { category ->
                        universalSearchViewModel.executeSearch(category.name)
                        openUniversalSearch(UniversalSearchQuery(text = category.name))
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
                is Screen.ProductDetail -> {
                    LaunchedEffect(screen.productId) {
                        productDetailViewModel.load(screen.productId)
                    }
                    ProductDetailScreen(
                        uiState = productDetailViewModel.uiState,
                        cartItems = cartViewModel.cartItems,
                        onAddToCart = { cartViewModel.addItem(it) },
                        onQuantityChange = { sku, quantity -> cartViewModel.updateQuantity(sku, quantity) },
                        onRemoveItem = { cartViewModel.removeItem(it) },
                        onRetry = { productDetailViewModel.load(screen.productId) }
                    )
                }
                is Screen.Cart -> CartScreen(
                    cartItems = cartViewModel.cartItems,
                    cartSubtotal = cartViewModel.effectiveSubtotal,
                    freeDeliveryThreshold = cartViewModel.freeDeliveryThreshold,
                    freeDeliveryEligible = cartViewModel.freeDeliveryEligible,
                    remainingForFreeDelivery = cartViewModel.remainingForFreeDelivery,
                    itemsSubtotal = MoneyFormatter.format(cartViewModel.effectiveSubtotal),
                    mrpSavings = if (cartViewModel.itemMrpSavings > java.math.BigDecimal.ZERO) MoneyFormatter.format(cartViewModel.itemMrpSavings) else null,
                    totalSavings = MoneyFormatter.format(cartViewModel.totalSavings),
                    couponDiscount = if (cartViewModel.couponDiscountAmount > java.math.BigDecimal.ZERO) MoneyFormatter.format(cartViewModel.couponDiscountAmount) else null,
                    appliedCouponCode = cartViewModel.appliedCouponCode,
                    couponDiscountAmount = cartViewModel.couponDiscountAmount,
                    availableCampaigns = cartViewModel.availableCampaigns,
                    couponFeedbackMessage = cartViewModel.couponFeedbackMessage,
                    isCouponError = cartViewModel.isCouponError,
                    onApplyCoupon = { code -> cartViewModel.applyCouponWithFeedback(code) },
                    onRemoveCoupon = { cartViewModel.removeCoupon() },
                    expressFee = MoneyFormatter.format(cartViewModel.effectiveExpressFee),
                    coldChainFee = if (cartViewModel.containsColdChain) MoneyFormatter.format(cartViewModel.effectiveColdChainFee) else null,
                    grandTotal = MoneyFormatter.format(cartViewModel.effectiveGrandTotal),
                    prescriptions = prescriptionViewModel.prescriptions,
                    attachedPrescriptionId = checkoutViewModel.uiState.prescriptionId,
                    deliveryAddress = addressViewModel.selectedAddress
                        ?: addressViewModel.addresses.firstOrNull { it.isDefault },
                    onChangeAddress = {
                        checkoutViewModel.start(authenticatedCustomerId, cartViewModel.cartItems, cartViewModel.couponDiscountAmount, cartViewModel.appliedCouponCode)
                        addressViewModel.init(authenticatedCustomerId, force = true)
                        navigate(Screen.AddressSelection(fromCheckout = true))
                    },
                    onQuantityChange = { sku, quantity -> cartViewModel.updateQuantity(sku, quantity) },
                    onRemoveItem = { cartViewModel.removeItem(it) },
                    onUploadPrescription = { showRxUploadDialog = true },
                    onAttachPrescription = { checkoutViewModel.attachPrescription(it) },
                    onProceedToAddress = {
                        val currentSelected = addressViewModel.selectedAddress
                            ?: addressViewModel.addresses.firstOrNull { it.isDefault }

                        val isCompleteSavedAddress = currentSelected != null &&
                            currentSelected.id.isNotBlank() &&
                            !currentSelected.id.startsWith("temp_gps_") &&
                            !currentSelected.tag.equals("Current Location", ignoreCase = true) &&
                            currentSelected.addressLine.isNotBlank() &&
                            currentSelected.city.isNotBlank() &&
                            currentSelected.postalCode.isNotBlank() &&
                            (currentSelected.latitude ?: 0.0) != 0.0 &&
                            (currentSelected.longitude ?: 0.0) != 0.0

                        checkoutViewModel.start(authenticatedCustomerId, cartViewModel.cartItems, cartViewModel.couponDiscountAmount, cartViewModel.appliedCouponCode)

                        if (isCompleteSavedAddress && currentSelected != null) {
                            checkoutViewModel.selectAddress(currentSelected)
                            navigate(Screen.PaymentGateway)
                        } else {
                            addressViewModel.init(authenticatedCustomerId, force = true)
                            navigate(Screen.AddressSelection(fromCheckout = true))
                        }
                    },
                    onStartShopping = { navigateRoot(Screen.Catalog(CatalogQuery())) },
                    onBack = { goBack() }
                )
                is Screen.AddressSelection -> {
                    val isFromCheckout = screen.fromCheckout
                    val isFromProfile = screen.fromProfile
                    val isDirectAddFlow = screen.directAddFlow
                    AddressScreen(
                        viewModel = addressViewModel,
                        serviceability = checkoutViewModel.uiState.serviceability,
                        onBack = { goBack() },
                        fromProfile = isFromProfile,
                        directAddFlow = isDirectAddFlow,
                        onSelectAddress = { address ->
                            if (isFromCheckout) {
                                if (checkoutViewModel.uiState.items.isEmpty() && cartViewModel.cartItems.isNotEmpty()) {
                                    checkoutViewModel.start(authenticatedCustomerId, cartViewModel.cartItems, cartViewModel.couponDiscountAmount, cartViewModel.appliedCouponCode)
                                }
                                checkoutViewModel.selectAddress(address)
                                addressViewModel.select(address)
                                if (backStack.lastOrNull() is Screen.PaymentGateway) {
                                    goBack()
                                } else {
                                    navigate(Screen.PaymentGateway)
                                }
                            } else if (isFromProfile) {
                                addressViewModel.setDefaultAddress(address.id)
                                addressViewModel.select(address)
                                scope.launch {
                                    snackbarHostState.showSnackbar("Default address set to ${address.tag.ifBlank { "Home" }}")
                                }
                            } else {
                                addressViewModel.select(address)
                                goBack()
                            }
                        },
                        onSavedSuccessfully = {
                            val saved = addressViewModel.selectedAddress ?: addressViewModel.addresses.firstOrNull { it.isDefault }
                            if (saved != null) {
                                addressViewModel.select(saved)
                                if (isFromCheckout) {
                                    if (checkoutViewModel.uiState.items.isEmpty() && cartViewModel.cartItems.isNotEmpty()) {
                                        checkoutViewModel.start(authenticatedCustomerId, cartViewModel.cartItems, cartViewModel.couponDiscountAmount, cartViewModel.appliedCouponCode)
                                    }
                                    checkoutViewModel.selectAddress(saved)
                                    if (backStack.lastOrNull() is Screen.PaymentGateway) {
                                        goBack()
                                    } else {
                                        navigate(Screen.PaymentGateway)
                                    }
                                } else {
                                    navigateRoot(Screen.Home)
                                }
                            }
                        }
                    )
                }
                is Screen.PaymentGateway -> {
                    if ((checkoutViewModel.uiState.items.isEmpty() && cartViewModel.cartItems.isNotEmpty()) ||
                        checkoutViewModel.customerId.isBlank()) {
                        checkoutViewModel.start(authenticatedCustomerId, cartViewModel.cartItems, cartViewModel.couponDiscountAmount, cartViewModel.appliedCouponCode)
                        val activeAddr = addressViewModel.selectedAddress ?: addressViewModel.addresses.firstOrNull { it.isDefault }
                        if (activeAddr != null && checkoutViewModel.uiState.address == null) {
                            checkoutViewModel.selectAddress(activeAddr)
                        }
                    }
                    PaymentScreen(
                        checkoutUiState = checkoutViewModel.uiState,
                        onAuthorize = { method ->
                            checkoutViewModel.executeCheckout(method)
                        },
                        onChangeAddress = {
                            addressViewModel.init(authenticatedCustomerId, force = true)
                            navigate(Screen.AddressSelection(fromCheckout = true))
                        },
                        onBack = { goBack() }
                    )
                }
                is Screen.OrderTracking -> OrderTrackingScreen(
                    detail = orderViewModel.detail,
                    liveTracking = orderViewModel.liveTracking,
                    isReconnecting = orderViewModel.isStreamReconnecting,
                    onRefresh = { orderViewModel.loadDetail(screen.orderId) },
                    onBack = { goBack() },
                    onReorderOrder = { order ->
                        val items = order.items.orEmpty()
                        if (items.isNotEmpty()) {
                            cartViewModel.reorderAll(items)
                            scope.launch {
                                snackbarHostState.showSnackbar("Added ${items.size} items to cart")
                            }
                            navigate(Screen.Cart)
                        }
                    },
                    onReorderItem = { item ->
                        cartViewModel.addReorderItem(item)
                        navigate(Screen.Cart)
                    }
                )
                is Screen.OrderHistory -> OrderHistoryScreen(
                    history = orderViewModel.history,
                    onRefresh = { orderViewModel.loadHistory(authenticatedCustomerId) },
                    onCancelOrder = { order -> orderViewModel.openCancelDialog(order) },
                    onReorderOrder = { order ->
                        val items = order.items.orEmpty()
                        if (items.isNotEmpty()) {
                            cartViewModel.reorderAll(items)
                            scope.launch {
                                snackbarHostState.showSnackbar("Added ${items.size} items to cart")
                            }
                            navigate(Screen.Cart)
                        }
                    },
                    onReorderItem = { item ->
                        cartViewModel.addReorderItem(item)
                        navigate(Screen.Cart)
                    },
                    onTrackOrder = { order ->
                        val targetId = order.resolvedId.ifBlank { order.id }
                        orderViewModel.loadDetail(targetId)
                        navigate(Screen.OrderTracking(targetId))
                    }
                )
                is Screen.Account -> AccountScreen(
                    customerId = authenticatedCustomerId,
                    profile = addressViewModel.profile,
                    fallbackPhone = session.phone,
                    orderCount = (orderViewModel.history as? OrderHistoryUiState.Content)?.orders?.size ?: 0,
                    addressCount = addressViewModel.addresses.size,
                    isLoadingAddresses = addressViewModel.isLoading && addressViewModel.addresses.isEmpty(),
                    prescriptionCount = prescriptionViewModel.prescriptions.size,
                    storeContactPhone = clientConfig.identity.supportPhone.takeIf { it.isNotBlank() } ?: "+91 1800-208-9999",
                    onOrders = {
                        navigateRoot(Screen.OrderHistory)
                        orderViewModel.loadHistory(authenticatedCustomerId)
                    },
                    onPrescriptions = {
                        prescriptionViewModel.load(authenticatedCustomerId)
                        navigate(Screen.Prescriptions)
                    },
                    onAddresses = {
                        addressViewModel.init(authenticatedCustomerId)
                        navigate(Screen.AddressSelection(fromProfile = true))
                    },
                    onNotifications = {
                        navigate(Screen.Notifications)
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
                is Screen.Notifications -> com.commerceos.android.ui.notifications.NotificationsScreen(
                    onBack = { goBack() },
                    onNavigateToOrders = {
                        navigateRoot(Screen.OrderHistory)
                        orderViewModel.loadHistory(authenticatedCustomerId)
                    },
                    onNavigateToPrescriptions = {
                        prescriptionViewModel.load(authenticatedCustomerId)
                        navigate(Screen.Prescriptions)
                    },
                    onNavigateToCart = {
                        navigate(Screen.Cart)
                    }
                )
                is Screen.Prescriptions -> PrescriptionVaultScreen(
                    prescriptions = prescriptionViewModel.prescriptions,
                    isLoading = prescriptionViewModel.isLoading,
                    errorMessage = prescriptionViewModel.errorMessage,
                    onRefresh = { prescriptionViewModel.load(authenticatedCustomerId) },
                    onUpload = { showRxUploadDialog = true },
                    onBack = { goBack() }
                )
                is Screen.Search -> SearchScreen(
                    initialQuery = screen.query,
                    viewModel = universalSearchViewModel,
                    trendingSuggestions = homeViewModel.storefrontSuggestions,
                    liveVerticals = homeViewModel.verticals.filter { it.isLive }.map { it.label },
                    cartQuantityMap = cartViewModel.cartItems.associate { it.sku to it.quantity },
                    cartItemCount = cartViewModel.cartItems.sumOf { it.quantity },
                    onPerformSearch = { searchQuery ->
                        openUniversalSearch(searchQuery)
                    },
                    onSelectSearchResult = { res ->
                        resolveShortcutDestination(res.toDestination())
                    },
                    onAddToCart = { res ->
                        cartViewModel.addItem(
                            com.commerceos.android.model.CommerceProduct(
                                id = res.entityId,
                                sku = res.sku ?: res.entityId,
                                name = res.title,
                                brandName = res.subtitle,
                                price = res.price ?: 0.0,
                                sellingPrice = res.price ?: 0.0,
                                image = res.image,
                                verticalId = res.vertical.ifBlank { "pharmacy" }
                            )
                        )
                    },
                    onUpdateQuantity = { sku, qty ->
                        if (qty <= 0) cartViewModel.removeItem(sku)
                        else cartViewModel.updateQuantity(sku, qty)
                    },
                    onViewCart = { navigate(Screen.Cart) },
                    onUploadPrescription = { showRxUploadDialog = true },
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
                    customerId = authenticatedCustomerId,
                    onDismiss = { showRxUploadDialog = false },
                    onUpload = { patientName, attachments, onResult ->
                        prescriptionViewModel.upload(
                            customerId = authenticatedCustomerId,
                            patientName = patientName,
                            attachments = attachments,
                            onError = { err -> onResult(false, err) },
                            onUploaded = { uploadedRx ->
                                checkoutViewModel.attachPrescription(uploadedRx.id)
                                onResult(true, null)
                            }
                        )
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
                val addressState = addressViewModel.platformUiState
                val place = addressViewModel.liveGpsPlace
                val placeFormatted = cleanAddressSegments(
                    place?.subLocality,
                    place?.locality,
                    place?.city,
                    place?.state,
                    place?.postalCode
                ).ifBlank { cleanAddressSegments(place?.formattedAddress) }

                AddressSelectionBottomSheet(
                    addresses = addressViewModel.addresses,
                    selectedAddressId = addressViewModel.selectedAddress?.id,
                    liveGpsPlaceFormatted = placeFormatted,
                    getDistanceMeters = { addr -> addressViewModel.getDistanceToSavedAddressMeters(addr) },
                    searchResults = addressState.placeSearchResults,
                    isSearchingPlaces = addressState.isSearchingPlaces,
                    onSearchQueryChange = { query -> addressViewModel.searchLocationPlaces(query) },
                    onSelectPlaceSearchResult = { result ->
                        showAddressBottomSheet = false
                        addressViewModel.selectPlaceSearchResult(result)
                        navigate(Screen.AddressSelection(directAddFlow = true))
                    },
                    onDismiss = {
                        showAddressBottomSheet = false
                        addressViewModel.searchLocationPlaces("")
                    },
                    onSelectAddress = { addr ->
                        addressViewModel.select(addr)
                        showAddressBottomSheet = false
                    },
                    onAddNewAddress = {
                        showAddressBottomSheet = false
                        addressViewModel.startAddAddressFlow()
                        navigate(Screen.AddressSelection(directAddFlow = true))
                    },
                    onUseCurrentLocation = {
                        addressViewModel.useCurrentGpsLocationPinpoint {
                            showAddressBottomSheet = false
                        }
                    },
                    onEditAddress = { addr ->
                        showAddressBottomSheet = false
                        addressViewModel.startEditAddressFlow(addr)
                        navigate(Screen.AddressSelection(directAddFlow = true))
                    },
                    onDeleteAddress = { id -> addressViewModel.deleteAddress(id) },
                    onSetDefaultAddress = { id -> addressViewModel.setDefaultAddress(id) }
                )
            }
        }
    }
}
}

@Composable
private fun CommerceBottomNavItem(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = Color(0xFF0E675E)
    val inactiveColor = Color(0xFF64748B)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) activeColor else inactiveColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeColor else inactiveColor
        )
    }
}
