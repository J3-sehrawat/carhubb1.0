package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccountStatus
import com.example.data.ActivityLogAction
import com.example.data.ActivityLogCategory
import com.example.data.AdminActivityLog
import com.example.data.AutoTradeRepository
import com.example.data.BillType
import com.example.data.BusinessProfile
import com.example.data.CartItem
import com.example.data.Category
import com.example.data.DEFAULT_LOW_STOCK_THRESHOLD
import com.example.data.InventoryHealthSummary
import com.example.data.Invoice
import com.example.data.InvoiceItem
import com.example.data.LOW_STOCK_FILTER
import com.example.data.NEW_ARRIVALS_FILTER
import com.example.data.NetworkConnectivityMonitor
import com.example.data.NotificationEvent
import com.example.data.Order
import com.example.data.OrderStatus
import com.example.data.PaymentMode
import com.example.data.Product
import com.example.data.StoreSettings
import com.example.data.SyncStatusSummary
import com.example.data.TaxType
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.extractFriendlyFirestoreMessage
import com.example.data.monitorProductInventory
import com.example.notifications.FcmManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class AutoTradeViewModel(
    private val repository: AutoTradeRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
    val currentUserId: String,
    val userEmail: String,
    val userDisplayName: String
) : ViewModel() {

    private val _toastMessage = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    private val _isActionBusy = MutableStateFlow(false)
    val isActionBusy: StateFlow<Boolean> = _isActionBusy.asStateFlow()

    private val _isSyncingNow = MutableStateFlow(false)

    // Live offline cache & cloud sync state across catalog, orders, and invoices
    val syncStatus: StateFlow<SyncStatusSummary> = combine(
        networkMonitor.isOnline,
        repository.catalogSyncMeta,
        repository.invoiceSyncMeta,
        repository.orderSyncMeta,
        _isSyncingNow
    ) { isOnline, catalogMeta, invoiceMeta, orderMeta, manualSync ->
        SyncStatusSummary(
            isOnline = isOnline,
            isCatalogFromCache = catalogMeta.isFromCache,
            isInvoicesFromCache = invoiceMeta.isFromCache,
            isOrdersFromCache = orderMeta.isFromCache,
            pendingProductIds = catalogMeta.pendingDocumentIds,
            pendingInvoiceIds = invoiceMeta.pendingDocumentIds,
            pendingOrderIds = orderMeta.pendingDocumentIds,
            isSyncingNow = manualSync || (isOnline && (
                catalogMeta.pendingDocumentIds.isNotEmpty() ||
                    invoiceMeta.pendingDocumentIds.isNotEmpty() ||
                    orderMeta.pendingDocumentIds.isNotEmpty()
                ))
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = SyncStatusSummary(isOnline = networkMonitor.isOnlineNow())
    )

    init {
        viewModelScope.launch {
            var wasOffline = !networkMonitor.isOnlineNow()
            networkMonitor.isOnline.collect { online ->
                if (!online) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    val hadPending = syncStatus.value.pendingWriteCount > 0
                    _isSyncingNow.value = true
                    repository.syncPendingWritesNow()
                    _isSyncingNow.value = false
                    emitToast(
                        if (hadPending) "Back online — queued offline changes synced!"
                        else "Back online — catalog & bills up to date"
                    )
                }
            }
        }
    }

    fun syncPendingChangesNow() {
        viewModelScope.launch {
            if (!networkMonitor.isOnlineNow()) {
                emitToast("Offline — changes are saved locally and will auto-sync when connected.")
                return@launch
            }
            _isSyncingNow.value = true
            repository.syncPendingWritesNow()
                .onSuccess { emitToast("Cloud sync complete — all data up to date") }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Sync deferred — will retry automatically")) }
            _isSyncingNow.value = false
        }
    }

    private fun withOfflineHint(baseMessage: String): String =
        if (repository.isCurrentlyOffline()) "$baseMessage (Saved offline — auto-syncs online)" else baseMessage

    override fun onCleared() {
        networkMonitor.unregister()
        super.onCleared()
    }

    private val _adminViewingAsBuyer = MutableStateFlow(false)
    val adminViewingAsBuyer: StateFlow<Boolean> = _adminViewingAsBuyer.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val _cartQuantities = MutableStateFlow<Map<String, Long>>(emptyMap())

    // 1. Live User Profile
    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error -> emit(unwrapFlowError(error, "Failed to load user profile")) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val canAccessCatalogFlow = profileState.map { state ->
        val profile = (state as? UiState.Success)?.data
        profile != null && (profile.roleIsAdmin() || profile.accountStatus() == AccountStatus.APPROVED)
    }.distinctUntilChanged()

    private val isAdminFlow = profileState.map { state ->
        val profile = (state as? UiState.Success)?.data
        profile != null && profile.roleIsAdmin()
    }.distinctUntilChanged()

    // 2. Real-time Products Catalog
    val productsState: StateFlow<UiState<List<Product>>> = canAccessCatalogFlow
        .flatMapLatest { allowed ->
            if (!allowed) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeProducts(currentUserId)
                    .map<List<Product>, UiState<List<Product>>> { UiState.Success(it) }
                    .catch { error -> emit(unwrapFlowError(error, "Failed to load products")) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    // 3. Real-time Categories
    val categories: StateFlow<List<Category>> = canAccessCatalogFlow
        .flatMapLatest { allowed ->
            if (!allowed) {
                flowOf(emptyList())
            } else {
                repository.observeCategories(currentUserId)
                    .catch { error ->
                        val firestoreError = findFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing categories", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    // 4. Business Billing & Inventory Settings Profile (Admin only)
    val businessProfile: StateFlow<BusinessProfile> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (!isAdmin) {
                flowOf(BusinessProfile())
            } else {
                repository.observeBusinessProfile(currentUserId)
                    .catch { error ->
                        val firestoreError = findFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(BusinessProfile())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = BusinessProfile()
        )

    // 4b. Real-time Inventory Level Monitor (evaluates all products against business lowStockThreshold)
    val inventoryHealth: StateFlow<InventoryHealthSummary> = combine(
        productsState,
        businessProfile
    ) { state, biz ->
        val all = (state as? UiState.Success)?.data.orEmpty()
        monitorProductInventory(all, biz.lowStockThreshold.coerceAtLeast(1L))
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = InventoryHealthSummary()
    )

    // 5. Filtered Products (Global search by product name, category, car model, HSN & description + category/low-stock chips)
    val filteredProducts: StateFlow<List<Product>> = combine(
        productsState,
        _searchQuery,
        _selectedCategory,
        businessProfile
    ) { state, query, category, biz ->
        val all = (state as? UiState.Success)?.data.orEmpty()
        val threshold = biz.lowStockThreshold.coerceAtLeast(1L)
        val q = query.trim().lowercase()
        val tokens = if (q.isEmpty()) emptyList() else q.split(Regex("\\s+")).filter { it.isNotBlank() }

        val matched = all.filter { product ->
            val matchesCategory = when {
                category == "All" -> true
                category == NEW_ARRIVALS_FILTER -> product.isFeaturedNewArrival()
                category == LOW_STOCK_FILTER -> product.isLowStock(threshold)
                else -> product.category.trim().equals(category.trim(), ignoreCase = true)
            }
            if (!matchesCategory) return@filter false
            if (tokens.isEmpty()) return@filter true

            val nameLower = product.name.lowercase()
            val catLower = product.category.lowercase()
            val modelLower = product.carModel.lowercase()
            val hsnLower = product.hsnCode.lowercase()
            val descLower = product.description.lowercase()

            tokens.all { token ->
                nameLower.contains(token) ||
                    catLower.contains(token) ||
                    modelLower.contains(token) ||
                    hsnLower.contains(token) ||
                    descLower.contains(token)
            }
        }

        when {
            q.isNotEmpty() -> {
                // Rank direct name/category matches first for fast global lookup
                matched.sortedBy { product ->
                    val nameLower = product.name.lowercase()
                    val catLower = product.category.lowercase()
                    when {
                        nameLower.startsWith(q) -> 0
                        catLower.startsWith(q) -> 1
                        nameLower.contains(q) -> 2
                        catLower.contains(q) -> 3
                        else -> 4
                    }
                }
            }
            category == LOW_STOCK_FILTER -> {
                // Surface the most depleted items at the very top for immediate restocking
                matched.sortedBy { it.trackedStockCount() ?: 0L }
            }
            else -> matched
        }
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList()
    )

    // 6. Store Settings (WhatsApp + Buyer New Arrivals Control)
    val storeSettings: StateFlow<StoreSettings> = canAccessCatalogFlow
        .flatMapLatest { allowed ->
            if (!allowed) {
                flowOf(StoreSettings())
            } else {
                repository.observeStoreSettings(currentUserId)
                    .catch { error ->
                        val firestoreError = findFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        emit(StoreSettings())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = StoreSettings()
        )

    // 6b. Live Newly Listed Products for Buyer Home Showcase (controlled by Admin)
    val newArrivalProducts: StateFlow<List<Product>> = combine(productsState, storeSettings) { state, settings ->
        val all = (state as? UiState.Success)?.data.orEmpty()
        val limit = settings.newArrivalsLimit.coerceIn(1L, 50L).toInt()
        all.filter { it.isFeaturedNewArrival() }.take(limit)
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList()
    )

    // 7. Real-time Orders
    val ordersState: StateFlow<UiState<List<Order>>> = combine(isAdminFlow, canAccessCatalogFlow) { isAdmin, allowed ->
        Pair(isAdmin, allowed)
    }.flatMapLatest { (isAdmin, allowed) ->
        when {
            !allowed -> flowOf(UiState.Success(emptyList()))
            isAdmin -> repository.observeAllOrders(currentUserId)
                .map<List<Order>, UiState<List<Order>>> { UiState.Success(it) }
                .catch { error -> emit(unwrapFlowError(error, "Failed to load orders")) }
            else -> repository.observeShopkeeperOrders(currentUserId)
                .map<List<Order>, UiState<List<Order>>> { UiState.Success(it) }
                .catch { error -> emit(unwrapFlowError(error, "Failed to load your orders")) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading
    )

    // 8. Real-time Invoices (Admin sees all; Shopkeeper sees own via whereEqualTo("shopkeeperId", uid))
    val invoicesState: StateFlow<UiState<List<Invoice>>> = combine(isAdminFlow, canAccessCatalogFlow) { isAdmin, allowed ->
        Pair(isAdmin, allowed)
    }.flatMapLatest { (isAdmin, allowed) ->
        when {
            !allowed -> flowOf(UiState.Success(emptyList()))
            isAdmin -> repository.observeAllInvoices(currentUserId)
                .map<List<Invoice>, UiState<List<Invoice>>> { UiState.Success(it) }
                .catch { error -> emit(unwrapFlowError(error, "Failed to load invoices")) }
            else -> repository.observeShopkeeperInvoices(currentUserId)
                .map<List<Invoice>, UiState<List<Invoice>>> { UiState.Success(it) }
                .catch { error -> emit(unwrapFlowError(error, "Failed to load your bills")) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = UiState.Loading
    )

    // 9. Real-time Shopkeepers list (STRICTLY Admin only)
    val usersState: StateFlow<UiState<List<UserProfile>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (!isAdmin) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeAllUsers(currentUserId)
                    .map<List<UserProfile>, UiState<List<UserProfile>>> { UiState.Success(it) }
                    .catch { error -> emit(unwrapFlowError(error, "Failed to load shopkeepers")) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    // 10. Live Cart Items
    val cartItems: StateFlow<List<CartItem>> = combine(productsState, _cartQuantities) { state, qtyMap ->
        val productsById = (state as? UiState.Success)?.data.orEmpty().associateBy { it.id }
        qtyMap.mapNotNull { (productId, qty) ->
            val product = productsById[productId] ?: return@mapNotNull null
            if (!product.inStock) return@mapNotNull null
            CartItem(product = product, qty = maxOf(qty, product.moq))
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = emptyList()
    )

    // 11. Real-time Shopkeeper Notifications (Order status updates + New wholesale products)
    val notifications: StateFlow<List<NotificationEvent>> = canAccessCatalogFlow
        .flatMapLatest { allowed ->
            if (!allowed) {
                flowOf(emptyList())
            } else {
                repository.observeShopkeeperNotifications(currentUserId)
                    .catch { error ->
                        val firestoreError = findFirestoreException(error)
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing notifications", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    // 12. Real-time Admin Activity Audit Logs (STRICTLY Admin only)
    val activityLogsState: StateFlow<UiState<List<AdminActivityLog>>> = isAdminFlow
        .flatMapLatest { isAdmin ->
            if (!isAdmin) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeActivityLogs(currentUserId)
                    .map<List<AdminActivityLog>, UiState<List<AdminActivityLog>>> { UiState.Success(it) }
                    .catch { error -> emit(unwrapFlowError(error, "Failed to load activity logs")) }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    fun updateSearchQuery(query: String) {
        val wasBlank = _searchQuery.value.isBlank()
        _searchQuery.value = query
        // Automatically search globally across all categories when user begins typing a search query
        if (wasBlank && query.isNotBlank() && _selectedCategory.value != "All") {
            _selectedCategory.value = "All"
        }
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun clearSearchAndFilters() {
        _searchQuery.value = ""
        _selectedCategory.value = "All"
    }

    fun toggleAdminBuyerPreview(asBuyer: Boolean) {
        _adminViewingAsBuyer.value = asBuyer
    }

    // --- CART OPERATIONS ---

    fun addToCart(product: Product) {
        val availableStock = product.trackedStockCount()
        if (!product.inStock || (availableStock != null && availableStock <= 0L)) {
            emitToast("${product.name} is currently out of stock")
            return
        }
        val current = _cartQuantities.value.toMutableMap()
        val existingQty = current[product.id] ?: 0L
        val step = product.moq.coerceAtLeast(1L)
        val desiredQty = if (existingQty == 0L) step else existingQty + step
        if (availableStock != null && existingQty >= availableStock) {
            emitToast("Only $availableStock pcs available in stock for ${product.name}")
            return
        }
        val finalQty = if (availableStock != null) desiredQty.coerceAtMost(availableStock) else desiredQty
        current[product.id] = finalQty
        _cartQuantities.value = current
        emitToast("Added ${product.name} (Qty: $finalQty)")
    }

    fun incrementCartItem(product: Product) {
        val current = _cartQuantities.value.toMutableMap()
        val existingQty = current[product.id] ?: product.moq.coerceAtLeast(1L)
        val availableStock = product.trackedStockCount()
        if (availableStock != null && existingQty >= availableStock) {
            emitToast("Only $availableStock pcs available in stock")
            return
        }
        current[product.id] = existingQty + 1L
        _cartQuantities.value = current
    }

    fun decrementCartItem(product: Product) {
        val current = _cartQuantities.value.toMutableMap()
        val existingQty = current[product.id] ?: return
        val minQty = product.moq.coerceAtLeast(1L)
        if (existingQty <= minQty) {
            current.remove(product.id)
            emitToast("Removed ${product.name} from cart")
        } else {
            current[product.id] = existingQty - 1L
        }
        _cartQuantities.value = current
    }

    fun removeCartItem(productId: String) {
        val current = _cartQuantities.value.toMutableMap()
        current.remove(productId)
        _cartQuantities.value = current
    }

    fun clearCart() {
        _cartQuantities.value = emptyMap()
    }

    // --- MUTATIONS ---

    fun registerProfile(name: String, shopName: String, phone: String, city: String) {
        runBusyAction {
            repository.createUserProfile(name, shopName, phone, city)
                .onSuccess { profile ->
                    if (profile.roleIsAdmin()) {
                        emitToast("Wholesaler Admin Hub activated!")
                    } else {
                        emitToast("Shop registered! Waiting for wholesaler approval.")
                    }
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Registration failed")) }
        }
    }

    fun updateOwnProfile(name: String, shopName: String, phone: String, city: String, gstin: String = "") {
        runBusyAction {
            repository.updateOwnProfile(name, shopName, phone, city, gstin)
                .onSuccess { emitToast("Profile updated successfully") }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Could not update profile")) }
        }
    }

    fun syncFcmRegistration(profile: UserProfile) {
        viewModelScope.launch {
            FcmManager.syncRegistrationAndTopics(repository, profile)
        }
    }

    fun updateNotificationPreferences(
        profile: UserProfile,
        notifyOrderUpdates: Boolean,
        notifyNewProducts: Boolean
    ) {
        viewModelScope.launch {
            repository.updateNotificationPreferences(notifyOrderUpdates, notifyNewProducts)
                .onSuccess {
                    FcmManager.syncRegistrationAndTopics(
                        repository = repository,
                        profile = profile.copy(
                            notifyOrderUpdates = notifyOrderUpdates,
                            notifyNewProducts = notifyNewProducts
                        )
                    )
                    emitToast("Notification preferences updated")
                }
                .onFailure {
                    emitToast(extractFriendlyFirestoreMessage(it, "Could not update notification settings"))
                }
        }
    }

    fun saveStoreSettings(businessName: String, whatsappPhone: String, city: String) {
        val current = storeSettings.value
        runBusyAction {
            repository.saveStoreSettings(
                businessName = businessName,
                whatsappPhone = whatsappPhone,
                city = city,
                showNewArrivals = current.showNewArrivals,
                newArrivalsTitle = current.newArrivalsTitle,
                newArrivalsLimit = current.newArrivalsLimit,
                primaryColorHex = current.primaryColorHex
            )
                .onSuccess {
                    logAdminAction(
                        action = ActivityLogAction.SETTINGS_UPDATED,
                        category = ActivityLogCategory.SETTINGS,
                        targetId = "store",
                        targetName = businessName.trim().ifBlank { "Store Settings" },
                        summary = "Updated WhatsApp order receiving & warehouse city ($city)",
                        previousValue = current.whatsappPhone.ifBlank { "Not set" },
                        newValue = whatsappPhone.trim()
                    )
                    emitToast("Wholesaler WhatsApp & store settings saved")
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to save store settings")) }
        }
    }

    fun saveBrandTheme(fallbackProfile: UserProfile, primaryColorHex: String) {
        val current = storeSettings.value
        val previousHex = current.primaryColorHex
        val cleanHex = primaryColorHex.trim().uppercase()
        runBusyAction {
            repository.saveBrandTheme(
                currentSettings = current,
                fallbackProfile = fallbackProfile,
                primaryColorHex = primaryColorHex
            )
                .onSuccess {
                    logAdminAction(
                        action = ActivityLogAction.THEME_UPDATED,
                        category = ActivityLogCategory.SETTINGS,
                        targetId = "store",
                        targetName = "Catalog Brand Theme",
                        summary = "Changed primary catalog brand color from $previousHex to $cleanHex",
                        previousValue = previousHex,
                        newValue = cleanHex
                    )
                    emitToast("Catalog brand theme updated live!")
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to update brand theme")) }
        }
    }

    fun saveNewArrivalsConfig(
        fallbackProfile: UserProfile,
        showNewArrivals: Boolean,
        newArrivalsTitle: String,
        newArrivalsLimit: Long
    ) {
        val current = storeSettings.value
        runBusyAction {
            repository.saveNewArrivalsSettings(
                currentSettings = current,
                fallbackProfile = fallbackProfile,
                showNewArrivals = showNewArrivals,
                newArrivalsTitle = newArrivalsTitle,
                newArrivalsLimit = newArrivalsLimit
            )
                .onSuccess {
                    logAdminAction(
                        action = ActivityLogAction.SETTINGS_UPDATED,
                        category = ActivityLogCategory.SETTINGS,
                        targetId = "store",
                        targetName = "New Arrivals Showcase",
                        summary = if (showNewArrivals) "Updated New Arrivals banner (\"${newArrivalsTitle.trim()}\", limit $newArrivalsLimit)"
                        else "Hidden New Arrivals showcase from Buyer Home",
                        previousValue = if (current.showNewArrivals) "Visible (${current.newArrivalsLimit})" else "Hidden",
                        newValue = if (showNewArrivals) "Visible ($newArrivalsLimit)" else "Hidden"
                    )
                    emitToast(
                        if (showNewArrivals) "Buyer New Arrivals section updated!"
                        else "Buyer New Arrivals section hidden"
                    )
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to update New Arrivals settings")) }
        }
    }

    fun saveBusinessProfile(
        firmName: String,
        address: String,
        phone: String,
        gstin: String,
        upiId: String,
        invoicePrefix: String,
        nextInvoiceNumber: Long,
        lowStockThreshold: Long = DEFAULT_LOW_STOCK_THRESHOLD
    ) {
        val previousThreshold = businessProfile.value.lowStockThreshold.coerceAtLeast(1L)
        val safeThreshold = lowStockThreshold.coerceIn(1L, 100_000L)
        runBusyAction {
            repository.saveBusinessProfile(
                firmName = firmName,
                address = address,
                phone = phone,
                gstin = gstin,
                upiId = upiId,
                invoicePrefix = invoicePrefix,
                nextInvoiceNumber = nextInvoiceNumber,
                lowStockThreshold = safeThreshold
            )
                .onSuccess {
                    logAdminAction(
                        action = ActivityLogAction.SETTINGS_UPDATED,
                        category = ActivityLogCategory.SETTINGS,
                        targetId = "businessProfile",
                        targetName = firmName.trim().ifBlank { "Business Profile" },
                        summary = "Saved business billing profile & low-stock alert threshold (< $safeThreshold pcs)",
                        previousValue = "Threshold < $previousThreshold pcs",
                        newValue = "Threshold < $safeThreshold pcs • ${invoicePrefix.ifBlank { "INV" }}-$nextInvoiceNumber"
                    )
                    emitToast("Business settings saved (Low stock alert < $safeThreshold pcs)")
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to save business settings")) }
        }
    }

    fun seedDefaultCategories() {
        runBusyAction {
            repository.seedDefaultCategoriesIfEmpty()
                .onSuccess { count ->
                    if (count > 0) emitToast("Seeded $count default accessory categories")
                    else emitToast("Categories are already populated")
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to seed categories")) }
        }
    }

    fun addCategory(name: String, onCreated: (String) -> Unit = {}) {
        runBusyAction {
            repository.addCategory(name)
                .onSuccess {
                    emitToast("Added category \"${name.trim()}\"")
                    onCreated(name.trim())
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to add category")) }
        }
    }

    fun saveProduct(
        editingProductId: String?,
        name: String,
        category: String,
        carModel: String,
        price: Double,
        moq: Long,
        inStock: Boolean,
        stockQuantity: Long,
        newArrival: Boolean,
        description: String,
        hsnCode: String,
        gstPercent: Long,
        imageUrls: List<String>,
        onSuccess: () -> Unit
    ) {
        val threshold = businessProfile.value.lowStockThreshold.coerceAtLeast(1L)
        val existingProduct = if (!editingProductId.isNullOrBlank()) {
            (productsState.value as? UiState.Success)?.data?.firstOrNull { it.id == editingProductId }
        } else {
            null
        }
        runBusyAction {
            if (editingProductId.isNullOrBlank()) {
                repository.addProduct(
                    name = name,
                    category = category,
                    carModel = carModel,
                    price = price,
                    moq = moq,
                    inStock = inStock,
                    stockQuantity = stockQuantity,
                    newArrival = newArrival,
                    description = description,
                    hsnCode = hsnCode,
                    gstPercent = gstPercent,
                    imageUrls = imageUrls
                ).onSuccess { newProductId ->
                    val priceLabel = AutoTradeRepository.formatInr(price)
                    logAdminAction(
                        action = ActivityLogAction.PRODUCT_ADDED,
                        category = ActivityLogCategory.PRODUCT,
                        targetId = newProductId,
                        targetName = name.trim(),
                        summary = "Added ${name.trim()} ($category • $carModel) at $priceLabel, MOQ $moq, Stock $stockQuantity pcs",
                        previousValue = "",
                        newValue = "$priceLabel • $stockQuantity pcs"
                    )
                    val lowStockSuffix = if (stockQuantity < threshold) " • Low Stock Alert (< $threshold pcs)" else ""
                    emitToast("Product published live & buyers notified!$lowStockSuffix")
                    onSuccess()
                }.onFailure {
                    emitToast(extractFriendlyFirestoreMessage(it, "Failed to save product"))
                }
            } else {
                repository.updateProduct(
                    productId = editingProductId,
                    name = name,
                    category = category,
                    carModel = carModel,
                    price = price,
                    moq = moq,
                    inStock = inStock,
                    stockQuantity = stockQuantity,
                    newArrival = newArrival,
                    description = description,
                    hsnCode = hsnCode,
                    gstPercent = gstPercent,
                    imageUrls = imageUrls
                ).onSuccess {
                    val cleanName = name.trim()
                    val newPriceLabel = AutoTradeRepository.formatInr(price)
                    val priceChanged = existingProduct != null && abs(existingProduct.price - price) >= 0.005
                    if (priceChanged && existingProduct != null) {
                        val oldPriceLabel = AutoTradeRepository.formatInr(existingProduct.price)
                        logAdminAction(
                            action = ActivityLogAction.PRICE_CHANGED,
                            category = ActivityLogCategory.PRICING,
                            targetId = editingProductId,
                            targetName = cleanName,
                            summary = "Changed wholesale price of $cleanName ($category) from $oldPriceLabel to $newPriceLabel",
                            previousValue = oldPriceLabel,
                            newValue = newPriceLabel
                        )
                    } else {
                        val prevSummary = existingProduct?.let { prev ->
                            val qtyLabel = prev.trackedStockCount()?.let { "$it pcs" }
                                ?: if (prev.inStock) "In Stock" else "Out of Stock"
                            "${AutoTradeRepository.formatInr(prev.price)} • $qtyLabel"
                        }.orEmpty()
                        logAdminAction(
                            action = ActivityLogAction.PRODUCT_UPDATED,
                            category = ActivityLogCategory.PRODUCT,
                            targetId = editingProductId,
                            targetName = cleanName,
                            summary = "Updated product details for $cleanName ($category • $carModel)",
                            previousValue = prevSummary,
                            newValue = "$newPriceLabel • $stockQuantity pcs"
                        )
                    }
                    val lowStockSuffix = if (stockQuantity < threshold) " • Low Stock Alert (< $threshold pcs)" else ""
                    emitToast("Product updated!$lowStockSuffix")
                    onSuccess()
                }.onFailure {
                    emitToast(extractFriendlyFirestoreMessage(it, "Failed to save product"))
                }
            }
        }
    }

    fun toggleProductStock(product: Product) {
        viewModelScope.launch {
            val next = !product.inStock
            repository.toggleProductStock(product.id, next)
                .onSuccess {
                    logAdminAction(
                        action = ActivityLogAction.STOCK_UPDATED,
                        category = ActivityLogCategory.PRODUCT,
                        targetId = product.id,
                        targetName = product.name,
                        summary = if (next) "Marked ${product.name} as In Stock" else "Marked ${product.name} as Out of Stock",
                        previousValue = if (product.inStock) "In Stock" else "Out of Stock",
                        newValue = if (next) "In Stock" else "Out of Stock"
                    )
                    emitToast(if (next) "${product.name} marked In Stock" else "${product.name} marked Out of Stock")
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Could not update stock status")) }
        }
    }

    fun updateProductStockQuantity(product: Product, newQuantity: Long) {
        val safeQty = newQuantity.coerceIn(0L, 1_000_000L)
        val threshold = businessProfile.value.lowStockThreshold.coerceAtLeast(1L)
        val previousQtyLabel = product.trackedStockCount()?.let { "$it pcs" }
            ?: if (product.inStock) "In Stock" else "Out of Stock"
        viewModelScope.launch {
            repository.updateProductStockQuantity(product.id, safeQty)
                .onSuccess {
                    logAdminAction(
                        action = ActivityLogAction.STOCK_UPDATED,
                        category = ActivityLogCategory.PRODUCT,
                        targetId = product.id,
                        targetName = product.name,
                        summary = "Adjusted inventory stock for ${product.name} from $previousQtyLabel to $safeQty pcs",
                        previousValue = previousQtyLabel,
                        newValue = "$safeQty pcs"
                    )
                    val msg = when {
                        safeQty == 0L -> "${product.name}: 0 pcs (Low Stock / Out of Stock)"
                        safeQty < threshold -> "${product.name}: $safeQty pcs — Low Stock Alert (< $threshold)"
                        else -> "${product.name}: Stock updated to $safeQty pcs"
                    }
                    emitToast(msg)
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Could not update stock quantity")) }
        }
    }

    fun bulkRestockProducts(
        products: List<Product>,
        targetQuantities: Map<String, Long>,
        modeDescription: String
    ) {
        if (products.isEmpty() || targetQuantities.isEmpty()) return
        val sanitizedUpdates = targetQuantities.mapValues { (_, qty) -> qty.coerceIn(0L, 1_000_000L) }
        val totalAdded = products.sumOf { prod ->
            val before = prod.trackedStockCount() ?: 0L
            val after = sanitizedUpdates[prod.id] ?: before
            (after - before).coerceAtLeast(0L)
        }
        runBusyAction {
            repository.bulkRestockProducts(sanitizedUpdates)
                .onSuccess { count ->
                    logAdminAction(
                        action = ActivityLogAction.STOCK_UPDATED,
                        category = ActivityLogCategory.PRODUCT,
                        targetId = "bulk_restock",
                        targetName = "Bulk Restock ($count items)",
                        summary = "Batch restocked $count low-stock ${if (count == 1) "item" else "items"} ($modeDescription, +$totalAdded pcs added)",
                        previousValue = "$count low-stock items",
                        newValue = "+$totalAdded pcs restocked"
                    )
                    emitToast(withOfflineHint("Restocked $count ${if (count == 1) "item" else "items"} (+$totalAdded pcs added)"))
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Bulk restock failed")) }
        }
    }

    fun toggleProductNewArrival(product: Product) {
        viewModelScope.launch {
            val next = !product.isFeaturedNewArrival()
            repository.toggleProductNewArrival(product.id, next)
                .onSuccess {
                    emitToast(
                        if (next) "${product.name} added to Buyer New Arrivals"
                        else "${product.name} removed from Buyer New Arrivals"
                    )
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Could not update New Arrival status")) }
        }
    }

    fun clearAllNewArrivals() {
        val all = (productsState.value as? UiState.Success)?.data.orEmpty()
        val toClear = all.filter { it.isFeaturedNewArrival() }.map { it.id }
        if (toClear.isEmpty()) {
            emitToast("No products are currently tagged as New Arrival.")
            return
        }
        runBusyAction {
            repository.bulkSetNewArrivals(productIdsToMark = emptyList(), productIdsToClear = toClear)
                .onSuccess { emitToast("Cleared New Arrival tag from ${toClear.size} products") }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to clear New Arrivals")) }
        }
    }

    fun markLatestAsNewArrivals(count: Int = 5) {
        val all = (productsState.value as? UiState.Success)?.data.orEmpty()
        if (all.isEmpty()) {
            emitToast("Add products first.")
            return
        }
        val latestIds = all.take(count).map { it.id }.toSet()
        val toMark = all.filter { it.id in latestIds && !it.isFeaturedNewArrival() }.map { it.id }
        val toClear = all.filter { it.id !in latestIds && it.isFeaturedNewArrival() }.map { it.id }
        if (toMark.isEmpty() && toClear.isEmpty()) {
            emitToast("Latest $count products are already featured in New Arrivals.")
            return
        }
        runBusyAction {
            repository.bulkSetNewArrivals(productIdsToMark = toMark, productIdsToClear = toClear)
                .onSuccess { emitToast("Featured latest ${latestIds.size} products in Buyer New Arrivals") }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to update New Arrivals")) }
        }
    }

    fun deleteProduct(product: Product) {
        runBusyAction {
            repository.deleteProduct(product.id)
                .onSuccess {
                    logAdminAction(
                        action = ActivityLogAction.PRODUCT_DELETED,
                        category = ActivityLogCategory.PRODUCT,
                        targetId = product.id,
                        targetName = product.name,
                        summary = "Removed ${product.name} (${product.category}) from wholesale catalog",
                        previousValue = AutoTradeRepository.formatInr(product.price),
                        newValue = "Deleted"
                    )
                    emitToast("Deleted ${product.name}")
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Could not delete product")) }
        }
    }

    fun updateShopkeeperStatus(user: UserProfile, status: AccountStatus) {
        val previousStatus = user.accountStatus()
        viewModelScope.launch {
            repository.updateShopkeeperStatus(user.id, status)
                .onSuccess {
                    val auditAction = if (status == AccountStatus.APPROVED) {
                        ActivityLogAction.SHOPKEEPER_APPROVED
                    } else {
                        ActivityLogAction.SHOPKEEPER_BLOCKED
                    }
                    val shopLabel = user.shopName.ifBlank { user.name }.ifBlank { "Shopkeeper" }
                    logAdminAction(
                        action = auditAction,
                        category = ActivityLogCategory.SHOPKEEPER,
                        targetId = user.id,
                        targetName = shopLabel,
                        summary = "${if (status == AccountStatus.APPROVED) "Approved" else "Blocked"} shopkeeper $shopLabel (${user.city.ifBlank { user.phone }})",
                        previousValue = previousStatus.label,
                        newValue = status.label
                    )
                    emitToast("${user.shopName} marked ${status.label}")
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Could not update shopkeeper status")) }
        }
    }

    fun placeOrder(profile: UserProfile, onPlaced: (String) -> Unit) {
        val items = cartItems.value
        if (items.isEmpty()) {
            emitToast("Add items to your cart first.")
            return
        }
        runBusyAction {
            repository.placeOrder(profile, items)
                .onSuccess { orderId ->
                    clearCart()
                    emitToast(withOfflineHint("Order #${orderId.takeLast(6).uppercase()} placed!"))
                    onPlaced(orderId)
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Could not place order")) }
        }
    }

    fun updateOrderStatus(order: Order, status: OrderStatus) {
        val previousStatus = order.orderStatus()
        viewModelScope.launch {
            repository.updateOrderStatus(order, status)
                .onSuccess { deductedProductsCount ->
                    val recipientToken = (usersState.value as? UiState.Success)?.data
                        ?.firstOrNull { it.id == order.shopkeeperId || it.userId == order.shopkeeperId }
                        ?.fcmToken
                        .orEmpty()
                    repository.publishOrderStatusNotification(
                        order = order,
                        newStatus = status,
                        targetFcmToken = recipientToken
                    )
                    val shortId = order.id.takeLast(6).uppercase()
                    val stockNote = if (deductedProductsCount > 0) {
                        val totalPcs = order.items.filter { it.productId.isNotBlank() }.sumOf { it.qty }
                        " • Stock deducted (-$totalPcs pcs)"
                    } else {
                        ""
                    }
                    logAdminAction(
                        action = ActivityLogAction.ORDER_STATUS_UPDATED,
                        category = ActivityLogCategory.ORDER,
                        targetId = order.id,
                        targetName = "${order.shopName} (#$shortId)",
                        summary = "Order #$shortId for ${order.shopName} moved from ${previousStatus.label} to ${status.label}$stockNote",
                        previousValue = previousStatus.label,
                        newValue = "${status.label}$stockNote"
                    )
                    emitToast(withOfflineHint("Order #$shortId -> ${status.label}$stockNote"))
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Could not update order status")) }
        }
    }

    fun createInvoice(
        orderId: String,
        billType: BillType,
        shopkeeperId: String,
        shopName: String,
        phone: String,
        city: String,
        shopGstin: String,
        items: List<InvoiceItem>,
        discountAmount: Double,
        taxType: TaxType,
        transportCharges: Double,
        initialPaidAmount: Double,
        initialPaymentMode: PaymentMode,
        initialPaymentDate: String,
        initialPaymentNote: String,
        fallbackProfile: UserProfile,
        onCreated: (Invoice) -> Unit
    ) {
        runBusyAction {
            repository.createInvoiceWithTransaction(
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
                fallbackProfile = fallbackProfile
            ).onSuccess { invoice ->
                val billedCatalogQty = items.filter { it.productId.isNotBlank() }.sumOf { it.qty }
                val stockNote = if (invoice.stockDeducted && billedCatalogQty > 0L) {
                    " • Stock updated (-$billedCatalogQty pcs)"
                } else {
                    ""
                }
                logAdminAction(
                    action = ActivityLogAction.INVOICE_CREATED,
                    category = ActivityLogCategory.BILLING,
                    targetId = invoice.id,
                    targetName = "${invoice.invoiceNumber} • ${invoice.shopName}",
                    summary = "Generated ${invoice.billType} ${invoice.invoiceNumber} for ${invoice.shopName} (${AutoTradeRepository.formatInr(invoice.grandTotal)})$stockNote",
                    previousValue = "",
                    newValue = "${AutoTradeRepository.formatInr(invoice.grandTotal)} • ${invoice.paymentStatus}"
                )
                emitToast(withOfflineHint("Bill ${invoice.invoiceNumber} created!$stockNote"))
                onCreated(invoice)
            }.onFailure {
                emitToast(extractFriendlyFirestoreMessage(it, "Failed to create invoice"))
            }
        }
    }

    fun recordInvoicePayment(
        invoice: Invoice,
        amount: Double,
        mode: PaymentMode,
        date: String,
        note: String,
        onRecorded: () -> Unit = {}
    ) {
        runBusyAction {
            repository.recordInvoicePayment(invoice, amount, mode, date, note)
                .onSuccess {
                    val amountLabel = AutoTradeRepository.formatInr(amount)
                    logAdminAction(
                        action = ActivityLogAction.PAYMENT_RECORDED,
                        category = ActivityLogCategory.BILLING,
                        targetId = invoice.id,
                        targetName = "${invoice.invoiceNumber} • ${invoice.shopName}",
                        summary = "Recorded $amountLabel (${mode.label}) payment against ${invoice.invoiceNumber}",
                        previousValue = "Due ${AutoTradeRepository.formatInr(invoice.balanceDue)}",
                        newValue = "+$amountLabel (${mode.label})"
                    )
                    emitToast(withOfflineHint("Payment of $amountLabel recorded!"))
                    onRecorded()
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to record payment")) }
        }
    }

    fun deleteInvoice(invoice: Invoice) {
        runBusyAction {
            repository.deleteInvoice(invoice.id)
                .onSuccess {
                    logAdminAction(
                        action = ActivityLogAction.INVOICE_DELETED,
                        category = ActivityLogCategory.BILLING,
                        targetId = invoice.id,
                        targetName = "${invoice.invoiceNumber} • ${invoice.shopName}",
                        summary = "Deleted ${invoice.billType} ${invoice.invoiceNumber} (${AutoTradeRepository.formatInr(invoice.grandTotal)})",
                        previousValue = AutoTradeRepository.formatInr(invoice.grandTotal),
                        newValue = "Deleted"
                    )
                    emitToast("Deleted ${invoice.invoiceNumber}")
                }
                .onFailure { emitToast(extractFriendlyFirestoreMessage(it, "Failed to delete invoice")) }
        }
    }

    private fun logAdminAction(
        action: ActivityLogAction,
        category: ActivityLogCategory,
        targetId: String,
        targetName: String,
        summary: String,
        previousValue: String = "",
        newValue: String = ""
    ) {
        val actorName = (profileState.value as? UiState.Success)?.data?.name
            ?.takeIf { it.isNotBlank() }
            ?: userDisplayName.ifBlank { userEmail.substringBefore("@") }
        viewModelScope.launch {
            repository.recordActivityLog(
                action = action,
                category = category,
                actorName = actorName,
                targetId = targetId,
                targetName = targetName,
                summary = summary,
                previousValue = previousValue,
                newValue = newValue
            )
        }
    }

    private fun runBusyAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isActionBusy.value = true
            try {
                block()
            } finally {
                _isActionBusy.value = false
            }
        }
    }

    fun emitToast(message: String) {
        _toastMessage.tryEmit(message)
    }

    private fun findFirestoreException(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()

    private fun unwrapFlowError(error: Throwable, fallback: String): UiState.Error {
        val firestoreError = findFirestoreException(error)
        if (firestoreError == null && error is CancellationException) throw error
        val root = firestoreError ?: error
        Log.w(TAG, fallback, root)
        return UiState.Error(extractFriendlyFirestoreMessage(root, fallback))
    }

    private companion object {
        const val TAG = "AutoTradeVM"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
