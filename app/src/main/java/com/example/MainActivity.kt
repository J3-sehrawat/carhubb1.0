package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.signOutUser
import com.example.data.AccountStatus
import com.example.data.AutoTradeRepository
import com.example.data.FirestoreProvider
import com.example.data.Invoice
import com.example.data.NOTIFICATION_TYPE_NEW_PRODUCT
import com.example.data.NOTIFICATION_TYPE_ORDER_STATUS
import com.example.data.NetworkConnectivityMonitor
import com.example.data.Order
import com.example.data.OrderStatus
import com.example.data.PaymentStatus
import com.example.data.PdfInvoiceGenerator
import com.example.data.Product
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.notifications.AutoTradeNotificationHelper
import com.example.notifications.FcmManager
import com.example.ui.AutoTradeViewModel
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.OfflineSyncStatusBanner
import com.example.ui.components.launchWhatsAppOrder
import com.example.ui.screens.AdminActivityLogScreen
import com.example.ui.screens.AdminBillingTab
import com.example.ui.screens.AdminInventoryTab
import com.example.ui.screens.AdminInvoiceFormScreen
import com.example.ui.screens.AdminLowStockDashboardScreen
import com.example.ui.screens.AdminOrdersTab
import com.example.ui.screens.AdminProductFormScreen
import com.example.ui.screens.AdminSettingsTab
import com.example.ui.screens.AdminShopkeepersTab
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CatalogPdfActionDialog
import com.example.ui.screens.InvoiceDetailDialog
import com.example.ui.screens.PendingOrBlockedScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.RecordPaymentDialog
import com.example.ui.screens.ShopkeeperBillsTab
import com.example.ui.screens.ShopkeeperCartTab
import com.example.ui.screens.ShopkeeperCatalogTab
import com.example.ui.screens.ShopkeeperNotificationsDialog
import com.example.ui.screens.ShopkeeperOrdersTab
import com.example.ui.screens.ShopkeeperProfileTab
import kotlinx.coroutines.launch
import com.example.ui.theme.AutoTradeTheme
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate900
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth

class MainActivity : ComponentActivity() {
    private val pendingNotificationTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AutoTradeNotificationHelper.ensureChannels(this)
        handleNotificationIntent(intent)
        setContent {
            AutoTradeTheme {
                AutoTradeAppRoot(
                    pendingNotificationTab = pendingNotificationTab.value,
                    onConsumeNotificationTab = { pendingNotificationTab.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val target = intent?.getStringExtra(AutoTradeNotificationHelper.EXTRA_TARGET_TAB)
        if (!target.isNullOrBlank()) {
            pendingNotificationTab.value = target
        }
    }
}

@Composable
private fun AutoTradeAppRoot(
    pendingNotificationTab: String?,
    onConsumeNotificationTab: () -> Unit
) {
    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = { currentUser = Firebase.auth.currentUser })
    } else {
        // Key composition & ViewModel by uid so switching accounts disposes old Firestore listeners cleanly
        key(user.uid) {
            AuthenticatedAutoTradeShell(
                currentUserId = user.uid,
                userEmail = user.email.orEmpty(),
                userDisplayName = user.displayName.orEmpty(),
                pendingNotificationTab = pendingNotificationTab,
                onConsumeNotificationTab = onConsumeNotificationTab
            )
        }
    }
}

private data class NavDestination(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val badgeCount: Int = 0
)

@Composable
private fun AuthenticatedAutoTradeShell(
    currentUserId: String,
    userEmail: String,
    userDisplayName: String,
    pendingNotificationTab: String?,
    onConsumeNotificationTab: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AutoTradeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val db = FirestoreProvider.getDatabase(app)
                val networkMonitor = NetworkConnectivityMonitor(app)
                AutoTradeViewModel(
                    repository = AutoTradeRepository(db = db, networkMonitor = networkMonitor),
                    networkMonitor = networkMonitor,
                    currentUserId = currentUserId,
                    userEmail = userEmail,
                    userDisplayName = userDisplayName
                )
            }
        }
    )

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.toastMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isActionBusy.collectAsStateWithLifecycle()

    val performSignOut: () -> Unit = {
        FcmManager.clearTopicsOnSignOut(currentUserId)
        signOutUser(credentialManager = credentialManager, scope = scope)
    }

    when (val state = profileState) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate50),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is UiState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate50)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Filled.Storefront,
                    title = "Unable to load account",
                    subtitle = state.message
                )
            }
        }

        is UiState.Success -> {
            val profile = state.data
            if (profile == null) {
                ProfileSetupScreen(
                    email = userEmail,
                    defaultDisplayName = userDisplayName,
                    isBusy = isBusy,
                    onSubmit = { name, shopName, phone, city ->
                        viewModel.registerProfile(name, shopName, phone, city)
                    },
                    onSignOut = performSignOut
                )
            } else if (!profile.roleIsAdmin() && profile.accountStatus() != AccountStatus.APPROVED) {
                PendingOrBlockedScreen(
                    profile = profile,
                    onSignOut = performSignOut
                )
            } else {
                ActiveWorkspaceShell(
                    profile = profile,
                    viewModel = viewModel,
                    snackbarHostState = snackbarHostState,
                    pendingNotificationTab = pendingNotificationTab,
                    onConsumeNotificationTab = onConsumeNotificationTab,
                    onSignOut = performSignOut
                )
            }
        }
    }
}

@Composable
private fun ActiveWorkspaceShell(
    profile: UserProfile,
    viewModel: AutoTradeViewModel,
    snackbarHostState: SnackbarHostState,
    pendingNotificationTab: String?,
    onConsumeNotificationTab: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val productsState by viewModel.productsState.collectAsStateWithLifecycle()
    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val inventoryHealth by viewModel.inventoryHealth.collectAsStateWithLifecycle()
    val newArrivalProducts by viewModel.newArrivalProducts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val ordersState by viewModel.ordersState.collectAsStateWithLifecycle()
    val invoicesState by viewModel.invoicesState.collectAsStateWithLifecycle()
    val usersState by viewModel.usersState.collectAsStateWithLifecycle()
    val activityLogsState by viewModel.activityLogsState.collectAsStateWithLifecycle()
    val storeSettings by viewModel.storeSettings.collectAsStateWithLifecycle()
    val businessProfile by viewModel.businessProfile.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val notifications by viewModel.notifications.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val adminViewingAsBuyer by viewModel.adminViewingAsBuyer.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val isBusy by viewModel.isActionBusy.collectAsStateWithLifecycle()

    val isAdminMode = profile.roleIsAdmin() && !adminViewingAsBuyer

    var adminTabIndex by remember { mutableIntStateOf(0) }
    var buyerTabIndex by remember { mutableIntStateOf(0) }

    // Android 13+ POST_NOTIFICATIONS runtime permission + FCM registration & live alert delivery
    var hasNotificationPermission by remember {
        mutableStateOf(AutoTradeNotificationHelper.hasNotificationPermission(context))
    }
    var hasPromptedNotificationPermission by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || AutoTradeNotificationHelper.hasNotificationPermission(context)
        if (granted) {
            viewModel.emitToast("Instant order & new product alerts enabled!")
        }
    }
    val requestNotificationPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Sync FCM device token & topic subscriptions (`new_products`, `user_<uid>`)
    LaunchedEffect(
        profile.id,
        profile.status,
        profile.role,
        profile.notifyOrderUpdates,
        profile.notifyNewProducts
    ) {
        viewModel.syncFcmRegistration(profile)
        if (!profile.roleIsAdmin() && !hasNotificationPermission && !hasPromptedNotificationPermission) {
            hasPromptedNotificationPermission = true
            requestNotificationPermission()
        }
    }

    // Handle notification tap deep-link routing into Orders or Catalog tab
    LaunchedEffect(pendingNotificationTab, isAdminMode) {
        when (pendingNotificationTab) {
            AutoTradeNotificationHelper.TARGET_TAB_ORDERS -> {
                if (isAdminMode) adminTabIndex = 1 else buyerTabIndex = 2
                onConsumeNotificationTab()
            }
            AutoTradeNotificationHelper.TARGET_TAB_CATALOG -> {
                if (isAdminMode) adminTabIndex = 0 else buyerTabIndex = 0
                onConsumeNotificationTab()
            }
        }
    }

    // Live instant system notification delivery for newly arriving Firestore notification events
    val sessionStartMs = remember(profile.id) { System.currentTimeMillis() }
    var lastSeenNotifMs by remember(profile.id) {
        mutableLongStateOf(AutoTradeNotificationHelper.getLastSeenTimestampMs(context, profile.id))
    }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(notifications, hasNotificationPermission, profile.notifyOrderUpdates, profile.notifyNewProducts) {
        if (profile.roleIsAdmin()) return@LaunchedEffect
        val userKey = profile.id.ifBlank { profile.userId }
        notifications.forEach { event ->
            val createdMs = event.createdAt?.toDate()?.time ?: return@forEach
            // Only raise system notification for fresh events arriving during this session
            if (createdMs < sessionStartMs - 10_000L) return@forEach
            if (AutoTradeNotificationHelper.hasDispatchedLocally(context, userKey, event.id)) return@forEach

            val enabledForType = when (event.type) {
                NOTIFICATION_TYPE_ORDER_STATUS -> profile.notifyOrderUpdates
                NOTIFICATION_TYPE_NEW_PRODUCT -> profile.notifyNewProducts
                else -> true
            }
            AutoTradeNotificationHelper.markDispatchedLocally(context, userKey, event.id)
            if (!enabledForType) return@forEach

            if (event.type == NOTIFICATION_TYPE_ORDER_STATUS) {
                AutoTradeNotificationHelper.showOrderStatusNotification(
                    context = context,
                    title = event.title,
                    body = event.body,
                    orderId = event.orderId.ifBlank { event.id }
                )
            } else {
                AutoTradeNotificationHelper.showNewProductNotification(
                    context = context,
                    title = event.title,
                    body = event.body,
                    productId = event.productId.ifBlank { event.id }
                )
            }
        }
    }

    val unreadNotificationsCount = remember(notifications, lastSeenNotifMs) {
        notifications.count { (it.createdAt?.toDate()?.time ?: 0L) > lastSeenNotifMs }
    }

    // Camera-first Add / Edit Product full-screen modal state
    var showProductForm by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    // Create Invoice (Manual or Order-to-Bill) full-screen modal state
    var showInvoiceForm by remember { mutableStateOf(false) }
    var invoiceSourceOrder by remember { mutableStateOf<Order?>(null) }

    // View Invoice modal (e.g., from Orders "View Bill" or after generating a new bill)
    var viewingInvoice by remember { mutableStateOf<Invoice?>(null) }
    var paymentTargetFromModal by remember { mutableStateOf<Invoice?>(null) }

    // Catalog PDF Export / Print Dialog
    var showCatalogPdfDialog by remember { mutableStateOf(false) }

    // Admin Activity Audit Log full-screen modal state
    var showActivityLog by remember { mutableStateOf(false) }

    // Dedicated Admin Low Stock Alerts Dashboard full-screen modal state
    var showLowStockDashboard by remember { mutableStateOf(false) }

    // Live primary brand color preview while Admin explores swatches in Settings
    var previewPrimaryColorHex by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(storeSettings.primaryColorHex) {
        previewPrimaryColorHex = null
    }
    LaunchedEffect(adminTabIndex, isAdminMode) {
        if (!isAdminMode || adminTabIndex != 4) {
            previewPrimaryColorHex = null
        }
    }

    val activeBrandHex = previewPrimaryColorHex ?: storeSettings.primaryColorHex

    val allProducts = (productsState as? UiState.Success)?.data.orEmpty()
    val allOrders = (ordersState as? UiState.Success)?.data.orEmpty()
    val allInvoices = (invoicesState as? UiState.Success)?.data.orEmpty()
    val allUsers = (usersState as? UiState.Success)?.data.orEmpty()

    AutoTradeTheme(primaryColorHex = activeBrandHex) {
    if (showProductForm && isAdminMode) {
        AdminProductFormScreen(
            editingProduct = editingProduct,
            categories = categories,
            lowStockThreshold = inventoryHealth.threshold,
            isBusy = isBusy,
            onAddCategory = { name, onCreated -> viewModel.addCategory(name, onCreated) },
            onSave = { id, name, category, carModel, price, moq, inStock, stockQuantity, newArrival, desc, hsnCode, gstPercent, images ->
                viewModel.saveProduct(
                    editingProductId = id,
                    name = name,
                    category = category,
                    carModel = carModel,
                    price = price,
                    moq = moq,
                    inStock = inStock,
                    stockQuantity = stockQuantity,
                    newArrival = newArrival,
                    description = desc,
                    hsnCode = hsnCode,
                    gstPercent = gstPercent,
                    imageUrls = images,
                    onSuccess = {
                        showProductForm = false
                        editingProduct = null
                    }
                )
            },
            onClose = {
                showProductForm = false
                editingProduct = null
            },
            onNotify = { viewModel.emitToast(it) }
        )
        return@AutoTradeTheme
    }

    if (showInvoiceForm && isAdminMode) {
        AdminInvoiceFormScreen(
            sourceOrder = invoiceSourceOrder,
            products = allProducts,
            shopkeepers = allUsers,
            businessProfile = businessProfile,
            isBusy = isBusy,
            onCreateInvoice = { orderId, billType, shopkeeperId, shopName, phone, city, shopGstin, items, discountAmount, taxType, transportCharges, initialPaidAmount, initialPaymentMode, initialPaymentDate, initialPaymentNote ->
                viewModel.createInvoice(
                    orderId = orderId,
                    billType = billType,
                    shopkeeperId = shopkeeperId,
                    shopName = shopName,
                    phone = phone,
                    city = city,
                    shopGstin = shopGstin,
                    items = items,
                    discountAmount = discountAmount,
                    taxType = taxType,
                    transportCharges = transportCharges,
                    initialPaidAmount = initialPaidAmount,
                    initialPaymentMode = initialPaymentMode,
                    initialPaymentDate = initialPaymentDate,
                    initialPaymentNote = initialPaymentNote,
                    fallbackProfile = profile,
                    onCreated = { created ->
                        showInvoiceForm = false
                        invoiceSourceOrder = null
                        adminTabIndex = 2 // Switch to Billing tab
                        viewingInvoice = created // Offer instant PDF / Print / WhatsApp
                    }
                )
            },
            onClose = {
                showInvoiceForm = false
                invoiceSourceOrder = null
            }
        )
        return@AutoTradeTheme
    }

    if (showActivityLog && isAdminMode) {
        AdminActivityLogScreen(
            logsState = activityLogsState,
            onClose = { showActivityLog = false }
        )
        return@AutoTradeTheme
    }

    if (showLowStockDashboard && isAdminMode) {
        AdminLowStockDashboardScreen(
            inventoryHealth = inventoryHealth,
            isBusy = isBusy,
            onQuickRestock = viewModel::updateProductStockQuantity,
            onBulkRestock = viewModel::bulkRestockProducts,
            onEditProduct = { product ->
                showLowStockDashboard = false
                editingProduct = product
                showProductForm = true
            },
            onOpenThresholdSettings = {
                showLowStockDashboard = false
                adminTabIndex = 4
            },
            onClose = { showLowStockDashboard = false }
        )
        return@AutoTradeTheme
    }

    val pendingOrdersCount = remember(allOrders) { allOrders.count { it.orderStatus() == OrderStatus.PENDING } }
    val unpaidBillsCount = remember(allInvoices) { allInvoices.count { it.paymentStatusEnum() != PaymentStatus.PAID } }
    val pendingBuyersCount = remember(allUsers) { allUsers.count { !it.roleIsAdmin() && it.accountStatus() == AccountStatus.PENDING } }

    val navItems = if (isAdminMode) {
        listOf(
            NavDestination("Inventory", Icons.Filled.Inventory2, Icons.Outlined.Inventory2, inventoryHealth.lowStockCount),
            NavDestination("Orders", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, pendingOrdersCount),
            NavDestination("Billing", Icons.Filled.Receipt, Icons.Outlined.Receipt, unpaidBillsCount),
            NavDestination("Buyers", Icons.Filled.Group, Icons.Outlined.Group, pendingBuyersCount),
            NavDestination("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
        )
    } else {
        listOf(
            NavDestination("Catalog", Icons.Filled.Storefront, Icons.Outlined.Storefront),
            NavDestination("Cart", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart, cartItems.size),
            NavDestination("Orders", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
            NavDestination("My Bills", Icons.Filled.Receipt, Icons.Outlined.Receipt, unpaidBillsCount),
            NavDestination("Profile", Icons.Filled.Person, Icons.Outlined.Person)
        )
    }

    val selectedIndex = if (isAdminMode) adminTabIndex else buyerTabIndex
    val onSelectTab: (Int) -> Unit = { idx ->
        if (isAdminMode) adminTabIndex = idx else buyerTabIndex = idx
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            floatingActionButton = {
                if (isAdminMode && adminTabIndex == 0) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            editingProduct = null
                            showProductForm = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Add Product", style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.testTag("add_product_fab")
                    )
                } else if (isAdminMode && adminTabIndex == 2) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            invoiceSourceOrder = null
                            showInvoiceForm = true
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Create Bill", style = MaterialTheme.typography.labelLarge) },
                        modifier = Modifier.testTag("create_bill_fab")
                    )
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = PureWhite,
                        tonalElevation = 4.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        navItems.forEachIndexed { idx, item ->
                            val selected = selectedIndex == idx
                            NavigationBarItem(
                                selected = selected,
                                onClick = { onSelectTab(idx) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (item.badgeCount > 0) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ) {
                                                    Text(item.badgeCount.coerceAtMost(99).toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                },
                                label = { Text(item.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = Slate900,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = Slate600,
                                    unselectedTextColor = Slate600
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(containerColor = PureWhite) {
                        navItems.forEachIndexed { idx, item ->
                            val selected = selectedIndex == idx
                            NavigationRailItem(
                                selected = selected,
                                onClick = { onSelectTab(idx) },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (item.badgeCount > 0) {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ) {
                                                    Text(item.badgeCount.coerceAtMost(99).toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    }
                                },
                                label = { Text(item.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (isAdminMode) {
                        when (adminTabIndex) {
                            0 -> AdminInventoryTab(
                                productsState = productsState,
                                filteredProducts = filteredProducts,
                                inventoryHealth = inventoryHealth,
                                categories = categories,
                                orders = allOrders,
                                users = allUsers,
                                storeSettings = storeSettings,
                                isBusy = isBusy,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                onSearchChange = viewModel::updateSearchQuery,
                                onSelectCategory = viewModel::selectCategory,
                                onToggleStock = viewModel::toggleProductStock,
                                onUpdateStockQuantity = viewModel::updateProductStockQuantity,
                                onToggleNewArrival = viewModel::toggleProductNewArrival,
                                onSaveNewArrivalsConfig = { showSection, title, limit ->
                                    viewModel.saveNewArrivalsConfig(profile, showSection, title, limit)
                                },
                                onClearAllNewArrivals = viewModel::clearAllNewArrivals,
                                onMarkLatestAsNewArrivals = viewModel::markLatestAsNewArrivals,
                                onEditProduct = { product ->
                                    editingProduct = product
                                    showProductForm = true
                                },
                                onDeleteProduct = viewModel::deleteProduct,
                                onOpenLowStockDashboard = { showLowStockDashboard = true },
                                onOpenBusinessSettings = { adminTabIndex = 4 },
                                onPrintCatalogPdf = { showCatalogPdfDialog = true },
                                onSwitchToBuyerPreview = { viewModel.toggleAdminBuyerPreview(true) }
                            )
                            1 -> AdminOrdersTab(
                                ordersState = ordersState,
                                invoices = allInvoices,
                                businessName = businessProfile.firmName.ifBlank { storeSettings.businessName },
                                onUpdateOrderStatus = viewModel::updateOrderStatus,
                                onGenerateBill = { order ->
                                    invoiceSourceOrder = order
                                    showInvoiceForm = true
                                },
                                onViewBill = { inv -> viewingInvoice = inv },
                                onNotify = viewModel::emitToast
                            )
                            2 -> AdminBillingTab(
                                invoicesState = invoicesState,
                                businessName = businessProfile.firmName.ifBlank { storeSettings.businessName },
                                pendingInvoiceIds = syncStatus.pendingInvoiceIds,
                                isBusy = isBusy,
                                onCreateManualBill = {
                                    invoiceSourceOrder = null
                                    showInvoiceForm = true
                                },
                                onOpenBusinessSettings = { adminTabIndex = 4 },
                                onPrintCatalogPdf = { showCatalogPdfDialog = true },
                                onRecordPayment = { inv, amount, mode, date, note, onDone ->
                                    viewModel.recordInvoicePayment(inv, amount, mode, date, note, onDone)
                                },
                                onDeleteInvoice = viewModel::deleteInvoice,
                                onNotify = viewModel::emitToast
                            )
                            3 -> AdminShopkeepersTab(
                                usersState = usersState,
                                invoices = allInvoices,
                                onUpdateStatus = viewModel::updateShopkeeperStatus
                            )
                            else -> AdminSettingsTab(
                                profile = profile,
                                storeSettings = storeSettings,
                                businessProfile = businessProfile,
                                activityLogsState = activityLogsState,
                                lowStockCount = inventoryHealth.lowStockCount,
                                syncStatus = syncStatus,
                                cachedProductsCount = allProducts.size,
                                pendingInvoicesCount = unpaidBillsCount,
                                isBusy = isBusy,
                                onSyncNow = viewModel::syncPendingChangesNow,
                                onOpenActivityLog = { showActivityLog = true },
                                onOpenLowStockDashboard = { showLowStockDashboard = true },
                                onPreviewBrandColor = { hex -> previewPrimaryColorHex = hex },
                                onSaveBrandTheme = { hex ->
                                    viewModel.saveBrandTheme(profile, hex)
                                },
                                onSaveSettings = viewModel::saveStoreSettings,
                                onSaveBusinessProfile = viewModel::saveBusinessProfile,
                                onPrintCatalogPdf = { showCatalogPdfDialog = true },
                                onSeedCategories = viewModel::seedDefaultCategories,
                                onSwitchToBuyerPreview = { viewModel.toggleAdminBuyerPreview(true) },
                                onSignOut = onSignOut
                            )
                        }
                    } else {
                        when (buyerTabIndex) {
                            0 -> ShopkeeperCatalogTab(
                                storeSettings = storeSettings,
                                productsState = productsState,
                                filteredProducts = filteredProducts,
                                newArrivalProducts = newArrivalProducts,
                                categories = categories,
                                cartItems = cartItems,
                                searchQuery = searchQuery,
                                selectedCategory = selectedCategory,
                                unreadNotificationsCount = unreadNotificationsCount,
                                isAdminPreview = profile.roleIsAdmin(),
                                onSearchChange = viewModel::updateSearchQuery,
                                onSelectCategory = viewModel::selectCategory,
                                onAddToCart = viewModel::addToCart,
                                onIncrementCart = viewModel::incrementCartItem,
                                onDecrementCart = viewModel::decrementCartItem,
                                onOpenNotifications = {
                                    val now = System.currentTimeMillis()
                                    lastSeenNotifMs = now
                                    AutoTradeNotificationHelper.setLastSeenTimestampMs(
                                        context,
                                        profile.id.ifBlank { profile.userId },
                                        now
                                    )
                                    showNotificationsDialog = true
                                },
                                onPrintCatalogPdf = { showCatalogPdfDialog = true },
                                onBackToAdmin = { viewModel.toggleAdminBuyerPreview(false) }
                            )
                            1 -> ShopkeeperCartTab(
                                profile = profile,
                                storeSettings = storeSettings,
                                cartItems = cartItems,
                                isBusy = isBusy,
                                onIncrement = viewModel::incrementCartItem,
                                onDecrement = viewModel::decrementCartItem,
                                onRemove = viewModel::removeCartItem,
                                onPlaceOrder = {
                                    viewModel.placeOrder(profile) {
                                        buyerTabIndex = 2 // Jump directly to My Orders
                                    }
                                }
                            )
                            2 -> ShopkeeperOrdersTab(
                                ordersState = ordersState,
                                storeSettings = storeSettings,
                                invoices = allInvoices,
                                onNotify = viewModel::emitToast
                            )
                            3 -> ShopkeeperBillsTab(
                                invoicesState = invoicesState,
                                businessName = storeSettings.businessName.ifBlank { "AutoTrade Wholesale" },
                                pendingInvoiceIds = syncStatus.pendingInvoiceIds,
                                onNotify = viewModel::emitToast
                            )
                            else -> ShopkeeperProfileTab(
                                profile = profile,
                                isAdminPreview = profile.roleIsAdmin(),
                                syncStatus = syncStatus,
                                cachedProductsCount = allProducts.size,
                                pendingInvoicesCount = unpaidBillsCount,
                                isBusy = isBusy,
                                hasNotificationPermission = hasNotificationPermission,
                                onSyncNow = viewModel::syncPendingChangesNow,
                                onRequestNotificationPermission = requestNotificationPermission,
                                onUpdateNotificationPreferences = { orderAlerts, productAlerts ->
                                    viewModel.updateNotificationPreferences(profile, orderAlerts, productAlerts)
                                },
                                onSaveProfile = viewModel::updateOwnProfile,
                                onBackToAdmin = { viewModel.toggleAdminBuyerPreview(false) },
                                onSignOut = onSignOut
                            )
                        }
                    }
                    }
                    OfflineSyncStatusBanner(
                        syncStatus = syncStatus,
                        onSyncNow = viewModel::syncPendingChangesNow
                    )
                }
            }
        }
    }

    viewingInvoice?.let { inv ->
        val liveInv = allInvoices.firstOrNull { it.id == inv.id } ?: inv
        InvoiceDetailDialog(
            invoice = liveInv,
            isAdmin = isAdminMode,
            onRecordPayment = { paymentTargetFromModal = liveInv },
            onOpenPdf = {
                scope.launch {
                    PdfInvoiceGenerator.generateInvoicePdf(context, liveInv)
                        .onSuccess { file -> PdfInvoiceGenerator.openPdfFile(context, file) }
                        .onFailure { viewModel.emitToast("PDF error: ${it.localizedMessage ?: "failed"}") }
                }
            },
            onPrintPdf = {
                scope.launch {
                    PdfInvoiceGenerator.generateInvoicePdf(context, liveInv)
                        .onSuccess { file -> PdfInvoiceGenerator.printPdfFile(context, file, "Invoice_${liveInv.invoiceNumber}") }
                        .onFailure { viewModel.emitToast("Print error: ${it.localizedMessage ?: "failed"}") }
                }
            },
            onSharePdf = {
                scope.launch {
                    PdfInvoiceGenerator.generateInvoicePdf(context, liveInv)
                        .onSuccess { file -> PdfInvoiceGenerator.sharePdfFile(context, file, "Invoice ${liveInv.invoiceNumber}") }
                        .onFailure { viewModel.emitToast("Share PDF failed") }
                }
            },
            onWhatsApp = {
                val msg = AutoTradeRepository.buildWhatsAppInvoiceSummary(liveInv)
                launchWhatsAppOrder(context, liveInv.phone, msg)
            },
            onDelete = if (isAdminMode) {
                {
                    viewModel.deleteInvoice(liveInv)
                    viewingInvoice = null
                }
            } else null,
            onDismiss = { viewingInvoice = null }
        )
    }

    paymentTargetFromModal?.let { inv ->
        RecordPaymentDialog(
            invoice = inv,
            isBusy = isBusy,
            onConfirm = { amount, mode, date, note ->
                viewModel.recordInvoicePayment(inv, amount, mode, date, note) {
                    paymentTargetFromModal = null
                }
            },
            onDismiss = { paymentTargetFromModal = null }
        )
    }

    if (showCatalogPdfDialog) {
        CatalogPdfActionDialog(
            businessName = businessProfile.firmName.ifBlank { storeSettings.businessName.ifBlank { "AutoTrade Wholesale" } },
            phone = businessProfile.phone.ifBlank { storeSettings.whatsappPhone },
            city = storeSettings.city,
            products = allProducts,
            onDismiss = { showCatalogPdfDialog = false }
        )
    }

    if (showNotificationsDialog) {
        ShopkeeperNotificationsDialog(
            notifications = notifications,
            hasSystemPermission = hasNotificationPermission,
            isFcmRegistered = profile.fcmToken.isNotBlank(),
            onRequestPermission = requestNotificationPermission,
            onOpenOrders = { buyerTabIndex = 2 },
            onOpenCatalog = { buyerTabIndex = 0 },
            onDismiss = { showNotificationsDialog = false }
        )
    }
    }
}
