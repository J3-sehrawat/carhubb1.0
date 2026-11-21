package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AutoTradeRepository
import com.example.data.BillType
import com.example.data.BusinessProfile
import com.example.data.GST_RATES
import com.example.data.InvoiceItem
import com.example.data.Order
import com.example.data.PaymentMode
import com.example.data.Product
import com.example.data.TaxType
import com.example.data.UserProfile
import com.example.ui.components.PriceText
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private data class EditableBillLine(
    val uid: Long,
    val productId: String,
    val name: String,
    val hsnCode: String,
    val qtyText: String,
    val rateText: String,
    val gstPercent: Long
) {
    val qty: Long get() = qtyText.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
    val rate: Double get() = rateText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val lineTotal: Double get() = AutoTradeRepository.round2(qty * rate)
}

@Composable
fun AdminInvoiceFormScreen(
    sourceOrder: Order?,
    products: List<Product>,
    shopkeepers: List<UserProfile>,
    businessProfile: BusinessProfile,
    isBusy: Boolean,
    onCreateInvoice: (
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
        initialPaymentNote: String
    ) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val productsById = remember(products) { products.associateBy { it.id } }
    val matchingBuyer = remember(sourceOrder, shopkeepers) {
        if (sourceOrder == null) null
        else shopkeepers.firstOrNull { it.id == sourceOrder.shopkeeperId || it.userId == sourceOrder.shopkeeperId }
    }

    var billType by remember { mutableStateOf(BillType.GST_INVOICE) }
    var taxType by remember { mutableStateOf(TaxType.CGST_SGST) }

    var selectedShopkeeperId by remember(sourceOrder) { mutableStateOf(sourceOrder?.shopkeeperId.orEmpty()) }
    var shopName by remember(sourceOrder) { mutableStateOf(sourceOrder?.shopName.orEmpty()) }
    var phone by remember(sourceOrder) { mutableStateOf(sourceOrder?.phone.orEmpty()) }
    var city by remember(sourceOrder) { mutableStateOf(sourceOrder?.city.orEmpty()) }
    var shopGstin by remember(sourceOrder, matchingBuyer) { mutableStateOf(matchingBuyer?.gstin.orEmpty()) }

    var nextLineUid by remember { mutableStateOf(100L) }
    val lineItems = remember(sourceOrder) {
        mutableStateListOf<EditableBillLine>().apply {
            sourceOrder?.items?.forEachIndexed { idx, item ->
                val matched = productsById[item.productId]
                add(
                    EditableBillLine(
                        uid = idx.toLong() + 1L,
                        productId = item.productId,
                        name = item.name,
                        hsnCode = matched?.hsnCode.orEmpty(),
                        qtyText = item.qty.toString(),
                        rateText = formatPlainNumber(item.price),
                        gstPercent = matched?.gstPercent ?: 18L
                    )
                )
            }
        }
    }

    var discountIsPercent by remember { mutableStateOf(false) }
    var discountInput by remember { mutableStateOf("") }
    var transportInput by remember { mutableStateOf("") }

    var initialPaidInput by remember { mutableStateOf("") }
    var initialPaymentMode by remember { mutableStateOf(PaymentMode.CASH) }
    var initialPaymentNote by remember { mutableStateOf("") }

    var showShopkeeperPicker by remember { mutableStateOf(false) }
    var showCatalogPicker by remember { mutableStateOf(false) }
    var showCustomItemDialog by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    // Real-time financial math
    val subtotal = AutoTradeRepository.round2(lineItems.sumOf { it.lineTotal })
    val rawDiscountVal = discountInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val discountAmount = AutoTradeRepository.round2(
        if (discountIsPercent) {
            (subtotal * (rawDiscountVal.coerceAtMost(100.0) / 100.0))
        } else {
            rawDiscountVal.coerceAtMost(subtotal)
        }
    )
    val taxableBase = max(0.0, subtotal - discountAmount)
    val effectiveTaxType = if (billType == BillType.GST_INVOICE) taxType else TaxType.NONE
    val taxAmount = if (effectiveTaxType != TaxType.NONE && subtotal > 0.0) {
        val ratio = taxableBase / subtotal
        AutoTradeRepository.round2(
            lineItems.sumOf { line -> (line.lineTotal * ratio) * (line.gstPercent.toDouble() / 100.0) }
        )
    } else 0.0
    val transportCharges = AutoTradeRepository.round2(transportInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0)
    val grandTotal = AutoTradeRepository.round2(taxableBase + taxAmount + transportCharges)
    val initialPaid = AutoTradeRepository.round2(
        (initialPaidInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0).coerceAtMost(grandTotal)
    )
    val balanceDue = AutoTradeRepository.round2(max(0.0, grandTotal - initialPaid))

    val nextNumberPreview = "${businessProfile.invoicePrefix.ifBlank { "INV" }}-${businessProfile.nextInvoiceNumber.coerceAtLeast(1L).toString().padStart(4, '0')}"

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
                    .testTag("invoice_form_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to billing",
                    tint = PureWhite
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (sourceOrder != null) "Generate Bill from Order" else "Create Wholesale Bill",
                    style = MaterialTheme.typography.titleLarge,
                    color = PureWhite
                )
                Text(
                    text = "Next Invoice: $nextNumberPreview",
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
                .widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. BILL TYPE & GST MODE CARD
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
                    Text("Bill Type", style = MaterialTheme.typography.titleSmall, color = Slate900)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BillType.entries.forEach { bt ->
                            FilterChip(
                                selected = billType == bt,
                                onClick = { billType = bt },
                                label = { Text(bt.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Slate900,
                                    selectedLabelColor = PureWhite
                                ),
                                modifier = Modifier.testTag("bill_type_${bt.name.lowercase()}")
                            )
                        }
                    }

                    if (billType == BillType.GST_INVOICE) {
                        Text("GST Tax Mode", style = MaterialTheme.typography.labelMedium, color = Slate600)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(TaxType.CGST_SGST, TaxType.IGST).forEach { tt ->
                                FilterChip(
                                    selected = taxType == tt,
                                    onClick = { taxType = tt },
                                    label = { Text(tt.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("tax_type_${tt.name.lowercase()}")
                                )
                            }
                        }
                    }
                }
            }

            // 2. PARTY / SHOPKEEPER DETAILS CARD
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Party / Buyer Details", style = MaterialTheme.typography.titleMedium, color = Slate900)
                        if (shopkeepers.isNotEmpty()) {
                            TextButton(
                                onClick = { showShopkeeperPicker = true },
                                modifier = Modifier.testTag("pick_shopkeeper_button")
                            ) {
                                Icon(Icons.Filled.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Buyer")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it.take(100) },
                        label = { Text("Shop / Party Name *") },
                        placeholder = { Text("e.g. Sharma Car Decor (or Walk-in Buyer)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bill_shop_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it.filter { c -> c.isDigit() }.take(15) },
                            label = { Text("Phone") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it.take(60) },
                            label = { Text("City") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (billType == BillType.GST_INVOICE) {
                        OutlinedTextField(
                            value = shopGstin,
                            onValueChange = { shopGstin = it.uppercase(Locale.US).take(20) },
                            label = { Text("Buyer GSTIN (Optional)") },
                            placeholder = { Text("e.g. 07ABCDE1234F1Z5") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 3. BILL LINE ITEMS CARD
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
                        text = "Bill Items (${lineItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate900
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showCatalogPicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_catalog_item_button")
                        ) {
                            Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From Catalog", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { showCustomItemDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("add_custom_item_button")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Custom Item", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (lineItems.isEmpty()) {
                        Text(
                            text = "No items added yet. Pick items from your catalog or add a custom accessory line item.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    } else {
                        lineItems.forEachIndexed { idx, line ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Slate50,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Slate200, RoundedCornerShape(12.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = line.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = Slate900,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (line.hsnCode.isNotBlank()) {
                                                Text(
                                                    text = "HSN: ${line.hsnCode}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Slate600
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = { lineItems.removeAt(idx) },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove item", tint = Danger600)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = line.qtyText,
                                            onValueChange = { txt ->
                                                lineItems[idx] = line.copy(qtyText = txt.filter { it.isDigit() }.take(5))
                                            },
                                            label = { Text("Qty") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(0.75f)
                                        )
                                        OutlinedTextField(
                                            value = line.rateText,
                                            onValueChange = { txt ->
                                                if (txt.all { it.isDigit() || it == '.' }) {
                                                    lineItems[idx] = line.copy(rateText = txt.take(10))
                                                }
                                            },
                                            label = { Text("Rate ₹") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.weight(0.9f)
                                        ) {
                                            Text("Total", style = MaterialTheme.typography.labelSmall, color = Slate600)
                                            PriceText(AutoTradeRepository.formatInr(line.lineTotal), fontSizeSp = 14)
                                        }
                                    }

                                    if (billType == BillType.GST_INVOICE) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("GST:", style = MaterialTheme.typography.labelSmall, color = Slate600)
                                            GST_RATES.forEach { rate ->
                                                FilterChip(
                                                    selected = line.gstPercent == rate,
                                                    onClick = { lineItems[idx] = line.copy(gstPercent = rate) },
                                                    label = { Text("$rate%", style = MaterialTheme.typography.labelSmall) },
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
                        }
                    }
                }
            }

            // 4. DISCOUNT, TRANSPORT & INITIAL PAYMENT CARD
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
                    Text("Adjustments & Initial Payment", style = MaterialTheme.typography.titleMedium, color = Slate900)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = discountInput,
                            onValueChange = { txt -> if (txt.all { it.isDigit() || it == '.' }) discountInput = txt.take(10) },
                            label = { Text(if (discountIsPercent) "Discount (%)" else "Discount (₹)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = !discountIsPercent,
                            onClick = { discountIsPercent = false },
                            label = { Text("Flat ₹") }
                        )
                        FilterChip(
                            selected = discountIsPercent,
                            onClick = { discountIsPercent = true },
                            label = { Text("%") }
                        )
                    }

                    OutlinedTextField(
                        value = transportInput,
                        onValueChange = { txt -> if (txt.all { it.isDigit() || it == '.' }) transportInput = txt.take(10) },
                        label = { Text("Transport / Packing Charges (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = Slate100)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = initialPaidInput,
                            onValueChange = { txt -> if (txt.all { it.isDigit() || it == '.' }) initialPaidInput = txt.take(10) },
                            label = { Text("Received Now (₹ Optional)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { if (grandTotal > 0.0) initialPaidInput = formatPlainNumber(grandTotal) }
                        ) {
                            Text("Full Paid")
                        }
                    }

                    if (initialPaid > 0.0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaymentMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = initialPaymentMode == mode,
                                    onClick = { initialPaymentMode = mode },
                                    label = { Text(mode.label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Slate900,
                                        selectedLabelColor = PureWhite
                                    )
                                )
                            }
                        }
                        OutlinedTextField(
                            value = initialPaymentNote,
                            onValueChange = { initialPaymentNote = it.take(120) },
                            label = { Text("Payment Note / Ref (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 5. LIVE TOTALS BREAKUP CARD
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Slate900,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SummaryRow("Subtotal", AutoTradeRepository.formatInr(subtotal), Slate400, PureWhite)
                    if (discountAmount > 0.0) {
                        SummaryRow("Discount", "- ${AutoTradeRepository.formatInr(discountAmount)}", Slate400, PureWhite)
                    }
                    if (taxAmount > 0.0) {
                        if (effectiveTaxType == TaxType.CGST_SGST) {
                            val half = taxAmount / 2.0
                            SummaryRow("CGST", AutoTradeRepository.formatInr(half), Slate400, PureWhite)
                            SummaryRow("SGST", AutoTradeRepository.formatInr(half), Slate400, PureWhite)
                        } else {
                            SummaryRow("IGST", AutoTradeRepository.formatInr(taxAmount), Slate400, PureWhite)
                        }
                    }
                    if (transportCharges > 0.0) {
                        SummaryRow("Transport / Packing", AutoTradeRepository.formatInr(transportCharges), Slate400, PureWhite)
                    }
                    HorizontalDivider(color = Slate800, modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GRAND TOTAL", style = MaterialTheme.typography.titleMedium, color = PureWhite)
                        PriceText(AutoTradeRepository.formatInr(grandTotal), color = PureWhite, fontSizeSp = 21)
                    }
                    if (initialPaid > 0.0) {
                        SummaryRow("Received Now", AutoTradeRepository.formatInr(initialPaid), Emerald600, Emerald600)
                        SummaryRow("Balance Due (Udhaar)", AutoTradeRepository.formatInr(balanceDue), Slate400, PureWhite)
                    }
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
                    val validItems = lineItems.filter { it.name.isNotBlank() && it.rate > 0.0 && it.qty >= 1L }
                    when {
                        shopName.trim().isEmpty() -> formError = "Enter shop or party name."
                        validItems.isEmpty() -> formError = "Add at least one bill item with a rate greater than ₹0."
                        grandTotal <= 0.0 -> formError = "Grand total must be greater than ₹0."
                        else -> {
                            formError = null
                            val today = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                            val invoiceItems = validItems.map { line ->
                                InvoiceItem(
                                    productId = line.productId,
                                    name = line.name.trim(),
                                    hsnCode = line.hsnCode.trim(),
                                    qty = line.qty,
                                    rate = line.rate,
                                    gstPercent = line.gstPercent,
                                    lineTotal = line.lineTotal
                                )
                            }
                            onCreateInvoice(
                                sourceOrder?.id.orEmpty(),
                                billType,
                                selectedShopkeeperId,
                                shopName.trim(),
                                phone.trim(),
                                city.trim(),
                                shopGstin.trim(),
                                invoiceItems,
                                discountAmount,
                                effectiveTaxType,
                                transportCharges,
                                initialPaid,
                                initialPaymentMode,
                                today,
                                initialPaymentNote.trim()
                            )
                        }
                    }
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("submit_invoice_button")
            ) {
                if (isBusy) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = PureWhite, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate & Save Bill", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showShopkeeperPicker) {
        ShopkeeperPickerDialog(
            shopkeepers = shopkeepers.filter { !it.roleIsAdmin() },
            onSelect = { buyer ->
                selectedShopkeeperId = buyer.id.ifBlank { buyer.userId }
                shopName = buyer.shopName.ifBlank { buyer.name }
                phone = buyer.phone
                city = buyer.city
                shopGstin = buyer.gstin
                showShopkeeperPicker = false
            },
            onDismiss = { showShopkeeperPicker = false }
        )
    }

    if (showCatalogPicker) {
        CatalogProductPickerDialog(
            products = products,
            onPickProduct = { prod ->
                nextLineUid++
                lineItems.add(
                    EditableBillLine(
                        uid = nextLineUid,
                        productId = prod.id,
                        name = prod.name,
                        hsnCode = prod.hsnCode,
                        qtyText = prod.moq.coerceAtLeast(1L).toString(),
                        rateText = formatPlainNumber(prod.price),
                        gstPercent = prod.gstPercent
                    )
                )
                showCatalogPicker = false
            },
            onDismiss = { showCatalogPicker = false }
        )
    }

    if (showCustomItemDialog) {
        CustomItemDialog(
            isGst = billType == BillType.GST_INVOICE,
            onAdd = { itemName, hsn, qty, rate, gst ->
                nextLineUid++
                lineItems.add(
                    EditableBillLine(
                        uid = nextLineUid,
                        productId = "",
                        name = itemName,
                        hsnCode = hsn,
                        qtyText = qty.toString(),
                        rateText = formatPlainNumber(rate),
                        gstPercent = gst
                    )
                )
                showCustomItemDialog = false
            },
            onDismiss = { showCustomItemDialog = false }
        )
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    labelColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = labelColor)
        Text(text = value, style = MaterialTheme.typography.labelLarge, color = valueColor)
    }
}

@Composable
private fun ShopkeeperPickerDialog(
    shopkeepers: List<UserProfile>,
    onSelect: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(shopkeepers, query) {
        val q = query.trim().lowercase()
        shopkeepers.filter {
            q.isEmpty() || it.shopName.lowercase().contains(q) || it.city.lowercase().contains(q) || it.phone.contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Shopkeeper") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search shop or city…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text("No registered shopkeepers found.", style = MaterialTheme.typography.bodySmall, color = Slate600)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { buyer ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Slate50,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Slate200, RoundedCornerShape(10.dp))
                                    .clickable { onSelect(buyer) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(buyer.shopName.ifBlank { buyer.name }, style = MaterialTheme.typography.titleSmall, color = Slate900)
                                    Text("${buyer.city} • ${buyer.phone}", style = MaterialTheme.typography.bodySmall, color = Slate600)
                                    if (buyer.gstin.isNotBlank()) {
                                        Text("GSTIN: ${buyer.gstin}", style = MaterialTheme.typography.labelSmall, color = Slate800)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CatalogProductPickerDialog(
    products: List<Product>,
    onPickProduct: (Product) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        val q = query.trim().lowercase()
        products.filter {
            q.isEmpty() || it.name.lowercase().contains(q) || it.carModel.lowercase().contains(q) || it.category.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick from Wholesale Catalog") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search product or car model…") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (filtered.isEmpty()) {
                    Text("No products found in catalog.", style = MaterialTheme.typography.bodySmall, color = Slate600)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { prod ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Slate50,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, Slate200, RoundedCornerShape(10.dp))
                                    .clickable { onPickProduct(prod) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(prod.name, style = MaterialTheme.typography.titleSmall, color = Slate900, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val stockLabel = prod.trackedStockCount()?.let { "Stock: $it pcs" }
                                            ?: if (prod.inStock) "In Stock" else "Out of Stock"
                                        Text(
                                            text = "${prod.carModel} • MOQ ${prod.moq} • GST ${prod.gstPercent}% • $stockLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (!prod.inStock || (prod.trackedStockCount() ?: 1L) <= 0L) Danger600 else Slate600
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    PriceText(AutoTradeRepository.formatInr(prod.price), fontSizeSp = 14, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun CustomItemDialog(
    isGst: Boolean,
    onAdd: (name: String, hsn: String, qty: Long, rate: Double, gstPercent: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var hsn by remember { mutableStateOf("") }
    var qtyText by remember { mutableStateOf("1") }
    var rateText by remember { mutableStateOf("") }
    var gst by remember { mutableStateOf(18L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Bill Item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(120) },
                    label = { Text("Item Description *") },
                    placeholder = { Text("e.g. Custom LED Fog Lamp Set") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text("Qty *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f)
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) rateText = it.take(10) },
                        label = { Text("Rate ₹ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1.2f)
                    )
                }
                if (isGst) {
                    OutlinedTextField(
                        value = hsn,
                        onValueChange = { hsn = it.filter { c -> c.isLetterOrDigit() }.take(12) },
                        label = { Text("HSN Code (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GST_RATES.forEach { rate ->
                            FilterChip(
                                selected = gst == rate,
                                onClick = { gst = rate },
                                label = { Text("$rate%") }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val q = qtyText.toLongOrNull() ?: 0L
                    val r = rateText.toDoubleOrNull() ?: 0.0
                    if (name.trim().isNotEmpty() && q >= 1L && r > 0.0) {
                        onAdd(name.trim(), hsn.trim(), q, r, gst)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Add Item")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatPlainNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value)
