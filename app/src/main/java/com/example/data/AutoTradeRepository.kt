package com.example.data

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToLong

class AutoTradeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val networkMonitor: NetworkConnectivityMonitor? = null
) {
    constructor(context: Context) : this(
        db = FirestoreProvider.getDatabase(context),
        networkMonitor = NetworkConnectivityMonitor(context)
    )

    private val _catalogSyncMeta = MutableStateFlow(CollectionSyncMeta())
    val catalogSyncMeta: StateFlow<CollectionSyncMeta> = _catalogSyncMeta.asStateFlow()

    private val _invoiceSyncMeta = MutableStateFlow(CollectionSyncMeta())
    val invoiceSyncMeta: StateFlow<CollectionSyncMeta> = _invoiceSyncMeta.asStateFlow()

    private val _orderSyncMeta = MutableStateFlow(CollectionSyncMeta())
    val orderSyncMeta: StateFlow<CollectionSyncMeta> = _orderSyncMeta.asStateFlow()

    fun isCurrentlyOffline(): Boolean = networkMonitor != null && !networkMonitor.isOnlineNow()

    private suspend fun <T> Task<T>.awaitWrite(fallback: T, op: OperationType, path: String): T {
        if (isCurrentlyOffline()) {
            addOnFailureListener { e -> handleFirestoreError(e, op, path) }
            return fallback
        }
        return withTimeoutOrNull(6_000L) { await() } ?: run {
            addOnFailureListener { e -> handleFirestoreError(e, op, path) }
            fallback
        }
    }

    suspend fun syncPendingWritesNow(): Result<Unit> {
        return try {
            requireUserId()
            if (isCurrentlyOffline()) {
                return Result.failure(IllegalStateException("Device is offline. Queued changes will sync automatically when connected."))
            }
            db.enableNetwork().await()
            withTimeoutOrNull(10_000L) { db.waitForPendingWrites().await() }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "sync/pendingWrites")
            Result.failure(e)
        }
    }

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        }
        return uid
    }

    private fun isCurrentUserBootstrappedAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email.equals(BOOTSTRAPPED_ADMIN_EMAIL, ignoreCase = true)
    }

    // --- 1. USER PROFILE ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, userId, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun createUserProfile(
        name: String,
        shopName: String,
        phone: String,
        city: String
    ): Result<UserProfile> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val email = auth.currentUser?.email.orEmpty().take(120)
        val path = "users/$uid"
        return try {
            val cleanName = name.trim()
            val cleanShop = shopName.trim()
            val cleanPhone = phone.filter { it.isDigit() }
            val cleanCity = city.trim()

            require(cleanName.length in 1..80) { "Please enter your full name." }
            require(cleanShop.length in 1..100) { "Please enter your shop or business name." }
            require(cleanPhone.length in 10..15) { "Please enter a valid 10-digit mobile number." }
            require(cleanCity.length in 1..60) { "Please enter your city." }

            val isAdmin = isCurrentUserBootstrappedAdmin() || checkAdminRecordExists(uid)
            val role = if (isAdmin) "admin" else "shopkeeper"
            val status = if (isAdmin) "approved" else "pending"

            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "email" to email,
                "shopName" to cleanShop,
                "phone" to cleanPhone,
                "city" to cleanCity,
                "gstin" to "",
                "role" to role,
                "status" to status,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).set(payload).await()

            if (isAdmin) {
                saveStoreSettings(cleanShop, cleanPhone, cleanCity)
                saveBusinessProfile(
                    firmName = cleanShop,
                    address = cleanCity,
                    phone = cleanPhone,
                    gstin = "",
                    upiId = "",
                    invoicePrefix = "INV",
                    nextInvoiceNumber = 1L,
                    lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD
                )
                seedDefaultCategoriesIfEmpty()
            }

            Result.success(
                UserProfile(
                    id = uid,
                    userId = uid,
                    name = cleanName,
                    email = email,
                    shopName = cleanShop,
                    phone = cleanPhone,
                    city = cleanCity,
                    gstin = "",
                    role = role,
                    status = status
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private suspend fun checkAdminRecordExists(uid: String): Boolean {
        return try {
            db.collection("admins").document(uid).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateOwnProfile(
        name: String,
        shopName: String,
        phone: String,
        city: String,
        gstin: String = ""
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            val cleanName = name.trim()
            val cleanShop = shopName.trim()
            val cleanPhone = phone.filter { it.isDigit() }
            val cleanCity = city.trim()
            val cleanGstin = gstin.trim().uppercase(Locale.US).take(20)

            require(cleanName.length in 1..80) { "Name must be 1–80 characters." }
            require(cleanShop.length in 1..100) { "Shop name must be 1–100 characters." }
            require(cleanPhone.length in 10..15) { "Enter a valid 10–15 digit phone number." }
            require(cleanCity.length in 1..60) { "City must be 1–60 characters." }

            db.collection("users").document(uid).update(
                mapOf(
                    "name" to cleanName,
                    "shopName" to cleanShop,
                    "phone" to cleanPhone,
                    "city" to cleanCity,
                    "gstin" to cleanGstin,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.UPDATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveFcmToken(token: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val cleanToken = token.trim().take(512)
        if (cleanToken.isEmpty()) return Result.success(Unit)
        val path = "users/$uid"
        return try {
            val ref = db.collection("users").document(uid)
            val snap = ref.get().await()
            if (!snap.exists()) return Result.success(Unit)
            ref.update(
                mapOf(
                    "fcmToken" to cleanToken,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateNotificationPreferences(
        notifyOrderUpdates: Boolean,
        notifyNewProducts: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(IllegalStateException("Not signed in"))
        val path = "users/$uid"
        return try {
            db.collection("users").document(uid).update(
                mapOf(
                    "notifyOrderUpdates" to notifyOrderUpdates,
                    "notifyNewProducts" to notifyNewProducts,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 2. ADMIN SHOPKEEPER MANAGEMENT ---

    fun observeAllUsers(adminUserId: String): Flow<List<UserProfile>> = flow {
        require(adminUserId.isNotBlank()) { "Admin user ID required" }
        val path = "users"
        emitAll(
            db.collection("users")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<UserProfile> {
                                when (it.accountStatus()) {
                                    AccountStatus.PENDING -> 0
                                    AccountStatus.APPROVED -> 1
                                    AccountStatus.BLOCKED -> 2
                                }
                            }.thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                        )
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, adminUserId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun updateShopkeeperStatus(targetUserId: String, newStatus: AccountStatus): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            requireUserId()
            require(targetUserId.isNotBlank()) { "Target user ID required" }
            db.collection("users").document(targetUserId).update(
                mapOf(
                    "status" to newStatus.raw,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.UPDATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 3. STORE SETTINGS & BUSINESS BILLING PROFILE ---

    fun observeStoreSettings(currentUserId: String): Flow<StoreSettings> = flow {
        require(currentUserId.isNotBlank()) { "User ID required" }
        val path = "settings/store"
        emitAll(
            db.collection("settings").document("store")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(StoreSettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: StoreSettings()
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, currentUserId, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun saveStoreSettings(
        businessName: String,
        whatsappPhone: String,
        city: String,
        showNewArrivals: Boolean = true,
        newArrivalsTitle: String = "Newly Listed Stock",
        newArrivalsLimit: Long = 10L,
        primaryColorHex: String = DEFAULT_PRIMARY_COLOR_HEX
    ): Result<Unit> {
        val path = "settings/store"
        return try {
            requireUserId()
            val cleanBusiness = businessName.trim()
            val cleanPhone = whatsappPhone.filter { it.isDigit() }
            val cleanCity = city.trim()
            val cleanTitle = newArrivalsTitle.trim().ifBlank { "Newly Listed Stock" }.take(60)
            val safeLimit = newArrivalsLimit.coerceIn(1L, 50L)
            val normalizedHex = normalizePrimaryColorHex(primaryColorHex)

            require(cleanBusiness.length in 1..100) { "Business name is required." }
            require(cleanPhone.length in 10..15) { "WhatsApp number must be 10–15 digits." }
            require(cleanCity.length in 1..60) { "City is required." }

            db.collection("settings").document("store").set(
                mapOf(
                    "businessName" to cleanBusiness,
                    "whatsappPhone" to cleanPhone,
                    "city" to cleanCity,
                    "showNewArrivals" to showNewArrivals,
                    "newArrivalsTitle" to cleanTitle,
                    "newArrivalsLimit" to safeLimit,
                    "primaryColorHex" to normalizedHex,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.WRITE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveNewArrivalsSettings(
        currentSettings: StoreSettings,
        fallbackProfile: UserProfile,
        showNewArrivals: Boolean,
        newArrivalsTitle: String,
        newArrivalsLimit: Long
    ): Result<Unit> {
        val safeBusiness = currentSettings.businessName.ifBlank {
            fallbackProfile.shopName.ifBlank { "AutoTrade Wholesale" }
        }
        val rawPhone = currentSettings.whatsappPhone.ifBlank { fallbackProfile.phone }
            .filter { it.isDigit() }
        val safePhone = if (rawPhone.length in 10..15) rawPhone else rawPhone.padEnd(10, '0').take(15)
        val safeCity = currentSettings.city.ifBlank {
            fallbackProfile.city.ifBlank { "Delhi" }
        }
        return saveStoreSettings(
            businessName = safeBusiness,
            whatsappPhone = safePhone,
            city = safeCity,
            showNewArrivals = showNewArrivals,
            newArrivalsTitle = newArrivalsTitle,
            newArrivalsLimit = newArrivalsLimit,
            primaryColorHex = currentSettings.primaryColorHex
        )
    }

    suspend fun saveBrandTheme(
        currentSettings: StoreSettings,
        fallbackProfile: UserProfile,
        primaryColorHex: String
    ): Result<Unit> {
        val candidate = primaryColorHex.trim().uppercase(Locale.US)
        val formatted = if (candidate.startsWith("#")) candidate else "#$candidate"
        if (!HEX_COLOR_REGEX.matches(formatted)) {
            return Result.failure(IllegalArgumentException("Enter a valid 6-digit hex color like #2563EB."))
        }
        val safeBusiness = currentSettings.businessName.ifBlank {
            fallbackProfile.shopName.ifBlank { "AutoTrade Wholesale" }
        }
        val rawPhone = currentSettings.whatsappPhone.ifBlank { fallbackProfile.phone }
            .filter { it.isDigit() }
        val safePhone = if (rawPhone.length in 10..15) rawPhone else rawPhone.padEnd(10, '0').take(15)
        val safeCity = currentSettings.city.ifBlank {
            fallbackProfile.city.ifBlank { "Delhi" }
        }
        return saveStoreSettings(
            businessName = safeBusiness,
            whatsappPhone = safePhone,
            city = safeCity,
            showNewArrivals = currentSettings.showNewArrivals,
            newArrivalsTitle = currentSettings.newArrivalsTitle,
            newArrivalsLimit = currentSettings.newArrivalsLimit,
            primaryColorHex = formatted
        )
    }

    private fun normalizePrimaryColorHex(raw: String): String {
        val trimmed = raw.trim().uppercase(Locale.US)
        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return if (HEX_COLOR_REGEX.matches(withHash)) withHash else DEFAULT_PRIMARY_COLOR_HEX
    }

    fun observeBusinessProfile(adminUserId: String): Flow<BusinessProfile> = flow {
        require(adminUserId.isNotBlank()) { "Admin user ID required" }
        val path = "settings/businessProfile"
        emitAll(
            db.collection("settings").document("businessProfile")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObject(BusinessProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: BusinessProfile()
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, adminUserId, OperationType.GET, path)
                    throw error
                }
        )
    }

    suspend fun saveBusinessProfile(
        firmName: String,
        address: String,
        phone: String,
        gstin: String,
        upiId: String,
        invoicePrefix: String,
        nextInvoiceNumber: Long,
        lowStockThreshold: Long = DEFAULT_LOW_STOCK_THRESHOLD
    ): Result<Unit> {
        val path = "settings/businessProfile"
        return try {
            requireUserId()
            val cleanFirm = firmName.trim()
            val cleanAddr = address.trim().take(250)
            val cleanPhone = phone.filter { it.isDigit() }
            val cleanGstin = gstin.trim().uppercase(Locale.US).take(20)
            val cleanUpi = upiId.trim().take(100)
            val cleanPrefix = invoicePrefix.trim().uppercase(Locale.US)
                .replace(Regex("[^A-Z0-9_-]"), "")
                .ifBlank { "INV" }
                .take(12)
            val safeNext = nextInvoiceNumber.coerceIn(1L, 99_999_999L)
            val safeThreshold = lowStockThreshold.coerceIn(1L, 100_000L)

            require(cleanFirm.length in 1..100) { "Firm name is required." }
            require(cleanPhone.length in 10..20) { "Enter a valid 10–15 digit business phone." }

            db.collection("settings").document("businessProfile").set(
                mapOf(
                    "firmName" to cleanFirm,
                    "address" to cleanAddr,
                    "phone" to cleanPhone,
                    "gstin" to cleanGstin,
                    "upiId" to cleanUpi,
                    "invoicePrefix" to cleanPrefix,
                    "nextInvoiceNumber" to safeNext,
                    "lowStockThreshold" to safeThreshold,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.WRITE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- 4. CATEGORIES ---

    fun observeCategories(currentUserId: String): Flow<List<Category>> = flow {
        require(currentUserId.isNotBlank()) { "User ID required" }
        val path = "categories"
        emitAll(
            db.collection("categories")
                .orderBy("name", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Category::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, currentUserId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addCategory(name: String): Result<Unit> {
        val cleanName = name.trim()
        val slug = cleanName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "cat_${System.currentTimeMillis()}" }
        val docId = "cat_$slug"
        val path = "categories/$docId"
        return try {
            requireUserId()
            require(cleanName.length in 1..50) { "Category name must be 1–50 characters." }
            val ref = db.collection("categories").document(docId)
            val snapshot = ref.get().await()
            if (snapshot.exists()) {
                ref.update(
                    mapOf(
                        "name" to cleanName,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                ref.set(
                    mapOf(
                        "name" to cleanName,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun seedDefaultCategoriesIfEmpty(): Result<Int> {
        val path = "categories"
        return try {
            requireUserId()
            val existing = db.collection("categories").limit(1).get().await()
            if (!existing.isEmpty) return Result.success(0)

            val batch = db.batch()
            DEFAULT_CATEGORIES.forEach { (id, name) ->
                val ref = db.collection("categories").document(id)
                batch.set(
                    ref,
                    mapOf(
                        "name" to name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(DEFAULT_CATEGORIES.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- 5. PRODUCTS (REAL-TIME CATALOG) ---

    fun observeProducts(currentUserId: String): Flow<List<Product>> = flow {
        require(currentUserId.isNotBlank()) { "User ID required" }
        val path = "products"
        emitAll(
            db.collection("products")
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    _catalogSyncMeta.value = CollectionSyncMeta(
                        isFromCache = snapshot.metadata.isFromCache,
                        pendingDocumentIds = snapshot.documents
                            .filter { it.metadata.hasPendingWrites() }
                            .map { it.id }
                            .toSet()
                    )
                    snapshot.toObjects(Product::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .distinctBy { it.id }
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, currentUserId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun addProduct(
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
    ): Result<String> {
        var path = "products"
        return try {
            requireUserId()
            val cleanName = name.trim()
            val cleanCategory = category.trim()
            val cleanModel = carModel.trim().ifBlank { "Universal" }
            val cleanDesc = description.trim()
            val cleanHsn = hsnCode.trim().take(20)
            val safeGst = if (gstPercent in GST_RATES) gstPercent else 18L
            val safeQty = stockQuantity.coerceIn(0L, 1_000_000L)
            val effectiveInStock = inStock && safeQty > 0L

            require(cleanName.length in 1..120) { "Product name must be 1–120 characters." }
            require(cleanCategory.length in 1..60) { "Select a product category." }
            require(cleanModel.length in 1..80) { "Car model must be 1–80 characters." }
            require(price > 0.0 && price <= 10_000_000.0) { "Enter a valid wholesale price in ₹." }
            require(moq in 1L..100_000L) { "MOQ must be at least 1." }
            require(cleanDesc.length <= 1000) { "Description must be under 1000 characters." }
            require(imageUrls.size <= 4) { "Maximum 4 photos allowed per product." }

            val docRef = db.collection("products").document()
            path = docRef.path
            val payload = mapOf(
                "name" to cleanName,
                "category" to cleanCategory,
                "carModel" to cleanModel,
                "price" to price,
                "moq" to moq,
                "inStock" to effectiveInStock,
                "stockQuantity" to safeQty,
                "newArrival" to newArrival,
                "description" to cleanDesc,
                "hsnCode" to cleanHsn,
                "gstPercent" to safeGst,
                "imageUrls" to imageUrls,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).awaitWrite(null, OperationType.CREATE, path)
            // Publish real-time + FCM topic notification event for approved shopkeepers
            publishNewProductNotification(
                productId = docRef.id,
                name = cleanName,
                category = cleanCategory,
                carModel = cleanModel,
                price = price,
                moq = moq
            )
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProduct(
        productId: String,
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
    ): Result<Unit> {
        val path = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank()) { "Product ID is required." }
            val cleanName = name.trim()
            val cleanCategory = category.trim()
            val cleanModel = carModel.trim().ifBlank { "Universal" }
            val cleanDesc = description.trim()
            val cleanHsn = hsnCode.trim().take(20)
            val safeGst = if (gstPercent in GST_RATES) gstPercent else 18L
            val safeQty = stockQuantity.coerceIn(0L, 1_000_000L)
            val effectiveInStock = inStock && safeQty > 0L

            require(cleanName.length in 1..120) { "Product name must be 1–120 characters." }
            require(cleanCategory.length in 1..60) { "Select a product category." }
            require(cleanModel.length in 1..80) { "Car model must be 1–80 characters." }
            require(price > 0.0 && price <= 10_000_000.0) { "Enter a valid wholesale price in ₹." }
            require(moq in 1L..100_000L) { "MOQ must be at least 1." }
            require(cleanDesc.length <= 1000) { "Description must be under 1000 characters." }
            require(imageUrls.size <= 4) { "Maximum 4 photos allowed per product." }

            db.collection("products").document(productId).update(
                mapOf(
                    "name" to cleanName,
                    "category" to cleanCategory,
                    "carModel" to cleanModel,
                    "price" to price,
                    "moq" to moq,
                    "inStock" to effectiveInStock,
                    "stockQuantity" to safeQty,
                    "newArrival" to newArrival,
                    "description" to cleanDesc,
                    "hsnCode" to cleanHsn,
                    "gstPercent" to safeGst,
                    "imageUrls" to imageUrls,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.UPDATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleProductStock(productId: String, inStock: Boolean): Result<Unit> {
        val path = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank()) { "Product ID is required." }
            db.collection("products").document(productId).update(
                mapOf(
                    "inStock" to inStock,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.UPDATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateProductStockQuantity(productId: String, stockQuantity: Long): Result<Unit> {
        val path = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank()) { "Product ID is required." }
            val safeQty = stockQuantity.coerceIn(0L, 1_000_000L)
            db.collection("products").document(productId).update(
                mapOf(
                    "stockQuantity" to safeQty,
                    "inStock" to (safeQty > 0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.UPDATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun bulkRestockProducts(updates: Map<String, Long>): Result<Int> {
        val path = "products"
        return try {
            requireUserId()
            val entries = updates.entries.filter { it.key.isNotBlank() }
            if (entries.isEmpty()) return Result.success(0)

            entries.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (productId, rawQty) ->
                    val safeQty = rawQty.coerceIn(0L, 1_000_000L)
                    batch.update(
                        db.collection("products").document(productId),
                        mapOf(
                            "stockQuantity" to safeQty,
                            "inStock" to (safeQty > 0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().awaitWrite(null, OperationType.WRITE, path)
            }
            Result.success(entries.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleProductNewArrival(productId: String, newArrival: Boolean): Result<Unit> {
        val path = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank()) { "Product ID is required." }
            db.collection("products").document(productId).update(
                mapOf(
                    "newArrival" to newArrival,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.UPDATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun bulkSetNewArrivals(
        productIdsToMark: List<String>,
        productIdsToClear: List<String>
    ): Result<Unit> {
        val path = "products"
        return try {
            requireUserId()
            val batch = db.batch()
            productIdsToMark.take(200).forEach { id ->
                batch.update(
                    db.collection("products").document(id),
                    mapOf(
                        "newArrival" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            productIdsToClear.take(200).forEach { id ->
                batch.update(
                    db.collection("products").document(id),
                    mapOf(
                        "newArrival" to false,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().awaitWrite(null, OperationType.UPDATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        val path = "products/$productId"
        return try {
            requireUserId()
            require(productId.isNotBlank()) { "Product ID is required." }
            db.collection("products").document(productId).delete().awaitWrite(null, OperationType.DELETE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 6. ORDERS ---

    fun observeAllOrders(adminUserId: String): Flow<List<Order>> = flow {
        require(adminUserId.isNotBlank()) { "Admin user ID required" }
        val path = "orders"
        emitAll(
            db.collection("orders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    _orderSyncMeta.value = CollectionSyncMeta(
                        isFromCache = snapshot.metadata.isFromCache,
                        pendingDocumentIds = snapshot.documents
                            .filter { it.metadata.hasPendingWrites() }
                            .map { it.id }
                            .toSet()
                    )
                    snapshot.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, adminUserId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeShopkeeperOrders(shopkeeperId: String): Flow<List<Order>> = flow {
        require(shopkeeperId.isNotBlank()) { "Shopkeeper ID required" }
        val path = "orders"
        emitAll(
            db.collection("orders")
                .whereEqualTo("shopkeeperId", shopkeeperId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    _orderSyncMeta.value = CollectionSyncMeta(
                        isFromCache = snapshot.metadata.isFromCache,
                        pendingDocumentIds = snapshot.documents
                            .filter { it.metadata.hasPendingWrites() }
                            .map { it.id }
                            .toSet()
                    )
                    snapshot.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, shopkeeperId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun placeOrder(
        profile: UserProfile,
        cartItems: List<CartItem>
    ): Result<String> {
        var path = "orders"
        return try {
            val uid = requireUserId()
            require(cartItems.isNotEmpty()) { "Your cart is empty." }
            require(cartItems.size <= 50) { "Maximum 50 line items per order." }

            val totalAmount = cartItems.sumOf { it.lineTotal }
            require(totalAmount > 0.0 && totalAmount <= 100_000_000.0) { "Invalid order total." }

            val itemMaps = cartItems.map { item ->
                val firstImage = item.product.imageUrls.firstOrNull().orEmpty()
                val orderImg = if (firstImage.startsWith("http")) firstImage else ""
                mapOf(
                    "productId" to item.product.id,
                    "name" to item.product.name,
                    "carModel" to item.product.carModel,
                    "price" to item.product.price,
                    "qty" to item.qty,
                    "imageUrl" to orderImg
                )
            }

            val docRef = db.collection("orders").document()
            path = docRef.path
            val payload = mapOf(
                "shopkeeperId" to uid,
                "shopName" to profile.shopName.trim().take(100).ifBlank { profile.name.take(100) },
                "phone" to profile.phone.filter { it.isDigit() }.take(15),
                "city" to profile.city.trim().take(60),
                "items" to itemMaps,
                "totalAmount" to totalAmount,
                "status" to OrderStatus.PENDING.label,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).awaitWrite(null, OperationType.CREATE, path)
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    /**
     * Updates order status and, when moving to [OrderStatus.DELIVERED], atomically deducts each
     * ordered item's quantity from `products/{productId}.stockQuantity` (once per order, guarded
     * by `orders/{orderId}.stockDeducted`). Returns the count of products whose stock was adjusted.
     */
    suspend fun updateOrderStatus(order: Order, newStatus: OrderStatus): Result<Int> {
        val orderId = order.id
        val path = "orders/$orderId"
        return try {
            requireUserId()
            require(orderId.isNotBlank()) { "Order ID required" }

            val orderRef = db.collection("orders").document(orderId)
            val deductionsByProductId: Map<String, Long> = if (newStatus == OrderStatus.DELIVERED) {
                order.items
                    .filter { it.productId.isNotBlank() && it.qty > 0L }
                    .groupBy { it.productId }
                    .mapValues { (_, list) -> list.sumOf { it.qty.coerceAtLeast(0L) } }
            } else {
                emptyMap()
            }

            if (newStatus != OrderStatus.DELIVERED || deductionsByProductId.isEmpty()) {
                orderRef.update(
                    mapOf(
                        "status" to newStatus.label,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitWrite(null, OperationType.UPDATE, path)
                return Result.success(0)
            }

            suspend fun queueOfflineDeliveryBatch(): Int {
                val cachedOrderSnap = try {
                    orderRef.get(Source.CACHE).await()
                } catch (_: Exception) {
                    null
                }
                val alreadyDeducted = order.stockDeducted || (cachedOrderSnap?.getBoolean("stockDeducted") == true)
                val batch = db.batch()
                var deductedProductsCount = 0

                if (!alreadyDeducted) {
                    deductionsByProductId.forEach { (productId, deductQty) ->
                        val prodRef = db.collection("products").document(productId)
                        val cachedProd = try {
                            prodRef.get(Source.CACHE).await()
                        } catch (_: Exception) {
                            null
                        }
                        if (cachedProd != null && cachedProd.exists()) {
                            val currentQty = cachedProd.getLong("stockQuantity")
                            if (currentQty != null) {
                                val newQty = (currentQty - deductQty).coerceAtLeast(0L)
                                batch.update(
                                    prodRef,
                                    mapOf(
                                        "stockQuantity" to newQty,
                                        "inStock" to (newQty > 0L),
                                        "updatedAt" to FieldValue.serverTimestamp()
                                    )
                                )
                                deductedProductsCount++
                            }
                        }
                    }
                    batch.update(
                        orderRef,
                        mapOf(
                            "status" to newStatus.label,
                            "stockDeducted" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    batch.update(
                        orderRef,
                        mapOf(
                            "status" to newStatus.label,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                batch.commit().awaitWrite(null, OperationType.UPDATE, path)
                return deductedProductsCount
            }

            val deductedCount = if (isCurrentlyOffline()) {
                queueOfflineDeliveryBatch()
            } else {
                try {
                    db.runTransaction { tx ->
                        // All Firestore transaction reads MUST execute before any writes
                        val orderSnap = tx.get(orderRef)
                        val alreadyDeducted = orderSnap.exists() && orderSnap.getBoolean("stockDeducted") == true
                        val prodEntries = if (!alreadyDeducted) {
                            deductionsByProductId.map { (productId, deductQty) ->
                                val prodRef = db.collection("products").document(productId)
                                Triple(prodRef, deductQty, tx.get(prodRef))
                            }
                        } else {
                            emptyList()
                        }

                        var updatedCount = 0
                        if (!alreadyDeducted) {
                            prodEntries.forEach { (prodRef, deductQty, prodSnap) ->
                                if (prodSnap.exists()) {
                                    val currentQty = prodSnap.getLong("stockQuantity")
                                    if (currentQty != null) {
                                        val newQty = (currentQty - deductQty).coerceAtLeast(0L)
                                        tx.update(
                                            prodRef,
                                            mapOf(
                                                "stockQuantity" to newQty,
                                                "inStock" to (newQty > 0L),
                                                "updatedAt" to FieldValue.serverTimestamp()
                                            )
                                        )
                                        updatedCount++
                                    }
                                }
                            }
                            tx.update(
                                orderRef,
                                mapOf(
                                    "status" to newStatus.label,
                                    "stockDeducted" to true,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        } else {
                            tx.update(
                                orderRef,
                                mapOf(
                                    "status" to newStatus.label,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                        updatedCount
                    }.await()
                } catch (txError: FirebaseFirestoreException) {
                    if (txError.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                        txError.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    ) {
                        queueOfflineDeliveryBatch()
                    } else {
                        throw txError
                    }
                }
            }

            Result.success(deductedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // --- 6b. FCM NOTIFICATIONS & REAL-TIME SHOPKEEPER ALERTS ---

    fun observeShopkeeperNotifications(shopkeeperId: String): Flow<List<NotificationEvent>> = flow {
        require(shopkeeperId.isNotBlank()) { "Shopkeeper ID required" }
        val path = "notifications"
        emitAll(
            db.collection("notifications")
                .whereIn("targetUserId", listOf(shopkeeperId, TARGET_ALL_BUYERS))
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(NotificationEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                        .take(40)
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, shopkeeperId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun publishOrderStatusNotification(
        order: Order,
        newStatus: OrderStatus,
        targetFcmToken: String = ""
    ): Result<Unit> {
        val docRef = db.collection("notifications").document()
        val path = docRef.path
        return try {
            requireUserId()
            val shortId = order.id.takeLast(6).uppercase(Locale.US)
            val title = "Order #$shortId ${newStatus.label}".take(160)
            val body = "Your wholesale order of ${formatInr(order.totalAmount)} (${order.items.size} ${if (order.items.size == 1) "item" else "items"}) is now ${newStatus.label}.".take(400)
            val payload = mapOf(
                "type" to NOTIFICATION_TYPE_ORDER_STATUS,
                "targetUserId" to order.shopkeeperId,
                "targetTopic" to "user_${order.shopkeeperId}",
                "targetFcmToken" to targetFcmToken.trim().take(512),
                "title" to title,
                "body" to body,
                "orderId" to order.id,
                "productId" to "",
                "status" to newStatus.label,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).awaitWrite(null, OperationType.CREATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun publishNewProductNotification(
        productId: String,
        name: String,
        category: String,
        carModel: String,
        price: Double,
        moq: Long
    ): Result<Unit> {
        val docRef = db.collection("notifications").document()
        val path = docRef.path
        return try {
            requireUserId()
            val title = "New Product: ${name.trim()}".take(160)
            val body = "${carModel.trim()} • ${category.trim()} • ${formatInr(price)} (MOQ $moq pcs)".take(400)
            val payload = mapOf(
                "type" to NOTIFICATION_TYPE_NEW_PRODUCT,
                "targetUserId" to TARGET_ALL_BUYERS,
                "targetTopic" to "new_products",
                "targetFcmToken" to "",
                "title" to title,
                "body" to body,
                "orderId" to "",
                "productId" to productId,
                "status" to "",
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).awaitWrite(null, OperationType.CREATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- 7. INVOICES & ATOMIC BILL GENERATION ---

    fun observeAllInvoices(adminUserId: String): Flow<List<Invoice>> = flow {
        require(adminUserId.isNotBlank()) { "Admin user ID required" }
        val path = "invoices"
        emitAll(
            db.collection("invoices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    _invoiceSyncMeta.value = CollectionSyncMeta(
                        isFromCache = snapshot.metadata.isFromCache,
                        pendingDocumentIds = snapshot.documents
                            .filter { it.metadata.hasPendingWrites() }
                            .map { it.id }
                            .toSet()
                    )
                    snapshot.toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, adminUserId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    fun observeShopkeeperInvoices(shopkeeperId: String): Flow<List<Invoice>> = flow {
        require(shopkeeperId.isNotBlank()) { "Shopkeeper ID required" }
        val path = "invoices"
        emitAll(
            db.collection("invoices")
                .whereEqualTo("shopkeeperId", shopkeeperId)
                .snapshots(MetadataChanges.INCLUDE)
                .map { snapshot ->
                    _invoiceSyncMeta.value = CollectionSyncMeta(
                        isFromCache = snapshot.metadata.isFromCache,
                        pendingDocumentIds = snapshot.documents
                            .filter { it.metadata.hasPendingWrites() }
                            .map { it.id }
                            .toSet()
                    )
                    snapshot.toObjects(Invoice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, shopkeeperId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    /**
     * Atomically increments `settings/businessProfile.nextInvoiceNumber`, writes the new Invoice,
     * and links `invoiceId` on the source Order (if created from an order) inside a single Firestore transaction.
     */
    suspend fun createInvoiceWithTransaction(
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
        initialPaymentNote: String,
        fallbackProfile: UserProfile
    ): Result<Invoice> {
        var path = "invoices"
        return try {
            requireUserId()
            val cleanShop = shopName.trim().take(100)
            val cleanPhone = phone.filter { it.isDigit() }.take(20)
            val cleanCity = city.trim().take(60)
            val cleanGstin = shopGstin.trim().uppercase(Locale.US).take(20)

            require(cleanShop.isNotEmpty()) { "Enter shop/party name." }
            require(items.isNotEmpty()) { "Add at least one bill item." }
            require(items.size <= 50) { "Maximum 50 line items per bill." }

            val subtotal = round2(items.sumOf { it.rate * it.qty })
            val clampedDiscount = round2(discountAmount.coerceIn(0.0, subtotal))
            val taxableBase = max(0.0, subtotal - clampedDiscount)
            val effectiveTaxType = if (billType == BillType.GST_INVOICE && taxType != TaxType.NONE) taxType else TaxType.NONE

            val taxAmount = if (effectiveTaxType != TaxType.NONE && subtotal > 0.0) {
                val discountRatio = taxableBase / subtotal
                round2(items.sumOf { item ->
                    val itemTaxable = (item.rate * item.qty) * discountRatio
                    itemTaxable * (item.gstPercent.toDouble() / 100.0)
                })
            } else {
                0.0
            }

            val cleanTransport = round2(transportCharges.coerceAtLeast(0.0))
            val grandTotal = round2(taxableBase + taxAmount + cleanTransport)
            require(grandTotal > 0.0 && grandTotal <= 100_000_000.0) { "Grand total must be greater than ₹0." }

            val clampedPaid = round2(initialPaidAmount.coerceIn(0.0, grandTotal))
            val balanceDue = round2(max(0.0, grandTotal - clampedPaid))
            val paymentStatus = when {
                balanceDue <= 0.0 -> PaymentStatus.PAID
                clampedPaid > 0.0 -> PaymentStatus.PARTIAL
                else -> PaymentStatus.UNPAID
            }

            val paymentHistory = if (clampedPaid > 0.0) {
                listOf(
                    PaymentRecord(
                        amount = clampedPaid,
                        mode = initialPaymentMode.label,
                        date = initialPaymentDate,
                        note = initialPaymentNote.trim().take(120)
                    )
                )
            } else emptyList()

            val itemMaps = items.map { item ->
                mapOf(
                    "productId" to item.productId,
                    "name" to item.name.trim().take(120),
                    "hsnCode" to item.hsnCode.trim().take(20),
                    "qty" to item.qty.coerceAtLeast(1L),
                    "rate" to round2(item.rate),
                    "gstPercent" to if (item.gstPercent in GST_RATES) item.gstPercent else 0L,
                    "lineTotal" to round2(item.rate * item.qty)
                )
            }

            val paymentMaps = paymentHistory.map { pay ->
                mapOf(
                    "amount" to pay.amount,
                    "mode" to pay.mode,
                    "date" to pay.date,
                    "note" to pay.note
                )
            }

            val profileDocRef = db.collection("settings").document("businessProfile")
            val invoiceDocRef = db.collection("invoices").document()
            path = invoiceDocRef.path

            val deductionsByProductId: Map<String, Long> = items
                .filter { it.productId.isNotBlank() && it.qty > 0L }
                .groupBy { it.productId }
                .mapValues { (_, list) -> list.sumOf { it.qty.coerceAtLeast(0L) } }

            fun buildInvoiceRecordAndPayloads(
                existingBiz: BusinessProfile?,
                stockDeductedFlag: Boolean
            ): Triple<Map<String, Any>, Map<String, Any>, Invoice> {
                val firmName = existingBiz?.firmName?.ifBlank { null }
                    ?: fallbackProfile.shopName.ifBlank { "AutoTrade Wholesale" }
                val firmAddress = existingBiz?.address.orEmpty().ifBlank { fallbackProfile.city }
                val firmPhone = existingBiz?.phone?.ifBlank { null }
                    ?: fallbackProfile.phone.ifBlank { "9999999999" }
                val firmGstin = existingBiz?.gstin.orEmpty()
                val firmUpiId = existingBiz?.upiId.orEmpty()
                val prefix = existingBiz?.invoicePrefix?.ifBlank { null } ?: "INV"
                val currentNum = (existingBiz?.nextInvoiceNumber ?: 1L).coerceAtLeast(1L)
                val nextNum = currentNum + 1L
                val preservedLowStockThreshold = (existingBiz?.lowStockThreshold ?: DEFAULT_LOW_STOCK_THRESHOLD)
                    .coerceIn(1L, 100_000L)

                val formattedInvNumber = "$prefix-${currentNum.toString().padStart(4, '0')}"

                val profilePayload = mapOf(
                    "firmName" to firmName.take(100),
                    "address" to firmAddress.take(250),
                    "phone" to firmPhone.filter { it.isDigit() }.padEnd(10, '0').take(20),
                    "gstin" to firmGstin.take(20),
                    "upiId" to firmUpiId.take(100),
                    "invoicePrefix" to prefix.take(12),
                    "nextInvoiceNumber" to nextNum,
                    "lowStockThreshold" to preservedLowStockThreshold,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                val invoicePayload = mapOf(
                    "invoiceNumber" to formattedInvNumber,
                    "orderId" to orderId.trim().take(128),
                    "billType" to billType.label,
                    "shopkeeperId" to shopkeeperId.trim().take(128),
                    "shopName" to cleanShop,
                    "phone" to cleanPhone,
                    "city" to cleanCity,
                    "shopGstin" to cleanGstin,
                    "firmName" to firmName.take(100),
                    "firmAddress" to firmAddress.take(250),
                    "firmPhone" to firmPhone.take(20),
                    "firmGstin" to firmGstin.take(20),
                    "firmUpiId" to firmUpiId.take(100),
                    "items" to itemMaps,
                    "subtotal" to subtotal,
                    "discountAmount" to clampedDiscount,
                    "taxType" to effectiveTaxType.label,
                    "taxAmount" to taxAmount,
                    "transportCharges" to cleanTransport,
                    "grandTotal" to grandTotal,
                    "paidAmount" to clampedPaid,
                    "balanceDue" to balanceDue,
                    "paymentStatus" to paymentStatus.label,
                    "paymentHistory" to paymentMaps,
                    "stockDeducted" to stockDeductedFlag,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                val inv = Invoice(
                    id = invoiceDocRef.id,
                    invoiceNumber = formattedInvNumber,
                    orderId = orderId,
                    billType = billType.label,
                    shopkeeperId = shopkeeperId,
                    shopName = cleanShop,
                    phone = cleanPhone,
                    city = cleanCity,
                    shopGstin = cleanGstin,
                    firmName = firmName,
                    firmAddress = firmAddress,
                    firmPhone = firmPhone,
                    firmGstin = firmGstin,
                    firmUpiId = firmUpiId,
                    items = items.map { it.copy(lineTotal = round2(it.rate * it.qty)) },
                    subtotal = subtotal,
                    discountAmount = clampedDiscount,
                    taxType = effectiveTaxType.label,
                    taxAmount = taxAmount,
                    transportCharges = cleanTransport,
                    grandTotal = grandTotal,
                    paidAmount = clampedPaid,
                    balanceDue = balanceDue,
                    paymentStatus = paymentStatus.label,
                    paymentHistory = paymentHistory,
                    stockDeducted = stockDeductedFlag
                )
                return Triple(profilePayload, invoicePayload, inv)
            }

            suspend fun queueOfflineInvoiceBatch(): Invoice {
                val cachedBiz = try {
                    profileDocRef.get(Source.CACHE).await().toObject(BusinessProfile::class.java)
                } catch (_: Exception) {
                    null
                }
                val orderAlreadyDeducted = if (orderId.isNotBlank()) {
                    try {
                        db.collection("orders").document(orderId).get(Source.CACHE).await()
                            .getBoolean("stockDeducted") == true
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    false
                }

                val pendingProductUpdates = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Long>>()
                if (!orderAlreadyDeducted && deductionsByProductId.isNotEmpty()) {
                    deductionsByProductId.forEach { (productId, deductQty) ->
                        val prodRef = db.collection("products").document(productId)
                        val cachedProd = try {
                            prodRef.get(Source.CACHE).await()
                        } catch (_: Exception) {
                            null
                        }
                        if (cachedProd != null && cachedProd.exists()) {
                            val currentQty = cachedProd.getLong("stockQuantity")
                            if (currentQty != null) {
                                pendingProductUpdates.add(prodRef to (currentQty - deductQty).coerceAtLeast(0L))
                            }
                        }
                    }
                }

                val stockWasDeductedNow = pendingProductUpdates.isNotEmpty()
                val (profilePayload, invoicePayload, inv) = buildInvoiceRecordAndPayloads(
                    existingBiz = cachedBiz,
                    stockDeductedFlag = stockWasDeductedNow || orderAlreadyDeducted
                )
                val batch = db.batch()
                batch.set(profileDocRef, profilePayload)
                batch.set(invoiceDocRef, invoicePayload)
                pendingProductUpdates.forEach { (prodRef, newQty) ->
                    batch.update(
                        prodRef,
                        mapOf(
                            "stockQuantity" to newQty,
                            "inStock" to (newQty > 0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                if (orderId.isNotBlank()) {
                    val orderUpdate = mutableMapOf<String, Any>(
                        "invoiceId" to invoiceDocRef.id,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    if (!orderAlreadyDeducted && stockWasDeductedNow) {
                        orderUpdate["stockDeducted"] = true
                    }
                    batch.update(db.collection("orders").document(orderId), orderUpdate)
                }
                batch.commit().awaitWrite(null, OperationType.CREATE, path)
                return inv
            }

            val createdInvoice = if (isCurrentlyOffline()) {
                queueOfflineInvoiceBatch()
            } else {
                try {
                    db.runTransaction { tx ->
                        // 1. All reads MUST happen before any writes in a Firestore transaction
                        val profileSnap = tx.get(profileDocRef)
                        val existingBiz = profileSnap.toObject(BusinessProfile::class.java)
                        val orderRef = if (orderId.isNotBlank()) db.collection("orders").document(orderId) else null
                        val orderSnap = orderRef?.let { tx.get(it) }
                        val orderAlreadyDeducted = orderSnap != null &&
                            orderSnap.exists() &&
                            orderSnap.getBoolean("stockDeducted") == true

                        val prodSnapshots = if (!orderAlreadyDeducted && deductionsByProductId.isNotEmpty()) {
                            deductionsByProductId.map { (productId, deductQty) ->
                                val prodRef = db.collection("products").document(productId)
                                Triple(prodRef, deductQty, tx.get(prodRef))
                            }
                        } else {
                            emptyList()
                        }

                        val updatesToApply = prodSnapshots.mapNotNull { (prodRef, deductQty, snap) ->
                            if (!snap.exists()) return@mapNotNull null
                            val currentQty = snap.getLong("stockQuantity") ?: return@mapNotNull null
                            prodRef to (currentQty - deductQty).coerceAtLeast(0L)
                        }
                        val stockWasDeductedNow = updatesToApply.isNotEmpty()

                        val (profilePayload, invoicePayload, inv) = buildInvoiceRecordAndPayloads(
                            existingBiz = existingBiz,
                            stockDeductedFlag = stockWasDeductedNow || orderAlreadyDeducted
                        )

                        // 2. Execute atomic writes
                        tx.set(profileDocRef, profilePayload)
                        tx.set(invoiceDocRef, invoicePayload)
                        updatesToApply.forEach { (prodRef, newQty) ->
                            tx.update(
                                prodRef,
                                mapOf(
                                    "stockQuantity" to newQty,
                                    "inStock" to (newQty > 0L),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                        if (orderRef != null) {
                            val orderUpdate = mutableMapOf<String, Any>(
                                "invoiceId" to invoiceDocRef.id,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                            if (!orderAlreadyDeducted && stockWasDeductedNow) {
                                orderUpdate["stockDeducted"] = true
                            }
                            tx.update(orderRef, orderUpdate)
                        }
                        inv
                    }.await()
                } catch (txError: FirebaseFirestoreException) {
                    if (txError.code == FirebaseFirestoreException.Code.UNAVAILABLE ||
                        txError.code == FirebaseFirestoreException.Code.FAILED_PRECONDITION
                    ) {
                        queueOfflineInvoiceBatch()
                    } else {
                        throw txError
                    }
                }
            }

            Result.success(createdInvoice)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun recordInvoicePayment(
        invoice: Invoice,
        amount: Double,
        mode: PaymentMode,
        date: String,
        note: String
    ): Result<Unit> {
        val path = "invoices/${invoice.id}"
        return try {
            requireUserId()
            require(invoice.id.isNotBlank()) { "Invoice ID is required." }
            require(amount > 0.0) { "Enter a payment amount greater than ₹0." }
            require(invoice.paymentHistory.size < 100) { "Maximum payment entries reached." }

            val newPaid = round2((invoice.paidAmount + amount).coerceAtMost(invoice.grandTotal))
            val newBalance = round2(max(0.0, invoice.grandTotal - newPaid))
            val newStatus = when {
                newBalance <= 0.0 -> PaymentStatus.PAID
                newPaid > 0.0 -> PaymentStatus.PARTIAL
                else -> PaymentStatus.UNPAID
            }

            val updatedHistory = invoice.paymentHistory + PaymentRecord(
                amount = round2(amount),
                mode = mode.label,
                date = date.trim().take(40),
                note = note.trim().take(120)
            )
            val historyMaps = updatedHistory.map { pay ->
                mapOf(
                    "amount" to pay.amount,
                    "mode" to pay.mode,
                    "date" to pay.date,
                    "note" to pay.note
                )
            }

            db.collection("invoices").document(invoice.id).update(
                mapOf(
                    "paidAmount" to newPaid,
                    "balanceDue" to newBalance,
                    "paymentStatus" to newStatus.label,
                    "paymentHistory" to historyMaps,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWrite(null, OperationType.UPDATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteInvoice(invoiceId: String): Result<Unit> {
        val path = "invoices/$invoiceId"
        return try {
            requireUserId()
            require(invoiceId.isNotBlank()) { "Invoice ID is required." }
            db.collection("invoices").document(invoiceId).delete().awaitWrite(null, OperationType.DELETE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- 9. ADMIN ACTIVITY LOGS (IMMUTABLE AUDIT TRAIL) ---

    fun observeActivityLogs(
        adminUserId: String,
        limit: Long = 100L
    ): Flow<List<AdminActivityLog>> = flow {
        require(adminUserId.isNotBlank()) { "Admin user ID required" }
        val path = "activityLogs"
        emitAll(
            db.collection("activityLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.coerceIn(1L, 200L))
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AdminActivityLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    logStreamErrorIfSignedIn(error, adminUserId, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun recordActivityLog(
        action: ActivityLogAction,
        category: ActivityLogCategory,
        actorName: String,
        targetId: String,
        targetName: String,
        summary: String,
        previousValue: String = "",
        newValue: String = ""
    ): Result<Unit> {
        val path = "activityLogs"
        return try {
            val uid = requireUserId()
            val email = auth.currentUser?.email.orEmpty().trim().take(120)
            val cleanActorName = actorName.trim()
                .ifBlank { auth.currentUser?.displayName.orEmpty().trim() }
                .ifBlank { email.substringBefore("@") }
                .take(100)
            val cleanTargetName = targetName.trim().ifBlank { "System" }.take(160)
            val cleanSummary = summary.trim()
                .ifBlank { "${action.label}: $cleanTargetName" }
                .take(300)

            val payload = mapOf(
                "action" to action.raw,
                "category" to category.raw,
                "actorUid" to uid,
                "actorEmail" to email,
                "actorName" to cleanActorName,
                "targetId" to targetId.trim().take(128),
                "targetName" to cleanTargetName,
                "summary" to cleanSummary,
                "previousValue" to previousValue.trim().take(200),
                "newValue" to newValue.trim().take(200),
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("activityLogs").add(payload).awaitWrite(null, OperationType.CREATE, path)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    private fun logStreamErrorIfSignedIn(
        error: Throwable,
        expectedUid: String,
        op: OperationType,
        path: String
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, op, path)
        }
    }

    companion object {
        private val HEX_COLOR_REGEX = Regex("^#[0-9A-F]{6}$")

        fun round2(value: Double): Double = (value * 100.0).roundToLong() / 100.0

        fun formatInr(amount: Double): String {
            val formatter = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
                maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
                minimumFractionDigits = 0
            }
            return "₹${formatter.format(amount)}"
        }

        fun buildWhatsAppOrderSummary(
            shopName: String,
            phone: String,
            city: String,
            items: List<OrderItem>,
            totalAmount: Double,
            orderId: String? = null
        ): String {
            val sb = StringBuilder()
            sb.append("*NEW WHOLESALE ORDER — AutoTrade*\n")
            if (!orderId.isNullOrBlank()) {
                sb.append("Order ID: #${orderId.takeLast(6).uppercase(Locale.US)}\n")
            }
            sb.append("Shop: $shopName\n")
            sb.append("City: $city\n")
            sb.append("Phone: $phone\n")
            sb.append("----------------------------\n")
            items.forEachIndexed { idx, item ->
                sb.append("${idx + 1}. ${item.name}")
                if (item.carModel.isNotBlank()) sb.append(" (${item.carModel})")
                sb.append("\n   Qty: ${item.qty} × ${formatInr(item.price)} = ${formatInr(item.lineTotal())}\n")
            }
            sb.append("----------------------------\n")
            sb.append("*Grand Total: ${formatInr(totalAmount)}*\n")
            sb.append("Please confirm availability & dispatch timeline.")
            return sb.toString()
        }

        fun buildWhatsAppInvoiceSummary(invoice: Invoice): String {
            val sb = StringBuilder()
            val firm = invoice.firmName.ifBlank { "AutoTrade Wholesale" }
            sb.append("*${invoice.billType.uppercase(Locale.US)} — $firm*\n")
            sb.append("Invoice No: *${invoice.invoiceNumber}*\n")
            sb.append("Party: ${invoice.shopName}")
            if (invoice.city.isNotBlank()) sb.append(" (${invoice.city})")
            sb.append("\n----------------------------\n")
            invoice.items.forEachIndexed { idx, item ->
                sb.append("${idx + 1}. ${item.name} — ${item.qty} × ${formatInr(item.rate)} = ${formatInr(item.lineTotal)}\n")
            }
            sb.append("----------------------------\n")
            if (invoice.discountAmount > 0.0) {
                sb.append("Discount: -${formatInr(invoice.discountAmount)}\n")
            }
            if (invoice.taxAmount > 0.0) {
                sb.append("Tax (${invoice.taxType}): ${formatInr(invoice.taxAmount)}\n")
            }
            if (invoice.transportCharges > 0.0) {
                sb.append("Transport: ${formatInr(invoice.transportCharges)}\n")
            }
            sb.append("*Grand Total: ${formatInr(invoice.grandTotal)}*\n")
            sb.append("Paid: ${formatInr(invoice.paidAmount)}\n")
            sb.append("*Balance Due: ${formatInr(invoice.balanceDue)}* (${invoice.paymentStatus})\n")
            if (invoice.firmUpiId.isNotBlank() && invoice.balanceDue > 0.0) {
                sb.append("UPI ID: ${invoice.firmUpiId}\n")
            }
            sb.append("Thank you for your business!")
            return sb.toString()
        }
    }
}
