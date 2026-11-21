package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material.icons.filled.ElectricalServices
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Category
import com.example.data.DEFAULT_CATEGORIES
import com.example.data.LOW_STOCK_FILTER
import com.example.data.NEW_ARRIVALS_FILTER
import com.example.data.Product
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.Danger100
import com.example.ui.theme.Danger600
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

/**
 * Merges Firestore categories, live product categories, and built-in automotive accessory types
 * into a deduplicated, stable list so users can always filter across automotive accessory types.
 */
fun buildCatalogCategoryList(
    categories: List<Category>,
    products: List<Product>
): List<String> {
    val seenLower = LinkedHashSet<String>()
    val result = mutableListOf<String>()

    fun addIfNew(raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        val key = trimmed.lowercase()
        if (key == "all" || key == NEW_ARRIVALS_FILTER.lowercase() || key == LOW_STOCK_FILTER.lowercase()) return
        if (seenLower.add(key)) {
            result.add(trimmed)
        }
    }

    categories.forEach { addIfNew(it.name) }
    DEFAULT_CATEGORIES.forEach { (_, name) -> addIfNew(name) }
    products.forEach { addIfNew(it.category) }

    return result
}

/**
 * Maps automotive accessory category names to intuitive Material icons for rapid visual scanning.
 */
fun resolveAccessoryCategoryIcon(categoryName: String): ImageVector {
    val lower = categoryName.trim().lowercase()
    return when {
        lower == "all" -> Icons.Filled.Apps
        lower == NEW_ARRIVALS_FILTER.lowercase() -> Icons.Filled.NewReleases
        lower == LOW_STOCK_FILTER.lowercase() -> Icons.Filled.WarningAmber
        lower.contains("seat") || lower.contains("cushion") || lower.contains("neck") -> Icons.Filled.EventSeat
        lower.contains("steer") || lower.contains("wheel") -> Icons.Filled.DonutLarge
        lower.contains("mat") || lower.contains("floor") || lower.contains("carpet") -> Icons.Filled.Layers
        lower.contains("perfume") || lower.contains("freshen") || lower.contains("scent") || lower.contains("air") -> Icons.Filled.Air
        lower.contains("light") || lower.contains("led") || lower.contains("lamp") || lower.contains("fog") || lower.contains("bulb") -> Icons.Filled.Lightbulb
        lower.contains("exterior") || lower.contains("chrome") || lower.contains("body") || lower.contains("bumper") || lower.contains("visor") || lower.contains("cover") -> Icons.Filled.DirectionsCar
        lower.contains("audio") || lower.contains("speaker") || lower.contains("woofer") || lower.contains("stereo") || lower.contains("android") -> Icons.Filled.Speaker
        lower.contains("cam") || lower.contains("dash") || lower.contains("sensor") -> Icons.Filled.Videocam
        lower.contains("charg") || lower.contains("electric") || lower.contains("horn") || lower.contains("wire") || lower.contains("cable") -> Icons.Filled.ElectricalServices
        else -> Icons.Filled.Build
    }
}

private data class FilterBarItem(
    val id: String,
    val label: String,
    val count: Int,
    val icon: ImageVector,
    val isLowStock: Boolean = false
)

/**
 * Dedicated horizontal automotive category filter bar positioned directly above the product catalog.
 * Features automotive accessory icons, live per-category SKU counts, smooth auto-scroll to active
 * selection, an optional pinned "New Arrivals" quick-filter, scroll-cue edge fade, and an active
 * filter summary strip with one-tap reset.
 */
@Composable
fun AutomotiveCategoryFilterBar(
    categories: List<Category>,
    products: List<Product>,
    selectedCategory: String,
    filteredCount: Int,
    onSelectCategory: (String) -> Unit,
    showPinnedNewArrivals: Boolean,
    newArrivalsCount: Int,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
    showLowStockChip: Boolean = false,
    lowStockCount: Int = 0,
    isSearchActive: Boolean = false
) {
    val mergedCategories = remember(categories, products) {
        buildCatalogCategoryList(categories, products)
    }
    val countsByCategoryLower = remember(products) {
        products.groupingBy { it.category.trim().lowercase() }.eachCount()
    }

    val isLowStockSelected = selectedCategory == LOW_STOCK_FILTER
    val filterItems = remember(mergedCategories, products.size, countsByCategoryLower, showLowStockChip, lowStockCount, isLowStockSelected) {
        buildList {
            add(
                FilterBarItem(
                    id = "All",
                    label = "All",
                    count = products.size,
                    icon = Icons.Filled.Apps
                )
            )
            if (showLowStockChip && (lowStockCount > 0 || isLowStockSelected)) {
                add(
                    FilterBarItem(
                        id = LOW_STOCK_FILTER,
                        label = "Low Stock",
                        count = lowStockCount,
                        icon = Icons.Filled.WarningAmber,
                        isLowStock = true
                    )
                )
            }
            mergedCategories.forEach { catName ->
                add(
                    FilterBarItem(
                        id = catName,
                        label = catName,
                        count = countsByCategoryLower[catName.lowercase()] ?: 0,
                        icon = resolveAccessoryCategoryIcon(catName)
                    )
                )
            }
        }
    }

    val listState = rememberLazyListState()
    val canScrollForward by remember {
        derivedStateOf { listState.canScrollForward }
    }

    // Automatically bring the active category chip into view when selected from search or elsewhere
    LaunchedEffect(selectedCategory, filterItems) {
        val targetIndex = filterItems.indexOfFirst { it.id.equals(selectedCategory, ignoreCase = true) }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(index = (targetIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PureWhite)
            .testTag("${testTagPrefix}_category_filter_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showPinnedNewArrivals) {
                val isNewSelected = selectedCategory == NEW_ARRIVALS_FILTER
                PinnedNewArrivalsChip(
                    count = newArrivalsCount,
                    selected = isNewSelected,
                    onClick = {
                        onSelectCategory(if (isNewSelected) "All" else NEW_ARRIVALS_FILTER)
                    },
                    modifier = Modifier
                        .padding(start = 14.dp, end = 8.dp)
                        .testTag("${testTagPrefix}_filter_new_arrivals")
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(Slate200)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = if (showPinnedNewArrivals) 10.dp else 14.dp,
                        end = 24.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(
                        items = filterItems,
                        key = { _, item -> item.id }
                    ) { _, item ->
                        val isSelected = selectedCategory.equals(item.id, ignoreCase = true)
                        AccessoryCategoryPill(
                            item = item,
                            selected = isSelected,
                            onClick = {
                                if (item.isLowStock && isSelected) {
                                    onSelectCategory("All")
                                } else {
                                    onSelectCategory(item.id)
                                }
                            },
                            modifier = Modifier.testTag(
                                if (item.isLowStock) "${testTagPrefix}_filter_low_stock"
                                else "${testTagPrefix}_category_chip_${item.id.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                            )
                        )
                    }
                }

                // Trailing gradient hint when additional accessory categories are available to scroll
                if (canScrollForward) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(24.dp)
                            .height(44.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, PureWhite)
                                )
                            )
                    )
                }
            }
        }

        // Contextual active-category banner with live filtered count and one-tap reset
        AnimatedVisibility(
            visible = selectedCategory != "All" && !isSearchActive,
            enter = expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(180)),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
        ) {
            val isNewFilter = selectedCategory == NEW_ARRIVALS_FILTER
            val isLowFilter = selectedCategory == LOW_STOCK_FILTER
            val stripBg = when {
                isNewFilter -> Danger100.copy(alpha = 0.55f)
                isLowFilter -> Amber50
                else -> Slate50
            }
            val iconTint = when {
                isNewFilter -> Danger600
                isLowFilter -> Amber600
                else -> MaterialTheme.colorScheme.primary
            }

            Column {
                HorizontalDivider(color = Slate100)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(stripBg)
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = resolveAccessoryCategoryIcon(selectedCategory),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = selectedCategory,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Slate900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = PureWhite,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.border(1.dp, Slate200, RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = "$filteredCount ${if (filteredCount == 1) "item" else "items"}",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = PureWhite,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) { onSelectCategory("All") }
                            .testTag("${testTagPrefix}_clear_category_filter")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Reset category filter",
                                tint = Slate600,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "All Types",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate800
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = Slate200)
    }
}

@Composable
private fun AccessoryCategoryPill(
    item: FilterBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brandPrimary = MaterialTheme.colorScheme.primary
    val hasItems = item.count > 0 || item.id == "All" || item.isLowStock

    val targetContainer = when {
        item.isLowStock && selected -> Amber600
        item.isLowStock -> Amber50
        selected -> Slate900
        hasItems -> Slate50
        else -> PureWhite
    }
    val targetBorder = when {
        item.isLowStock && selected -> Amber600
        item.isLowStock -> Amber600.copy(alpha = 0.45f)
        selected -> brandPrimary
        hasItems -> Slate200
        else -> Slate200.copy(alpha = 0.7f)
    }
    val targetContent = when {
        item.isLowStock && selected -> PureWhite
        item.isLowStock -> Amber600
        selected -> PureWhite
        hasItems -> Slate800
        else -> Slate400
    }
    val targetIconTint = when {
        item.isLowStock && selected -> PureWhite
        item.isLowStock -> Amber600
        selected -> brandPrimary
        hasItems -> Slate600
        else -> Slate400
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(160),
        label = "cat_pill_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(160),
        label = "cat_pill_border"
    )
    val contentColor by animateColorAsState(
        targetValue = targetContent,
        animationSpec = tween(160),
        label = "cat_pill_text"
    )
    val iconTint by animateColorAsState(
        targetValue = targetIconTint,
        animationSpec = tween(160),
        label = "cat_pill_icon"
    )

    // 48.dp minimum touch target height wrapping a sleek 38.dp automotive pill
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .semantics { this.selected = selected }
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(11.dp),
            shadowElevation = if (selected) 2.dp else 0.dp,
            modifier = Modifier
                .height(38.dp)
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(11.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                item.isLowStock && selected -> PureWhite.copy(alpha = 0.2f)
                                item.isLowStock -> Amber100
                                selected -> brandPrimary.copy(alpha = 0.22f)
                                hasItems -> Slate100
                                else -> Slate50
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1
                )

                if (item.count > 0) {
                    Surface(
                        color = when {
                            item.isLowStock && selected -> PureWhite.copy(alpha = 0.24f)
                            item.isLowStock -> Amber100
                            selected -> brandPrimary
                            else -> Slate200.copy(alpha = 0.75f)
                        },
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = item.count.toString(),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                item.isLowStock && selected -> PureWhite
                                item.isLowStock -> Amber600
                                selected -> MaterialTheme.colorScheme.onPrimary
                                else -> Slate800
                            },
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
