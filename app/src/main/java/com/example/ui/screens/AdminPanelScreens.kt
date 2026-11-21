package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AccountStatus
import com.example.data.AdminActivityLog
import com.example.data.AutoTradeRepository
import com.example.data.BusinessProfile
import com.example.data.Category
import com.example.data.DEFAULT_LOW_STOCK_THRESHOLD
import com.example.data.ExcelReportExporter
import com.example.data.InventoryHealthSummary
import com.example.data.Invoice
import com.example.data.LOW_STOCK_FILTER
import com.example.data.NEW_ARRIVALS_FILTER
import com.example.data.Order
import com.example.data.OrderStatus
import com.example.data.Product
import com.example.data.StoreSettings
import com.example.data.SyncStatusSummary
import com.example.data.UiState
import com.example.data.UserProfile
import java.util.Locale
import com.example.ui.components.AutoScrollingProductPhotoSlider
import com.example.ui.components.AutomotiveCategoryFilterBar
import com.example.ui.components.AutomotiveHeader
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.GlobalCatalogSearchBar
import com.example.ui.components.GlobalSearchStatusBanner
import com.example.ui.components.OfflinePersistenceStatusCard
import com.example.ui.components.PinnedNewArrivalsChip
import com.example.ui.components.PriceText
import com.example.ui.components.ProductImageGalleryDialog
import com.example.ui.components.ProductPhoto
import com.example.ui.components.StatusPill
import com.example.ui.components.accountStatusColors
import com.example.ui.components.buildCatalogCategoryList
import com.example.ui.components.formatTimestamp
import com.example.ui.components.launchWhatsAppOrder
import com.example.ui.components.orderStatusColors
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.Cobalt600
import com.example.ui.theme.Danger100
import com.example.ui.theme.Danger600
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald600
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.WhatsAppGreen

@Composable
fun AdminInventoryTab(
    productsState: UiState<List<Product>>,
    filteredProducts: List<Product>,
    inventoryHealth: InventoryHealthSummary,
    categories: List<Category>,
    orders: List<Order>,
    users: List<UserProfile>,
    storeSettings: StoreSettings,
    isBusy: Boolean,
    searchQuery: String,
    selectedCategory: String,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleStock: (Product) -> Unit,
    onUpdateStockQuantity: (Product, Long) -> Unit,
    onToggleNewArrival: (Product) -> Unit,
    onSaveNewArrivalsConfig: (showSection: Boolean, title: String, limit: Long) -> Unit,
    onClearAllNewArrivals: () -> Unit,
    onMarkLatestAsNewArrivals: (Int) -> Unit,
    onEditProduct: (Product) -> Unit,
    onDeleteProduct: (Product) -> Unit,
    onOpenLowStockDashboard: () -> Unit,
    onOpenBusinessSettings: () -> Unit,
    onPrintCatalogPdf: () -> Unit,
    onSwitchToBuyerPreview: () -> Unit
) {
    var galleryState by remember { mutableStateOf<Pair<Product, Int>?>(null) }
    var deleteConfirmProduct by remember { mutableStateOf<Product?>(null) }
    var stockUpdateTarget by remember { mutableStateOf<Product?>(null) }
    var showNewArrivalsControlDialog by remember { mutableStateOf(false) }

    val allProductsList = (productsState as? UiState.Success)?.data.orEmpty()
    val totalProducts = allProductsList.size
    val newArrivalsCount = allProductsList.count { it.isFeaturedNewArrival() }
    val pendingOrdersCount = orders.count { it.orderStatus() == OrderStatus.PENDING }
    val pendingBuyersCount = users.count { !it.roleIsAdmin() && it.accountStatus() == AccountStatus.PENDING }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = "Wholesaler Hub",
            subtitle = "Live Inventory & Instant Stock Sync",
            trailingAction = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onPrintCatalogPdf)
                            .testTag("admin_catalog_pdf_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Print,
                                contentDescription = "Print Catalog PDF",
                                tint = PureWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "PDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite
                            )
                        }
                    }
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onSwitchToBuyerPreview)
                            .testTag("admin_preview_buyer_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = "Preview Buyer Catalog",
                                tint = PureWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Buyer",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite
                            )
                        }
                    }
                }
            },
            bottomContent = {
                // High-contrast KPI strip inside dark header
                val catalogCountText = if (totalProducts == 1) "1 Item" else "$totalProducts Items"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KpiChip(
                        label = "Catalog",
                        value = catalogCountText,
                        valueColor = PureWhite,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_kpi_catalog"),
                        onClick = {
                            onSearchChange("")
                            onSelectCategory("All")
                        }
                    )
                    KpiChip(
                        label = "Low Stock",
                        value = "${inventoryHealth.lowStockCount}",
                        valueColor = if (inventoryHealth.lowStockCount > 0) Amber600 else Emerald600,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("admin_kpi_low_stock"),
                        onClick = onOpenLowStockDashboard
                    )
                    KpiChip("Orders", "$pendingOrdersCount", if (pendingOrdersCount > 0) Amber600 else PureWhite, Modifier.weight(1f))
                    KpiChip("Buyers", "$pendingBuyersCount", if (pendingBuyersCount > 0) MaterialTheme.colorScheme.primary else PureWhite, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                GlobalCatalogSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    placeholder = "Search by product name or category…",
                    testTagName = "admin_search_input"
                )
            }
        )

        // Horizontal Automotive Accessory Category Filter Bar (with pinned New Arrivals & Low Stock quick-filters)
        AutomotiveCategoryFilterBar(
            categories = categories,
            products = allProductsList,
            selectedCategory = selectedCategory,
            filteredCount = filteredProducts.size,
            onSelectCategory = onSelectCategory,
            showPinnedNewArrivals = true,
            newArrivalsCount = newArrivalsCount,
            showLowStockChip = true,
            lowStockCount = inventoryHealth.lowStockCount,
            isSearchActive = searchQuery.isNotBlank(),
            testTagPrefix = "admin"
        )

        val matchingCategoryNames = remember(categories, allProductsList, searchQuery) {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) emptyList()
            else buildCatalogCategoryList(categories, allProductsList).filter { it.lowercase().contains(q) }
        }
        GlobalSearchStatusBanner(
            searchQuery = searchQuery,
            selectedCategory = selectedCategory,
            resultCount = filteredProducts.size,
            totalCount = totalProducts,
            matchingCategories = matchingCategoryNames,
            onSelectCategory = onSelectCategory,
            onClearSearch = { onSearchChange("") }
        )

        when (productsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(
                    icon = Icons.Filled.Inventory2,
                    title = "Could not load catalog",
                    subtitle = productsState.message
                )
            }
            is UiState.Success -> {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Live Inventory Level Monitor & Low Stock Alert Card
                    if (totalProducts > 0) {
                        item(key = "admin_low_stock_monitor_card") {
                            AdminLowStockMonitorBanner(
                                inventoryHealth = inventoryHealth,
                                isFilteringLowStock = selectedCategory == LOW_STOCK_FILTER,
                                onToggleLowStockFilter = {
                                    onSelectCategory(
                                        if (selectedCategory == LOW_STOCK_FILTER) "All" else LOW_STOCK_FILTER
                                    )
                                },
                                onOpenDashboard = onOpenLowStockDashboard,
                                onQuickRestockProduct = { prod -> stockUpdateTarget = prod },
                                onOpenThresholdSettings = onOpenBusinessSettings
                            )
                        }
                    }

                    // Admin Control Card for Buyer Panel's New Arrivals Section
                    item(key = "buyer_new_arrivals_control_card") {
                        AdminNewArrivalsBannerCard(
                            storeSettings = storeSettings,
                            featuredCount = newArrivalsCount,
                            onQuickToggleSection = { enabled ->
                                onSaveNewArrivalsConfig(
                                    enabled,
                                    storeSettings.newArrivalsTitle,
                                    storeSettings.newArrivalsLimit
                                )
                            },
                            onOpenControlModal = { showNewArrivalsControlDialog = true }
                        )
                    }

                    if (filteredProducts.isEmpty()) {
                        item(key = "empty_products_state") {
                            EmptyStateCard(
                                icon = Icons.Filled.Inventory2,
                                title = if (totalProducts == 0) "Your wholesale catalog is empty"
                                else if (selectedCategory == LOW_STOCK_FILTER) "No low-stock items!"
                                else if (searchQuery.isNotBlank()) "No results for \"${searchQuery.trim()}\""
                                else "No matching products",
                                subtitle = if (totalProducts == 0)
                                    "Tap the + Add Product button below to snap product photos and publish live."
                                else if (selectedCategory == LOW_STOCK_FILTER)
                                    "All tracked items currently have at least ${inventoryHealth.threshold} pcs in stock."
                                else "Try searching by another product name or category, or clear your filters.",
                                action = if (totalProducts > 0 && (searchQuery.isNotBlank() || selectedCategory != "All")) {
                                    {
                                        OutlinedButton(
                                            onClick = {
                                                onSearchChange("")
                                                onSelectCategory("All")
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Clear Search & Filters")
                                        }
                                    }
                                } else null
                            )
                        }
                    } else {
                        items(filteredProducts, key = { it.id }) { product ->
                            AdminProductCard(
                                product = product,
                                lowStockThreshold = inventoryHealth.threshold,
                                onPhotoClick = { page ->
                                    if (product.imageUrls.isNotEmpty()) galleryState = product to page
                                },
                                onToggleStock = { onToggleStock(product) },
                                onQuickStockClick = { stockUpdateTarget = product },
                                onToggleNewArrival = { onToggleNewArrival(product) },
                                onEdit = { onEditProduct(product) },
                                onDelete = { deleteConfirmProduct = product }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewArrivalsControlDialog) {
        AdminNewArrivalsControlDialog(
            storeSettings = storeSettings,
            featuredCount = newArrivalsCount,
            isBusy = isBusy,
            onSave = { showSection, title, limit ->
                onSaveNewArrivalsConfig(showSection, title, limit)
                showNewArrivalsControlDialog = false
            },
            onMarkLatestFive = {
                onMarkLatestAsNewArrivals(5)
                showNewArrivalsControlDialog = false
            },
            onClearAll = {
                onClearAllNewArrivals()
                showNewArrivalsControlDialog = false
            },
            onDismiss = { showNewArrivalsControlDialog = false }
        )
    }

    stockUpdateTarget?.let { prod ->
        QuickStockUpdateDialog(
            product = prod,
            lowStockThreshold = inventoryHealth.threshold,
            onSave = { newQty ->
                onUpdateStockQuantity(prod, newQty)
                stockUpdateTarget = null
            },
            onDismiss = { stockUpdateTarget = null }
        )
    }

    galleryState?.let { (prod, initialPage) ->
        ProductImageGalleryDialog(
            productName = prod.name,
            imageUrls = prod.imageUrls,
            initialPage = initialPage,
            onDismiss = { galleryState = null }
        )
    }

    deleteConfirmProduct?.let { prod ->
        AlertDialog(
            onDismissRequest = { deleteConfirmProduct = null },
            title = { Text("Delete Product?") },
            text = { Text("Remove \"${prod.name}\" from your live wholesale catalog? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteProduct(prod)
                        deleteConfirmProduct = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger600)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun KpiChip(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate800,
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Slate400)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, color = valueColor)
        }
    }
}

@Composable
private fun AdminNewArrivalsBannerCard(
    storeSettings: StoreSettings,
    featuredCount: Int,
    onQuickToggleSection: (Boolean) -> Unit,
    onOpenControlModal: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PureWhite,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (storeSettings.showNewArrivals) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Slate200,
                RoundedCornerShape(14.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NewReleases,
                        contentDescription = null,
                        tint = if (storeSettings.showNewArrivals) MaterialTheme.colorScheme.primary else Slate400,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Buyer Home: New Arrivals",
                                style = MaterialTheme.typography.titleSmall,
                                color = Slate900
                            )
                            StatusPill(
                                text = if (storeSettings.showNewArrivals) "LIVE" else "HIDDEN",
                                containerColor = if (storeSettings.showNewArrivals) Emerald100 else Slate100,
                                contentColor = if (storeSettings.showNewArrivals) Emerald600 else Slate600
                            )
                        }
                        Text(
                            text = "\"${storeSettings.newArrivalsTitle.ifBlank { "Newly Listed Stock" }}\" • $featuredCount tagged (max ${storeSettings.newArrivalsLimit})",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = storeSettings.showNewArrivals,
                    onCheckedChange = onQuickToggleSection,
                    colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("admin_new_arrivals_master_switch")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenControlModal,
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag("admin_manage_new_arrivals_button")
            ) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Control Buyer New Arrivals Section", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AdminNewArrivalsControlDialog(
    storeSettings: StoreSettings,
    featuredCount: Int,
    isBusy: Boolean,
    onSave: (showSection: Boolean, title: String, limit: Long) -> Unit,
    onMarkLatestFive: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showSection by remember(storeSettings) { mutableStateOf(storeSettings.showNewArrivals) }
    var sectionTitle by remember(storeSettings) {
        mutableStateOf(storeSettings.newArrivalsTitle.ifBlank { "Newly Listed Stock" })
    }
    var maxLimit by remember(storeSettings) { mutableStateOf(storeSettings.newArrivalsLimit.coerceIn(1L, 50L)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Control Buyer 'New Arrivals'") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Control what buyers see in the dedicated New Arrivals section at the top of their Home tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show on Buyer Home", style = MaterialTheme.typography.titleSmall, color = Slate900)
                    Switch(
                        checked = showSection,
                        onCheckedChange = { showSection = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                    )
                }

                OutlinedTextField(
                    value = sectionTitle,
                    onValueChange = { sectionTitle = it.take(60) },
                    label = { Text("Section Heading on Buyer Home") },
                    placeholder = { Text("e.g. Newly Listed Stock, Fresh Arrivals") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_arrivals_title_input")
                )

                Column {
                    Text("Max Items in Carousel", style = MaterialTheme.typography.labelMedium, color = Slate600)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5L, 10L, 15L, 20L, 50L).forEach { opt ->
                            FilterChip(
                                selected = maxLimit == opt,
                                onClick = { maxLimit = opt },
                                label = { Text(if (opt == 50L) "All" else "$opt") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Slate900,
                                    selectedLabelColor = PureWhite
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = Slate200)
                Text("Quick Bulk Controls ($featuredCount currently tagged)", style = MaterialTheme.typography.labelMedium, color = Slate900)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onMarkLatestFive,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Latest 5 New", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onClearAll,
                        enabled = !isBusy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger600),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear All Tags", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(showSection, sectionTitle.trim().ifBlank { "Newly Listed Stock" }, maxLimit) },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("save_new_arrivals_config_button")
            ) {
                Text("Save Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AdminLowStockMonitorBanner(
    inventoryHealth: InventoryHealthSummary,
    isFilteringLowStock: Boolean,
    onToggleLowStockFilter: () -> Unit,
    onOpenDashboard: () -> Unit,
    onQuickRestockProduct: (Product) -> Unit,
    onOpenThresholdSettings: () -> Unit
) {
    val hasAlerts = inventoryHealth.lowStockCount > 0
    val borderColor = if (hasAlerts) Amber600.copy(alpha = 0.6f) else Emerald600.copy(alpha = 0.3f)
    val bgColor = if (hasAlerts) Amber50 else PureWhite

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .testTag("admin_low_stock_monitor_banner")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (hasAlerts) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (hasAlerts) Amber600 else Emerald600,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (hasAlerts) "Low Stock Alert" else "Inventory Healthy",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (hasAlerts) Slate900 else Emerald600
                            )
                            StatusPill(
                                text = if (hasAlerts) "${inventoryHealth.lowStockCount} BELOW ${inventoryHealth.threshold}" else "MIN ${inventoryHealth.threshold} PCS",
                                containerColor = if (hasAlerts) Danger100 else Emerald100,
                                contentColor = if (hasAlerts) Danger600 else Emerald600
                            )
                        }
                        Text(
                            text = if (hasAlerts) {
                                "${inventoryHealth.lowStockCount} ${if (inventoryHealth.lowStockCount == 1) "item" else "items"} below threshold (${inventoryHealth.threshold} pcs)" +
                                    if (inventoryHealth.outOfStockCount > 0) " • ${inventoryHealth.outOfStockCount} out of stock" else ""
                            } else {
                                "Monitoring ${inventoryHealth.trackedProductsCount}/${inventoryHealth.totalProducts} items against < ${inventoryHealth.threshold} pcs alert threshold"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate700
                        )
                    }
                }
                IconButton(
                    onClick = onOpenThresholdSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("admin_low_stock_threshold_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Edit Low Stock Threshold",
                        tint = Slate700,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (hasAlerts) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    inventoryHealth.lowStockProducts.take(6).forEach { prod ->
                        val qty = prod.trackedStockCount() ?: 0L
                        Surface(
                            color = PureWhite,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    if (qty == 0L) Danger600.copy(alpha = 0.45f) else Amber600.copy(alpha = 0.45f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onQuickRestockProduct(prod) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = prod.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "(${qty} left)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (qty == 0L) Danger600 else Amber600
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenDashboard,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Amber600,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("admin_open_low_stock_dashboard_cta")
                    ) {
                        Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Open Alerts Dashboard",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    OutlinedButton(
                        onClick = onToggleLowStockFilter,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(44.dp)
                            .testTag("admin_low_stock_filter_cta")
                    ) {
                        Text(
                            text = if (isFilteringLowStock) "All" else "Filter",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenDashboard,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("admin_open_low_stock_dashboard_cta")
                ) {
                    Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Low Stock Alerts Dashboard", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
internal fun QuickStockUpdateDialog(
    product: Product,
    lowStockThreshold: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initialQty = product.trackedStockCount() ?: 25L
    var qtyText by remember(product) { mutableStateOf(initialQty.toString()) }
    val currentQty = qtyText.toLongOrNull() ?: 0L
    val willBeLow = currentQty < lowStockThreshold

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Update Stock Quantity", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { qtyText = (currentQty - 1L).coerceAtLeast(0L).toString() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease stock by 1")
                    }
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() }.take(7) },
                        label = { Text("Qty (pcs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_stock_qty_input")
                    )
                    OutlinedButton(
                        onClick = { qtyText = (currentQty + 1L).coerceAtMost(1_000_000L).toString() },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase stock by 1")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(-5L, 5L, 10L, 25L, 50L).forEach { delta ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val next = (currentQty + delta).coerceIn(0L, 1_000_000L)
                                qtyText = next.toString()
                            },
                            label = {
                                Text(
                                    text = if (delta > 0) "+$delta" else "$delta",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }

                Surface(
                    color = if (currentQty == 0L) Danger100 else if (willBeLow) Amber50 else Emerald100,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (willBeLow) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (currentQty == 0L) Danger600 else if (willBeLow) Amber600 else Emerald600,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                currentQty == 0L -> "0 pcs — triggers Low Stock & Out of Stock"
                                willBeLow -> "Low Stock alert active (< $lowStockThreshold pcs threshold)"
                                else -> "Healthy stock level (≥ $lowStockThreshold pcs threshold)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentQty == 0L) Danger600 else if (willBeLow) Amber600 else Emerald600
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(currentQty.coerceIn(0L, 1_000_000L)) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("confirm_quick_stock_save_button")
            ) {
                Text("Save Stock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AdminProductCard(
    product: Product,
    lowStockThreshold: Long,
    onPhotoClick: (Int) -> Unit,
    onToggleStock: () -> Unit,
    onQuickStockClick: () -> Unit,
    onToggleNewArrival: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isNewFeatured = product.isFeaturedNewArrival()
    val trackedQty = product.trackedStockCount()
    val isLowStock = product.isLowStock(lowStockThreshold)
    val isZeroStock = trackedQty == 0L

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isLowStock) 1.5.dp else 1.dp,
                color = when {
                    isZeroStock -> Danger600.copy(alpha = 0.55f)
                    isLowStock -> Amber600.copy(alpha = 0.65f)
                    else -> Slate200
                },
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isLowStock) {
                Surface(
                    color = if (isZeroStock) Danger100 else Amber100,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable(onClick = onQuickStockClick)
                        .testTag("low_stock_badge_${product.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = "Low Stock Alert",
                                tint = if (isZeroStock) Danger600 else Amber600,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (isZeroStock) "LOW STOCK • 0 PCS LEFT"
                                else "LOW STOCK • ${trackedQty} PCS LEFT (MIN $lowStockThreshold)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isZeroStock) Danger600 else Amber600
                            )
                        }
                        Text(
                            text = "Restock",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AutoScrollingProductPhotoSlider(
                        imageUrls = product.imageUrls,
                        contentDescription = product.name,
                        autoScrollIntervalMs = 3400L,
                        showDotIndicators = false,
                        onPhotoClick = onPhotoClick,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (product.imageUrls.size > 1) {
                        Surface(
                            color = Slate900.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "${product.imageUrls.size} pics",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPill(
                            text = product.carModel,
                            containerColor = Slate100,
                            contentColor = Slate800
                        )
                        StatusPill(
                            text = product.category,
                            containerColor = Color(0xFFEFF6FF),
                            contentColor = Cobalt600
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PriceText(AutoTradeRepository.formatInr(product.price), fontSizeSp = 17)
                        Text(
                            text = "MOQ: ${product.moq} pcs",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate600
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = when {
                                isZeroStock -> Danger100
                                isLowStock -> Amber100
                                trackedQty != null -> Emerald100
                                else -> Slate100
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .clickable(onClick = onQuickStockClick)
                                .testTag("product_stock_pill_${product.id}")
                        ) {
                            Text(
                                text = if (trackedQty != null) "Qty: $trackedQty pcs ✎" else "Set Stock Qty +",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isZeroStock -> Danger600
                                    isLowStock -> Amber600
                                    trackedQty != null -> Emerald600
                                    else -> Slate700
                                },
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        if (product.hsnCode.isNotBlank() || product.gstPercent > 0L) {
                            Text(
                                text = buildString {
                                    if (product.hsnCode.isNotBlank()) append("HSN ${product.hsnCode}")
                                    if (product.gstPercent > 0L) {
                                        if (isNotEmpty()) append(" • ")
                                        append("GST ${product.gstPercent}%")
                                    }
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate600,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Switch(
                        checked = product.inStock,
                        onCheckedChange = { onToggleStock() },
                        colors = SwitchDefaults.colors(checkedTrackColor = Emerald600)
                    )
                    Text(
                        text = if (product.inStock) "In Stock" else "Out",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (product.inStock) Emerald600 else Danger600
                    )
                    FilterChip(
                        selected = isNewFeatured,
                        onClick = onToggleNewArrival,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.NewReleases,
                                contentDescription = "Toggle New Arrival",
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = if (isNewFeatured) "New" else "Mark New",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("toggle_new_arrival_${product.id}")
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${product.name}", tint = Slate800)
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete ${product.name}", tint = Danger600)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminOrdersTab(
    ordersState: UiState<List<Order>>,
    invoices: List<Invoice>,
    businessName: String = "AutoTrade Wholesale",
    onUpdateOrderStatus: (Order, OrderStatus) -> Unit,
    onGenerateBill: (Order) -> Unit,
    onViewBill: (Invoice) -> Unit,
    onNotify: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var filterStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showExcelExportDialog by remember { mutableStateOf(false) }

    val allOrders = (ordersState as? UiState.Success)?.data.orEmpty()
    val filteredOrders = remember(allOrders, filterStatus, searchQuery) {
        val q = searchQuery.trim().lowercase()
        allOrders.filter { order ->
            val matchStatus = filterStatus == null || order.orderStatus() == filterStatus
            val matchQuery = q.isEmpty() ||
                order.shopName.lowercase().contains(q) ||
                order.city.lowercase().contains(q) ||
                order.phone.lowercase().contains(q) ||
                order.id.takeLast(6).lowercase().contains(q) ||
                order.items.any { it.name.lowercase().contains(q) || it.carModel.lowercase().contains(q) }
            matchStatus && matchQuery
        }
    }
    val activeFilterLabel = remember(filterStatus, searchQuery) {
        buildString {
            append(filterStatus?.label ?: "All")
            val q = searchQuery.trim()
            if (q.isNotEmpty()) append("_$q")
        }
    }
    val activeFilterSummary = remember(filterStatus, searchQuery, filteredOrders) {
        val parts = mutableListOf<String>()
        parts.add("Status: ${filterStatus?.label ?: "All"}")
        val q = searchQuery.trim()
        if (q.isNotEmpty()) parts.add("Search: \"$q\"")
        val totalVal = filteredOrders.sumOf { it.totalAmount }
        parts.add("${filteredOrders.size} ${if (filteredOrders.size == 1) "order" else "orders"} (${AutoTradeRepository.formatInr(totalVal)})")
        parts.joinToString(" • ")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = "Incoming Orders",
            subtitle = "One-tap status updates synced live to buyers",
            trailingAction = {
                ExcelExportHeaderChip(
                    enabled = filteredOrders.isNotEmpty(),
                    testTagName = "admin_orders_export_excel_button",
                    onClick = { showExcelExportDialog = true }
                )
            },
            bottomContent = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search shop, city, order # or item…", color = Slate400) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Slate400) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear order search", tint = Slate400)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Slate800,
                        unfocusedContainerColor = Slate800,
                        focusedTextColor = PureWhite,
                        unfocusedTextColor = PureWhite,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Slate700
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_orders_search_input")
                )
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterStatus == null,
                onClick = { filterStatus = null },
                label = { Text("All (${allOrders.size})") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = PureWhite)
            )
            OrderStatus.entries.forEach { st ->
                val count = allOrders.count { it.orderStatus() == st }
                FilterChip(
                    selected = filterStatus == st,
                    onClick = { filterStatus = st },
                    label = { Text("${st.label} ($count)") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = PureWhite)
                )
            }
        }
        HorizontalDivider(color = Slate200)

        if (allOrders.isNotEmpty()) {
            FilteredExcelExportStrip(
                summaryText = "Showing ${filteredOrders.size} of ${allOrders.size} orders • Total ${AutoTradeRepository.formatInr(filteredOrders.sumOf { it.totalAmount })}",
                hasRows = filteredOrders.isNotEmpty(),
                testTagName = "admin_orders_export_strip_button",
                onExportClick = { showExcelExportDialog = true }
            )
            HorizontalDivider(color = Slate200)
        }

        when (ordersState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(Icons.Filled.ReceiptLong, "Could not load orders", ordersState.message)
            }
            is UiState.Success -> {
                if (filteredOrders.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        EmptyStateCard(
                            icon = Icons.Filled.ReceiptLong,
                            title = if (allOrders.isEmpty()) "No orders yet" else "No matching orders",
                            subtitle = if (allOrders.isEmpty())
                                "Approved shopkeeper orders appear here instantaneously with full item breakdown."
                            else "Try clearing your status filter or search term."
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredOrders, key = { it.id }) { order ->
                            val linkedInvoice = remember(order, invoices) {
                                invoices.firstOrNull {
                                    (order.invoiceId.isNotBlank() && it.id == order.invoiceId) || it.orderId == order.id
                                }
                            }
                            AdminOrderCard(
                                order = order,
                                linkedInvoice = linkedInvoice,
                                onSelectStatus = { onUpdateOrderStatus(order, it) },
                                onGenerateBill = { onGenerateBill(order) },
                                onViewBill = { linkedInvoice?.let(onViewBill) },
                                onWhatsAppBuyer = {
                                    val msg = AutoTradeRepository.buildWhatsAppOrderSummary(
                                        shopName = order.shopName,
                                        phone = order.phone,
                                        city = order.city,
                                        items = order.items,
                                        totalAmount = order.totalAmount,
                                        orderId = order.id
                                    )
                                    launchWhatsAppOrder(context, order.phone, msg)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExcelExportDialog) {
        ExcelExportActionDialog(
            title = "Export Orders to Excel",
            filterSummary = activeFilterSummary,
            sheetsIncluded = listOf("Orders Summary", "Order Line Items"),
            generateFile = {
                ExcelReportExporter.exportOrdersToXlsx(
                    context = context,
                    orders = filteredOrders,
                    invoices = invoices,
                    businessName = businessName,
                    reportTitle = "Wholesale Orders Report",
                    filterLabel = activeFilterLabel,
                    filterSummary = activeFilterSummary
                )
            },
            onNotify = onNotify,
            onDismiss = { showExcelExportDialog = false }
        )
    }
}

@Composable
private fun AdminOrderCard(
    order: Order,
    linkedInvoice: Invoice?,
    onSelectStatus: (OrderStatus) -> Unit,
    onGenerateBill: () -> Unit,
    onViewBill: () -> Unit,
    onWhatsAppBuyer: () -> Unit
) {
    val currentStatus = order.orderStatus()
    val (bg, fg) = orderStatusColors(currentStatus)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = order.shopName,
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900
                    )
                    Text(
                        text = "${order.city} • ${order.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                    Text(
                        text = "#${order.id.takeLast(6).uppercase()} • ${formatTimestamp(order.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Slate400
                    )
                }
                StatusPill(text = currentStatus.label, containerColor = bg, contentColor = fg)
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(10.dp))

            order.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.name}${if (item.carModel.isNotBlank()) " (${item.carModel})" else ""} × ${item.qty}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate800,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AutoTradeRepository.formatInr(item.lineTotal()),
                        style = MaterialTheme.typography.labelLarge,
                        color = Slate900
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Total Amount", style = MaterialTheme.typography.labelSmall, color = Slate600)
                    PriceText(AutoTradeRepository.formatInr(order.totalAmount), color = MaterialTheme.colorScheme.primary, fontSizeSp = 19)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (linkedInvoice != null) {
                        Button(
                            onClick = onViewBill,
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("view_bill_button_${order.id}")
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("View Bill", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        Button(
                            onClick = onGenerateBill,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("generate_bill_button_${order.id}")
                        ) {
                            Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Generate Bill", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = onWhatsAppBuyer,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Filled.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Update Order Status", style = MaterialTheme.typography.labelSmall, color = Slate600)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OrderStatus.entries.forEach { st ->
                    val selected = st == currentStatus
                    FilterChip(
                        selected = selected,
                        onClick = { if (!selected) onSelectStatus(st) },
                        label = { Text(st.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Slate900,
                            selectedLabelColor = PureWhite
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AdminShopkeepersTab(
    usersState: UiState<List<UserProfile>>,
    invoices: List<Invoice>,
    onUpdateStatus: (UserProfile, AccountStatus) -> Unit
) {
    var filter by remember { mutableStateOf<AccountStatus?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = "Shopkeeper Approvals",
            subtitle = "Approve verified buyers to unlock wholesale rates"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filter == null,
                onClick = { filter = null },
                label = { Text("All Buyers") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = PureWhite)
            )
            AccountStatus.entries.forEach { st ->
                FilterChip(
                    selected = filter == st,
                    onClick = { filter = st },
                    label = { Text(st.label) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = PureWhite)
                )
            }
        }
        HorizontalDivider(color = Slate200)

        when (usersState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(Icons.Filled.Group, "Could not load buyers", usersState.message)
            }
            is UiState.Success -> {
                val buyers = usersState.data
                    .filter { !it.roleIsAdmin() }
                    .filter { filter == null || it.accountStatus() == filter }
                if (buyers.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        EmptyStateCard(
                            icon = Icons.Filled.Group,
                            title = "No shopkeepers in this view",
                            subtitle = "New shopkeeper signups appear here automatically for one-tap approval."
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(buyers, key = { it.id }) { buyer ->
                            val pendingUdhaar = remember(buyer, invoices) {
                                invoices.filter { inv ->
                                    (inv.shopkeeperId.isNotBlank() && (inv.shopkeeperId == buyer.id || inv.shopkeeperId == buyer.userId)) ||
                                        (inv.shopkeeperId.isBlank() && inv.shopName.equals(buyer.shopName, ignoreCase = true))
                                }.sumOf { it.balanceDue }
                            }
                            ShopkeeperApprovalCard(
                                buyer = buyer,
                                pendingUdhaar = pendingUdhaar,
                                onApprove = { onUpdateStatus(buyer, AccountStatus.APPROVED) },
                                onBlock = { onUpdateStatus(buyer, AccountStatus.BLOCKED) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopkeeperApprovalCard(
    buyer: UserProfile,
    pendingUdhaar: Double,
    onApprove: () -> Unit,
    onBlock: () -> Unit
) {
    val status = buyer.accountStatus()
    val (bg, fg) = accountStatusColors(status)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(buyer.shopName, style = MaterialTheme.typography.titleMedium, color = Slate900)
                    Text("${buyer.name} • ${buyer.city}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                    Text("Mobile: ${buyer.phone}", style = MaterialTheme.typography.labelMedium, color = Slate800)
                    if (buyer.gstin.isNotBlank()) {
                        Text("GSTIN: ${buyer.gstin}", style = MaterialTheme.typography.labelSmall, color = Slate600)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusPill(text = status.label, containerColor = bg, contentColor = fg)
                    if (buyer.fcmToken.isNotBlank() && status == AccountStatus.APPROVED) {
                        StatusPill(
                            text = "FCM Push Ready",
                            containerColor = Color(0xFFEFF6FF),
                            contentColor = Cobalt600
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (pendingUdhaar > 0.0) Danger100 else Emerald100,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (pendingUdhaar > 0.0) "Pending Balance (Udhaar)" else "Account Settled",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pendingUdhaar > 0.0) Danger600 else Emerald600
                    )
                    PriceText(
                        formattedPrice = AutoTradeRepository.formatInr(pendingUdhaar),
                        color = if (pendingUdhaar > 0.0) Danger600 else Emerald600,
                        fontSizeSp = 15
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (status != AccountStatus.APPROVED) {
                    Button(
                        onClick = onApprove,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Approve")
                    }
                }
                if (status != AccountStatus.BLOCKED) {
                    OutlinedButton(
                        onClick = onBlock,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger600),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Block")
                    }
                }
            }
        }
    }
}

@Composable
fun AdminSettingsTab(
    profile: UserProfile,
    storeSettings: StoreSettings,
    businessProfile: BusinessProfile,
    activityLogsState: UiState<List<AdminActivityLog>>,
    lowStockCount: Int,
    syncStatus: SyncStatusSummary,
    cachedProductsCount: Int,
    pendingInvoicesCount: Int,
    isBusy: Boolean,
    onSyncNow: () -> Unit,
    onOpenActivityLog: () -> Unit,
    onOpenLowStockDashboard: () -> Unit,
    onPreviewBrandColor: (String?) -> Unit,
    onSaveBrandTheme: (String) -> Unit,
    onSaveSettings: (businessName: String, whatsappPhone: String, city: String) -> Unit,
    onSaveBusinessProfile: (
        firmName: String,
        address: String,
        phone: String,
        gstin: String,
        upiId: String,
        invoicePrefix: String,
        nextInvoiceNumber: Long,
        lowStockThreshold: Long
    ) -> Unit,
    onPrintCatalogPdf: () -> Unit,
    onSeedCategories: () -> Unit,
    onSwitchToBuyerPreview: () -> Unit,
    onSignOut: () -> Unit
) {
    var businessName by remember(storeSettings) { mutableStateOf(storeSettings.businessName.ifBlank { profile.shopName }) }
    var whatsappPhone by remember(storeSettings) { mutableStateOf(storeSettings.whatsappPhone.ifBlank { profile.phone }) }
    var city by remember(storeSettings) { mutableStateOf(storeSettings.city.ifBlank { profile.city }) }

    var firmName by remember(businessProfile) { mutableStateOf(businessProfile.firmName.ifBlank { profile.shopName }) }
    var firmAddress by remember(businessProfile) { mutableStateOf(businessProfile.address.ifBlank { profile.city }) }
    var firmPhone by remember(businessProfile) { mutableStateOf(businessProfile.phone.ifBlank { profile.phone }) }
    var firmGstin by remember(businessProfile) { mutableStateOf(businessProfile.gstin) }
    var firmUpiId by remember(businessProfile) { mutableStateOf(businessProfile.upiId) }
    var invoicePrefix by remember(businessProfile) { mutableStateOf(businessProfile.invoicePrefix.ifBlank { "INV" }) }
    var nextInvNumText by remember(businessProfile) { mutableStateOf(businessProfile.nextInvoiceNumber.coerceAtLeast(1L).toString()) }
    var lowStockThresholdText by remember(businessProfile) {
        mutableStateOf(businessProfile.lowStockThreshold.coerceAtLeast(1L).toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = "Wholesaler Settings",
            subtitle = profile.email,
            trailingAction = {
                IconButton(
                    onClick = onOpenActivityLog,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("header_activity_log_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = "Open Admin Activity Log",
                        tint = PureWhite
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. ADMIN ACTIVITY LOG (SECURITY & ACCOUNTABILITY AUDIT TRAIL)
            AdminActivityLogSummaryCard(
                logsState = activityLogsState,
                onOpenFullLog = onOpenActivityLog
            )

            // 0B. FIRESTORE OFFLINE PERSISTENCE & AUTO-SYNC STATUS
            OfflinePersistenceStatusCard(
                syncStatus = syncStatus,
                cachedProductsCount = cachedProductsCount,
                pendingInvoicesCount = pendingInvoicesCount,
                onSyncNow = onSyncNow
            )

            // 1. CATALOG BRAND & THEME SETTINGS CARD
            AdminThemeSettingsCard(
                savedPrimaryColorHex = storeSettings.primaryColorHex,
                businessName = businessName.ifBlank { firmName },
                isBusy = isBusy,
                onPreviewBrandColor = onPreviewBrandColor,
                onSaveBrandTheme = onSaveBrandTheme
            )

            // 1. BUSINESS BILLING & GST PROFILE CARD
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PureWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Business Billing & Invoice Profile",
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900
                    )
                    Text(
                        text = "Printed on every GST Invoice, Cash Memo PDF, and WhatsApp bill summary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                    OutlinedTextField(
                        value = firmName,
                        onValueChange = { firmName = it.take(100) },
                        label = { Text("Firm / Agency Name *") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("firm_name_input")
                    )
                    OutlinedTextField(
                        value = firmAddress,
                        onValueChange = { firmAddress = it.take(250) },
                        label = { Text("Shop / Warehouse Address") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = firmPhone,
                        onValueChange = { firmPhone = it.filter { c -> c.isDigit() }.take(15) },
                        label = { Text("Billing Contact Phone *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = firmGstin,
                        onValueChange = { firmGstin = it.uppercase(Locale.US).take(20) },
                        label = { Text("Firm GSTIN (Optional)") },
                        placeholder = { Text("e.g. 07ABCDE1234F1Z5") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = firmUpiId,
                        onValueChange = { firmUpiId = it.trim().take(100) },
                        label = { Text("UPI ID for Payments (Optional)") },
                        placeholder = { Text("e.g. autotrade@okaxis") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = invoicePrefix,
                            onValueChange = { invoicePrefix = it.uppercase(Locale.US).filter { c -> c.isLetterOrDigit() || c == '-' }.take(12) },
                            label = { Text("Invoice Prefix") },
                            placeholder = { Text("INV") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = nextInvNumText,
                            onValueChange = { nextInvNumText = it.filter { c -> c.isDigit() }.take(6) },
                            label = { Text("Next Invoice #") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = Slate100)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = Amber600,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Inventory Low Stock Alert Threshold",
                            style = MaterialTheme.typography.titleSmall,
                            color = Slate900
                        )
                    }
                    OutlinedTextField(
                        value = lowStockThresholdText,
                        onValueChange = { lowStockThresholdText = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text("Low Stock Threshold (pcs) *") },
                        supportingText = {
                            Text("Items with stock below this quantity show a 'Low Stock' alert badge in Admin Inventory.")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("low_stock_threshold_input")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5L, 10L, 15L, 25L, 50L).forEach { preset ->
                            FilterChip(
                                selected = lowStockThresholdText.toLongOrNull() == preset,
                                onClick = { lowStockThresholdText = preset.toString() },
                                label = { Text("< $preset pcs", style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Amber600,
                                    selectedLabelColor = PureWhite
                                )
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val nextNum = nextInvNumText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
                            val threshold = lowStockThresholdText.toLongOrNull()
                                ?.coerceIn(1L, 100_000L)
                                ?: DEFAULT_LOW_STOCK_THRESHOLD
                            onSaveBusinessProfile(
                                firmName,
                                firmAddress,
                                firmPhone,
                                firmGstin,
                                firmUpiId,
                                invoicePrefix.ifBlank { "INV" },
                                nextNum,
                                threshold
                            )
                        },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_business_profile_button")
                    ) {
                        Text("Save Business & Inventory Settings")
                    }
                    OutlinedButton(
                        onClick = onOpenLowStockDashboard,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_open_low_stock_dashboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.WarningAmber,
                            contentDescription = null,
                            tint = if (lowStockCount > 0) Amber600 else Emerald600,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (lowStockCount > 0) "Open Low Stock Alerts ($lowStockCount)" else "Open Low Stock Alerts Dashboard",
                            color = Slate900
                        )
                    }
                }
            }

            // 2. WHATSAPP ORDER RECEIVING CARD
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PureWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "WhatsApp Order Receiving Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900
                    )
                    Text(
                        text = "When shopkeepers tap 'Send on WhatsApp', orders route to this number.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { businessName = it },
                        label = { Text("Wholesale Business Name") },
                        leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = whatsappPhone,
                        onValueChange = { whatsappPhone = it.filter { ch -> ch.isDigit() }.take(15) },
                        label = { Text("WhatsApp Phone (10-digit or with 91)") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("Warehouse City") },
                        leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onSaveSettings(businessName, whatsappPhone, city) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_store_settings_button")
                    ) {
                        Text("Save Store Settings")
                    }
                }
            }

            // 3. QUICK ACTIONS CARD
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PureWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, color = Slate900)
                    Button(
                        onClick = onPrintCatalogPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_print_catalog_pdf_button")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Print / Export Full Catalog PDF")
                    }
                    OutlinedButton(
                        onClick = onSeedCategories,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Seed Default Car Accessory Categories")
                    }
                    OutlinedButton(
                        onClick = onSwitchToBuyerPreview,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Shopkeeper Buyer Catalog View")
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger600),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("admin_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
                    }
                }
            }
        }
    }
}
