package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AutoTradeRepository
import com.example.data.InventoryHealthSummary
import com.example.data.Product
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.ProductPhoto
import com.example.ui.components.StatusPill
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.Danger100
import com.example.ui.theme.Danger600
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950

private enum class LowStockSeverityFilter(val label: String) {
    ALL("All Alerts"),
    OUT_OF_STOCK("Out of Stock"),
    CRITICAL("Critical"),
    WARNING("Warning")
}

private enum class BulkRestockMode(val title: String, val subtitle: String) {
    FILL_TO_THRESHOLD(
        title = "Fill to Alert Threshold",
        subtitle = "Sets each item to the exact minimum threshold to clear alerts"
    ),
    FILL_TO_SAFE_BUFFER(
        title = "Fill to Safe Buffer (2×)",
        subtitle = "Sets each item to double the threshold for comfortable stock coverage"
    ),
    ADD_PACK_25(
        title = "Add +25 pcs to Each",
        subtitle = "Adds a standard wholesale carton (+25 pcs) to every listed item"
    )
}

/**
 * Dedicated full-screen Admin dashboard for monitoring products whose inventory quantity
 * has fallen below the Business Settings low-stock threshold, with instant single-item
 * and batch restocking quick actions.
 */
@Composable
fun AdminLowStockDashboardScreen(
    inventoryHealth: InventoryHealthSummary,
    isBusy: Boolean,
    onQuickRestock: (Product, Long) -> Unit,
    onBulkRestock: (products: List<Product>, targetQuantities: Map<String, Long>, modeDescription: String) -> Unit,
    onEditProduct: (Product) -> Unit,
    onOpenThresholdSettings: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    var searchQuery by remember { mutableStateOf("") }
    var severityFilter by remember { mutableStateOf(LowStockSeverityFilter.ALL) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var customStockTarget by remember { mutableStateOf<Product?>(null) }
    var showBulkRestockDialog by remember { mutableStateOf(false) }

    val threshold = inventoryHealth.threshold.coerceAtLeast(1L)
    val criticalCutoff = (threshold / 2L).coerceAtLeast(1L)
    val allLowStock = inventoryHealth.lowStockProducts

    val outOfStockItems = remember(allLowStock) {
        allLowStock.filter { (it.trackedStockCount() ?: 0L) <= 0L }
    }
    val criticalItems = remember(allLowStock, criticalCutoff) {
        allLowStock.filter {
            val qty = it.trackedStockCount() ?: 0L
            qty in 1L..criticalCutoff
        }
    }
    val warningItems = remember(allLowStock, criticalCutoff) {
        allLowStock.filter {
            val qty = it.trackedStockCount() ?: 0L
            qty > criticalCutoff
        }
    }
    val affectedCategories = remember(allLowStock) {
        allLowStock.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val totalUnitsDeficit = remember(allLowStock, threshold) {
        allLowStock.sumOf { prod ->
            val qty = prod.trackedStockCount() ?: 0L
            (threshold - qty).coerceAtLeast(0L)
        }
    }
    val estimatedRestockValue = remember(allLowStock, threshold) {
        allLowStock.sumOf { prod ->
            val qty = prod.trackedStockCount() ?: 0L
            val needed = (threshold - qty).coerceAtLeast(0L)
            needed * prod.price
        }
    }

    val filteredItems = remember(allLowStock, searchQuery, severityFilter, selectedCategory, criticalCutoff) {
        val q = searchQuery.trim().lowercase()
        allLowStock.filter { prod ->
            val qty = prod.trackedStockCount() ?: 0L
            val matchesSeverity = when (severityFilter) {
                LowStockSeverityFilter.ALL -> true
                LowStockSeverityFilter.OUT_OF_STOCK -> qty <= 0L
                LowStockSeverityFilter.CRITICAL -> qty in 1L..criticalCutoff
                LowStockSeverityFilter.WARNING -> qty > criticalCutoff
            }
            if (!matchesSeverity) return@filter false
            if (selectedCategory != null && !prod.category.equals(selectedCategory, ignoreCase = true)) {
                return@filter false
            }
            if (q.isEmpty()) return@filter true
            prod.name.lowercase().contains(q) ||
                prod.category.lowercase().contains(q) ||
                prod.carModel.lowercase().contains(q) ||
                prod.hsnCode.lowercase().contains(q)
        }
    }

    val recommendedTarget = remember(threshold) {
        maxOf(threshold * 2L, threshold + 10L)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .navigationBarsPadding()
    ) {
        // Dark automotive command header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Slate950, Slate900)))
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("low_stock_dashboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Inventory",
                            tint = PureWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Low Stock Alerts",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Threshold: < $threshold pcs • ${inventoryHealth.trackedProductsCount}/${inventoryHealth.totalProducts} tracked",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .border(1.dp, Slate700, RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenThresholdSettings)
                        .testTag("low_stock_dashboard_threshold_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Edit low stock threshold",
                            tint = Color(0xFFFBBF24),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Limit: $threshold",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = PureWhite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4-tile live triage KPI strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LowStockKpiTile(
                    label = "Low Stock",
                    value = allLowStock.size.toString(),
                    accent = if (allLowStock.isNotEmpty()) Color(0xFFFBBF24) else Color(0xFF34D399),
                    modifier = Modifier.weight(1f)
                )
                LowStockKpiTile(
                    label = "Out (0)",
                    value = outOfStockItems.size.toString(),
                    accent = if (outOfStockItems.isNotEmpty()) Color(0xFFF87171) else PureWhite,
                    modifier = Modifier.weight(1f)
                )
                LowStockKpiTile(
                    label = "Need Pcs",
                    value = "+$totalUnitsDeficit",
                    accent = Color(0xFF60A5FA),
                    modifier = Modifier.weight(1f)
                )
                LowStockKpiTile(
                    label = "Est. Cost",
                    value = compactInr(estimatedRestockValue),
                    accent = PureWhite,
                    modifier = Modifier.weight(1f)
                )
            }

            if (allLowStock.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search low-stock products, category, car model…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate400,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search low stock items",
                            tint = Slate400
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = Slate400
                                )
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
                        unfocusedBorderColor = Slate700,
                        cursorColor = PureWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("low_stock_dashboard_search_input")
                )
            }
        }

        if (allLowStock.isNotEmpty()) {
            // Severity & affected category filter bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = severityFilter == LowStockSeverityFilter.ALL && selectedCategory == null,
                    onClick = {
                        severityFilter = LowStockSeverityFilter.ALL
                        selectedCategory = null
                    },
                    label = { Text("All (${allLowStock.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Slate900,
                        selectedLabelColor = PureWhite
                    ),
                    modifier = Modifier.testTag("low_stock_filter_all")
                )
                if (outOfStockItems.isNotEmpty()) {
                    FilterChip(
                        selected = severityFilter == LowStockSeverityFilter.OUT_OF_STOCK,
                        onClick = {
                            severityFilter = if (severityFilter == LowStockSeverityFilter.OUT_OF_STOCK) {
                                LowStockSeverityFilter.ALL
                            } else {
                                LowStockSeverityFilter.OUT_OF_STOCK
                            }
                        },
                        label = { Text("Out of Stock (${outOfStockItems.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Danger100,
                            labelColor = Danger600,
                            selectedContainerColor = Danger600,
                            selectedLabelColor = PureWhite
                        ),
                        modifier = Modifier.testTag("low_stock_filter_oos")
                    )
                }
                if (criticalItems.isNotEmpty()) {
                    FilterChip(
                        selected = severityFilter == LowStockSeverityFilter.CRITICAL,
                        onClick = {
                            severityFilter = if (severityFilter == LowStockSeverityFilter.CRITICAL) {
                                LowStockSeverityFilter.ALL
                            } else {
                                LowStockSeverityFilter.CRITICAL
                            }
                        },
                        label = { Text("Critical ≤$criticalCutoff (${criticalItems.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Amber50,
                            labelColor = Amber600,
                            selectedContainerColor = Amber600,
                            selectedLabelColor = PureWhite
                        ),
                        modifier = Modifier.testTag("low_stock_filter_critical")
                    )
                }
                if (warningItems.isNotEmpty()) {
                    FilterChip(
                        selected = severityFilter == LowStockSeverityFilter.WARNING,
                        onClick = {
                            severityFilter = if (severityFilter == LowStockSeverityFilter.WARNING) {
                                LowStockSeverityFilter.ALL
                            } else {
                                LowStockSeverityFilter.WARNING
                            }
                        },
                        label = { Text("Warning (${warningItems.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Slate900,
                            selectedLabelColor = PureWhite
                        ),
                        modifier = Modifier.testTag("low_stock_filter_warning")
                    )
                }
                if (affectedCategories.size > 1) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(Slate200)
                    )
                    affectedCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }
            HorizontalDivider(color = Slate200)
        }

        // Main dashboard body
        if (allLowStock.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PureWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Emerald600.copy(alpha = 0.28f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Emerald100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "All stock healthy",
                                tint = Emerald600,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                        Text(
                            text = "All Inventory Levels Healthy",
                            style = MaterialTheme.typography.titleLarge,
                            color = Slate900
                        )
                        Text(
                            text = "Every tracked SKU (${inventoryHealth.trackedProductsCount} of ${inventoryHealth.totalProducts} products) currently holds at least $threshold pcs in stock.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate600
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onOpenThresholdSettings,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("healthy_state_threshold_button")
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Adjust Threshold")
                            }
                            Button(
                                onClick = onClose,
                                colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Text("Back to Catalog")
                            }
                        }
                    }
                }
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                EmptyStateCard(
                    icon = Icons.Filled.Inventory2,
                    title = "No matching low-stock items",
                    subtitle = "Try clearing your search or switching back to All Alerts.",
                    action = {
                        OutlinedButton(
                            onClick = {
                                searchQuery = ""
                                severityFilter = LowStockSeverityFilter.ALL
                                selectedCategory = null
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Reset Filters")
                        }
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("low_stock_dashboard_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bulk restock action card
                item(key = "bulk_restock_action_bar") {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Amber50,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Amber600.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${filteredItems.size} ${if (filteredItems.size == 1) "item needs" else "items need"} restocking",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = "Ordered by lowest stock first • Tap any quick button to restock instantly",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate700
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = { showBulkRestockDialog = true },
                                enabled = !isBusy && filteredItems.isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Slate900,
                                    contentColor = PureWhite
                                ),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .height(44.dp)
                                    .testTag("low_stock_bulk_restock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DoneAll,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restock All", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                items(filteredItems, key = { it.id }) { product ->
                    LowStockAlertProductCard(
                        product = product,
                        threshold = threshold,
                        recommendedTarget = recommendedTarget,
                        isBusy = isBusy,
                        onQuickAdd = { delta ->
                            val current = product.trackedStockCount() ?: 0L
                            onQuickRestock(product, (current + delta).coerceAtMost(1_000_000L))
                        },
                        onRestockToTarget = { targetQty ->
                            onQuickRestock(product, targetQty)
                        },
                        onCustomRestock = { customStockTarget = product },
                        onEditProduct = { onEditProduct(product) }
                    )
                }
            }
        }
    }

    customStockTarget?.let { prod ->
        QuickStockUpdateDialog(
            product = prod,
            lowStockThreshold = threshold,
            onSave = { newQty ->
                onQuickRestock(prod, newQty)
                customStockTarget = null
            },
            onDismiss = { customStockTarget = null }
        )
    }

    if (showBulkRestockDialog) {
        BulkRestockConfirmDialog(
            items = filteredItems,
            threshold = threshold,
            safeBufferTarget = recommendedTarget,
            onConfirm = { mode ->
                val updates = filteredItems.associate { prod ->
                    val current = prod.trackedStockCount() ?: 0L
                    val nextQty = when (mode) {
                        BulkRestockMode.FILL_TO_THRESHOLD -> maxOf(current, threshold)
                        BulkRestockMode.FILL_TO_SAFE_BUFFER -> maxOf(current, recommendedTarget)
                        BulkRestockMode.ADD_PACK_25 -> (current + 25L).coerceAtMost(1_000_000L)
                    }
                    prod.id to nextQty
                }
                onBulkRestock(filteredItems, updates, mode.title)
                showBulkRestockDialog = false
            },
            onDismiss = { showBulkRestockDialog = false }
        )
    }
}

@Composable
private fun LowStockKpiTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Slate800,
        modifier = modifier.border(1.dp, Slate700, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LowStockAlertProductCard(
    product: Product,
    threshold: Long,
    recommendedTarget: Long,
    isBusy: Boolean,
    onQuickAdd: (Long) -> Unit,
    onRestockToTarget: (Long) -> Unit,
    onCustomRestock: () -> Unit,
    onEditProduct: () -> Unit
) {
    val currentQty = product.trackedStockCount() ?: 0L
    val isOutOfStock = currentQty <= 0L
    val deficit = (threshold - currentQty).coerceAtLeast(1L)
    val fillFraction by animateFloatAsState(
        targetValue = (currentQty.toFloat() / threshold.toFloat()).coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "stock_fill_progress"
    )
    val accentColor = if (isOutOfStock) Danger600 else Amber600
    val accentBg = if (isOutOfStock) Danger100 else Amber100

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isOutOfStock) Danger600.copy(alpha = 0.45f) else Amber600.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp)
            )
            .testTag("low_stock_card_${product.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top product info row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                ProductPhoto(
                    imageSource = product.imageUrls.firstOrNull(),
                    contentDescription = product.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = if (isOutOfStock) "OUT OF STOCK" else "LOW STOCK",
                            containerColor = accentBg,
                            contentColor = accentColor
                        )
                        IconButton(
                            onClick = onEditProduct,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit ${product.name}",
                                tint = Slate600,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${product.category} • ${product.carModel} • ${AutoTradeRepository.formatInr(product.price)} (MOQ ${product.moq})",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Stock level gauge & deficit indicator
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOutOfStock) Danger100.copy(alpha = 0.45f) else Amber50,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WarningAmber,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "$currentQty / $threshold pcs",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = accentColor
                            )
                        }
                        Text(
                            text = "Need +$deficit pcs to clear alert",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate700
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(PureWhite)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fillFraction.coerceAtLeast(if (isOutOfStock) 0f else 0.06f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor)
                        )
                    }
                }
            }

            // One-tap quick restock actions row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Restock",
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate600
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onQuickAdd(10L) },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("restock_plus_10_${product.id}")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            text = "10",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { onQuickAdd(25L) },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("restock_plus_25_${product.id}")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Text(
                            text = "25",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = { onRestockToTarget(recommendedTarget) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Emerald600,
                            contentColor = PureWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1.35f)
                            .height(48.dp)
                            .testTag("restock_target_${product.id}")
                    ) {
                        Text(
                            text = "Fill ${recommendedTarget}p",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                    OutlinedButton(
                        onClick = onCustomRestock,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier
                            .width(52.dp)
                            .height(48.dp)
                            .testTag("restock_custom_${product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Custom stock quantity for ${product.name}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkRestockConfirmDialog(
    items: List<Product>,
    threshold: Long,
    safeBufferTarget: Long,
    onConfirm: (BulkRestockMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMode by remember { mutableStateOf(BulkRestockMode.FILL_TO_SAFE_BUFFER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restock ${items.size} Low-Stock ${if (items.size == 1) "Item" else "Items"}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose how to update stock across all currently listed low-stock products:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
                BulkRestockMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    val previewLabel = when (mode) {
                        BulkRestockMode.FILL_TO_THRESHOLD -> "Set min $threshold pcs"
                        BulkRestockMode.FILL_TO_SAFE_BUFFER -> "Set min $safeBufferTarget pcs"
                        BulkRestockMode.ADD_PACK_25 -> "+25 pcs each"
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Slate50,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Slate200,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedMode = mode }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Slate900
                                )
                                StatusPill(
                                    text = previewLabel,
                                    containerColor = PureWhite,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.primary else Slate700
                                )
                            }
                            Text(
                                text = mode.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedMode) },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                modifier = Modifier.testTag("confirm_bulk_restock_button")
            ) {
                Text("Restock Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun compactInr(amount: Double): String = when {
    amount >= 10_000_000.0 -> "₹%.1fCr".format(amount / 10_000_000.0)
    amount >= 100_000.0 -> "₹%.1fL".format(amount / 100_000.0)
    amount >= 1_000.0 -> "₹%.1fK".format(amount / 1_000.0)
    else -> AutoTradeRepository.formatInr(amount)
}
