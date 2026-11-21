package com.example.notifications

import android.util.Log
import com.example.R
import com.example.data.AutoTradeRepository
import com.example.data.FirestoreProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging receiver for AutoTrade push alerts.
 * Handles FCM token refresh (persisting the latest token to Firestore `users/{uid}`)
 * and displays instant system notifications for Order Status updates and New Product listings.
 */
class AutoTradeMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed")
        val currentUid = Firebase.auth.currentUser?.uid ?: return
        serviceScope.launch {
            try {
                val db = FirestoreProvider.getDatabase(applicationContext)
                AutoTradeRepository(db).saveFcmToken(token)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to persist refreshed FCM token for $currentUid", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val type = data["type"].orEmpty()
        val title = data["title"]
            ?: message.notification?.title
            ?: when (type) {
                "order_status" -> "Order Status Updated"
                "new_product" -> "New Wholesale Product Added"
                else -> getString(R.string.app_name)
            }
        val body = data["body"]
            ?: message.notification?.body
            ?: "Open AutoTrade to view the latest wholesale update."

        val orderId = data["orderId"].orEmpty()
        val productId = data["productId"].orEmpty()
        val notificationDocId = data["notificationId"].orEmpty()

        // Mark locally dispatched if this came from a Firestore notification event so live listener doesn't duplicate it
        val uid = Firebase.auth.currentUser?.uid
        if (!uid.isNullOrBlank() && notificationDocId.isNotBlank()) {
            AutoTradeNotificationHelper.markDispatchedLocally(applicationContext, uid, notificationDocId)
        }

        when (type) {
            "order_status" -> AutoTradeNotificationHelper.showOrderStatusNotification(
                context = applicationContext,
                title = title,
                body = body,
                orderId = orderId.ifBlank { System.currentTimeMillis().toString() }
            )
            "new_product" -> AutoTradeNotificationHelper.showNewProductNotification(
                context = applicationContext,
                title = title,
                body = body,
                productId = productId.ifBlank { System.currentTimeMillis().toString() }
            )
            else -> AutoTradeNotificationHelper.showNewProductNotification(
                context = applicationContext,
                title = title,
                body = body,
                productId = message.messageId ?: System.currentTimeMillis().toString()
            )
        }
    }

    private companion object {
        const val TAG = "AutoTradeFCM"
    }
}
