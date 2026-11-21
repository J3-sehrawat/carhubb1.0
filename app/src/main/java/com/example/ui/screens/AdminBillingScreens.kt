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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.AutoTradeRepository
import com.example.data.ExcelReportExporter
import com.example.data.Invoice
import com.example.data.PaymentMode
import com.example.data.PaymentStatus
import com.example.data.PdfInvoiceGenerator
import com.example.data.Product
import com.example.data.TaxType
import com.example.data.UiState
import com.example.ui.components.AutomotiveHeader
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.PriceText
import com.example.ui.components.StatusPill
import com.example.ui.components.formatTimestamp
import com.example.ui.components.launchWhatsAppOrder
import com.example.ui.components.paymentStatusColors
import com.example.ui.theme.Amber100
import com.example.ui.theme.Amber600
import com.example.ui.theme.Cobalt100
import com.example.ui.theme.Cobalt600
import com.example.ui.theme.Danger600
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AdminBillingTab(
    invoicesState: UiState<List<Invoice>>,
    businessName: String = "AutoTrade Wholesale",
    pendingInvoiceIds: Set<String> = emptySet(),
    isBusy: Boolean,
    onCreateManualBill: () -> Unit,
    onOpenBusinessSettings: () -> Unit,
    onPrintCatalogPdf: () -> Unit,
    onRecordPayment: (Invoice, Double, PaymentMode, String, String, () -> Unit) -> Unit,
    onDeleteInvoice: (Invoice) -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<PaymentStatus?>(null) }
    var paymentTarget by remember { mutableStateOf<Invoice?>(null) }
    var detailTarget by remember { mutableStateOf<Invoice?>(null) }
    var deleteTarget by remember { mutableStateOf<Invoice?>(null) }
    var showExcelExportDialog by remember { mutableStateOf(false) }

    val allInvoices = (invoicesState as? UiState.Success)?.data.orEmpty()
    val filteredInvoices = remember(allInvoices, statusFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        allInvoices.filter { inv ->
            val matchStatus = statusFilter == null || inv.paymentStatusEnum() == statusFilter
            val matchQuery = q.isEmpty() ||
                inv.shopName.lowercase().contains(q) ||
                inv.invoiceNumber.lowercase().contains(q) ||
                inv.city.lowercase().contains(q)
            matchStatus && matchQuery
        }
    }
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

    // Live Billing KPIs: Today's Billing, This Month's Billing, Total Pending Udhaar
    val todayCal = remember { Calendar.getInstance() }
    val todayTotal = remember(allInvoices) {
        allInvoices.filter { inv ->
            val d = inv.createdAt?.toDate() ?: return@filter false
            val c = Calendar.getInstance().apply { time = d }
            c.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                c.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        }.sumOf { it.grandTotal }
    }
    val monthTotal = remember(allInvoices) {
        allInvoices.filter { inv ->
            val d = inv.createdAt?.toDate() ?: return@filter false
            val c = Calendar.getInstance().apply { time = d }
            c.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
                c.get(Calendar.MONTH) == todayCal.get(Calendar.MONTH)
        }.sumOf { it.grandTotal }
    }
    val pendingUdhaar = remember(allInvoices) { allInvoices.sumOf { it.balanceDue } }

    fun runInvoicePdf(invoice: Invoice, printDirectly: Boolean) {
        scope.launch {
            PdfInvoiceGenerator.generateInvoicePdf(context, invoice)
                .onSuccess { file ->
                    if (printDirectly) {
                        PdfInvoiceGenerator.printPdfFile(context, file, "Invoice_${invoice.invoiceNumber}")
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
            title = "Billing & Udhaar",
            subtitle = "GST Invoices, Cash Memos & Party Dues",
            trailingAction = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExcelExportHeaderChip(
                        enabled = filteredInvoices.isNotEmpty(),
                        testTagName = "admin_billing_export_excel_button",
                        onClick = { showExcelExportDialog = true }
                    )
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onPrintCatalogPdf)
                            .testTag("billing_catalog_pdf_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = "Catalog PDF", tint = PureWhite, modifier = Modifier.size(16.dp))
                            Text("Catalog", style = MaterialTheme.typography.labelSmall, color = PureWhite)
                        }
                    }
                    Surface(
                        color = Slate800,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable(onClick = onOpenBusinessSettings)
                            .testTag("billing_settings_shortcut_button")
                    ) {
                        Box(modifier = Modifier.padding(8.dp)) {
                            Icon(Icons.Filled.Settings, contentDescription = "Business Settings", tint = PureWhite, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            },
            bottomContent = {
                // 3-card Billing KPI strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BillingKpiCard("Today", AutoTradeRepository.formatInr(todayTotal), PureWhite, Modifier.weight(1f))
                    BillingKpiCard("This Month", AutoTradeRepository.formatInr(monthTotal), Emerald600, Modifier.weight(1f))
                    BillingKpiCard(
                        "Pending Udhaar",
                        AutoTradeRepository.formatInr(pendingUdhaar),
                        if (pendingUdhaar > 0.0) Danger600 else Emerald600,
                        Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search shop name or INV number…", color = Slate400) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Slate400) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Slate400)
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
                        .testTag("billing_search_input")
                )
            }
        )

        // Status filter chips + Create Manual Bill CTA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("All (${allInvoices.size})") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = PureWhite)
            )
            PaymentStatus.entries.forEach { st ->
                val count = allInvoices.count { it.paymentStatusEnum() == st }
                FilterChip(
                    selected = statusFilter == st,
                    onClick = { statusFilter = st },
                    label = { Text("${st.label} ($count)") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Slate900, selectedLabelColor = PureWhite)
                )
            }
        }
        HorizontalDivider(color = Slate200)
        if (allInvoices.isNotEmpty()) {
            FilteredExcelExportStrip(
                summaryText = "Showing ${filteredInvoices.size} of ${allInvoices.size} bills • Due ${AutoTradeRepository.formatInr(filteredInvoices.sumOf { it.balanceDue })}",
                hasRows = filteredInvoices.isNotEmpty(),
                testTagName = "admin_billing_export_strip_button",
                onExportClick = { showExcelExportDialog = true }
            )
            HorizontalDivider(color = Slate200)
        }

        when (invoicesState) {
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            is UiState.Error -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                EmptyStateCard(Icons.Filled.Receipt, "Could not load invoices", invoicesState.message)
            }
            is UiState.Success -> {
                val filtered = filteredInvoices

                if (filtered.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                        EmptyStateCard(
                            icon = Icons.Filled.Receipt,
                            title = if (allInvoices.isEmpty()) "No bills created yet" else "No matching bills",
                            subtitle = if (allInvoices.isEmpty())
                                "Create a manual GST/Cash Memo bill below or tap 'Generate Bill' on any order."
                            else "Try clearing your search filter.",
                            action = {
                                Button(
                                    onClick = onCreateManualBill,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create Manual Bill")
                                }
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.id }) { invoice ->
                            AdminInvoiceCard(
                                invoice = invoice,
                                isPendingSync = invoice.id in pendingInvoiceIds,
                                onViewDetail = { detailTarget = invoice },
                                onRecordPayment = { paymentTarget = invoice },
                                onOpenPdf = { runInvoicePdf(invoice, printDirectly = false) },
                                onPrintPdf = { runInvoicePdf(invoice, printDirectly = true) },
                                onWhatsApp = {
                                    val msg = AutoTradeRepository.buildWhatsAppInvoiceSummary(invoice)
                                    launchWhatsAppOrder(context, invoice.phone, msg)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    paymentTarget?.let { inv ->
        RecordPaymentDialog(
            invoice = inv,
            isBusy = isBusy,
            onConfirm = { amount, mode, date, note ->
                onRecordPayment(inv, amount, mode, date, note) {
                    paymentTarget = null
                }
            },
            onDismiss = { paymentTarget = null }
        )
    }

    detailTarget?.let { inv ->
        // Always show freshest version from live allInvoices list
        val liveInv = allInvoices.firstOrNull { it.id == inv.id } ?: inv
        InvoiceDetailDialog(
            invoice = liveInv,
            isAdmin = true,
            onRecordPayment = { paymentTarget = liveInv },
            onOpenPdf = { runInvoicePdf(liveInv, printDirectly = false) },
            onPrintPdf = { runInvoicePdf(liveInv, printDirectly = true) },
            onSharePdf = {
                scope.launch {
                    PdfInvoiceGenerator.generateInvoicePdf(context, liveInv)
                        .onSuccess { file -> PdfInvoiceGenerator.sharePdfFile(context, file, "Invoice ${liveInv.invoiceNumber}") }
                        .onFailure { onNotify("Failed to share PDF") }
                }
            },
            onWhatsApp = {
                val msg = AutoTradeRepository.buildWhatsAppInvoiceSummary(liveInv)
                launchWhatsAppOrder(context, liveInv.phone, msg)
            },
            onDelete = {
                detailTarget = null
                deleteTarget = liveInv
            },
            onDismiss = { detailTarget = null }
        )
    }

    deleteTarget?.let { inv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Invoice ${inv.invoiceNumber}?") },
            text = { Text("Permanently remove this invoice for ${inv.shopName}? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteInvoice(inv)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Danger600)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }

    if (showExcelExportDialog) {
        ExcelExportActionDialog(
            title = "Export Billing to Excel",
            filterSummary = activeFilterSummary,
            sheetsIncluded = listOf("Invoices Summary", "Line Items", "Payment History"),
            generateFile = {
                ExcelReportExporter.exportInvoicesToXlsx(
                    context = context,
                    invoices = filteredInvoices,
                    businessName = businessName,
                    reportTitle = "Billing & Invoices Report",
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
private fun BillingKpiCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Slate800,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = Slate400, maxLines = 1)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AdminInvoiceCard(
    invoice: Invoice,
    isPendingSync: Boolean = false,
    onViewDetail: () -> Unit,
    onRecordPayment: () -> Unit,
    onOpenPdf: () -> Unit,
    onPrintPdf: () -> Unit,
    onWhatsApp: () -> Unit
) {
    val payStatus = invoice.paymentStatusEnum()
    val (bg, fg) = paymentStatusColors(payStatus)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = PureWhite,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Slate200, RoundedCornerShape(16.dp))
            .clickable(onClick = onViewDetail)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = invoice.invoiceNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Slate900
                        )
                        StatusPill(
                            text = if (invoice.billType.startsWith("GST")) "GST" else "Estimate",
                            containerColor = Cobalt100,
                            contentColor = Cobalt600
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = invoice.shopName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Slate800,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${invoice.city.ifBlank { "Walk-in" }} • ${formatTimestamp(invoice.createdAt)} • ${invoice.items.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate600
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatusPill(text = payStatus.label, containerColor = bg, contentColor = fg)
                    if (isPendingSync) {
                        StatusPill(
                            text = "Pending Sync",
                            containerColor = Amber100,
                            contentColor = Amber600
                        )
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
                    Text("Grand Total", style = MaterialTheme.typography.labelSmall, color = Slate600)
                    PriceText(AutoTradeRepository.formatInr(invoice.grandTotal), fontSizeSp = 17, color = Slate900)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Paid", style = MaterialTheme.typography.labelSmall, color = Slate600)
                    PriceText(AutoTradeRepository.formatInr(invoice.paidAmount), fontSizeSp = 15, color = Emerald600)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Balance Due", style = MaterialTheme.typography.labelSmall, color = Slate600)
                    PriceText(
                        AutoTradeRepository.formatInr(invoice.balanceDue),
                        fontSizeSp = 16,
                        color = if (invoice.balanceDue > 0.0) Danger600 else Emerald600
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (invoice.balanceDue > 0.0) {
                    Button(
                        onClick = onRecordPayment,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Filled.CurrencyRupee, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Record Payment", style = MaterialTheme.typography.labelMedium)
                    }
                }
                OutlinedButton(
                    onClick = onOpenPdf,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onPrintPdf,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Print", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onWhatsApp,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("WhatsApp", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun RecordPaymentDialog(
    invoice: Invoice,
    isBusy: Boolean,
    onConfirm: (amount: Double, mode: PaymentMode, date: String, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    val today = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()) }
    var amountText by remember(invoice) {
        mutableStateOf(
            if (invoice.balanceDue % 1.0 == 0.0) invoice.balanceDue.toLong().toString()
            else String.format(Locale.US, "%.2f", invoice.balanceDue)
        )
    }
    var mode by remember { mutableStateOf(PaymentMode.UPI) }
    var date by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment • ${invoice.invoiceNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "${invoice.shopName} • Due: ${AutoTradeRepository.formatInr(invoice.balanceDue)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slate600
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) amountText = it.take(10) },
                    label = { Text("Amount Received (₹) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("record_payment_amount_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PaymentMode.entries.forEach { m ->
                        FilterChip(
                            selected = mode == m,
                            onClick = { mode = m },
                            label = { Text(m.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Slate900,
                                selectedLabelColor = PureWhite
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(40) },
                    label = { Text("Payment Date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    label = { Text("Note / UTR Ref (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (amt > 0.0) onConfirm(amt, mode, date.ifBlank { today }, note.trim())
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                modifier = Modifier.testTag("confirm_record_payment_button")
            ) {
                Text("Save Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun InvoiceDetailDialog(
    invoice: Invoice,
    isAdmin: Boolean,
    onRecordPayment: (() -> Unit)? = null,
    onOpenPdf: () -> Unit,
    onPrintPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onWhatsApp: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val payStatus = invoice.paymentStatusEnum()
    val (bg, fg) = paymentStatusColors(payStatus)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(invoice.invoiceNumber, style = MaterialTheme.typography.titleLarge)
                    Text(invoice.billType, style = MaterialTheme.typography.bodySmall, color = Slate600)
                }
                StatusPill(payStatus.label, bg, fg)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(invoice.shopName, style = MaterialTheme.typography.titleMedium, color = Slate900)
                val meta = listOfNotNull(
                    invoice.city.takeIf { it.isNotBlank() },
                    invoice.phone.takeIf { it.isNotBlank() },
                    invoice.shopGstin.takeIf { it.isNotBlank() }?.let { "GSTIN: $it" }
                ).joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = Slate600)
                }
                Text(formatTimestamp(invoice.createdAt), style = MaterialTheme.typography.labelSmall, color = Slate400)

                HorizontalDivider(color = Slate200)

                invoice.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.bodyMedium, color = Slate900)
                            Text(
                                text = buildString {
                                    append("${item.qty} × ${AutoTradeRepository.formatInr(item.rate)}")
                                    if (item.hsnCode.isNotBlank()) append(" • HSN ${item.hsnCode}")
                                    if (invoice.billType.startsWith("GST")) append(" • ${item.gstPercent}% GST")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = Slate600
                            )
                        }
                        Text(
                            AutoTradeRepository.formatInr(item.lineTotal),
                            style = MaterialTheme.typography.labelLarge,
                            color = Slate900
                        )
                    }
                }

                HorizontalDivider(color = Slate200)

                DetailAmountRow("Subtotal", AutoTradeRepository.formatInr(invoice.subtotal))
                if (invoice.discountAmount > 0.0) {
                    DetailAmountRow("Discount", "- ${AutoTradeRepository.formatInr(invoice.discountAmount)}")
                }
                if (invoice.taxAmount > 0.0) {
                    if (invoice.taxTypeEnum() == TaxType.CGST_SGST) {
                        DetailAmountRow("CGST", AutoTradeRepository.formatInr(invoice.taxAmount / 2.0))
                        DetailAmountRow("SGST", AutoTradeRepository.formatInr(invoice.taxAmount / 2.0))
                    } else {
                        DetailAmountRow("IGST", AutoTradeRepository.formatInr(invoice.taxAmount))
                    }
                }
                if (invoice.transportCharges > 0.0) {
                    DetailAmountRow("Transport / Packing", AutoTradeRepository.formatInr(invoice.transportCharges))
                }
                DetailAmountRow("Grand Total", AutoTradeRepository.formatInr(invoice.grandTotal), bold = true)
                DetailAmountRow("Paid", AutoTradeRepository.formatInr(invoice.paidAmount), valueColor = Emerald600)
                DetailAmountRow(
                    "Balance Due",
                    AutoTradeRepository.formatInr(invoice.balanceDue),
                    bold = true,
                    valueColor = if (invoice.balanceDue > 0.0) Danger600 else Emerald600
                )

                if (invoice.firmUpiId.isNotBlank()) {
                    Surface(
                        color = Slate100,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "UPI ID: ${invoice.firmUpiId}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Slate900,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (invoice.paymentHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Payment History", style = MaterialTheme.typography.labelMedium, color = Slate900)
                    invoice.paymentHistory.forEach { pay ->
                        Text(
                            text = "• ${pay.date}: ${AutoTradeRepository.formatInr(pay.amount)} (${pay.mode})${if (pay.note.isNotBlank()) " - ${pay.note}" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate600
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = Slate900),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF")
                    }
                    Button(
                        onClick = onPrintPdf,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSharePdf,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share PDF")
                    }
                    if (onWhatsApp != null) {
                        OutlinedButton(
                            onClick = onWhatsApp,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppGreen),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("WhatsApp")
                        }
                    }
                }

                if (isAdmin && onRecordPayment != null && invoice.balanceDue > 0.0) {
                    Button(
                        onClick = onRecordPayment,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CurrencyRupee, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Record Payment")
                    }
                }

                if (isAdmin && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Danger600),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete Invoice")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun DetailAmountRow(
    label: String,
    value: String,
    bold: Boolean = false,
    valueColor: Color = Slate900
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            color = Slate800
        )
        Text(
            text = value,
            style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}

/**
 * Reusable Full Catalog PDF generator dialog for both Wholesaler Admin & Approved Shopkeepers.
 */
@Composable
fun CatalogPdfActionDialog(
    businessName: String,
    phone: String,
    city: String,
    products: List<Product>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var generatedFile by remember { mutableStateOf<File?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(products) {
        PdfInvoiceGenerator.generateCatalogPdf(
            context = context,
            businessName = businessName,
            phone = phone,
            city = city,
            products = products
        ).onSuccess { file ->
            generatedFile = file
        }.onFailure { err ->
            errorMsg = err.localizedMessage ?: "Failed to generate catalog PDF."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wholesale Catalog PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    errorMsg != null -> {
                        Text(errorMsg!!, color = Danger600, style = MaterialTheme.typography.bodyMedium)
                    }
                    generatedFile == null -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                            Text(
                                "Generating A4 PDF catalog (${products.size} ${if (products.size == 1) "Item" else "Items"})…",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    else -> {
                        val file = generatedFile!!
                        Text(
                            text = "Catalog PDF ready (${products.size} products grouped by category with photos, MOQ, HSN/GST & wholesale rates).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Slate800
                        )
                        Button(
                            onClick = {
                                PdfInvoiceGenerator.printPdfFile(context, file, "AutoTrade_Catalog")
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("print_catalog_pdf_confirm_button")
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Print Full Catalog")
                        }
                        OutlinedButton(
                            onClick = {
                                PdfInvoiceGenerator.openPdfFile(context, file)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open / Save PDF")
                        }
                        OutlinedButton(
                            onClick = {
                                PdfInvoiceGenerator.sharePdfFile(context, file, "$businessName Wholesale Catalog")
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Share PDF (WhatsApp / Email)")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
