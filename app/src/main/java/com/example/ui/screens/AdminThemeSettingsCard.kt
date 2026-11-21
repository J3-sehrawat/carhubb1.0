package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DEFAULT_PRIMARY_COLOR_HEX
import com.example.ui.components.PriceText
import com.example.ui.components.StatusPill
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber600
import com.example.ui.theme.BRAND_COLOR_PRESETS
import com.example.ui.theme.BrandColorPreset
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
import com.example.ui.theme.Slate950
import com.example.ui.theme.parseBrandHexOrNull
import com.example.ui.theme.resolveBrandPalette
import java.util.Locale

@Composable
fun AdminThemeSettingsCard(
    savedPrimaryColorHex: String,
    businessName: String,
    isBusy: Boolean,
    onPreviewBrandColor: (String?) -> Unit,
    onSaveBrandTheme: (String) -> Unit
) {
    val normalizedSaved = remember(savedPrimaryColorHex) {
        resolveBrandPalette(savedPrimaryColorHex).hex
    }
    var selectedHex by remember(normalizedSaved) { mutableStateOf(normalizedSaved) }
    var hexInput by remember(normalizedSaved) { mutableStateOf(normalizedSaved) }

    val parsedInputColor = remember(hexInput) { parseBrandHexOrNull(hexInput) }
    val isInputValid = parsedInputColor != null
    val previewPalette = remember(selectedHex) { resolveBrandPalette(selectedHex) }
    val hasUnsavedChanges = !previewPalette.hex.equals(normalizedSaved, ignoreCase = true)
    val matchedPreset = remember(previewPalette.hex) {
        BRAND_COLOR_PRESETS.firstOrNull { it.hex.equals(previewPalette.hex, ignoreCase = true) }
    }

    LaunchedEffect(selectedHex, normalizedSaved) {
        if (selectedHex.equals(normalizedSaved, ignoreCase = true)) {
            onPreviewBrandColor(null)
        } else {
            onPreviewBrandColor(selectedHex)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .testTag("admin_theme_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card Header
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(previewPalette.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = previewPalette.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Catalog Brand & Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = Slate900
                        )
                        Text(
                            text = "Updates primary color live across Admin & Buyer Catalog",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                }
                if (hasUnsavedChanges) {
                    StatusPill(
                        text = "PREVIEW",
                        containerColor = Amber100,
                        contentColor = Amber600
                    )
                }
            }

            // Live Brand Identity Preview Mini-Catalog Card
            CatalogBrandPreviewBanner(
                businessName = businessName.ifBlank { "AutoTrade Wholesale" },
                paletteHex = previewPalette.hex,
                presetName = matchedPreset?.name ?: "Custom Brand Color"
            )

            // Curated Brand Color Presets (2 rows x 4 swatches)
            Text(
                text = "Choose Primary Brand Color",
                style = MaterialTheme.typography.labelMedium,
                color = Slate900
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BRAND_COLOR_PRESETS.chunked(4).forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowPresets.forEach { preset ->
                            val isSelected = previewPalette.hex.equals(preset.hex, ignoreCase = true)
                            BrandSwatchTile(
                                preset = preset,
                                selected = isSelected,
                                onClick = {
                                    selectedHex = preset.hex
                                    hexInput = preset.hex
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Optional Custom Hex Color Input
            OutlinedTextField(
                value = hexInput,
                onValueChange = { raw ->
                    val filtered = raw.uppercase(Locale.US)
                        .filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }
                        .take(7)
                    hexInput = filtered
                    val validHex = filtered.let { if (it.startsWith("#")) it else "#$it" }
                    if (parseBrandHexOrNull(validHex) != null) {
                        selectedHex = validHex
                    }
                },
                label = { Text("Custom Brand Hex Code") },
                placeholder = { Text("#E11D48") },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(parsedInputColor ?: Slate200)
                            .border(1.dp, Slate400.copy(alpha = 0.5f), CircleShape)
                    )
                },
                supportingText = {
                    if (!isInputValid) {
                        Text(
                            text = "Enter a 6-digit hex color (e.g. #2563EB)",
                            color = Danger600
                        )
                    } else {
                        Text(
                            text = "Active: ${matchedPreset?.name ?: "Custom"} (${previewPalette.hex})",
                            color = Slate600
                        )
                    }
                },
                isError = !isInputValid,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("theme_custom_hex_input")
            )

            // Apply & Reset Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onSaveBrandTheme(previewPalette.hex) },
                    enabled = !isBusy && isInputValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = previewPalette.primary,
                        contentColor = previewPalette.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_brand_theme_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasUnsavedChanges) "Apply Brand Theme" else "Brand Theme Active",
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (!previewPalette.hex.equals(DEFAULT_PRIMARY_COLOR_HEX, ignoreCase = true) ||
                    !normalizedSaved.equals(DEFAULT_PRIMARY_COLOR_HEX, ignoreCase = true)
                ) {
                    OutlinedButton(
                        onClick = {
                            selectedHex = DEFAULT_PRIMARY_COLOR_HEX
                            hexInput = DEFAULT_PRIMARY_COLOR_HEX
                            if (!normalizedSaved.equals(DEFAULT_PRIMARY_COLOR_HEX, ignoreCase = true)) {
                                onSaveBrandTheme(DEFAULT_PRIMARY_COLOR_HEX)
                            }
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("reset_brand_theme_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reset to Racing Crimson",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogBrandPreviewBanner(
    businessName: String,
    paletteHex: String,
    presetName: String
) {
    val palette = remember(paletteHex) { resolveBrandPalette(paletteHex) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Slate50,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate200, RoundedCornerShape(14.dp))
    ) {
        Column {
            // Mini Dark Header Bar Preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate950)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(palette.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DirectionsCar,
                            contentDescription = null,
                            tint = palette.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column {
                        Text(
                            text = businessName,
                            style = MaterialTheme.typography.labelLarge,
                            color = PureWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Buyer Catalog Live Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = Slate400
                        )
                    }
                }
                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = palette.hex,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            // Mini Catalog Item Preview Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPill(
                            text = "NEW",
                            containerColor = palette.primary,
                            contentColor = palette.onPrimary
                        )
                        StatusPill(
                            text = presetName,
                            containerColor = palette.primaryContainer,
                            contentColor = palette.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "7D Luxury Car Floor Mat",
                        style = MaterialTheme.typography.labelLarge,
                        color = Slate900,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    PriceText(
                        formattedPrice = "₹1,850",
                        fontSizeSp = 15,
                        color = palette.primary
                    )
                }

                Surface(
                    color = palette.primary,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddShoppingCart,
                            contentDescription = null,
                            tint = palette.onPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Add (5)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrandSwatchTile(
    preset: BrandColorPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) preset.primaryContainer else Slate50,
        modifier = modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) preset.primary else Slate200,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("theme_swatch_${preset.hex.removePrefix("#").lowercase(Locale.US)}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(preset.primary)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) PureWhite else Slate100,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "${preset.name} selected",
                        tint = PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = preset.name.substringBefore(" "),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) preset.onPrimaryContainer else Slate900,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
