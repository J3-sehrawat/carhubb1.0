package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Centralized provider for AutoTrade's named Firestore database instance with persistent
 * on-device disk caching explicitly enabled before the first read or write.
 *
 * Guarantees that catalog items (including embedded product photos), orders, and pending
 * invoices remain readable and writable offline, automatically syncing when back online.
 */
object FirestoreProvider {
    private const val TAG = "FirestoreProvider"

    @Volatile
    private var configuredDb: FirebaseFirestore? = null

    fun getDatabase(context: Context): FirebaseFirestore {
        configuredDb?.let { return it }
        return synchronized(this) {
            configuredDb?.let { return@synchronized it }
            val appContext = context.applicationContext
            val databaseId = appContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            try {
                val persistentCache = PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                val settings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(persistentCache)
                    .build()
                db.firestoreSettings = settings
                Log.i(TAG, "Firestore offline persistent disk cache enabled (unlimited) for DB '$databaseId'")
            } catch (e: IllegalStateException) {
                // Already initialized earlier in this process; persistent cache settings remain active.
                Log.d(TAG, "Firestore settings already locked for DB '$databaseId': ${e.message}")
            }
            configuredDb = db
            db
        }
    }
}
