package com.example

import android.app.Application
import com.example.data.FirestoreProvider

/**
 * Application entry point ensuring Firestore's persistent offline disk cache is configured
 * at process startup before any Activity, ViewModel, or FirebaseMessagingService touches Firestore.
 */
class AutoTradeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirestoreProvider.getDatabase(this)
    }
}
