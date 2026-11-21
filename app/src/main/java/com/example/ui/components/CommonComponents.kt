package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.AccountStatus
import com.example.data.ImageCompressor
import com.example.data.OrderStatus
import com.example.data.PaymentStatus
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber600
import com.example.ui.theme.Cobalt100
import com.example.ui.theme.Cobalt600
import com.example.ui.theme.Danger100
import com.example.ui.theme.Danger600
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AutomotiveHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingAction: @Composable (() -> Unit)? = null,
    bottomContent: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Slate950, Slate900)
                )
            )
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
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailingAction != null) {
                Spacer(modifier = Modifier.width(8.dp))
                trailingAction()
            }
        }
        if (bottomContent != null) {
            Spacer(modifier = Modifier.height(12.dp))
            bottomContent()
        }
    }
}

@Composable
fun ProductPhoto(
    imageSource: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (imageSource.isNullOrBlank()) {
        Box(
            modifier = modifier.background(Slate100),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.ImageNotSupported,
                contentDescription = contentDescription,
                tint = Slate400,
                modifier = Modifier.size(32.dp)
            )
        }
    } else if (imageSource.startsWith("data:image")) {
        val cachedBitmap = remember(imageSource) { ImageCompressor.getCachedImageBitmap(imageSource) }
        val imageBitmap by produceState<ImageBitmap?>(initialValue = cachedBitmap, key1 = imageSource) {
            if (value == null) {
                value = ImageCompressor.decodeDataUriToImageBitmapCached(imageSource)
            }
        }
        val readyBitmap = imageBitmap
        if (readyBitmap != null) {
            Image(
                bitmap = readyBitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale
            )
        } else {
            Box(
                modifier = modifier.background(Slate100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ImageNotSupported,
                    contentDescription = contentDescription,
                    tint = Slate400,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    } else {
        AsyncImage(
            model = imageSource,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

/**
 * Product photo carousel that automatically cycles through multiple photos and also allows
 * smooth manual left/right swiping. Auto-scroll pauses while the user is dragging and resumes
 * cleanly from the manually selected photo.
 */
@Composable
fun AutoScrollingProductPhotoSlider(
    imageUrls: List<String>,
    contentDescription: String,
    modifier: Modifier = Modifier,
    autoScrollIntervalMs: Long = 3200L,
    userScrollEnabled: Boolean = true,
    showDotIndicators: Boolean = true,
    onPhotoClick: ((pageIndex: Int) -> Unit)? = null
) {
    if (imageUrls.size <= 1) {
        val clickMod = if (onPhotoClick != null && imageUrls.isNotEmpty()) {
            modifier.clickable { onPhotoClick(0) }
        } else {
            modifier
        }
        ProductPhoto(
            imageSource = imageUrls.firstOrNull(),
            contentDescription = contentDescription,
            modifier = clickMod
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { imageUrls.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(imageUrls.size, isDragged, autoScrollIntervalMs) {
        if (isDragged) return@LaunchedEffect
        while (true) {
            delay(autoScrollIntervalMs)
            if (!pagerState.isScrollInProgress && imageUrls.size > 1) {
                val nextPage = (pagerState.currentPage + 1) % imageUrls.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Box(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = userScrollEnabled,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageMod = if (onPhotoClick != null) {
                Modifier
                    .fillMaxSize()
                    .clickable { onPhotoClick(page) }
            } else {
                Modifier.fillMaxSize()
            }
            ProductPhoto(
                imageSource = imageUrls[page],
                contentDescription = "$contentDescription photo ${page + 1}",
                modifier = pageMod
            )
        }

        if (showDotIndicators) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Slate900.copy(alpha = 0.62f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(imageUrls.size) { index ->
                    val active = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (active) 16.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (active) PureWhite else PureWhite.copy(alpha = 0.45f))
                    )
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1
        )
    }
}

@Composable
fun PinnedNewArrivalsChip(
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeRed = Color(0xFFB91C1C)
    FilterChip(
        selected = selected,
        onClick = onClick,
        leadingIcon = {
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.NewReleases,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        label = {
            Text(
                text = "New Arrivals ($count)",
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        },
        shape = RoundedCornerShape(10.dp),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Danger600,
            selectedBorderColor = Slate900,
            borderWidth = 1.dp,
            selectedBorderWidth = 2.dp
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Danger600,
            labelColor = PureWhite,
            iconColor = PureWhite,
            selectedContainerColor = activeRed,
            selectedLabelColor = PureWhite,
            selectedLeadingIconColor = PureWhite
        ),
        modifier = modifier
    )
}

/**
 * Shared Global Catalog Search Bar used across both Admin and Buyer panels to filter products
 * instantaneously by product name, category, car model, or HSN code.
 */
@Composable
fun GlobalCatalogSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    testTagName: String,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                color = Slate400,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search catalog",
                tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary else Slate400
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                    },
                    modifier = Modifier.testTag("${testTagName}_clear")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = PureWhite
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Slate800,
            unfocusedContainerColor = Slate800,
            focusedTextColor = PureWhite,
            unfocusedTextColor = PureWhite,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Slate700
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagName)
    )
}

/**
 * Contextual search results strip shown when a global search query is active, displaying live
 * match count, one-tap "All Categories" expansion if narrowed, matching category quick-jumps,
 * and a clear button.
 */
@Composable
fun GlobalSearchStatusBanner(
    searchQuery: String,
    selectedCategory: String,
    resultCount: Int,
    totalCount: Int,
    matchingCategories: List<String>,
    onSelectCategory: (String) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trimmed = searchQuery.trim()
    if (trimmed.isEmpty()) return

    Surface(
        color = Slate100,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val scopeLabel = if (selectedCategory == "All") "all categories" else selectedCategory
                Text(
                    text = "$resultCount of $totalCount ${if (totalCount == 1) "item" else "items"} in $scopeLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = Slate800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedCategory != "All") {
                        Text(
                            text = "Search All",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Cobalt600,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onSelectCategory("All") }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "Clear",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClearSearch)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("global_search_banner_clear")
                    )
                }
            }

            if (matchingCategories.isNotEmpty() && selectedCategory == "All") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Category,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(14.dp)
                    )
                    matchingCategories.take(3).forEach { catName ->
                        Surface(
                            color = PureWhite,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .border(1.dp, Slate200, RoundedCornerShape(8.dp))
                                .clickable {
                                    onSelectCategory(catName)
                                    onClearSearch()
                                }
                        ) {
                            Text(
                                text = catName,
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate900,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun orderStatusColors(status: OrderStatus): Pair<Color, Color> = when (status) {
    OrderStatus.PENDING -> Amber100 to Amber600
    OrderStatus.CONFIRMED -> Cobalt100 to Cobalt600
    OrderStatus.DISPATCHED -> Color(0xFFEDE9FE) to Color(0xFF6D28D9)
    OrderStatus.DELIVERED -> Emerald100 to Emerald600
}

fun accountStatusColors(status: AccountStatus): Pair<Color, Color> = when (status) {
    AccountStatus.PENDING -> Amber100 to Amber600
    AccountStatus.APPROVED -> Emerald100 to Emerald600
    AccountStatus.BLOCKED -> Danger100 to Danger600
}

fun paymentStatusColors(status: PaymentStatus): Pair<Color, Color> = when (status) {
    PaymentStatus.PAID -> Emerald100 to Emerald600
    PaymentStatus.PARTIAL -> Amber100 to Amber600
    PaymentStatus.UNPAID -> Danger100 to Danger600
}

@Composable
fun PriceText(
    formattedPrice: String,
    modifier: Modifier = Modifier,
    color: Color = Slate900,
    fontSizeSp: Int = 18
) {
    Text(
        text = formattedPrice,
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = fontSizeSp.sp,
        color = color,
        modifier = modifier
    )
}

fun formatTimestamp(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date)
}

fun isRecentArrival(timestamp: Timestamp?): Boolean {
    if (timestamp == null) return true
    val ageMs = System.currentTimeMillis() - timestamp.toDate().time
    return ageMs <= 7L * 24L * 60L * 60L * 1000L // Within last 7 days
}

fun launchWhatsAppOrder(context: Context, phone: String, message: String) {
    val cleanDigits = phone.filter { it.isDigit() }
    val normalizedPhone = when {
        cleanDigits.length == 10 -> "91$cleanDigits"
        cleanDigits.isNotBlank() -> cleanDigits
        else -> ""
    }
    val encoded = Uri.encode(message)
    val url = if (normalizedPhone.isNotBlank()) {
        "https://wa.me/$normalizedPhone?text=$encoded"
    } else {
        "https://wa.me/?text=$encoded"
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        // Fallback to standard share sheet so user can still send via WhatsApp/SMS
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "Send Order Summary"))
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open WhatsApp or share sheet", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun ProductImageGalleryDialog(
    productName: String,
    imageUrls: List<String>,
    initialPage: Int = 0,
    onDismiss: () -> Unit
) {
    if (imageUrls.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, imageUrls.lastIndex),
        pageCount = { imageUrls.size }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Slate950.copy(alpha = 0.96f))
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offset by remember(page) { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(page) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 3.5f)
                                scale = newScale
                                offset = if (newScale > 1f) {
                                    Offset(
                                        x = (offset.x + pan.x).coerceIn(-400f, 400f),
                                        y = (offset.y + pan.y).coerceIn(-400f, 400f)
                                    )
                                } else {
                                    Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ProductPhoto(
                        imageSource = imageUrls[page],
                        contentDescription = "$productName photo ${page + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    )
                }
            }

            // Top bar with product title and close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.titleMedium,
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Photo ${pagerState.currentPage + 1} of ${imageUrls.size} • Pinch to zoom",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate400
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Slate800)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close photo gallery",
                        tint = PureWhite
                    )
                }
            }

            // Bottom dot indicators
            if (imageUrls.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(imageUrls.size) { index ->
                        val active = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(if (active) 24.dp else 8.dp)
                                .clip(CircleShape)
                                .background(if (active) MaterialTheme.colorScheme.primary else Slate400.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Slate200, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = PureWhite
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Slate100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Slate800,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Slate900
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(16.dp))
                action()
            }
        }
    }
}
