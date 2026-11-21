package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddBox
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ActivityLogAction
import com.example.data.ActivityLogCategory
import com.example.data.AdminActivityLog
import com.example.data.UiState
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.StatusPill
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
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate950
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ActivityVisualStyle(
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)

private fun styleForAction(action: ActivityLogAction): ActivityVisualStyle = when (action) {
    ActivityLogAction.PRODUCT_ADDED -> ActivityVisualStyle(
        icon = Icons.Filled.AddBox,
        containerColor = Emerald100,
        contentColor = Emerald600
    )
    ActivityLogAction.PRICE_CHANGED -> ActivityVisualStyle(
        icon = Icons.Filled.CurrencyRupee,
        containerColor = Amber100,
        contentColor = Amber600
    )
    ActivityLogAction.PRODUCT_UPDATED -> ActivityVisualStyle(
        icon = Icons.Filled.Edit,
        containerColor = Cobalt100,
        contentColor = Cobalt600
    )
    ActivityLogAction.PRODUCT_DELETED -> ActivityVisualStyle(
        icon = Icons.Filled.DeleteOutline,
        containerColor = Danger100,
        contentColor = Danger600
    )
    ActivityLogAction.STOCK_UPDATED -> ActivityVisualStyle(
        icon = Icons.Filled.Inventory2,
        containerColor = Cobalt100,
        contentColor = Cobalt600
    )
    ActivityLogAction.SHOPKEEPER_APPROVED -> ActivityVisualStyle(
        icon = Icons.Filled.CheckCircle,
        containerColor = Emerald100,
        contentColor = Emerald600
    )
    ActivityLogAction.SHOPKEEPER_BLOCKED -> ActivityVisualStyle(
        icon = Icons.Filled.Block,
        containerColor = Danger100,
        contentColor = Danger600
    )
    ActivityLogAction.ORDER_STATUS_UPDATED -> ActivityVisualStyle(
        icon = Icons.Filled.LocalShipping,
        containerColor = Cobalt100,
        contentColor = Cobalt600
    )
    ActivityLogAction.INVOICE_CREATED -> ActivityVisualStyle(
        icon = Icons.Filled.Receipt,
        containerColor = Cobalt100,
        contentColor = Cobalt600
    )
    ActivityLogAction.PAYMENT_RECORDED -> ActivityVisualStyle(
        icon = Icons.Filled.Payments,
        containerColor = Emerald100,
        contentColor = Emerald600
    )
    ActivityLogAction.INVOICE_DELETED -> ActivityVisualStyle(
        icon = Icons.Filled.DeleteOutline,
        containerColor = Danger100,
        contentColor = Danger600
    )
    ActivityLogAction.THEME_UPDATED -> ActivityVisualStyle(
        icon = Icons.Filled.Palette,
        containerColor = Amber100,
        contentColor = Amber600
    )
    ActivityLogAction.SETTINGS_UPDATED -> ActivityVisualStyle(
        icon = Icons.Filled.Settings,
        containerColor = Slate100,
        contentColor = Slate700
    )
}

private fun formatAuditTimestamp(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "Just now"
    val diffMs = System.currentTimeMillis() - date.time
    return when {
        diffMs < 60_000L -> "Just now"
        diffMs < 3_600_000L -> "${diffMs / 60_000L}m ago"
        diffMs < 86_400_000L -> {
            val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(date)
            "Today, $timeStr"
        }
        else -> SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(date)
    }
}

private fun formatAuditExactDate(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: Date()
    return SimpleDateFormat("dd MMM yyyy • hh:mm:ss a", Locale.US).format(date)
}

/**
 * Compact preview card inside Admin Settings that surfaces the immutable security audit trail
 * and opens the full-screen Activity Log screen.
 */
@Composable
fun AdminActivityLogSummaryCard(
    logsState: UiState<List<AdminActivityLog>>,
    onOpenFullLog: () -> Unit
) {
    val logs = (logsState as? UiState.Success)?.data.orEmpty()
    val priceChangesCount = remember(logs) {
        logs.count { it.actionEnum() == ActivityLogAction.PRICE_CHANGED }
    }
    val approvalsCount = remember(logs) {
        logs.count {
            val a = it.actionEnum()
            a == ActivityLogAction.SHOPKEEPER_APPROVED || a == ActivityLogAction.SHOPKEEPER_BLOCKED
        }
    }

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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Slate900),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = PureWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Admin Activity Log",
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate900
                        )
                        Text(
                            text = "Tracks products, price edits & buyer approvals",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                }
                StatusPill(
                    text = "IMMUTABLE",
                    containerColor = Emerald100,
                    contentColor = Emerald600
                )
            }

            // Quick accountability metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AuditMiniStat(
                    label = "Total Events",
                    value = logs.size.toString(),
                    tint = Slate900,
                    modifier = Modifier.weight(1f)
                )
                AuditMiniStat(
                    label = "Price Edits",
                    value = priceChangesCount.toString(),
                    tint = Amber600,
                    modifier = Modifier.weight(1f)
                )
                AuditMiniStat(
                    label = "Buyer Audits",
                    value = approvalsCount.toString(),
                    tint = Emerald600,
                    modifier = Modifier.weight(1f)
                )
            }

            when (logsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                is UiState.Error -> {
                    Text(
                        text = logsState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger600
                    )
                }

                is UiState.Success -> {
                    if (logs.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Slate50,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No admin actions recorded yet. Product additions, price updates, and shopkeeper approvals will appear here automatically.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            logs.take(3).forEach { entry ->
                                CompactAuditPreviewRow(
                                    log = entry,
                                    onClick = onOpenFullLog
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onOpenFullLog,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Slate900,
                    contentColor = PureWhite
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_activity_log_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.History,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (logs.isEmpty()) "Open Full Activity Log" else "View All ${logs.size} Activity Logs",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun AuditMiniStat(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate50,
        modifier = modifier.border(1.dp, Slate200, RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = tint
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Slate600,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactAuditPreviewRow(
    log: AdminActivityLog,
    onClick: () -> Unit
) {
    val action = log.actionEnum()
    val style = styleForAction(action)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Slate50,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(style.containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.targetName.ifBlank { action.label },
                    style = MaterialTheme.typography.labelLarge,
                    color = Slate900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = log.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatAuditTimestamp(log.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = Slate600
            )
        }
    }
}

/**
 * Full-screen Admin Security & Accountability Activity Log with category filtering,
 * keyword search, KPI strip, and visual before -> after change pills.
 */
@Composable
fun AdminActivityLogScreen(
    logsState: UiState<List<AdminActivityLog>>,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<ActivityLogCategory?>(null) }

    val allLogs = (logsState as? UiState.Success)?.data.orEmpty()

    val priceChangeCount = remember(allLogs) {
        allLogs.count { it.actionEnum() == ActivityLogAction.PRICE_CHANGED }
    }
    val shopkeeperAuditCount = remember(allLogs) {
        allLogs.count { it.categoryEnum() == ActivityLogCategory.SHOPKEEPER }
    }
    val productEventCount = remember(allLogs) {
        allLogs.count {
            val cat = it.categoryEnum()
            cat == ActivityLogCategory.PRODUCT || cat == ActivityLogCategory.PRICING
        }
    }

    val filteredLogs = remember(allLogs, searchQuery, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        allLogs.filter { entry ->
            val matchesCat = selectedCategory == null || entry.categoryEnum() == selectedCategory
            if (!matchesCat) return@filter false
            if (q.isEmpty()) return@filter true
            entry.targetName.lowercase().contains(q) ||
                entry.summary.lowercase().contains(q) ||
                entry.actorName.lowercase().contains(q) ||
                entry.actorEmail.lowercase().contains(q) ||
                entry.actionEnum().label.lowercase().contains(q) ||
                entry.previousValue.lowercase().contains(q) ||
                entry.newValue.lowercase().contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .navigationBarsPadding()
    ) {
        // Dark automotive audit header
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
                            .testTag("activity_log_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Settings",
                            tint = PureWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Admin Activity Log",
                            style = MaterialTheme.typography.titleLarge,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VerifiedUser,
                                contentDescription = null,
                                tint = Emerald600,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Append-only immutable Firestore audit trail",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate400,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // KPI strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeaderKpiTile(
                    label = "Total Logs",
                    value = allLogs.size.toString(),
                    accent = PureWhite,
                    modifier = Modifier.weight(1f)
                )
                HeaderKpiTile(
                    label = "Price Edits",
                    value = priceChangeCount.toString(),
                    accent = Color(0xFFFBBF24),
                    modifier = Modifier.weight(1f)
                )
                HeaderKpiTile(
                    label = "Products",
                    value = productEventCount.toString(),
                    accent = Color(0xFF60A5FA),
                    modifier = Modifier.weight(1f)
                )
                HeaderKpiTile(
                    label = "Buyers",
                    value = shopkeeperAuditCount.toString(),
                    accent = Color(0xFF34D399),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audit search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search product, shopkeeper, price, or admin...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = "Search activity logs",
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
                    .testTag("activity_log_search_input")
            )
        }

        // Category filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("All (${allLogs.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Slate900,
                    selectedLabelColor = PureWhite
                ),
                modifier = Modifier.testTag("activity_filter_all")
            )
            ActivityLogCategory.entries.forEach { cat ->
                val count = allLogs.count { it.categoryEnum() == cat }
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = {
                        selectedCategory = if (selectedCategory == cat) null else cat
                    },
                    label = {
                        Text(if (count > 0) "${cat.label} ($count)" else cat.label)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("activity_filter_${cat.raw}")
                )
            }
        }
        HorizontalDivider(color = Slate200)

        when (logsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard(
                        icon = Icons.Filled.Security,
                        title = "Unable to load audit logs",
                        subtitle = logsState.message
                    )
                }
            }

            is UiState.Success -> {
                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyStateCard(
                            icon = Icons.Filled.History,
                            title = if (allLogs.isEmpty()) "No Activity Recorded Yet" else "No Matching Audit Entries",
                            subtitle = if (allLogs.isEmpty()) {
                                "Critical actions like adding products, changing wholesale prices, and approving shopkeepers are automatically logged here."
                            } else {
                                "Try clearing your search query or switching to All categories."
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("activity_log_list"),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { log ->
                            AdminActivityLogCard(log = log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderKpiTile(
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
                fontSize = 17.sp,
                color = accent,
                maxLines = 1
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
private fun AdminActivityLogCard(log: AdminActivityLog) {
    val action = log.actionEnum()
    val style = styleForAction(action)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(style.containerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = style.icon,
                            contentDescription = action.label,
                            tint = style.contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = log.targetName.ifBlank { action.label },
                            style = MaterialTheme.typography.titleSmall,
                            color = Slate900,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatAuditTimestamp(log.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate600
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(
                    text = action.label,
                    containerColor = style.containerColor,
                    contentColor = style.contentColor
                )
            }

            Text(
                text = log.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate800
            )

            // Visual value diff banner (e.g., ₹1,800 -> ₹2,100 or Pending -> Approved)
            if (log.previousValue.isNotBlank() || log.newValue.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Slate50,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Slate200, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (log.previousValue.isNotBlank()) {
                            Text(
                                text = log.previousValue,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Slate600,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "changed to",
                                tint = Slate400,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        if (log.newValue.isNotBlank()) {
                            Text(
                                text = log.newValue,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = style.contentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Slate100)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val actorLabel = buildString {
                    append(log.actorName.ifBlank { "Admin" })
                    if (log.actorEmail.isNotBlank()) {
                        append(" • ")
                        append(log.actorEmail)
                    }
                }
                Text(
                    text = actorLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Slate600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatAuditExactDate(log.createdAt),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = Slate400
                )
            }
        }
    }
}
