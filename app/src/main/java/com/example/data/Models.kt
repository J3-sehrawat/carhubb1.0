package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val BOOTSTRAPPED_ADMIN_EMAIL = "sehrawatj000@gmail.com"

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    CONFIRMED("Confirmed"),
    DISPATCHED("Dispatched"),
    DELIVERED("Delivered");

    companion object {
        fun fromRaw(raw: String?): OrderStatus =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

enum class AccountStatus(val raw: String, val label: String) {
    PENDING("pending", "Pending"),
    APPROVED("approved", "Approved"),
    BLOCKED("blocked", "Blocked");

    companion object {
        fun fromRaw(raw: String?): AccountStatus =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

enum class BillType(val label: String) {
    GST_INVOICE("GST Invoice"),
    ESTIMATE_CASH_MEMO("Estimate / Cash Memo");

    companion object {
        fun fromRaw(raw: String?): BillType =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: GST_INVOICE
    }
}

enum class TaxType(val label: String) {
    CGST_SGST("CGST+SGST"),
    IGST("IGST"),
    NONE("None");

    companion object {
        fun fromRaw(raw: String?): TaxType =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: NONE
    }
}

enum class PaymentStatus(val label: String) {
    UNPAID("Unpaid"),
    PARTIAL("Partial"),
    PAID("Paid");

    companion object {
        fun fromRaw(raw: String?): PaymentStatus =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: UNPAID
    }
}

enum class PaymentMode(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    BANK_TRANSFER("Bank Transfer");

    companion object {
        fun fromRaw(raw: String?): PaymentMode =
            entries.firstOrNull { it.label.equals(raw, ignoreCase = true) } ?: CASH
    }
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val email: String = "",
    val shopName: String = "",
    val phone: String = "",
    val city: String = "",
    val gstin: String = "",
    val fcmToken: String = "",
    val notifyOrderUpdates: Boolean = true,
    val notifyNewProducts: Boolean = true,
    val role: String = "shopkeeper",
    val status: String = "pending",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleIsAdmin(): Boolean = role.equals("admin", ignoreCase = true)
    fun accountStatus(): AccountStatus = AccountStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class Category(
    @DocumentId val id: String = "",
    val name: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val name: String = "",
    val category: String = "",
    val carModel: String = "Universal",
    val price: Double = 0.0,
    val moq: Long = 1L,
    val inStock: Boolean = true,
    val stockQuantity: Long? = null,
    val newArrival: Boolean? = null,
    val description: String = "",
    val hsnCode: String = "",
    val gstPercent: Long = 18L,
    val imageUrls: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    /**
     * Explicit Admin control wins when `newArrival` is set; legacy products without the field
     * automatically appear as new for 7 days from creation until Admin toggles them.
     */
    fun isFeaturedNewArrival(): Boolean {
        if (newArrival != null) return newArrival
        val created = createdAt?.toDate()?.time ?: return true
        return (System.currentTimeMillis() - created) <= 7L * 24L * 60L * 60L * 1000L
    }

    /**
     * Returns the tracked inventory count, treating explicitly Out-of-Stock legacy items as 0.
     */
    fun trackedStockCount(): Long? = stockQuantity ?: if (!inStock) 0L else null

    /**
     * Triggers a Low Stock alert whenever tracked inventory falls strictly below the business threshold.
     */
    fun isLowStock(threshold: Long): Boolean {
        if (threshold <= 0L) return false
        val qty = trackedStockCount() ?: return false
        return qty < threshold
    }
}

@Keep
@IgnoreExtraProperties
data class OrderItem(
    val productId: String = "",
    val name: String = "",
    val carModel: String = "",
    val price: Double = 0.0,
    val qty: Long = 1L,
    val imageUrl: String = ""
) {
    fun lineTotal(): Double = price * qty
}

@Keep
@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val shopkeeperId: String = "",
    val shopName: String = "",
    val phone: String = "",
    val city: String = "",
    val items: List<OrderItem> = emptyList(),
    val totalAmount: Double = 0.0,
    val status: String = "Pending",
    val invoiceId: String = "",
    val stockDeducted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun orderStatus(): OrderStatus = OrderStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class StoreSettings(
    @DocumentId val id: String = "",
    val businessName: String = "AutoTrade Wholesale",
    val whatsappPhone: String = "",
    val city: String = "Delhi",
    val showNewArrivals: Boolean = true,
    val newArrivalsTitle: String = "Newly Listed Stock",
    val newArrivalsLimit: Long = 10L,
    val primaryColorHex: String = DEFAULT_PRIMARY_COLOR_HEX,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class BusinessProfile(
    @DocumentId val id: String = "",
    val firmName: String = "AutoTrade Wholesale",
    val address: String = "",
    val phone: String = "",
    val gstin: String = "",
    val upiId: String = "",
    val invoicePrefix: String = "INV",
    val nextInvoiceNumber: Long = 1L,
    val lowStockThreshold: Long = DEFAULT_LOW_STOCK_THRESHOLD,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class InvoiceItem(
    val productId: String = "",
    val name: String = "",
    val hsnCode: String = "",
    val qty: Long = 1L,
    val rate: Double = 0.0,
    val gstPercent: Long = 18L,
    val lineTotal: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class PaymentRecord(
    val amount: Double = 0.0,
    val mode: String = "Cash",
    val date: String = "",
    val note: String = ""
)

@Keep
@IgnoreExtraProperties
data class Invoice(
    @DocumentId val id: String = "",
    val invoiceNumber: String = "",
    val orderId: String = "",
    val billType: String = BillType.GST_INVOICE.label,
    val shopkeeperId: String = "",
    val shopName: String = "",
    val phone: String = "",
    val city: String = "",
    val shopGstin: String = "",
    val firmName: String = "",
    val firmAddress: String = "",
    val firmPhone: String = "",
    val firmGstin: String = "",
    val firmUpiId: String = "",
    val items: List<InvoiceItem> = emptyList(),
    val subtotal: Double = 0.0,
    val discountAmount: Double = 0.0,
    val taxType: String = TaxType.CGST_SGST.label,
    val taxAmount: Double = 0.0,
    val transportCharges: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paidAmount: Double = 0.0,
    val balanceDue: Double = 0.0,
    val paymentStatus: String = PaymentStatus.UNPAID.label,
    val paymentHistory: List<PaymentRecord> = emptyList(),
    val stockDeducted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun billTypeEnum(): BillType = BillType.fromRaw(billType)
    fun taxTypeEnum(): TaxType = TaxType.fromRaw(taxType)
    fun paymentStatusEnum(): PaymentStatus = PaymentStatus.fromRaw(paymentStatus)
}

data class CartItem(
    val product: Product,
    val qty: Long
) {
    val lineTotal: Double get() = product.price * qty
}

const val NOTIFICATION_TYPE_ORDER_STATUS = "order_status"
const val NOTIFICATION_TYPE_NEW_PRODUCT = "new_product"
const val TARGET_ALL_BUYERS = "ALL_BUYERS"

@Keep
@IgnoreExtraProperties
data class NotificationEvent(
    @DocumentId val id: String = "",
    val type: String = NOTIFICATION_TYPE_NEW_PRODUCT,
    val targetUserId: String = TARGET_ALL_BUYERS,
    val targetTopic: String = "new_products",
    val targetFcmToken: String = "",
    val title: String = "",
    val body: String = "",
    val orderId: String = "",
    val productId: String = "",
    val status: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

enum class ActivityLogAction(val raw: String, val label: String) {
    PRODUCT_ADDED("product_added", "Product Added"),
    PRICE_CHANGED("price_changed", "Price Changed"),
    PRODUCT_UPDATED("product_updated", "Product Updated"),
    PRODUCT_DELETED("product_deleted", "Product Deleted"),
    STOCK_UPDATED("stock_updated", "Stock Updated"),
    SHOPKEEPER_APPROVED("shopkeeper_approved", "Buyer Approved"),
    SHOPKEEPER_BLOCKED("shopkeeper_blocked", "Buyer Blocked"),
    ORDER_STATUS_UPDATED("order_status_updated", "Order Updated"),
    INVOICE_CREATED("invoice_created", "Bill Created"),
    PAYMENT_RECORDED("payment_recorded", "Payment Recorded"),
    INVOICE_DELETED("invoice_deleted", "Bill Deleted"),
    SETTINGS_UPDATED("settings_updated", "Settings Updated"),
    THEME_UPDATED("theme_updated", "Brand Theme");

    companion object {
        fun fromRaw(raw: String?): ActivityLogAction =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: SETTINGS_UPDATED
    }
}

enum class ActivityLogCategory(val raw: String, val label: String) {
    PRICING("pricing", "Price Changes"),
    PRODUCT("product", "Products"),
    SHOPKEEPER("shopkeeper", "Shopkeepers"),
    ORDER("order", "Orders"),
    BILLING("billing", "Billing"),
    SETTINGS("settings", "Settings");

    companion object {
        fun fromRaw(raw: String?): ActivityLogCategory =
            entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: SETTINGS
    }
}

@Keep
@IgnoreExtraProperties
data class AdminActivityLog(
    @DocumentId val id: String = "",
    val action: String = ActivityLogAction.SETTINGS_UPDATED.raw,
    val category: String = ActivityLogCategory.SETTINGS.raw,
    val actorUid: String = "",
    val actorEmail: String = "",
    val actorName: String = "",
    val targetId: String = "",
    val targetName: String = "",
    val summary: String = "",
    val previousValue: String = "",
    val newValue: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun actionEnum(): ActivityLogAction = ActivityLogAction.fromRaw(action)
    fun categoryEnum(): ActivityLogCategory = ActivityLogCategory.fromRaw(category)
}

data class CollectionSyncMeta(
    val isFromCache: Boolean = false,
    val pendingDocumentIds: Set<String> = emptySet()
)

data class SyncStatusSummary(
    val isOnline: Boolean = true,
    val isCatalogFromCache: Boolean = false,
    val isInvoicesFromCache: Boolean = false,
    val isOrdersFromCache: Boolean = false,
    val pendingProductIds: Set<String> = emptySet(),
    val pendingInvoiceIds: Set<String> = emptySet(),
    val pendingOrderIds: Set<String> = emptySet(),
    val isSyncingNow: Boolean = false
) {
    val pendingDocumentIds: Set<String> get() = pendingProductIds + pendingInvoiceIds + pendingOrderIds
    val pendingWriteCount: Int get() = pendingDocumentIds.size
    val isViewingCachedData: Boolean
        get() = !isOnline || isCatalogFromCache || isInvoicesFromCache || isOrdersFromCache
}

const val NEW_ARRIVALS_FILTER = "New Arrivals"
const val LOW_STOCK_FILTER = "Low Stock"
const val DEFAULT_LOW_STOCK_THRESHOLD = 10L
const val DEFAULT_PRIMARY_COLOR_HEX = "#E11D48"

data class InventoryHealthSummary(
    val totalProducts: Int = 0,
    val trackedProductsCount: Int = 0,
    val lowStockProducts: List<Product> = emptyList(),
    val outOfStockCount: Int = 0,
    val threshold: Long = DEFAULT_LOW_STOCK_THRESHOLD
) {
    val lowStockCount: Int get() = lowStockProducts.size
}

/**
 * Monitors all catalog products against the Business Settings low-stock threshold and returns
 * low-stock items ordered from lowest remaining quantity to highest for immediate restock triage.
 */
fun monitorProductInventory(
    products: List<Product>,
    threshold: Long
): InventoryHealthSummary {
    val safeThreshold = threshold.coerceAtLeast(1L)
    val lowStock = products
        .filter { it.isLowStock(safeThreshold) }
        .sortedWith(
            compareBy<Product> { it.trackedStockCount() ?: 0L }
                .thenBy { it.name.lowercase() }
        )
    val outOfStockCount = products.count { !it.inStock || (it.stockQuantity != null && it.stockQuantity <= 0L) }
    val trackedCount = products.count { it.trackedStockCount() != null }
    return InventoryHealthSummary(
        totalProducts = products.size,
        trackedProductsCount = trackedCount,
        lowStockProducts = lowStock,
        outOfStockCount = outOfStockCount,
        threshold = safeThreshold
    )
}

val GST_RATES = listOf(0L, 5L, 12L, 18L, 28L)

val DEFAULT_CATEGORIES = listOf(
    "cat_seat_covers" to "Seat Covers",
    "cat_steering_covers" to "Steering Covers",
    "cat_floor_mats" to "7D/Floor Mats",
    "cat_perfumes" to "Perfumes",
    "cat_lighting" to "Lighting",
    "cat_exterior" to "Exterior",
    "cat_general" to "General Accessories"
)

val POPULAR_CAR_MODELS = listOf(
    "Universal",
    "Swift",
    "Creta",
    "Thar",
    "Fronx",
    "Nexon",
    "Scorpio N",
    "Baleno",
    "Venue",
    "Innova Hycross"
)
