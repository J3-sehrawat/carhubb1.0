package com.example.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generates standards-compliant Office Open XML (.xlsx) spreadsheets for offline B2B reporting:
 * 1. Billing & Invoices Report (3 sheets: Invoices Summary, Line Items, Payment History)
 * 2. Wholesale Orders Report (2 sheets: Orders Summary, Order Line Items)
 *
 * Pure Kotlin OOXML writer using java.util.zip.ZipOutputStream — zero heavy dependencies and
 * zero dangerous storage permissions (uses MediaStore.Downloads on API 29+, SAF CreateDocument,
 * and FileProvider for Open/Share).
 */
object ExcelReportExporter {

    const val XLSX_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    private fun getExportCacheDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun sanitizeFileSegment(raw: String): String =
        raw.trim()
            .replace(Regex("[^a-zA-Z0-9_-]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "All" }

    private fun timestampFileSlug(): String =
        SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    private fun formatDisplayDate(date: Date?): String {
        if (date == null) return ""
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(date)
    }

    private fun formatShortDate(date: Date?): String {
        if (date == null) return ""
        return SimpleDateFormat("dd MMM yyyy", Locale.US).format(date)
    }

    /**
     * Builds a 3-worksheet .xlsx report for a filtered list of Invoices.
     */
    suspend fun exportInvoicesToXlsx(
        context: Context,
        invoices: List<Invoice>,
        businessName: String,
        reportTitle: String,
        filterLabel: String,
        filterSummary: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (invoices.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No invoices match the current filter to export."))
            }
            val safeFilter = sanitizeFileSegment(filterLabel)
            val file = File(
                getExportCacheDir(context),
                "AutoTrade_Billing_${safeFilter}_${timestampFileSlug()}.xlsx"
            )
            FileOutputStream(file).use { out ->
                writeInvoicesWorkbook(
                    outputStream = out,
                    invoices = invoices,
                    businessName = businessName,
                    reportTitle = reportTitle,
                    filterSummary = filterSummary
                )
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Builds a 2-worksheet .xlsx report for a filtered list of Orders.
     */
    suspend fun exportOrdersToXlsx(
        context: Context,
        orders: List<Order>,
        invoices: List<Invoice>,
        businessName: String,
        reportTitle: String,
        filterLabel: String,
        filterSummary: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (orders.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No orders match the current filter to export."))
            }
            val safeFilter = sanitizeFileSegment(filterLabel)
            val file = File(
                getExportCacheDir(context),
                "AutoTrade_Orders_${safeFilter}_${timestampFileSlug()}.xlsx"
            )
            FileOutputStream(file).use { out ->
                writeOrdersWorkbook(
                    outputStream = out,
                    orders = orders,
                    invoices = invoices,
                    businessName = businessName,
                    reportTitle = reportTitle,
                    filterSummary = filterSummary
                )
            }
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Pure JVM writer for the 3-sheet Invoices workbook (also directly unit-testable).
     */
    fun writeInvoicesWorkbook(
        outputStream: OutputStream,
        invoices: List<Invoice>,
        businessName: String,
        reportTitle: String,
        filterSummary: String,
        generatedAt: Date = Date()
    ) {
        val firm = businessName.ifBlank { "AutoTrade Wholesale" }
        val generatedStr = formatDisplayDate(generatedAt)
        val metaLine = "$filterSummary  |  Generated: $generatedStr"

        val wb = XlsxWorkbookBuilder()

        // --- SHEET 1: Invoices Summary ---
        val summaryHeaders = listOf(
            "Invoice No.",
            "Date",
            "Bill Type",
            "Shop Name",
            "City",
            "Phone",
            "Shop GSTIN",
            "Order Ref",
            "Items",
            "Tax Type",
            "Subtotal (INR)",
            "Discount (INR)",
            "CGST (INR)",
            "SGST (INR)",
            "IGST (INR)",
            "Total Tax (INR)",
            "Transport (INR)",
            "Grand Total (INR)",
            "Paid Amount (INR)",
            "Balance Due (INR)",
            "Payment Status"
        )
        var totalItemsCount = 0L
        var sumSubtotal = 0.0
        var sumDiscount = 0.0
        var sumCgst = 0.0
        var sumSgst = 0.0
        var sumIgst = 0.0
        var sumTax = 0.0
        var sumTransport = 0.0
        var sumGrandTotal = 0.0
        var sumPaid = 0.0
        var sumBalance = 0.0

        val summaryRows = invoices.map { inv ->
            val isCgstSgst = inv.taxTypeEnum() == TaxType.CGST_SGST && inv.taxAmount > 0.0
            val isIgst = inv.taxTypeEnum() == TaxType.IGST && inv.taxAmount > 0.0
            val cgst = if (isCgstSgst) inv.taxAmount / 2.0 else 0.0
            val sgst = if (isCgstSgst) inv.taxAmount / 2.0 else 0.0
            val igst = if (isIgst) inv.taxAmount else 0.0
            val itemCount = inv.items.size.toLong()

            totalItemsCount += itemCount
            sumSubtotal += inv.subtotal
            sumDiscount += inv.discountAmount
            sumCgst += cgst
            sumSgst += sgst
            sumIgst += igst
            sumTax += inv.taxAmount
            sumTransport += inv.transportCharges
            sumGrandTotal += inv.grandTotal
            sumPaid += inv.paidAmount
            sumBalance += inv.balanceDue

            listOf(
                XlsxCell.Text(inv.invoiceNumber),
                XlsxCell.Text(formatShortDate(inv.createdAt?.toDate())),
                XlsxCell.Text(inv.billType),
                XlsxCell.Text(inv.shopName),
                XlsxCell.Text(inv.city.ifBlank { "Walk-in" }),
                XlsxCell.Text(inv.phone),
                XlsxCell.Text(inv.shopGstin),
                XlsxCell.Text(if (inv.orderId.isNotBlank()) "#${inv.orderId.takeLast(6).uppercase(Locale.US)}" else "-"),
                XlsxCell.Integer(itemCount),
                XlsxCell.Text(inv.taxType),
                XlsxCell.Currency(inv.subtotal),
                XlsxCell.Currency(inv.discountAmount),
                XlsxCell.Currency(cgst),
                XlsxCell.Currency(sgst),
                XlsxCell.Currency(igst),
                XlsxCell.Currency(inv.taxAmount),
                XlsxCell.Currency(inv.transportCharges),
                XlsxCell.Currency(inv.grandTotal),
                XlsxCell.Currency(inv.paidAmount),
                XlsxCell.Currency(inv.balanceDue),
                XlsxCell.Text(inv.paymentStatus)
            )
        }

        val summaryTotalsRow = listOf(
            XlsxCell.TotalText("TOTAL (${invoices.size})"),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalInteger(totalItemsCount),
            XlsxCell.TotalText(""),
            XlsxCell.TotalCurrency(sumSubtotal),
            XlsxCell.TotalCurrency(sumDiscount),
            XlsxCell.TotalCurrency(sumCgst),
            XlsxCell.TotalCurrency(sumSgst),
            XlsxCell.TotalCurrency(sumIgst),
            XlsxCell.TotalCurrency(sumTax),
            XlsxCell.TotalCurrency(sumTransport),
            XlsxCell.TotalCurrency(sumGrandTotal),
            XlsxCell.TotalCurrency(sumPaid),
            XlsxCell.TotalCurrency(sumBalance),
            XlsxCell.TotalText("")
        )

        wb.addSheet(
            name = "Invoices Summary",
            title = "$firm — $reportTitle",
            subtitle = metaLine,
            headers = summaryHeaders,
            rows = summaryRows,
            totalsRow = summaryTotalsRow
        )

        // --- SHEET 2: Line Items ---
        val lineItemHeaders = listOf(
            "Invoice No.",
            "Date",
            "Shop Name",
            "Bill Type",
            "Item Name",
            "HSN Code",
            "Qty",
            "Rate (INR)",
            "GST %",
            "Line Total (INR)"
        )
        var totalLineQty = 0L
        var totalLineValue = 0.0
        val lineItemRows = mutableListOf<List<XlsxCell>>()
        invoices.forEach { inv ->
            val dateText = formatShortDate(inv.createdAt?.toDate())
            inv.items.forEach { item ->
                totalLineQty += item.qty
                totalLineValue += item.lineTotal
                lineItemRows.add(
                    listOf(
                        XlsxCell.Text(inv.invoiceNumber),
                        XlsxCell.Text(dateText),
                        XlsxCell.Text(inv.shopName),
                        XlsxCell.Text(inv.billType),
                        XlsxCell.Text(item.name),
                        XlsxCell.Text(item.hsnCode.ifBlank { "-" }),
                        XlsxCell.Integer(item.qty),
                        XlsxCell.Currency(item.rate),
                        XlsxCell.Integer(item.gstPercent),
                        XlsxCell.Currency(item.lineTotal)
                    )
                )
            }
        }
        val lineItemTotals = if (lineItemRows.isNotEmpty()) {
            listOf(
                XlsxCell.TotalText("TOTAL (${lineItemRows.size} lines)"),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalInteger(totalLineQty),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalCurrency(totalLineValue)
            )
        } else null

        wb.addSheet(
            name = "Line Items",
            title = "$firm — Invoice Line Items",
            subtitle = metaLine,
            headers = lineItemHeaders,
            rows = lineItemRows,
            totalsRow = lineItemTotals
        )

        // --- SHEET 3: Payment History ---
        val paymentHeaders = listOf(
            "Invoice No.",
            "Shop Name",
            "Payment Date",
            "Mode",
            "Amount Received (INR)",
            "Note / UTR Reference"
        )
        var totalPaymentsReceived = 0.0
        val paymentRows = mutableListOf<List<XlsxCell>>()
        invoices.forEach { inv ->
            inv.paymentHistory.forEach { pay ->
                totalPaymentsReceived += pay.amount
                paymentRows.add(
                    listOf(
                        XlsxCell.Text(inv.invoiceNumber),
                        XlsxCell.Text(inv.shopName),
                        XlsxCell.Text(pay.date),
                        XlsxCell.Text(pay.mode),
                        XlsxCell.Currency(pay.amount),
                        XlsxCell.Text(pay.note)
                    )
                )
            }
        }
        val paymentTotals = if (paymentRows.isNotEmpty()) {
            listOf(
                XlsxCell.TotalText("TOTAL (${paymentRows.size} payments)"),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalCurrency(totalPaymentsReceived),
                XlsxCell.TotalText("")
            )
        } else null

        wb.addSheet(
            name = "Payment History",
            title = "$firm — Payment Ledger",
            subtitle = metaLine,
            headers = paymentHeaders,
            rows = paymentRows,
            totalsRow = paymentTotals
        )

        wb.writeTo(outputStream)
    }

    /**
     * Pure JVM writer for the 2-sheet Orders workbook (also directly unit-testable).
     */
    fun writeOrdersWorkbook(
        outputStream: OutputStream,
        orders: List<Order>,
        invoices: List<Invoice>,
        businessName: String,
        reportTitle: String,
        filterSummary: String,
        generatedAt: Date = Date()
    ) {
        val firm = businessName.ifBlank { "AutoTrade Wholesale" }
        val generatedStr = formatDisplayDate(generatedAt)
        val metaLine = "$filterSummary  |  Generated: $generatedStr"

        val invoiceById = invoices.associateBy { it.id }
        val invoiceByOrderId = invoices.filter { it.orderId.isNotBlank() }.associateBy { it.orderId }

        val wb = XlsxWorkbookBuilder()

        // --- SHEET 1: Orders Summary ---
        val orderHeaders = listOf(
            "Order ID",
            "Order Date",
            "Shop Name",
            "City",
            "Phone",
            "Status",
            "Invoice No.",
            "Line Items",
            "Total Qty",
            "Total Amount (INR)",
            "Items Summary"
        )
        var sumLineItems = 0L
        var sumQty = 0L
        var sumAmount = 0.0

        val orderRows = orders.map { order ->
            val linkedInvoice = (if (order.invoiceId.isNotBlank()) invoiceById[order.invoiceId] else null)
                ?: invoiceByOrderId[order.id]
            val lineCount = order.items.size.toLong()
            val qtySum = order.items.sumOf { it.qty }
            val itemsSummary = order.items.joinToString("; ") { item ->
                val model = if (item.carModel.isNotBlank()) " (${item.carModel})" else ""
                "${item.name}$model x${item.qty}"
            }

            sumLineItems += lineCount
            sumQty += qtySum
            sumAmount += order.totalAmount

            listOf(
                XlsxCell.Text("#${order.id.takeLast(6).uppercase(Locale.US)}"),
                XlsxCell.Text(formatDisplayDate(order.createdAt?.toDate())),
                XlsxCell.Text(order.shopName),
                XlsxCell.Text(order.city),
                XlsxCell.Text(order.phone),
                XlsxCell.Text(order.orderStatus().label),
                XlsxCell.Text(linkedInvoice?.invoiceNumber ?: "Not Billed"),
                XlsxCell.Integer(lineCount),
                XlsxCell.Integer(qtySum),
                XlsxCell.Currency(order.totalAmount),
                XlsxCell.Text(itemsSummary)
            )
        }

        val orderTotals = listOf(
            XlsxCell.TotalText("TOTAL (${orders.size})"),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalText(""),
            XlsxCell.TotalInteger(sumLineItems),
            XlsxCell.TotalInteger(sumQty),
            XlsxCell.TotalCurrency(sumAmount),
            XlsxCell.TotalText("")
        )

        wb.addSheet(
            name = "Orders Summary",
            title = "$firm — $reportTitle",
            subtitle = metaLine,
            headers = orderHeaders,
            rows = orderRows,
            totalsRow = orderTotals
        )

        // --- SHEET 2: Order Line Items ---
        val itemHeaders = listOf(
            "Order ID",
            "Order Date",
            "Shop Name",
            "City",
            "Status",
            "Product Name",
            "Car Model",
            "Qty",
            "Unit Price (INR)",
            "Line Total (INR)"
        )
        var allItemQty = 0L
        var allItemTotal = 0.0
        val itemRows = mutableListOf<List<XlsxCell>>()
        orders.forEach { order ->
            val shortId = "#${order.id.takeLast(6).uppercase(Locale.US)}"
            val dateText = formatShortDate(order.createdAt?.toDate())
            val statusLabel = order.orderStatus().label
            order.items.forEach { item ->
                val lineTotal = item.lineTotal()
                allItemQty += item.qty
                allItemTotal += lineTotal
                itemRows.add(
                    listOf(
                        XlsxCell.Text(shortId),
                        XlsxCell.Text(dateText),
                        XlsxCell.Text(order.shopName),
                        XlsxCell.Text(order.city),
                        XlsxCell.Text(statusLabel),
                        XlsxCell.Text(item.name),
                        XlsxCell.Text(item.carModel.ifBlank { "Universal" }),
                        XlsxCell.Integer(item.qty),
                        XlsxCell.Currency(item.price),
                        XlsxCell.Currency(lineTotal)
                    )
                )
            }
        }
        val itemTotals = if (itemRows.isNotEmpty()) {
            listOf(
                XlsxCell.TotalText("TOTAL (${itemRows.size} lines)"),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalText(""),
                XlsxCell.TotalInteger(allItemQty),
                XlsxCell.TotalText(""),
                XlsxCell.TotalCurrency(allItemTotal)
            )
        } else null

        wb.addSheet(
            name = "Order Line Items",
            title = "$firm — Order Line Items Breakdown",
            subtitle = metaLine,
            headers = itemHeaders,
            rows = itemRows,
            totalsRow = itemTotals
        )

        wb.writeTo(outputStream)
    }

    /**
     * Saves the generated .xlsx file directly into the device Downloads folder on Android 10+ (API 29+)
     * with zero storage permissions required. Returns null on API < 29 so the caller can invoke SAF.
     */
    suspend fun saveToDownloads(context: Context, sourceFile: File): Result<String> =
        withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return@withContext Result.failure(UnsupportedOperationException("Use Save As on Android 9 and below."))
            }
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sourceFile.name)
                    put(MediaStore.Downloads.MIME_TYPE, XLSX_MIME_TYPE)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AutoTrade")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to create Downloads entry."))

                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(sourceFile).use { input ->
                            input.copyTo(out)
                        }
                    } ?: return@withContext Result.failure(IllegalStateException("Unable to open Downloads output stream."))

                    val publishValues = ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }
                    resolver.update(uri, publishValues, null, null)
                    Result.success("Downloads/AutoTrade/${sourceFile.name}")
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    Result.failure(e)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Copies the generated .xlsx file to any Storage Access Framework URI returned by CreateDocument.
     */
    suspend fun copyToDocumentUri(context: Context, sourceFile: File, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val out = context.contentResolver.openOutputStream(targetUri)
                    ?: return@withContext Result.failure(IllegalStateException("Unable to open destination file."))
                out.use { output ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(output)
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun openXlsxFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, XLSX_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(viewIntent, "Open Excel Report"))
        } catch (_: Exception) {
            shareXlsxFile(context, file, file.nameWithoutExtension)
        }
    }

    fun shareXlsxFile(context: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "Share Excel (.xlsx)"))
        } catch (_: Exception) {
            Toast.makeText(context, "No app available to share Excel file", Toast.LENGTH_SHORT).show()
        }
    }
}

sealed interface XlsxCell {
    data class Text(val value: String) : XlsxCell
    data class Integer(val value: Long) : XlsxCell
    data class Currency(val value: Double) : XlsxCell
    data class TotalText(val value: String) : XlsxCell
    data class TotalInteger(val value: Long) : XlsxCell
    data class TotalCurrency(val value: Double) : XlsxCell
}

/**
 * Minimal, standards-compliant OOXML (.xlsx) workbook builder supporting multiple styled sheets,
 * shared string table, INR currency formatting, frozen header rows, auto-filters, and auto-widths.
 */
class XlsxWorkbookBuilder {
    private data class SheetSpec(
        val name: String,
        val title: String,
        val subtitle: String,
        val headers: List<String>,
        val rows: List<List<XlsxCell>>,
        val totalsRow: List<XlsxCell>?
    )

    private val sheets = mutableListOf<SheetSpec>()
    private val sharedStrings = LinkedHashMap<String, Int>()

    fun addSheet(
        name: String,
        title: String,
        subtitle: String,
        headers: List<String>,
        rows: List<List<XlsxCell>>,
        totalsRow: List<XlsxCell>? = null
    ) {
        val safeName = sanitizeSheetName(name, sheets.size + 1)
        sheets.add(SheetSpec(safeName, title, subtitle, headers, rows, totalsRow))
    }

    private fun internString(raw: String): Int {
        val clean = sanitizeXmlText(raw)
        return sharedStrings.getOrPut(clean) { sharedStrings.size }
    }

    fun writeTo(outputStream: OutputStream) {
        require(sheets.isNotEmpty()) { "Workbook must contain at least one worksheet." }

        // Pre-build worksheet XMLs so sharedStrings table is completely populated first
        val sheetXmls = sheets.mapIndexed { idx, spec ->
            buildWorksheetXml(spec, isPrimaryTab = idx == 0)
        }

        ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml())
            writeZipEntry(zip, "_rels/.rels", buildRootRelsXml())
            writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml())
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml())
            writeZipEntry(zip, "xl/styles.xml", buildStylesXml())
            writeZipEntry(zip, "xl/sharedStrings.xml", buildSharedStringsXml())
            sheetXmls.forEachIndexed { idx, xml ->
                writeZipEntry(zip, "xl/worksheets/sheet${idx + 1}.xml", xml)
            }
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildWorksheetXml(spec: SheetSpec, isPrimaryTab: Boolean): String {
        val colCount = spec.headers.size.coerceAtLeast(1)
        val colWidths = IntArray(colCount) { i ->
            spec.headers.getOrNull(i)?.length?.plus(4) ?: 12
        }

        fun trackWidth(colIdx: Int, textLen: Int) {
            if (colIdx in colWidths.indices) {
                val target = (textLen + 4).coerceIn(10, 44)
                if (target > colWidths[colIdx]) colWidths[colIdx] = target
            }
        }

        val sb = StringBuilder(4096)
        val rowsXml = StringBuilder(4096)

        // Row 1: Report Title
        val titleIdx = internString(spec.title)
        rowsXml.append("""<row r="1" ht="24" customHeight="1"><c r="A1" t="s" s="7"><v>$titleIdx</v></c></row>""")

        // Row 2: Subtitle / Filter Metadata
        val subIdx = internString(spec.subtitle)
        rowsXml.append("""<row r="2" ht="18" customHeight="1"><c r="A2" t="s" s="8"><v>$subIdx</v></c></row>""")

        // Row 4: Table Header
        rowsXml.append("""<row r="4" ht="22" customHeight="1">""")
        spec.headers.forEachIndexed { colIdx, header ->
            val ref = "${columnLetter(colIdx)}4"
            val sIdx = internString(header)
            rowsXml.append("""<c r="$ref" t="s" s="1"><v>$sIdx</v></c>""")
        }
        rowsXml.append("</row>")

        // Rows 5..: Data Rows
        var currentRow = 5
        spec.rows.forEachIndexed { rowOffset, cells ->
            val isZebra = rowOffset % 2 == 1
            rowsXml.append("""<row r="$currentRow">""")
            cells.forEachIndexed { colIdx, cell ->
                val ref = "${columnLetter(colIdx)}$currentRow"
                appendCell(rowsXml, ref, cell, isZebra) { len -> trackWidth(colIdx, len) }
            }
            rowsXml.append("</row>")
            currentRow++
        }

        val lastDataRow = (currentRow - 1).coerceAtLeast(4)

        // Totals Row
        spec.totalsRow?.let { totals ->
            rowsXml.append("""<row r="$currentRow" ht="22" customHeight="1">""")
            totals.forEachIndexed { colIdx, cell ->
                val ref = "${columnLetter(colIdx)}$currentRow"
                appendCell(rowsXml, ref, cell, isZebra = false) { len -> trackWidth(colIdx, len) }
            }
            rowsXml.append("</row>")
        }

        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("<sheetViews>")
        sb.append("""<sheetView tabSelected="${if (isPrimaryTab) 1 else 0}" workbookViewId="0">""")
        sb.append("""<pane ySplit="4" topLeftCell="A5" activePane="bottomLeft" state="frozen"/>""")
        sb.append("</sheetView>")
        sb.append("</sheetViews>")
        sb.append("""<sheetFormatPr defaultRowHeight="19"/>""")
        sb.append("<cols>")
        colWidths.forEachIndexed { idx, w ->
            val colNum = idx + 1
            sb.append("""<col min="$colNum" max="$colNum" width="$w" customWidth="1"/>""")
        }
        sb.append("</cols>")
        sb.append("<sheetData>")
        sb.append(rowsXml)
        sb.append("</sheetData>")
        val lastCol = columnLetter(colCount - 1)
        sb.append("""<autoFilter ref="A4:$lastCol$lastDataRow"/>""")
        sb.append("</worksheet>")
        return sb.toString()
    }

    private fun appendCell(
        sb: StringBuilder,
        ref: String,
        cell: XlsxCell,
        isZebra: Boolean,
        onMeasure: (Int) -> Unit
    ) {
        when (cell) {
            is XlsxCell.Text -> {
                val style = if (isZebra) 9 else 0
                val sIdx = internString(cell.value)
                onMeasure(cell.value.length)
                sb.append("""<c r="$ref" t="s" s="$style"><v>$sIdx</v></c>""")
            }
            is XlsxCell.Integer -> {
                val style = if (isZebra) 11 else 3
                val numStr = cell.value.toString()
                onMeasure(numStr.length + 2)
                sb.append("""<c r="$ref" s="$style"><v>$numStr</v></c>""")
            }
            is XlsxCell.Currency -> {
                val style = if (isZebra) 10 else 2
                val safe = if (cell.value.isFinite()) cell.value else 0.0
                val formatted = String.format(Locale.US, "%.2f", safe)
                onMeasure(formatted.length + 3)
                sb.append("""<c r="$ref" s="$style"><v>$formatted</v></c>""")
            }
            is XlsxCell.TotalText -> {
                val sIdx = internString(cell.value)
                onMeasure(cell.value.length)
                sb.append("""<c r="$ref" t="s" s="4"><v>$sIdx</v></c>""")
            }
            is XlsxCell.TotalInteger -> {
                val numStr = cell.value.toString()
                onMeasure(numStr.length + 2)
                sb.append("""<c r="$ref" s="6"><v>$numStr</v></c>""")
            }
            is XlsxCell.TotalCurrency -> {
                val safe = if (cell.value.isFinite()) cell.value else 0.0
                val formatted = String.format(Locale.US, "%.2f", safe)
                onMeasure(formatted.length + 3)
                sb.append("""<c r="$ref" s="5"><v>$formatted</v></c>""")
            }
        }
    }

    private fun buildContentTypesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("""<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""")
        sheets.indices.forEach { idx ->
            append("""<Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private fun buildRootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun buildWorkbookXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        sheets.forEachIndexed { idx, spec ->
            append("""<sheet name="${escapeXml(spec.name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
        }
        append("</sheets>")
        append("</workbook>")
    }

    private fun buildWorkbookRelsXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        sheets.indices.forEach { idx ->
            append("""<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>""")
        }
        val stylesId = sheets.size + 1
        val stringsId = sheets.size + 2
        append("""<Relationship Id="rId$stylesId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""<Relationship Id="rId$stringsId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""")
        append("</Relationships>")
    }

    private fun buildStylesXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
            """<numFmts count="2">""" +
            """<numFmt numFmtId="164" formatCode="&quot;₹&quot;#,##0.00"/>""" +
            """<numFmt numFmtId="165" formatCode="#,##0"/>""" +
            """</numFmts>""" +
            """<fonts count="5">""" +
            """<font><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="11"/><color rgb="FF0F172A"/><name val="Calibri"/></font>""" +
            """<font><b/><sz val="14"/><color rgb="FF0F172A"/><name val="Calibri"/></font>""" +
            """<font><sz val="10"/><color rgb="FF475569"/><name val="Calibri"/></font>""" +
            """</fonts>""" +
            """<fills count="5">""" +
            """<fill><patternFill patternType="none"/></fill>""" +
            """<fill><patternFill patternType="gray125"/></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FF0F172A"/><bgColor indexed="64"/></patternFill></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/><bgColor indexed="64"/></patternFill></fill>""" +
            """<fill><patternFill patternType="solid"><fgColor rgb="FFECFDF5"/><bgColor indexed="64"/></patternFill></fill>""" +
            """</fills>""" +
            """<borders count="3">""" +
            """<border><left/><right/><top/><bottom/><diagonal/></border>""" +
            """<border>""" +
            """<left style="thin"><color rgb="FFE2E8F0"/></left>""" +
            """<right style="thin"><color rgb="FFE2E8F0"/></right>""" +
            """<top style="thin"><color rgb="FFE2E8F0"/></top>""" +
            """<bottom style="thin"><color rgb="FFE2E8F0"/></bottom>""" +
            """<diagonal/>""" +
            """</border>""" +
            """<border>""" +
            """<left style="thin"><color rgb="FFE2E8F0"/></left>""" +
            """<right style="thin"><color rgb="FFE2E8F0"/></right>""" +
            """<top style="thin"><color rgb="FF0F172A"/></top>""" +
            """<bottom style="double"><color rgb="FF0F172A"/></bottom>""" +
            """<diagonal/>""" +
            """</border>""" +
            """</borders>""" +
            """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
            """<cellXfs count="12">""" +
            """<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            """<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            """<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
            """<xf numFmtId="165" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
            """<xf numFmtId="0" fontId="2" fillId="3" borderId="2" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            """<xf numFmtId="164" fontId="2" fillId="3" borderId="2" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
            """<xf numFmtId="165" fontId="2" fillId="3" borderId="2" xfId="0" applyNumberFormat="1" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
            """<xf numFmtId="0" fontId="3" fillId="4" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            """<xf numFmtId="0" fontId="4" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            """<xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""" +
            """<xf numFmtId="164" fontId="0" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
            """<xf numFmtId="165" fontId="0" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="right" vertical="center"/></xf>""" +
            """</cellXfs>""" +
            """</styleSheet>"""

    private fun buildSharedStringsXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${sharedStrings.size}" uniqueCount="${sharedStrings.size}">""")
        sharedStrings.keys.forEach { text ->
            val preserve = if (text.startsWith(" ") || text.endsWith(" ")) """ xml:space="preserve"""" else ""
            append("""<si><t$preserve>${escapeXml(text)}</t></si>""")
        }
        append("</sst>")
    }

    companion object {
        fun columnLetter(zeroBasedIndex: Int): String {
            var n = zeroBasedIndex
            val sb = StringBuilder()
            while (n >= 0) {
                sb.append(('A'.code + (n % 26)).toChar())
                n = (n / 26) - 1
            }
            return sb.reverse().toString()
        }

        private fun sanitizeSheetName(raw: String, fallbackIndex: Int): String {
            val cleaned = raw.replace(Regex("""[:\\/?*\[\]]"""), " ").trim().take(31)
            return cleaned.ifBlank { "Sheet$fallbackIndex" }
        }

        private fun sanitizeXmlText(raw: String): String = buildString(raw.length) {
            for (ch in raw) {
                val code = ch.code
                val validXmlChar = code == 0x9 || code == 0xA || code == 0xD ||
                    (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
                if (validXmlChar) append(ch)
            }
        }

        private fun escapeXml(raw: String): String = buildString(raw.length + 8) {
            for (ch in raw) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }
}
