package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object AutoTradeNotificationHelper {
    const val CHANNEL_ORDERS = "autotrade_orders"
    const val CHANNEL_PRODUCTS = "autotrade_products"

    const val EXTRA_TARGET_TAB = "extra_target_tab"
    const val TARGET_TAB_ORDERS = "orders"
    const val TARGET_TAB_CATALOG = "catalog"

    private const val PREFS_NAME = "autotrade_notification_prefs"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val ordersChannel = NotificationChannel(
            CHANNEL_ORDERS,
            "Order Status Updates",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Instant alerts when the wholesaler confirms, dispatches, or delivers your order."
            enableVibration(true)
        }

        val productsChannel = NotificationChannel(
            CHANNEL_PRODUCTS,
            "New Wholesale Products",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Instant alerts when new car accessories are added to the wholesale catalog."
        }

        manager.createNotificationChannel(ordersChannel)
        manager.createNotificationChannel(productsChannel)
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showOrderStatusNotification(
        context: Context,
        title: String,
        body: String,
        orderId: String
    ) {
        postNotification(
            context = context,
            channelId = CHANNEL_ORDERS,
            notificationId = ("order_$orderId").hashCode(),
            title = title,
            body = body,
            targetTab = TARGET_TAB_ORDERS,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    fun showNewProductNotification(
        context: Context,
        title: String,
        body: String,
        productId: String
    ) {
        postNotification(
            context = context,
            channelId = CHANNEL_PRODUCTS,
            notificationId = ("product_$productId").hashCode(),
            title = title,
            body = body,
            targetTab = TARGET_TAB_CATALOG,
            priority = NotificationCompat.PRIORITY_DEFAULT
        )
    }

    private fun postNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        targetTab: String,
        priority: Int
    ) {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_TARGET_TAB, targetTab)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Gracefully ignore if user revoked notification permission in system settings
        }
    }

    fun getLastSeenTimestampMs(context: Context, userId: String): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong("last_seen_$userId", 0L)
    }

    fun setLastSeenTimestampMs(context: Context, userId: String, timestampMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong("last_seen_$userId", timestampMs).apply()
    }

    fun hasDispatchedLocally(context: Context, userId: String, notificationId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val delivered = prefs.getStringSet("delivered_$userId", emptySet()).orEmpty()
        return notificationId in delivered
    }

    fun markDispatchedLocally(context: Context, userId: String, notificationId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("delivered_$userId", emptySet()).orEmpty().toMutableSet()
        existing.add(notificationId)
        // Bound stored IDs to keep SharedPreferences lightweight
        val trimmed = if (existing.size > 120) existing.take(100).toSet() else existing
        prefs.edit().putStringSet("delivered_$userId", trimmed).apply()
    }
}
