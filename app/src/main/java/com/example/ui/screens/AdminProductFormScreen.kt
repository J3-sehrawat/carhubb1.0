package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.Category
import com.example.data.DEFAULT_CATEGORIES
import com.example.data.GST_RATES
import com.example.data.ImageCompressor
import com.example.data.POPULAR_CAR_MODELS
import com.example.data.Product
import com.example.ui.components.ProductPhoto
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber50
import com.example.ui.theme.Amber600
import com.example.ui.theme.Danger100
import com.example.ui.theme.Danger600
import com.example.ui.theme.Emerald600
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate50
import com.example.ui.theme.Slate600
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.launch

private data class PhotoSlot(
    val dataUri: String,
    val sizeKb: Int? = null
)

@Composable
fun AdminProductFormScreen(
    editingProduct: Product?,
    categories: List<Category>,
    lowStockThreshold: Long,
    isBusy: Boolean,
    onAddCategory: (String, (String) -> Unit) -> Unit,
    onSave: (
        productId: String?,
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
        imageUrls: List<String>
    ) -> Unit,
    onClose: () -> Unit,
    onNotify: (String) -> Unit
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val categoryNames = remember(categories) {
        val fromDb = categories.map { it.name }.filter { it.isNotBlank() }
        if (fromDb.isNotEmpty()) fromDb else DEFAULT_CATEGORIES.map { it.second }
    }

    var name by remember(editingProduct) { mutableStateOf(editingProduct?.name.orEmpty()) }
    var selectedCategory by remember(editingProduct, categoryNames) {
        mutableStateOf(editingProduct?.category?.ifBlank { null } ?: categoryNames.firstOrNull().orEmpty())
    }
    var carModel by remember(editingProduct) { mutableStateOf(editingProduct?.carModel ?: "Universal") }
    var priceText by remember(editingProduct) {
        mutableStateOf(
            editingProduct?.price?.let {
                if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
            }.orEmpty()
        )
    }
    var moqText by remember(editingProduct) { mutableStateOf((editingProduct?.moq ?: 5L).toString()) }
    var stockQtyText by remember(editingProduct) {
        val initialQty = editingProduct?.stockQuantity
            ?: if (editingProduct != null && !editingProduct.inStock) 0L else 25L
        mutableStateOf(initialQty.toString())
    }
    var inStock by remember(editingProduct) { mutableStateOf(editingProduct?.inStock ?: true) }
    var newArrival by remember(editingProduct) { mutableStateOf(editingProduct?.isFeaturedNewArrival() ?: true) }
    var hsnCode by remember(editingProduct) { mutableStateOf(editingProduct?.hsnCode.orEmpty()) }
    var gstPercent by remember(editingProduct) { mutableStateOf(editingProduct?.gstPercent ?: 18L) }
    var description by remember(editingProduct) { mutableStateOf(editingProduct?.description.orEmpty()) }

    val photos = remember(editingProduct) {
        mutableStateListOf<PhotoSlot>().apply {
            editingProduct?.imageUrls?.take(4)?.forEach { add(PhotoSlot(it)) }
        }
    }

    var isCompressing by remember { mutableStateOf(false) }
    var compressionProgress by remember { mutableFloatStateOf(0f) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var retakeSlotIndex by remember { mutableStateOf<Int?>(null) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    fun compressAndInsert(uris: List<Uri>, replaceIndex: Int? = null) {
        if (uris.isEmpty()) return
        scope.launch {
            isCompressing = true
            compressionProgress = 0.05f
            try {
                for ((idx, uri) in uris.withIndex()) {
                    if (replaceIndex == null && photos.size >= 4) break
                    val result = ImageCompressor.compressUriToDataUri(context, uri) { stepProgress ->
                        compressionProgress = ((idx + stepProgress) / uris.size).coerceIn(0.05f, 1f)
                    }
                    result.onSuccess { compressed ->
                        val slot = PhotoSlot(compressed.dataUri, compressed.sizeKb)
                        if (replaceIndex != null && replaceIndex in photos.indices) {
                            photos[replaceIndex] = slot
                        } else if (photos.size < 4) {
                            photos.add(slot)
                        }
                    }.onFailure { err ->
                        onNotify(err.localizedMessage ?: "Failed to process image")
                    }
                }
            } finally {
                isCompressing = false
                compressionProgress = 0f
                retakeSlotIndex = null
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            compressAndInsert(listOf(uri), replaceIndex = retakeSlotIndex)
        } else {
            retakeSlotIndex = null
        }
    }

    fun launchRearCamera(replaceIndex: Int? = null) {
        if (replaceIndex == null && photos.size >= 4) {
            onNotify("Maximum 4 photos per product reached.")
            return
        }
        try {
            val file = ImageCompressor.createTempCameraFile(context)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            pendingCameraUri = uri
            retakeSlotIndex = replaceIndex
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            onNotify("Unable to launch camera: ${e.localizedMessage ?: "error"}")
        }
    }

    var pendingCameraSlotAfterPermission by remember { mutableStateOf<Int?>(null) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchRearCamera(pendingCameraSlotAfterPermission)
        } else {
            onNotify("Camera permission denied. You can still upload from Gallery.")
        }
        pendingCameraSlotAfterPermission = null
    }

    fun requestCameraAndCapture(replaceIndex: Int? = null) {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            launchRearCamera(replaceIndex)
        } else {
            pendingCameraSlotAfterPermission = replaceIndex
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Zero-permission Android Photo Picker (Play Policy compliant!)
    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 4)
    ) { uris ->
        if (uris.isNotEmpty()) {
            val remaining = (4 - photos.size).coerceAtLeast(0)
            compressAndInsert(uris.take(remaining))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate50)
            .navigationBarsPadding()
    ) {
        // Dark Slate Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Slate900)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("product_form_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to inventory",
                    tint = PureWhite
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (editingProduct == null) stringResource(R.string.add_product)
                    else stringResource(R.string.edit_product),
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite
                )
                Text(
                    text = "Snap photos & publish live to buyers",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate400
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CAMERA-FIRST PHOTO CAPTURE CARD
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
                                text = "Product Photos (${photos.size}/4)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Slate900
                            )
                            Text(
                                text = "Auto-compressed for fast mobile catalog loading",
                                style = MaterialTheme.typography.bodySmall,
                                color = Slate600
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Primary Camera CTA + Secondary Gallery CTA
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { requestCameraAndCapture(replaceIndex = null) },
                            enabled = photos.size < 4 && !isCompressing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                                .testTag("take_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.take_photo),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = photos.size < 4 && !isCompressing,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("upload_gallery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.upload_gallery),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    if (isCompressing) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { compressionProgress },
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Compressing photo (${(compressionProgress * 100).toInt()}%)…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (photos.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            photos.forEachIndexed { index, slot ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                                ) {
                                    ProductPhoto(
                                        imageSource = slot.dataUri,
                                        contentDescription = "Product angle ${index + 1}",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Delete badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Slate900.copy(alpha = 0.8f))
                                            .clickable { photos.removeAt(index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Delete photo ${index + 1}",
                                            tint = PureWhite,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    // Retake badge
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp)
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                                            .clickable { requestCameraAndCapture(replaceIndex = index) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Retake photo ${index + 1}",
                                            tint = PureWhite,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    if (slot.sizeKb != null) {
                                        Surface(
                                            color = Slate900.copy(alpha = 0.75f),
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                        ) {
                                            Text(
                                                text = "${slot.sizeKb}KB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = PureWhite,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            repeat((4 - photos.size).coerceAtLeast(0)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Slate100)
                                        .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                                        .clickable { requestCameraAndCapture(replaceIndex = null) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add photo slot",
                                        tint = Slate400
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // PRODUCT DETAILS CARD
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = PureWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name *") },
                        placeholder = { Text("e.g. Nappa Diamond 7D Seat Cover") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_name_input")
                    )

                    // Category selector + "+ Add Category"
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Category *",
                                style = MaterialTheme.typography.titleSmall,
                                color = Slate900
                            )
                            TextButton(
                                onClick = { showNewCategoryDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("add_category_button")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Category")
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoryNames.forEach { cat ->
                                val selected = selectedCategory.equals(cat, ignoreCase = true)
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Slate900,
                                        selectedLabelColor = PureWhite
                                    )
                                )
                            }
                        }
                    }

                    // Car Model input + quick chips
                    Column {
                        OutlinedTextField(
                            value = carModel,
                            onValueChange = { carModel = it },
                            label = { Text("Compatible Car Model *") },
                            placeholder = { Text("Universal, Swift, Creta, Thar, Fronx") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_car_model_input")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            POPULAR_CAR_MODELS.forEach { model ->
                                FilterChip(
                                    selected = carModel.equals(model, ignoreCase = true),
                                    onClick = { carModel = model },
                                    label = { Text(model, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    // Wholesale Price (INR) & MOQ side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = priceText,
                            onValueChange = { input ->
                                if (input.all { it.isDigit() || it == '.' }) priceText = input
                            },
                            label = { Text("Wholesale ₹ *") },
                            prefix = { Text("₹ ", fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("product_price_input")
                        )

                        OutlinedTextField(
                            value = moqText,
                            onValueChange = { input -> moqText = input.filter { it.isDigit() }.take(5) },
                            label = { Text("MOQ (pcs) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.9f)
                                .testTag("product_moq_input")
                        )
                    }

                    // Warehouse Inventory Stock Quantity + Live Low Stock Threshold Warning
                    val parsedStockPreview = stockQtyText.toLongOrNull()
                    val isPreviewLowStock = parsedStockPreview != null && parsedStockPreview < lowStockThreshold
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stockQtyText,
                            onValueChange = { input ->
                                val cleaned = input.filter { it.isDigit() }.take(7)
                                stockQtyText = cleaned
                                val parsed = cleaned.toLongOrNull()
                                if (parsed != null) {
                                    inStock = parsed > 0L
                                }
                            },
                            label = { Text("Available Stock Quantity (pcs) *") },
                            supportingText = {
                                Text("Low Stock alert triggers below $lowStockThreshold pcs (configured in Business Settings)")
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_stock_qty_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(0L, 5L, 10L, 25L, 50L, 100L).forEach { preset ->
                                FilterChip(
                                    selected = parsedStockPreview == preset,
                                    onClick = {
                                        stockQtyText = preset.toString()
                                        inStock = preset > 0L
                                    },
                                    label = { Text("$preset pcs", style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                        if (isPreviewLowStock) {
                            Surface(
                                color = if (parsedStockPreview == 0L) Danger100 else Amber50,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        1.dp,
                                        if (parsedStockPreview == 0L) Danger600.copy(alpha = 0.4f) else Amber600.copy(alpha = 0.45f),
                                        RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.WarningAmber,
                                        contentDescription = null,
                                        tint = if (parsedStockPreview == 0L) Danger600 else Amber600,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (parsedStockPreview == 0L)
                                            "0 pcs in stock — item will show a Low Stock & Out of Stock alert."
                                        else
                                            "Quantity ($parsedStockPreview pcs) is below threshold ($lowStockThreshold pcs) — will trigger a 'Low Stock' badge.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (parsedStockPreview == 0L) Danger600 else Amber600
                                    )
                                }
                            }
                        }
                    }

                    // Optional HSN Code & GST % Slab for GST Billing
                    OutlinedTextField(
                        value = hsnCode,
                        onValueChange = { hsnCode = it.filter { ch -> ch.isLetterOrDigit() }.take(12) },
                        label = { Text("HSN Code (Optional)") },
                        placeholder = { Text("e.g. 87089900") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_hsn_input")
                    )

                    Column {
                        Text(
                            text = "GST Slab (%) for Tax Invoices",
                            style = MaterialTheme.typography.titleSmall,
                            color = Slate900
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GST_RATES.forEach { rate ->
                                FilterChip(
                                    selected = gstPercent == rate,
                                    onClick = { gstPercent = rate },
                                    label = { Text("${rate}%") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Slate900,
                                        selectedLabelColor = PureWhite
                                    ),
                                    modifier = Modifier.testTag("product_gst_chip_$rate")
                                )
                            }
                        }
                    }

                    // Stock status toggle row
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate50,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (inStock) "In Stock" else "Out of Stock",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (inStock) Emerald600 else Danger600
                                )
                                Text(
                                    text = "Visible immediately to all approved buyers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate600
                                )
                            }
                            Switch(
                                checked = inStock,
                                onCheckedChange = { inStock = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = Emerald600),
                                modifier = Modifier.testTag("product_in_stock_switch")
                            )
                        }
                    }

                    // Buyer Home New Arrivals highlight toggle
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Slate50,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show in Buyer 'New Arrivals'",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (newArrival) MaterialTheme.colorScheme.primary else Slate800
                                )
                                Text(
                                    text = "Feature this item at the top of Buyer Home tab",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Slate600
                                )
                            }
                            Switch(
                                checked = newArrival,
                                onCheckedChange = { newArrival = it },
                                colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("product_new_arrival_switch")
                            )
                        }
                    }

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(1000) },
                        label = { Text("Material / Fitting Description") },
                        placeholder = { Text("PU Nappa leather, waterproof backing, 1-year stitching warranty…") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_description_input")
                    )
                }
            }

            if (!formError.isNullOrBlank()) {
                Surface(
                    color = Danger100,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formError!!,
                        color = Danger600,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    val parsedPrice = priceText.toDoubleOrNull()
                    val parsedMoq = moqText.toLongOrNull()
                    val parsedStockQty = stockQtyText.toLongOrNull()
                    when {
                        name.trim().isEmpty() -> formError = "Enter a product name."
                        selectedCategory.trim().isEmpty() -> formError = "Select a category."
                        carModel.trim().isEmpty() -> formError = "Enter compatible car model (e.g. Universal)."
                        parsedPrice == null || parsedPrice <= 0.0 -> formError = "Enter a valid wholesale price in ₹."
                        parsedMoq == null || parsedMoq < 1L -> formError = "MOQ must be at least 1."
                        parsedStockQty == null || parsedStockQty < 0L -> formError = "Enter a valid available stock quantity (0 or more)."
                        else -> {
                            formError = null
                            onSave(
                                editingProduct?.id,
                                name.trim(),
                                selectedCategory.trim(),
                                carModel.trim(),
                                parsedPrice,
                                parsedMoq,
                                inStock && parsedStockQty > 0L,
                                parsedStockQty,
                                newArrival,
                                description.trim(),
                                hsnCode.trim(),
                                gstPercent,
                                photos.map { it.dataUri }
                            )
                        }
                    }
                },
                enabled = !isBusy && !isCompressing,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("save_product_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = PureWhite,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (editingProduct == null) "Publish Product Live" else "Save Changes",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showNewCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showNewCategoryDialog = false },
            title = { Text("Add Category") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it.take(50) },
                    label = { Text("Category Name") },
                    placeholder = { Text("e.g. Android Stereos, Roof Rails") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newCategoryName.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddCategory(trimmed) { createdName ->
                                selectedCategory = createdName
                            }
                            newCategoryName = ""
                            showNewCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
