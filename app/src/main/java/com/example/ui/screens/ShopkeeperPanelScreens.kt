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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AutoTradeRepository
import com.example.data.CartItem
import com.example.data.Category
import com.example.data.ExcelReportExporter
import com.example.data.Invoice
import com.example.data.NEW_ARRIVALS_FILTER
import com.example.data.Order
import com.example.data.OrderItem
import com.example.data.OrderStatus
import com.example.data.PaymentStatus
import com.example.data.PdfInvoiceGenerator
import com.example.data.Product
import com.example.data.StoreSettings
import com.example.data.SyncStatusSummary
import com.example.data.UiState
import com.example.data.UserProfile
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
import com.example.ui.components.buildCatalogCategoryList
import com.example.ui.components.formatTimestamp
import com.example.ui.components.launchWhatsAppOrder
import com.example.ui.components.orderStatusColors
import com.example.ui.components.paymentStatusColors
import kotlinx.coroutines.launch
import java.util.Locale
import com.example.ui.theme.Amber100
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
fun ShopkeeperCatalogTab(
    storeSettings: StoreSettings,
    productsState: UiState<List<Product>>,
    filteredProducts: List<Product>,
    newArrivalProducts: List<Product>,
    categories: List<Category>,
    cartItems: List<CartItem>,
    searchQuery: String,
    selectedCategory: String,
    unreadNotificationsCount: Int,
    isAdminPreview: Boolean,
    onSearchChange: (String) -> Unit,
    onSelectCategory: (String) -> Unit,
    onAddToCart: (Product) -> Unit,
    onIncrementCart: (Product) -> Unit,
    onDecrementCart: (Product) -> Unit,
    onOpenNotifications: () -> Unit,
    onPrintCatalogPdf: () -> Unit,
    onBackToAdmin: () -> Unit
) {
    var galleryState by remember { mutableStateOf<Pair<Product, Int>?>(null) }
    val cartQtyByProductId = remember(cartItems) { cartItems.associate { it.product.id to it.qty } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = storeSettings.businessName.ifBlank { "AutoTrade Catalog" },
            subtitle = "Live Wholesale Rates • Instant Availability",
            trailingAction = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onOpenNotifications)
                            .testTag("buyer_notifications_button")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationsCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(unreadNotificationsCount.coerceAtMost(99).toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (unreadNotificationsCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                    contentDescription = "Alerts and Notifications",
                                    tint = PureWhite,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onPrintCatalogPdf)
                            .testTag("buyer_catalog_pdf_button")
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
                            Text("PDF", style = MaterialTheme.typography.labelSmall, color = PureWhite)
                        }
                    }
                    if (isAdminPreview) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .clickable(onClick = onBackToAdmin)
                                .testTag("back_to_admin_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AdminPanelSettings,
                                    contentDescription = "Back to Admin Panel",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            },
            bottomContent = {
                GlobalCatalogSearchBar(
                    query = searchQuery,
                    onQueryChange = onSearchChange,
                    placeholder = stringResource(R.string.search_placeholder),
                    testTagName = "buyer_search_input"
                )
            }
        )

        // Horizontal Automotive Accessory Category Filter Bar
        val allBuyerProducts = (productsState as? UiState.Success)?.data.orEmpty()
        val showPinnedNewArrivals = storeSettings.showNewArrivals
        val showNewFeature = storeSettings.showNewArrivals && newArrivalProducts.isNotEmpty()
        AutomotiveCategoryFilterBar(
            categories = categories,
            products = allBuyerProducts,
            selectedCategory = selectedCategory,
            filteredCount = filteredProducts.size,
            onSelectCategory = onSelectCategory,
            showPinnedNewArrivals = showPinnedNewArrivals,
            newArrivalsCount = newArrivalProducts.size,
            isSearchActive = searchQuery.isNotBlank(),
            testTagPrefix = "buyer"
        )

        val allBuyerProductsCount = allBuyerProducts.size
        val matchingCategoryNames = remember(categories, allBuyerProducts, searchQuery) {
            val q = searchQuery.trim().lowercase()
            if (q.isEmpty()) emptyList()
            else buildCatalogCategoryList(categories, allBuyerProducts).filter { it.lowercase().contains(q) }
        }
        GlobalSearchStatusBanner(
            searchQuery = searchQuery,
            selectedCategory = selectedCategory,
            resultCount = filteredProducts.size,
            totalCount = allBuyerProductsCount,
            matchingCategories = matchingCategoryNames,
            onSelectCategory = onSelectCategory,
            onClearSearch = { onSearchChange("") }
        )

        when (productsState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(Icons.Filled.Inventory2, "Unable to load catalog", productsState.message)
            }
            is UiState.Success -> {
                if (filteredProducts.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        EmptyStateCard(
                            icon = Icons.Filled.Inventory2,
                            title = if (searchQuery.isNotBlank()) "No results for \"${searchQuery.trim()}\""
                            else "No accessories found",
                            subtitle = if (searchQuery.isNotBlank() || selectedCategory != "All")
                                "Try searching by another product name or category, or reset filters."
                            else "New stock added by the wholesaler will appear live here automatically.",
                            action = if (searchQuery.isNotBlank() || selectedCategory != "All") {
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
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Dedicated Buyer Home "Newly Listed Stock" Showcase Section (Admin-controlled)
                        if (showNewFeature && selectedCategory == "All" && searchQuery.isBlank()) {
                            item(key = "buyer_new_arrivals_showcase") {
                                BuyerNewArrivalsSection(
                                    title = storeSettings.newArrivalsTitle.ifBlank { "Newly Listed Stock" },
                                    products = newArrivalProducts,
                                    cartQtyByProductId = cartQtyByProductId,
                                    onViewAllNew = { onSelectCategory(NEW_ARRIVALS_FILTER) },
                                    onOpenGallery = { prod, page ->
                                        if (prod.imageUrls.isNotEmpty()) galleryState = prod to page
                                    },
                                    onAddToCart = onAddToCart,
                                    onIncrement = onIncrementCart,
                                    onDecrement = onDecrementCart
                                )
                            }

                            item(key = "all_catalog_divider_heading") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "All Wholesale Products",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Slate900
                                    )
                                    Text(
                                        text = "${filteredProducts.size} items",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Slate600
                                    )
                                }
                            }
                        }

                        items(filteredProducts, key = { it.id }) { product ->
                            BuyerProductCard(
                                product = product,
                                showNewBadge = storeSettings.showNewArrivals && product.isFeaturedNewArrival(),
                                cartQty = cartQtyByProductId[product.id] ?: 0L,
                                onOpenGallery = { page ->
                                    if (product.imageUrls.isNotEmpty()) galleryState = product to page
                                },
                                onAddToCart = { onAddToCart(product) },
                                onIncrement = { onIncrementCart(product) },
                                onDecrement = { onDecrementCart(product) }
                            )
                        }
                    }
                }
            }
        }
    }

    galleryState?.let { (prod, initialPage) ->
        ProductImageGalleryDialog(
            productName = prod.name,
            imageUrls = prod.imageUrls,
            initialPage = initialPage,
            onDismiss = { galleryState = null }
        )
    }
}

@Composable
private fun BuyerNewArrivalsSection(
    title: String,
    products: List<Product>,
    cartQtyByProductId: Map<String, Long>,
    onViewAllNew: () -> Unit,
    onOpenGallery: (Product, Int) -> Unit,
    onAddToCart: (Product) -> Unit,
    onIncrement: (Product) -> Unit,
    onDecrement: (Product) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Slate900,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("buyer_new_arrivals_section")
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "NEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${products.size} fresh wholesale listings",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400
                        )
                    }
                }

                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable(onClick = onViewAllNew)
                        .testTag("buyer_view_all_new_button")
                ) {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(products, key = { "new_${it.id}" }) { product ->
                    val cartQty = cartQtyByProductId[product.id] ?: 0L
                    val availableStock = product.trackedStockCount()
                    val isAvailable = product.inStock && (availableStock == null || availableStock > 0L)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PureWhite,
                        modifier = Modifier
                            .width(214.dp)
                            .border(1.dp, Slate200, RoundedCornerShape(14.dp))
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(128.dp)
                            ) {
                                AutoScrollingProductPhotoSlider(
                                    imageUrls = product.imageUrls,
                                    contentDescription = product.name,
                                    autoScrollIntervalMs = 3400L,
                                    onPhotoClick = { page -> onOpenGallery(product, page) },
                                    modifier = Modifier.fillMaxSize()
                                )
                                StatusPill(
                                    text = "NEW",
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(8.dp)
                                )
                                if (!isAvailable) {
                                    StatusPill(
                                        text = "OUT",
                                        containerColor = Danger100,
                                        contentColor = Danger600,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    )
                                } else if (availableStock != null) {
                                    val isLow = availableStock <= 5L
                                    StatusPill(
                                        text = "$availableStock pcs",
                                        containerColor = if (isLow) Amber100 else Emerald100,
                                        contentColor = if (isLow) Amber600 else Emerald600,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.padding(10.dp)) {
                                StatusPill(product.carModel, Slate100, Slate800)
                                Spacer(modifier = Modifier.height(5.dp))
                                Text(
                                    text = product.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PriceText(AutoTradeRepository.formatInr(product.price), fontSizeSp = 16, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "MOQ ${product.moq}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Slate600
                                    )
                                }
                                if (availableStock != null) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (isAvailable) "Stock: $availableStock pcs" else "Out of Stock (0 pcs)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            !isAvailable -> Danger600
                                            availableStock <= 5L -> Amber600
                                            else -> Emerald600
                                        }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (!isAvailable) {
                                    Surface(
                                        color = Slate100,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Out of Stock",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Slate400,
                                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
                                        )
                                    }
                                } else if (cartQty == 0L) {
                                    Button(
                                        onClick = { onAddToCart(product) },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                    ) {
                                        Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text("Add (${product.moq})", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(40.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Slate900),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(onClick = { onDecrement(product) }, modifier = Modifier.size(38.dp)) {
                                            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = PureWhite, modifier = Modifier.size(16.dp))
                                        }
                                        Text(
                                            text = "$cartQty",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = PureWhite
                                        )
                                        IconButton(onClick = { onIncrement(product) }, modifier = Modifier.size(38.dp)) {
                                            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = PureWhite, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BuyerProductCard(
    product: Product,
    showNewBadge: Boolean,
    cartQty: Long,
    onOpenGallery: (Int) -> Unit,
    onAddToCart: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    val isNew = showNewBadge
    val availableStock = product.trackedStockCount()
    val isAvailable = product.inStock && (availableStock == null || availableStock > 0L)
    val isLowForBuyer = isAvailable && availableStock != null && availableStock <= 5L

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PureWhite,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate200, RoundedCornerShape(18.dp))
    ) {
        Column {
            // Product Hero Image Slider (auto-scrolls + supports manual left/right swipe & tap to zoom)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(195.dp)
            ) {
                AutoScrollingProductPhotoSlider(
                    imageUrls = product.imageUrls,
                    contentDescription = product.name,
                    autoScrollIntervalMs = 3200L,
                    onPhotoClick = onOpenGallery,
                    modifier = Modifier.fillMaxSize()
                )

                // Top-left badges
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isNew && isAvailable) {
                        StatusPill(
                            text = stringResource(R.string.new_arrival),
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    StatusPill(
                        text = when {
                            !isAvailable -> "OUT OF STOCK"
                            isLowForBuyer -> "ONLY $availableStock LEFT"
                            availableStock != null -> "IN STOCK • $availableStock pcs"
                            else -> "IN STOCK"
                        },
                        containerColor = when {
                            !isAvailable -> Danger100
                            isLowForBuyer -> Amber100
                            else -> Emerald100
                        },
                        contentColor = when {
                            !isAvailable -> Danger600
                            isLowForBuyer -> Amber600
                            else -> Emerald600
                        },
                        modifier = Modifier.testTag("buyer_stock_badge_${product.id}")
                    )
                }

                // Bottom-right photo count / zoom indicator
                if (product.imageUrls.isNotEmpty()) {
                    Surface(
                        color = Slate900.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ZoomIn,
                                contentDescription = "Tap to zoom",
                                tint = PureWhite,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (product.imageUrls.size > 1) "${product.imageUrls.size} Photos" else "Zoom",
                                style = MaterialTheme.typography.labelSmall,
                                color = PureWhite
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        StatusPill(product.carModel, Slate100, Slate800)
                        StatusPill(product.category, Color(0xFFEFF6FF), Cobalt600)
                    }
                    if (availableStock != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusPill(
                            text = if (isAvailable) "Stock: $availableStock pcs" else "Stock: 0 pcs",
                            containerColor = when {
                                !isAvailable -> Danger100
                                isLowForBuyer -> Amber100
                                else -> Emerald100
                            },
                            contentColor = when {
                                !isAvailable -> Danger600
                                isLowForBuyer -> Amber600
                                else -> Emerald600
                            },
                            modifier = Modifier.testTag("buyer_stock_qty_${product.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Slate900,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (product.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = product.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        PriceText(AutoTradeRepository.formatInr(product.price), fontSizeSp = 21)
                        Text(
                            text = if (availableStock != null) {
                                "MOQ: ${product.moq} pcs • Available: $availableStock pcs"
                            } else {
                                "MOQ: ${product.moq} pcs • Wholesale Rate"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (!isAvailable) Danger600 else Slate600
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (!isAvailable) {
                        Surface(
                            color = Slate100,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "Unavailable",
                                style = MaterialTheme.typography.labelMedium,
                                color = Slate400,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    } else if (cartQty == 0L) {
                        Button(
                            onClick = onAddToCart,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add (${product.moq})", style = MaterialTheme.typography.labelLarge)
                        }
                    } else {
                        val atStockLimit = availableStock != null && cartQty >= availableStock
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Slate900)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(onClick = onDecrement, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Filled.Remove, contentDescription = "Decrease quantity", tint = PureWhite)
                            }
                            Text(
                                text = "$cartQty",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureWhite,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(onClick = onIncrement, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Increase quantity",
                                    tint = if (atStockLimit) Slate400 else PureWhite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopkeeperCartTab(
    profile: UserProfile,
    storeSettings: StoreSettings,
    cartItems: List<CartItem>,
    isBusy: Boolean,
    onIncrement: (Product) -> Unit,
    onDecrement: (Product) -> Unit,
    onRemove: (String) -> Unit,
    onPlaceOrder: () -> Unit
) {
    val context = LocalContext.current
    val grandTotal = remember(cartItems) { cartItems.sumOf { it.lineTotal } }
    val totalUnits = remember(cartItems) { cartItems.sumOf { it.qty } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = "Wholesale Cart",
            subtitle = if (cartItems.isEmpty()) "Ready for quick B2B ordering"
            else "${cartItems.size} ${if (cartItems.size == 1) "Item" else "Items"} • $totalUnits total pcs"
        )

        if (cartItems.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(
                    icon = Icons.Filled.ShoppingCart,
                    title = "Your cart is empty",
                    subtitle = "Browse the live catalog and tap Add to start building your wholesale order from MOQ."
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(cartItems, key = { it.product.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = PureWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate200, RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ProductPhoto(
                                imageSource = item.product.imageUrls.firstOrNull(),
                                contentDescription = item.product.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.product.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = Slate900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.product.carModel} • ${AutoTradeRepository.formatInr(item.product.price)}/pc • MOQ ${item.product.moq}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate600
                                )
                                val avail = item.product.trackedStockCount()
                                if (avail != null) {
                                    Text(
                                        text = if (item.qty >= avail) "Available Stock: $avail pcs (Max reached)" else "Available Stock: $avail pcs",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (item.qty >= avail) Amber600 else Emerald600
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                PriceText(AutoTradeRepository.formatInr(item.lineTotal), fontSizeSp = 15, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Slate100)
                                ) {
                                    IconButton(onClick = { onDecrement(item.product) }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = Slate900, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = "${item.qty}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Slate900,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(onClick = { onIncrement(item.product) }, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.Filled.Add, contentDescription = "Increase", tint = Slate900, modifier = Modifier.size(18.dp))
                                    }
                                }
                                IconButton(onClick = { onRemove(item.product.id) }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = Danger600, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Checkout Footer Card
            Surface(
                color = PureWhite,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate200)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Estimated Wholesale Total", style = MaterialTheme.typography.bodySmall, color = Slate600)
                            Text("For ${profile.shopName} (${profile.city})", style = MaterialTheme.typography.labelSmall, color = Slate400)
                        }
                        PriceText(AutoTradeRepository.formatInr(grandTotal), fontSizeSp = 22, color = Slate900)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPlaceOrder,
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(52.dp)
                                .testTag("place_order_button")
                        ) {
                            if (isBusy) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = PureWhite, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.place_order), style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        Button(
                            onClick = {
                                val orderItems = cartItems.map {
                                    OrderItem(
                                        productId = it.product.id,
                                        name = it.product.name,
                                        carModel = it.product.carModel,
                                        price = it.product.price,
                                        qty = it.qty
                                    )
                                }
                                val summary = AutoTradeRepository.buildWhatsAppOrderSummary(
                                    shopName = profile.shopName.ifBlank { profile.name },
                                    phone = profile.phone,
                                    city = profile.city,
                                    items = orderItems,
                                    totalAmount = grandTotal
                                )
                                launchWhatsAppOrder(context, storeSettings.whatsappPhone, summary)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("whatsapp_order_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopkeeperOrdersTab(
    ordersState: UiState<List<Order>>,
    storeSettings: StoreSettings,
    invoices: List<Invoice> = emptyList(),
    onNotify: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var filterStatus by remember { mutableStateOf<OrderStatus?>(null) }
    var showExcelExportDialog by remember { mutableStateOf(false) }

    val allOrders = (ordersState as? UiState.Success)?.data.orEmpty()
    val filteredOrders = remember(allOrders, filterStatus) {
        allOrders.filter { filterStatus == null || it.orderStatus() == filterStatus }
    }
    val filterSummary = remember(filterStatus, filteredOrders) {
        val total = filteredOrders.sumOf { it.totalAmount }
        "Status: ${filterStatus?.label ?: "All"} • ${filteredOrders.size} ${if (filteredOrders.size == 1) "order" else "orders"} (${AutoTradeRepository.formatInr(total)})"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = "My Orders",
            subtitle = "Real-time dispatch & delivery status",
            trailingAction = {
                ExcelExportHeaderChip(
                    enabled = filteredOrders.isNotEmpty(),
                    testTagName = "buyer_orders_export_excel_button",
                    onClick = { showExcelExportDialog = true }
                )
            }
        )

        if (allOrders.isNotEmpty()) {
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
            FilteredExcelExportStrip(
                summaryText = "Showing ${filteredOrders.size} of ${allOrders.size} orders • Total ${AutoTradeRepository.formatInr(filteredOrders.sumOf { it.totalAmount })}",
                hasRows = filteredOrders.isNotEmpty(),
                testTagName = "buyer_orders_export_strip_button",
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
                            title = if (allOrders.isEmpty()) "No orders placed yet" else "No matching orders",
                            subtitle = if (allOrders.isEmpty())
                                "Orders you place from Cart appear here with live status updates from the wholesaler."
                            else "Try selecting a different order status filter above."
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredOrders, key = { it.id }) { order ->
                            BuyerOrderCard(
                                order = order,
                                onResendWhatsApp = {
                                    val msg = AutoTradeRepository.buildWhatsAppOrderSummary(
                                        shopName = order.shopName,
                                        phone = order.phone,
                                        city = order.city,
                                        items = order.items,
                                        totalAmount = order.totalAmount,
                                        orderId = order.id
                                    )
                                    launchWhatsAppOrder(context, storeSettings.whatsappPhone, msg)
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
            title = "Export My Orders to Excel",
            filterSummary = filterSummary,
            sheetsIncluded = listOf("Orders Summary", "Order Line Items"),
            generateFile = {
                ExcelReportExporter.exportOrdersToXlsx(
                    context = context,
                    orders = filteredOrders,
                    invoices = invoices,
                    businessName = storeSettings.businessName.ifBlank { "AutoTrade Wholesale" },
                    reportTitle = "My Orders Report",
                    filterLabel = filterStatus?.label ?: "All",
                    filterSummary = filterSummary
                )
            },
            onNotify = onNotify,
            onDismiss = { showExcelExportDialog = false }
        )
    }
}

@Composable
private fun BuyerOrderCard(
    order: Order,
    onResendWhatsApp: () -> Unit
) {
    val status = order.orderStatus()
    val (bg, fg) = orderStatusColors(status)
    val steps = OrderStatus.entries
    val currentIdx = steps.indexOf(status)

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Order #${order.id.takeLast(6).uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900
                    )
                    Text(
                        text = formatTimestamp(order.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                }
                StatusPill(status.label, bg, fg)
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-step live delivery tracker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                steps.forEachIndexed { idx, step ->
                    val reached = idx <= currentIdx
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (reached) Emerald600 else Slate200),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reached) PureWhite else Slate600
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = step.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (reached) Slate900 else Slate400,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Slate100)
            Spacer(modifier = Modifier.height(8.dp))

            order.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${item.name} × ${item.qty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate800,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = AutoTradeRepository.formatInr(item.lineTotal()),
                        style = MaterialTheme.typography.labelMedium,
                        color = Slate900
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PriceText(AutoTradeRepository.formatInr(order.totalAmount), fontSizeSp = 18, color = MaterialTheme.colorScheme.primary)
                OutlinedButton(
                    onClick = onResendWhatsApp,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send on WhatsApp", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun ShopkeeperBillsTab(
    invoicesState: UiState<List<Invoice>>,
    businessName: String = "AutoTrade Wholesale",
    pendingInvoiceIds: Set<String> = emptySet(),
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var detailInvoice by remember { mutableStateOf<Invoice?>(null) }
    var statusFilter by remember { mutableStateOf<PaymentStatus?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showExcelExportDialog by remember { mutableStateOf(false) }

    val invoices = (invoicesState as? UiState.Success)?.data.orEmpty()
    val filteredInvoices = remember(invoices, statusFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        invoices.filter { inv ->
            val matchStatus = statusFilter == null || inv.paymentStatusEnum() == statusFilter
            val matchQuery = q.isEmpty() ||
                inv.invoiceNumber.lowercase().contains(q) ||
                inv.billType.lowercase().contains(q)
            matchStatus && matchQuery
        }
    }
    val totalPending = remember(invoices) { invoices.sumOf { it.balanceDue } }
    val activeFilterLabel = remember(statusFilter, searchQuery) {
        buildString {
            append(statusFilter?.label ?: "All")
            val q = searchQuery.trim()
            if (q.isNotEmpty()) append("_$q")
        }
    }
    val activeFilterSummary = remember(statusFilter, searchQuery, filteredInvoices) {
        val parts = mutableListOf<String>()
        parts.add("Status: ${statusFilter?.label ?: "All"}")
        val q = searchQuery.trim()
        if (q.isNotEmpty()) parts.add("Search: \"$q\"")
        val totalVal = filteredInvoices.sumOf { it.grandTotal }
        parts.add("${filteredInvoices.size} ${if (filteredInvoices.size == 1) "bill" else "bills"} (${AutoTradeRepository.formatInr(totalVal)})")
        parts.joinToString(" • ")
    }

    fun openOrPrintBill(invoice: Invoice, printDirectly: Boolean) {
        scope.launch {
            PdfInvoiceGenerator.generateInvoicePdf(context, invoice)
                .onSuccess { file ->
                    if (printDirectly) {
                        PdfInvoiceGenerator.printPdfFile(context, file, "Bill_${invoice.invoiceNumber}")
                    } else {
                        PdfInvoiceGenerator.openPdfFile(context, file)
                    }
                }
                .onFailure { err ->
                    onNotify("PDF error: ${err.localizedMessage ?: "failed"}")
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = "My Bills & Invoices",
            subtitle = "Live GST Tax Invoices & Pending Balance",
            trailingAction = {
                ExcelExportHeaderChip(
                    enabled = filteredInvoices.isNotEmpty(),
                    testTagName = "buyer_bills_export_excel_button",
                    onClick = { showExcelExportDialog = true }
                )
            },
            bottomContent = {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Total Pending Balance (Udhaar)",
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate400
                            )
                            Text(
                                text = "${invoices.size} total bills",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400
                            )
                        }
                        PriceText(
                            formattedPrice = AutoTradeRepository.formatInr(totalPending),
                            color = if (totalPending > 0.0) Danger600 else Emerald600,
                            fontSizeSp = 21
                        )
                    }
                }
                if (invoices.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search invoice number or bill type…", color = Slate400) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Slate400) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear bill search", tint = Slate400)
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
                            .testTag("buyer_bills_search_input")
                    )
                }
            }
        )

        if (invoices.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = statusFilter == null,
                    onClick = { statusFilter = null },
                    label = { Text("All (${invoices.size})") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = PureWhite)
                )
                PaymentStatus.entries.forEach { st ->
                    val count = invoices.count { it.paymentStatusEnum() == st }
                    FilterChip(
                        selected = statusFilter == st,
                        onClick = { statusFilter = st },
                        label = { Text("${st.label} ($count)") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = PureWhite)
                    )
                }
            }
            HorizontalDivider(color = Slate200)
            FilteredExcelExportStrip(
                summaryText = "Showing ${filteredInvoices.size} of ${invoices.size} bills • Due ${AutoTradeRepository.formatInr(filteredInvoices.sumOf { it.balanceDue })}",
                hasRows = filteredInvoices.isNotEmpty(),
                testTagName = "buyer_bills_export_strip_button",
                onExportClick = { showExcelExportDialog = true }
            )
            HorizontalDivider(color = Slate200)
        }

        when (invoicesState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(Icons.Filled.Receipt, "Could not load your bills", invoicesState.message)
            }
            is UiState.Success -> {
                if (filteredInvoices.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        EmptyStateCard(
                            icon = Icons.Filled.Receipt,
                            title = if (invoices.isEmpty()) "No bills issued yet" else "No matching bills",
                            subtitle = if (invoices.isEmpty())
                                "GST Tax Invoices and Cash Memos generated by the wholesaler for your shop appear live here."
                            else "Try clearing your status filter or search query."
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredInvoices, key = { it.id }) { inv ->
                            val status = inv.paymentStatusEnum()
                            val (bg, fg) = paymentStatusColors(status)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = PureWhite,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Slate200, RoundedCornerShape(16.dp))
                                    .clickable { detailInvoice = inv }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = inv.invoiceNumber,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Slate900
                                            )
                                            Text(
                                                text = "${inv.billType} • ${formatTimestamp(inv.createdAt)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Slate600
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            StatusPill(status.label, bg, fg)
                                            if (inv.id in pendingInvoiceIds) {
                                                StatusPill("Pending Sync", Amber100, Amber600)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = Slate100)
                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Bill Total", style = MaterialTheme.typography.labelSmall, color = Slate600)
                                            PriceText(AutoTradeRepository.formatInr(inv.grandTotal), fontSizeSp = 17, color = Slate900)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Balance Due", style = MaterialTheme.typography.labelSmall, color = Slate600)
                                            PriceText(
                                                AutoTradeRepository.formatInr(inv.balanceDue),
                                                fontSizeSp = 16,
                                                color = if (inv.balanceDue > 0.0) Danger600 else Emerald600
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { openOrPrintBill(inv, printDirectly = false) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .testTag("buyer_download_pdf_${inv.id}")
                                        ) {
                                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Download PDF", style = MaterialTheme.typography.labelMedium)
                                        }
                                        OutlinedButton(
                                            onClick = { openOrPrintBill(inv, printDirectly = true) },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(0.8f)
                                                .height(46.dp)
                                                .testTag("buyer_print_pdf_${inv.id}")
                                        ) {
                                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Print", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    detailInvoice?.let { inv ->
        val liveInv = invoices.firstOrNull { it.id == inv.id } ?: inv
        InvoiceDetailDialog(
            invoice = liveInv,
            isAdmin = false,
            onOpenPdf = { openOrPrintBill(liveInv, printDirectly = false) },
            onPrintPdf = { openOrPrintBill(liveInv, printDirectly = true) },
            onSharePdf = {
                scope.launch {
                    PdfInvoiceGenerator.generateInvoicePdf(context, liveInv)
                        .onSuccess { file -> PdfInvoiceGenerator.sharePdfFile(context, file, "Invoice ${liveInv.invoiceNumber}") }
                        .onFailure { onNotify("Failed to share PDF") }
                }
            },
            onDismiss = { detailInvoice = null }
        )
    }

    if (showExcelExportDialog) {
        ExcelExportActionDialog(
            title = "Export My Bills to Excel",
            filterSummary = activeFilterSummary,
            sheetsIncluded = listOf("Invoices Summary", "Line Items", "Payment History"),
            generateFile = {
                ExcelReportExporter.exportInvoicesToXlsx(
                    context = context,
                    invoices = filteredInvoices,
                    businessName = businessName,
                    reportTitle = "My Bills & Invoices Report",
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
fun ShopkeeperProfileTab(
    profile: UserProfile,
    isAdminPreview: Boolean,
    syncStatus: SyncStatusSummary,
    cachedProductsCount: Int,
    pendingInvoicesCount: Int,
    isBusy: Boolean,
    hasNotificationPermission: Boolean,
    onSyncNow: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onUpdateNotificationPreferences: (notifyOrderUpdates: Boolean, notifyNewProducts: Boolean) -> Unit,
    onSaveProfile: (name: String, shopName: String, phone: String, city: String, gstin: String) -> Unit,
    onBackToAdmin: () -> Unit,
    onSignOut: () -> Unit
) {
    var name by remember(profile) { mutableStateOf(profile.name) }
    var shopName by remember(profile) { mutableStateOf(profile.shopName) }
    var phone by remember(profile) { mutableStateOf(profile.phone) }
    var city by remember(profile) { mutableStateOf(profile.city) }
    var gstin by remember(profile) { mutableStateOf(profile.gstin) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
    ) {
        AutomotiveHeader(
            title = profile.shopName.ifBlank { "Shop Profile" },
            subtitle = "Approved Wholesale Buyer • ${profile.city}"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isAdminPreview) {
                Button(
                    onClick = onBackToAdmin,
                    colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Switch Back to Wholesaler Admin Panel")
                }
            }

            // FCM Push Notification Preferences Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Push Notifications (FCM)", style = MaterialTheme.typography.titleMedium, color = Slate900)
                        }
                        StatusPill(
                            text = if (hasNotificationPermission && profile.fcmToken.isNotBlank()) "ACTIVE"
                            else if (hasNotificationPermission) "READY"
                            else "OFF",
                            containerColor = if (hasNotificationPermission) Emerald100 else Danger100,
                            contentColor = if (hasNotificationPermission) Emerald600 else Danger600
                        )
                    }

                    if (!hasNotificationPermission) {
                        Button(
                            onClick = onRequestNotificationPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("enable_push_notifications_button")
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enable System Push Alerts")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Order Status Updates", style = MaterialTheme.typography.titleSmall, color = Slate900)
                            Text(
                                "Instant alerts when orders are Confirmed, Dispatched, or Delivered",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = profile.notifyOrderUpdates,
                            onCheckedChange = { checked ->
                                onUpdateNotificationPreferences(checked, profile.notifyNewProducts)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("toggle_order_notifications")
                        )
                    }

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("New Wholesale Products", style = MaterialTheme.typography.titleSmall, color = Slate900)
                            Text(
                                "Instant alerts when new car accessories are added to the catalog",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = profile.notifyNewProducts,
                            onCheckedChange = { checked ->
                                onUpdateNotificationPreferences(profile.notifyOrderUpdates, checked)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("toggle_product_notifications")
                        )
                    }
                }
            }

            // Firestore Offline Persistence & Auto-Sync Status Card
            OfflinePersistenceStatusCard(
                syncStatus = syncStatus,
                cachedProductsCount = cachedProductsCount,
                pendingInvoicesCount = pendingInvoicesCount,
                onSyncNow = onSyncNow
            )

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shop Information", style = MaterialTheme.typography.titleMedium, color = Slate900)
                        StatusPill("APPROVED", Emerald100, Emerald600)
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Owner Name") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Shop Name") },
                        leadingIcon = { Icon(Icons.Filled.Storefront, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter { c -> c.isDigit() }.take(15) },
                        label = { Text("Mobile Number") },
                        leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City") },
                        leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gstin,
                        onValueChange = { gstin = it.uppercase(Locale.US).take(20) },
                        label = { Text("Shop GSTIN (Optional for GST Bills)") },
                        placeholder = { Text("e.g. 07ABCDE1234F1Z5") },
                        leadingIcon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("buyer_gstin_input")
                    )
                    Button(
                        onClick = { onSaveProfile(name, shopName, phone, city, gstin) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_buyer_profile_button")
                    ) {
                        Text("Save Profile Changes")
                    }
                }
            }

            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger600),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("buyer_sign_out_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}
