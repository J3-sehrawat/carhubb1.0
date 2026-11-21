package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SyncStatusSummary
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.Cobalt100
import com.example.ui.theme.Cobalt600
import com.example.ui.theme.Emerald100
import com.example.ui.theme.Emerald600
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

/**
 * Compact live connectivity & sync status bar displayed automatically across Admin and
 * Shopkeeper workspaces whenever the device loses connectivity or is syncing queued offline writes.
 */
@Composable
fun OfflineSyncStatusBanner(
    syncStatus: SyncStatusSummary,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = !syncStatus.isOnline || syncStatus.pendingWriteCount > 0 || syncStatus.isSyncingNow

    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        val isOffline = !syncStatus.isOnline
        val bgColor = if (isOffline) Slate900 else Cobalt600
        val accentTint = if (isOffline) Color(0xFFFBBF24) else PureWhite
        val pendingCount = syncStatus.pendingWriteCount

        Surface(
            color = bgColor,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isOffline && !syncStatus.isSyncingNow, onClick = onSyncNow)
                .testTag("offline_sync_status_banner")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isOffline) {
                        Icon(
                            imageVector = Icons.Filled.CloudOff,
                            contentDescription = "Offline mode",
                            tint = accentTint,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PureWhite
                        )
                    }
                    Column {
                        Text(
                            text = when {
                                isOffline -> "Offline Mode • Cached Catalog & Bills"
                                pendingCount > 0 -> "Back Online • Syncing $pendingCount queued ${if (pendingCount == 1) "change" else "changes"}…"
                                else -> "Syncing with Cloud…"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isOffline) {
                            Text(
                                text = if (pendingCount > 0) {
                                    "$pendingCount offline ${if (pendingCount == 1) "change" else "changes"} queued • Auto-syncs when online"
                                } else {
                                    "Viewing on-device cache • Changes auto-sync once back online"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (isOffline && pendingCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(
                        text = "$pendingCount QUEUED",
                        containerColor = Amber100,
                        contentColor = Amber600
                    )
                }
            }
        }
    }
}

/**
 * Settings / Profile status card showing active Firestore offline persistence, cached record
 * counts (catalog items & pending invoices), pending write queue, and a manual Sync Now control.
 */
@Composable
fun OfflinePersistenceStatusCard(
    syncStatus: SyncStatusSummary,
    cachedProductsCount: Int,
    pendingInvoicesCount: Int,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isOffline = !syncStatus.isOnline
    val pendingWrites = syncStatus.pendingWriteCount
    val (pillText, pillBg, pillFg) = when {
        isOffline -> Triple("OFFLINE MODE", Amber100, Amber600)
        syncStatus.isSyncingNow || pendingWrites > 0 -> Triple("SYNCING", Cobalt100, Cobalt600)
        else -> Triple("OFFLINE READY", Emerald100, Emerald600)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isOffline) Amber600.copy(alpha = 0.5f) else Slate200,
                shape = RoundedCornerShape(16.dp)
            )
            .testTag("offline_persistence_status_card")
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
                            .background(if (isOffline) Amber50 else Emerald100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOffline) Icons.Filled.CloudOff else Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = if (isOffline) Amber600 else Emerald600,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Offline Access & Auto-Sync",
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate900
                        )
                        Text(
                            text = if (isOffline) {
                                "Network unavailable — serving cached data"
                            } else {
                                "Firestore disk persistence active"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                }
                StatusPill(text = pillText, containerColor = pillBg, contentColor = pillFg)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OfflineMetricTile(
                    label = "Cached SKUs",
                    value = cachedProductsCount.toString(),
                    tint = Slate900,
                    modifier = Modifier.weight(1f)
                )
                OfflineMetricTile(
                    label = "Pending Bills",
                    value = pendingInvoicesCount.toString(),
                    tint = if (pendingInvoicesCount > 0) Amber600 else Emerald600,
                    modifier = Modifier.weight(1f)
                )
                OfflineMetricTile(
                    label = "Sync Queue",
                    value = pendingWrites.toString(),
                    tint = if (pendingWrites > 0) Cobalt600 else Emerald600,
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Catalog products, photos, orders, and pending invoices remain available when network drops. Any changes made offline sync automatically once reconnected.",
                style = MaterialTheme.typography.bodySmall,
                color = Slate600
            )

            OutlinedButton(
                onClick = onSyncNow,
                enabled = !syncStatus.isSyncingNow,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("offline_sync_now_button")
            ) {
                if (syncStatus.isSyncingNow) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Syncing Pending Changes…")
                } else {
                    Icon(
                        imageVector = if (isOffline) Icons.Filled.CloudOff else Icons.Filled.Sync,
                        contentDescription = null,
                        tint = if (isOffline) Amber600 else Slate800,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isOffline) {
                            "Waiting for Connection ($pendingWrites queued)"
                        } else if (pendingWrites > 0) {
                            "Sync $pendingWrites Queued ${if (pendingWrites == 1) "Change" else "Changes"} Now"
                        } else {
                            "Verify Cloud Sync Now"
                        },
                        color = Slate900
                    )
                }
            }
        }
    }
}

@Composable
private fun OfflineMetricTile(
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
