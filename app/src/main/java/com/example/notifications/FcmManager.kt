package com.example.notifications

import android.util.Log
import com.example.data.AccountStatus
import com.example.data.AutoTradeRepository
import com.example.data.UserProfile
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

/**
 * Coordinates FCM registration token syncing and topic subscriptions for approved shopkeepers:
 * - `new_products`: broadcast topic for newly added wholesale products
 * - `user_<uid>`: targeted topic for per-shopkeeper order status updates
 */
object FcmManager {
    private const val TAG = "FcmManager"
    const val TOPIC_NEW_PRODUCTS = "new_products"

    fun userOrderTopic(userId: String): String = "user_$userId"

    suspend fun syncRegistrationAndTopics(
        repository: AutoTradeRepository,
        profile: UserProfile
    ): String? {
        val messaging = FirebaseMessaging.getInstance()
        val token = try {
            messaging.token.await()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to fetch FCM token right now", e)
            null
        }

        if (!token.isNullOrBlank() && token != profile.fcmToken) {
            repository.saveFcmToken(token)
        }

        val isApprovedShopkeeper = !profile.roleIsAdmin() && profile.accountStatus() == AccountStatus.APPROVED
        val orderTopic = userOrderTopic(profile.userId.ifBlank { profile.id })

        try {
            if (isApprovedShopkeeper && profile.notifyNewProducts) {
                messaging.subscribeToTopic(TOPIC_NEW_PRODUCTS).await()
            } else {
                messaging.unsubscribeFromTopic(TOPIC_NEW_PRODUCTS).await()
            }

            if (isApprovedShopkeeper && profile.notifyOrderUpdates && orderTopic.length > 5) {
                messaging.subscribeToTopic(orderTopic).await()
            } else if (orderTopic.length > 5) {
                messaging.unsubscribeFromTopic(orderTopic).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM topic subscription sync deferred", e)
        }

        return token ?: profile.fcmToken.takeIf { it.isNotBlank() }
    }

    fun clearTopicsOnSignOut(userId: String) {
        try {
            val messaging = FirebaseMessaging.getInstance()
            messaging.unsubscribeFromTopic(TOPIC_NEW_PRODUCTS)
            if (userId.isNotBlank()) {
                messaging.unsubscribeFromTopic(userOrderTopic(userId))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unsubscribe FCM topics on sign-out", e)
        }
    }
}
