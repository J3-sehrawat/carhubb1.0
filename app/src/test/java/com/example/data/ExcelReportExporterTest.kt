package com.example.data

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Date
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExcelReportExporterTest {

    private fun readZipEntries(bytes: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entries[entry.name] = zis.readBytes().toString(Charsets.UTF_8)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return entries
    }

    private fun assertAllXmlWellFormed(entries: Map<String, String>) {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        entries.forEach { (name, xml) ->
            val doc = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            assertTrue("Entry $name must have a root element", doc.documentElement != null)
        }
    }

    @Test
    fun columnLetter_handlesSingleAndDoubleLetters() {
        assertEquals("A", XlsxWorkbookBuilder.columnLetter(0))
        assertEquals("U", XlsxWorkbookBuilder.columnLetter(20))
        assertEquals("Z", XlsxWorkbookBuilder.columnLetter(25))
        assertEquals("AA", XlsxWorkbookBuilder.columnLetter(26))
        assertEquals("AB", XlsxWorkbookBuilder.columnLetter(27))
    }

    @Test
    fun writeInvoicesWorkbook_producesValidMultiSheetXlsxWithEscapingAndTotals() {
        val sampleInvoices = listOf(
            Invoice(
                id = "inv1",
                invoiceNumber = "INV-001",
                orderId = "order_123456",
                billType = BillType.GST_INVOICE.label,
                shopName = "Sharma & Sons <Auto> \"Motors\"",
                phone = "9876543210",
                city = "Delhi",
                shopGstin = "07ABCDE1234F1Z5",
                items = listOf(
                    InvoiceItem(
                        productId = "p1",
                        name = "7D Luxury Floor Mats & Liners",
                        hsnCode = "8708",
                        qty = 5L,
                        rate = 1200.0,
                        gstPercent = 18L,
                        lineTotal = 6000.0
                    )
                ),
                subtotal = 6000.0,
                discountAmount = 200.0,
                taxType = TaxType.CGST_SGST.label,
                taxAmount = 1044.0,
                transportCharges = 156.0,
                grandTotal = 7000.0,
                paidAmount = 4000.0,
                balanceDue = 3000.0,
                paymentStatus = PaymentStatus.PARTIAL.label,
                paymentHistory = listOf(
                    PaymentRecord(amount = 4000.0, mode = "UPI", date = "21 Nov 2026", note = "UTR #9988 & Ref")
                ),
                createdAt = Timestamp(Date(1700000000000L))
            ),
            Invoice(
                id = "inv2",
                invoiceNumber = "INV-002",
                billType = BillType.ESTIMATE_CASH_MEMO.label,
                shopName = "Metro Car Decor",
                city = "Noida",
                items = listOf(
                    InvoiceItem(name = "Steering Cover", qty = 10L, rate = 250.0, gstPercent = 0L, lineTotal = 2500.0)
                ),
                subtotal = 2500.0,
                taxType = TaxType.NONE.label,
                grandTotal = 2500.0,
                paidAmount = 2500.0,
                balanceDue = 0.0,
                paymentStatus = PaymentStatus.PAID.label,
                createdAt = Timestamp(Date(1700086400000L))
            )
        )

        val out = ByteArrayOutputStream()
        ExcelReportExporter.writeInvoicesWorkbook(
            outputStream = out,
            invoices = sampleInvoices,
            businessName = "AutoTrade & Co.",
            reportTitle = "Billing & Invoices Report",
            filterSummary = "Status: All • 2 bills",
            generatedAt = Date(1700100000000L)
        )

        val entries = readZipEntries(out.toByteArray())
        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(entries.containsKey("xl/styles.xml"))
        assertTrue(entries.containsKey("xl/sharedStrings.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet3.xml"))

        assertAllXmlWellFormed(entries)

        val workbookXml = entries.getValue("xl/workbook.xml")
        assertTrue(workbookXml.contains("Invoices Summary"))
        assertTrue(workbookXml.contains("Line Items"))
        assertTrue(workbookXml.contains("Payment History"))

        val sharedStrings = entries.getValue("xl/sharedStrings.xml")
        assertTrue(sharedStrings.contains("Sharma &amp; Sons &lt;Auto&gt; &quot;Motors&quot;"))
        assertTrue(sharedStrings.contains("TOTAL (2)"))
        assertTrue(sharedStrings.contains("UTR #9988 &amp; Ref"))

        val sheet1 = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue("Totals row must contain combined grand total 9500.00", sheet1.contains("<v>9500.00</v>"))
        assertTrue("Totals row must contain combined balance due 3000.00", sheet1.contains("<v>3000.00</v>"))
    }

    @Test
    fun writeOrdersWorkbook_producesValidTwoSheetXlsxWithLinkedInvoiceAndTotals() {
        val orders = listOf(
            Order(
                id = "ord_abcdef123456",
                shopkeeperId = "buyer1",
                shopName = "Apex Car Accessories",
                phone = "9988776655",
                city = "Jaipur",
                items = listOf(
                    OrderItem(productId = "p1", name = "LED Headlight H4", carModel = "Swift", price = 900.0, qty = 4L),
                    OrderItem(productId = "p2", name = "7D Mat", carModel = "Creta", price = 1500.0, qty = 2L)
                ),
                totalAmount = 6600.0,
                status = OrderStatus.DISPATCHED.label,
                invoiceId = "inv_99",
                createdAt = Timestamp(Date(1700000000000L))
            )
        )
        val invoices = listOf(
            Invoice(id = "inv_99", invoiceNumber = "INV-099", orderId = "ord_abcdef123456")
        )

        val out = ByteArrayOutputStream()
        ExcelReportExporter.writeOrdersWorkbook(
            outputStream = out,
            orders = orders,
            invoices = invoices,
            businessName = "AutoTrade Wholesale",
            reportTitle = "Wholesale Orders Report",
            filterSummary = "Status: Dispatched • 1 order",
            generatedAt = Date(1700100000000L)
        )

        val entries = readZipEntries(out.toByteArray())
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertAllXmlWellFormed(entries)

        val sharedStrings = entries.getValue("xl/sharedStrings.xml")
        assertTrue(sharedStrings.contains("INV-099"))
        assertTrue(sharedStrings.contains("LED Headlight H4"))
        assertTrue(sharedStrings.contains("TOTAL (1)"))

        val sheet1 = entries.getValue("xl/worksheets/sheet1.xml")
        assertTrue(sheet1.contains("<v>6600.00</v>"))
        assertTrue(sheet1.contains("<v>6</v>")) // total qty = 4 + 2
    }
}
