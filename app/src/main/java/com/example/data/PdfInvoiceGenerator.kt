package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Generates professional A4 PDF documents for:
 * 1. GST Tax Invoices / Cash Memos (with tax breakup, payment ledger, UPI ID)
 * 2. Full Wholesale Product Catalogs (grouped by category with product photos, MOQ, HSN/GST & INR pricing)
 *
 * Uses Android's built-in PdfDocument + PrintManager + FileProvider (zero storage permissions needed).
 */
object PdfInvoiceGenerator {
    private const val PAGE_WIDTH = 595 // A4 width in PostScript points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in PostScript points
    private const val MARGIN = 32f

    private val colorSlate900 = Color.parseColor("#0F172A")
    private val colorSlate700 = Color.parseColor("#334155")
    private val colorSlate500 = Color.parseColor("#64748B")
    private val colorSlate200 = Color.parseColor("#E2E8F0")
    private val colorSlate100 = Color.parseColor("#F1F5F9")
    private val colorCrimson = Color.parseColor("#E11D48")
    private val colorEmerald = Color.parseColor("#059669")
    private val colorAmber = Color.parseColor("#D97706")

    private fun getPdfCacheDir(context: Context): File =
        File(context.cacheDir, "pdfs").apply { mkdirs() }

    suspend fun generateInvoicePdf(
        context: Context,
        invoice: Invoice
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val document = PdfDocument()
            var pageNum = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            var canvas = page.canvas

            val headerBg = Paint().apply { color = colorSlate900 }
            val accentBg = Paint().apply { color = colorCrimson }
            val boxFill = Paint().apply { color = colorSlate100 }
            val borderPaint = Paint().apply {
                color = colorSlate200
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val whiteSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                textSize = 9.5f
            }
            val boldDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorSlate900
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val regularDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorSlate700
                textSize = 9.5f
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorSlate500
                textSize = 8.5f
            }
            val rightBold = Paint(boldDark).apply { textAlign = Paint.Align.RIGHT }
            val rightRegular = Paint(regularDark).apply { textAlign = Paint.Align.RIGHT }

            val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                .format(invoice.createdAt?.toDate() ?: Date())

            // --- TOP HEADER BANNER ---
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 96f, headerBg)
            canvas.drawRect(0f, 92f, PAGE_WIDTH.toFloat(), 96f, accentBg)

            val firmName = invoice.firmName.ifBlank { "AutoTrade Wholesale" }
            canvas.drawText(firmName.take(36), MARGIN, 34f, titlePaint)
            var leftY = 50f
            if (invoice.firmAddress.isNotBlank()) {
                canvas.drawText(invoice.firmAddress.take(58), MARGIN, leftY, whiteSubPaint)
                leftY += 13f
            }
            val contactLine = buildString {
                if (invoice.firmPhone.isNotBlank()) append("Ph: ${invoice.firmPhone}")
                if (invoice.firmGstin.isNotBlank()) {
                    if (isNotEmpty()) append("  |  ")
                    append("GSTIN: ${invoice.firmGstin}")
                }
            }
            if (contactLine.isNotBlank()) {
                canvas.drawText(contactLine, MARGIN, leftY, whiteSubPaint)
            }

            // Right side Invoice badge & meta
            val badgeText = invoice.billType.uppercase(Locale.US)
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(badgeText, PAGE_WIDTH - MARGIN, 30f, badgePaint)
            val invNoPaint = Paint(titlePaint).apply {
                textSize = 15f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("#${invoice.invoiceNumber}", PAGE_WIDTH - MARGIN, 52f, invNoPaint)
            val datePaint = Paint(whiteSubPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText(dateStr, PAGE_WIDTH - MARGIN, 68f, datePaint)

            var y = 114f

            // --- BILLED TO PARTY BOX ---
            val partyBox = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 66f)
            canvas.drawRoundRect(partyBox, 6f, 6f, boxFill)
            canvas.drawRoundRect(partyBox, 6f, 6f, borderPaint)

            canvas.drawText("BILLED TO (PARTY)", MARGIN + 12f, y + 16f, mutedPaint)
            canvas.drawText(invoice.shopName.take(44), MARGIN + 12f, y + 33f, boldDark.apply { textSize = 12f })
            boldDark.textSize = 10.5f
            val partyMeta = listOfNotNull(
                invoice.city.takeIf { it.isNotBlank() },
                invoice.phone.takeIf { it.isNotBlank() }?.let { "Ph: $it" }
            ).joinToString("  •  ")
            if (partyMeta.isNotBlank()) {
                canvas.drawText(partyMeta, MARGIN + 12f, y + 48f, regularDark)
            }
            if (invoice.shopGstin.isNotBlank()) {
                canvas.drawText("GSTIN: ${invoice.shopGstin}", MARGIN + 12f, y + 60f, mutedPaint)
            }

            // Status pill on right of party box
            val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = when (invoice.paymentStatusEnum()) {
                    PaymentStatus.PAID -> colorEmerald
                    PaymentStatus.PARTIAL -> colorAmber
                    PaymentStatus.UNPAID -> colorCrimson
                }
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("STATUS: ${invoice.paymentStatus.uppercase(Locale.US)}", PAGE_WIDTH - MARGIN - 12f, y + 26f, statusPaint)
            if (invoice.orderId.isNotBlank()) {
                canvas.drawText("Order Ref: #${invoice.orderId.takeLast(6).uppercase(Locale.US)}", PAGE_WIDTH - MARGIN - 12f, y + 44f, rightRegular)
            }

            y += 82f

            // --- ITEMS TABLE HEADER ---
            val isGst = invoice.billTypeEnum() == BillType.GST_INVOICE
            fun drawTableHeader(c: Canvas, topY: Float): Float {
                c.drawRect(MARGIN, topY, PAGE_WIDTH - MARGIN, topY + 24f, headerBg)
                val thLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = 9f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                val thRight = Paint(thLeft).apply { textAlign = Paint.Align.RIGHT }
                c.drawText("#", MARGIN + 6f, topY + 15f, thLeft)
                c.drawText("ITEM DESCRIPTION", MARGIN + 26f, topY + 15f, thLeft)
                if (isGst) {
                    c.drawText("HSN", MARGIN + 248f, topY + 15f, thLeft)
                    c.drawText("QTY", MARGIN + 332f, topY + 15f, thRight)
                    c.drawText("RATE", MARGIN + 400f, topY + 15f, thRight)
                    c.drawText("GST", MARGIN + 445f, topY + 15f, thRight)
                } else {
                    c.drawText("QTY", MARGIN + 340f, topY + 15f, thRight)
                    c.drawText("RATE", MARGIN + 425f, topY + 15f, thRight)
                }
                c.drawText("AMOUNT", PAGE_WIDTH - MARGIN - 6f, topY + 15f, thRight)
                return topY + 24f
            }

            y = drawTableHeader(canvas, y)

            // --- ITEMS ROWS ---
            invoice.items.forEachIndexed { idx, item ->
                if (y > PAGE_HEIGHT - 160f) {
                    document.finishPage(page)
                    pageNum++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                    canvas = page.canvas
                    y = drawTableHeader(canvas, MARGIN)
                }

                if (idx % 2 == 1) {
                    canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 22f, boxFill)
                }
                canvas.drawLine(MARGIN, y + 22f, PAGE_WIDTH - MARGIN, y + 22f, borderPaint)

                canvas.drawText("${idx + 1}", MARGIN + 6f, y + 15f, regularDark)
                val maxNameChars = if (isGst) 34 else 44
                canvas.drawText(item.name.take(maxNameChars), MARGIN + 26f, y + 15f, boldDark.apply { textSize = 9.5f })
                boldDark.textSize = 10.5f

                if (isGst) {
                    canvas.drawText(item.hsnCode.ifBlank { "-" }.take(10), MARGIN + 248f, y + 15f, mutedPaint)
                    canvas.drawText("${item.qty}", MARGIN + 332f, y + 15f, rightRegular)
                    canvas.drawText(AutoTradeRepository.formatInr(item.rate), MARGIN + 400f, y + 15f, rightRegular)
                    canvas.drawText("${item.gstPercent}%", MARGIN + 445f, y + 15f, rightRegular)
                } else {
                    canvas.drawText("${item.qty}", MARGIN + 340f, y + 15f, rightRegular)
                    canvas.drawText(AutoTradeRepository.formatInr(item.rate), MARGIN + 425f, y + 15f, rightRegular)
                }
                canvas.drawText(AutoTradeRepository.formatInr(item.lineTotal), PAGE_WIDTH - MARGIN - 6f, y + 15f, rightBold)
                y += 22f
            }

            y += 16f
            if (y > PAGE_HEIGHT - 210f) {
                document.finishPage(page)
                pageNum++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }

            // --- TOTALS & UPI SECTION ---
            val leftColWidth = 250f
            val rightBoxLeft = MARGIN + leftColWidth + 16f

            // Left: UPI & Payment History box
            var leftBoxY = y
            if (invoice.firmUpiId.isNotBlank()) {
                val upiRect = RectF(MARGIN, leftBoxY, MARGIN + leftColWidth, leftBoxY + 48f)
                canvas.drawRoundRect(upiRect, 6f, 6f, boxFill)
                canvas.drawRoundRect(upiRect, 6f, 6f, borderPaint)
                canvas.drawText("PAY VIA UPI", MARGIN + 10f, leftBoxY + 16f, mutedPaint)
                canvas.drawText(invoice.firmUpiId, MARGIN + 10f, leftBoxY + 34f, boldDark)
                leftBoxY += 58f
            }

            if (invoice.paymentHistory.isNotEmpty()) {
                canvas.drawText("PAYMENT HISTORY", MARGIN, leftBoxY + 10f, mutedPaint)
                leftBoxY += 20f
                invoice.paymentHistory.take(6).forEach { pay ->
                    val entry = "• ${pay.date}: ${AutoTradeRepository.formatInr(pay.amount)} (${pay.mode})"
                    canvas.drawText(entry.take(42), MARGIN, leftBoxY, regularDark)
                    leftBoxY += 13f
                }
            }

            // Right: Financial Summary Box
            var sumY = y
            fun drawSumRow(label: String, value: String, bold: Boolean = false, color: Int = colorSlate900) {
                val lp = if (bold) Paint(boldDark).apply { this.color = color } else Paint(regularDark)
                val rp = if (bold) Paint(rightBold).apply { this.color = color } else Paint(rightRegular)
                canvas.drawText(label, rightBoxLeft, sumY + 12f, lp)
                canvas.drawText(value, PAGE_WIDTH - MARGIN, sumY + 12f, rp)
                sumY += 18f
            }

            drawSumRow("Subtotal", AutoTradeRepository.formatInr(invoice.subtotal))
            if (invoice.discountAmount > 0.0) {
                drawSumRow("Discount", "- ${AutoTradeRepository.formatInr(invoice.discountAmount)}")
            }
            if (invoice.taxAmount > 0.0) {
                when (invoice.taxTypeEnum()) {
                    TaxType.CGST_SGST -> {
                        val half = invoice.taxAmount / 2.0
                        drawSumRow("CGST", AutoTradeRepository.formatInr(half))
                        drawSumRow("SGST", AutoTradeRepository.formatInr(half))
                    }
                    TaxType.IGST -> drawSumRow("IGST", AutoTradeRepository.formatInr(invoice.taxAmount))
                    TaxType.NONE -> Unit
                }
            }
            if (invoice.transportCharges > 0.0) {
                drawSumRow("Transport / Packing", AutoTradeRepository.formatInr(invoice.transportCharges))
            }

            canvas.drawLine(rightBoxLeft, sumY + 2f, PAGE_WIDTH - MARGIN, sumY + 2f, borderPaint)
            sumY += 8f
            drawSumRow("GRAND TOTAL", AutoTradeRepository.formatInr(invoice.grandTotal), bold = true, color = colorSlate900)
            drawSumRow("Paid Amount", AutoTradeRepository.formatInr(invoice.paidAmount), bold = false, color = colorEmerald)
            drawSumRow(
                "BALANCE DUE",
                AutoTradeRepository.formatInr(invoice.balanceDue),
                bold = true,
                color = if (invoice.balanceDue > 0.0) colorCrimson else colorEmerald
            )

            // Footer
            val footerPaint = Paint(mutedPaint).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText(
                "Generated via AutoTrade B2B Wholesale • Computer-generated invoice",
                PAGE_WIDTH / 2f,
                PAGE_HEIGHT - 22f,
                footerPaint
            )

            document.finishPage(page)

            val safeNum = invoice.invoiceNumber.replace(Regex("[^a-zA-Z0-9_-]"), "_")
            val file = File(getPdfCacheDir(context), "Invoice_${safeNum}.pdf")
            FileOutputStream(file).use { out -> document.writeTo(out) }
            document.close()
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a print-ready A4 Wholesale Product Catalog PDF grouped by category with
     * real compressed product thumbnails, car model compatibility, HSN/GST, MOQ, and ₹ INR rate.
     */
    suspend fun generateCatalogPdf(
        context: Context,
        businessName: String,
        phone: String,
        city: String,
        products: List<Product>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (products.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No products in catalog to export."))
            }
            val document = PdfDocument()
            var pageNum = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            var canvas = page.canvas

            val headerBg = Paint().apply { color = colorSlate900 }
            val accentBg = Paint().apply { color = colorCrimson }
            val cardBg = Paint().apply { color = Color.WHITE }
            val placeholderBg = Paint().apply { color = colorSlate100 }
            val borderPaint = Paint().apply {
                color = colorSlate200
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val whiteTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 19f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val whiteSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                textSize = 9.5f
            }
            val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorSlate900
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val itemMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorSlate500
                textSize = 8.5f
            }
            val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = colorCrimson
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            val stockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }

            val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
            val title = businessName.ifBlank { "AutoTrade Wholesale" }

            // Cover Header on Page 1
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 88f, headerBg)
            canvas.drawRect(0f, 84f, PAGE_WIDTH.toFloat(), 88f, accentBg)
            canvas.drawText(title.take(36), MARGIN, 34f, whiteTitle)
            canvas.drawText("B2B WHOLESALE CAR ACCESSORIES CATALOG", MARGIN, 52f, whiteSub)
            val contactMeta = listOfNotNull(
                city.takeIf { it.isNotBlank() },
                phone.takeIf { it.isNotBlank() }?.let { "WhatsApp: $it" },
                "Updated: $dateStr",
                "${products.size} ${if (products.size == 1) "Item" else "Items"}"
            ).joinToString("  •  ")
            canvas.drawText(contactMeta, MARGIN, 70f, whiteSub)

            var y = 104f

            fun startNewPage() {
                val footer = Paint(itemMeta).apply { textAlign = Paint.Align.CENTER }
                canvas.drawText("$title  •  Wholesale Catalog  •  Page $pageNum", PAGE_WIDTH / 2f, PAGE_HEIGHT - 18f, footer)
                document.finishPage(page)
                pageNum++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
                canvas = page.canvas
                y = MARGIN
            }

            val grouped = products.groupBy { it.category.ifBlank { "General Accessories" } }.toSortedMap()

            grouped.forEach { (category, catProducts) ->
                if (y > PAGE_HEIGHT - 110f) startNewPage()

                // Category Band
                val band = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 24f)
                canvas.drawRoundRect(band, 4f, 4f, headerBg)
                canvas.drawText("${category.uppercase(Locale.US)}  (${catProducts.size})", MARGIN + 10f, y + 16f, catPaint)
                y += 32f

                catProducts.forEach { product ->
                    val rowHeight = 64f
                    if (y + rowHeight > PAGE_HEIGHT - 36f) startNewPage()

                    val rowRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight)
                    canvas.drawRoundRect(rowRect, 6f, 6f, cardBg)
                    canvas.drawRoundRect(rowRect, 6f, 6f, borderPaint)

                    // Thumbnail on left
                    val thumbRect = RectF(MARGIN + 6f, y + 6f, MARGIN + 58f, y + 58f)
                    val firstImg = product.imageUrls.firstOrNull()
                    val bmp: Bitmap? = if (!firstImg.isNullOrBlank() && firstImg.startsWith("data:image")) {
                        ImageCompressor.decodeDataUriToBitmap(firstImg)
                    } else null

                    if (bmp != null) {
                        val src = Rect(0, 0, bmp.width, bmp.height)
                        canvas.drawBitmap(bmp, src, thumbRect, null)
                        bmp.recycle()
                    } else {
                        canvas.drawRoundRect(thumbRect, 4f, 4f, placeholderBg)
                        canvas.drawText("IMG", thumbRect.left + 16f, thumbRect.top + 30f, itemMeta)
                    }

                    val textLeft = MARGIN + 68f
                    canvas.drawText(product.name.take(42), textLeft, y + 20f, itemTitle)
                    val modelLine = buildString {
                        append("Car: ${product.carModel.ifBlank { "Universal" }}")
                        append("  •  MOQ: ${product.moq} pcs")
                        if (product.hsnCode.isNotBlank()) append("  •  HSN: ${product.hsnCode}")
                        if (product.gstPercent > 0L) append("  •  GST: ${product.gstPercent}%")
                    }
                    canvas.drawText(modelLine.take(64), textLeft, y + 36f, itemMeta)
                    if (product.description.isNotBlank()) {
                        canvas.drawText(product.description.take(66), textLeft, y + 52f, itemMeta)
                    }

                    // Price & Stock on right
                    canvas.drawText(AutoTradeRepository.formatInr(product.price), PAGE_WIDTH - MARGIN - 10f, y + 24f, pricePaint)
                    stockPaint.color = if (product.inStock) colorEmerald else colorCrimson
                    canvas.drawText(
                        if (product.inStock) "IN STOCK" else "OUT OF STOCK",
                        PAGE_WIDTH - MARGIN - 10f,
                        y + 44f,
                        stockPaint
                    )

                    y += rowHeight + 8f
                }

                y += 8f
            }

            val footer = Paint(itemMeta).apply { textAlign = Paint.Align.CENTER }
            canvas.drawText("$title  •  Wholesale Catalog  •  Page $pageNum", PAGE_WIDTH / 2f, PAGE_HEIGHT - 18f, footer)
            document.finishPage(page)

            val file = File(getPdfCacheDir(context), "AutoTrade_Catalog_${System.currentTimeMillis()}.pdf")
            FileOutputStream(file).use { out -> document.writeTo(out) }
            document.close()
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openPdfFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(viewIntent, "Open PDF"))
        } catch (_: Exception) {
            sharePdfFile(context, file, file.nameWithoutExtension)
        }
    }

    fun sharePdfFile(context: Context, file: File, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        } catch (_: Exception) {
            Toast.makeText(context, "No app available to share PDF", Toast.LENGTH_SHORT).show()
        }
    }

    fun printPdfFile(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            openPdfFile(context, file)
            return
        }
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback?,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback?.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback?.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor?,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback?
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination?.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback?.onWriteFailed(e.localizedMessage)
                }
            }
        }
        try {
            printManager.print(jobName, adapter, PrintAttributes.Builder().build())
        } catch (_: Exception) {
            openPdfFile(context, file)
        }
    }
}
