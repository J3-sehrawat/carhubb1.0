package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks real-time device network connectivity via Android's ConnectivityManager so the app
 * can display live offline/sync banners, route writes safely into Firestore's persistent
 * local queue when offline, and confirm cloud synchronization upon reconnection.
 */
class NetworkConnectivityMonitor(context: Context) {
    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(computeOnlineNow())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = computeOnlineNow()
        }

        override fun onLost(network: Network) {
            _isOnline.value = computeOnlineNow()
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            _isOnline.value = hasInternet && isValidated || computeOnlineNow()
        }

        override fun onUnavailable() {
            _isOnline.value = false
        }
    }

    init {
        val cm = connectivityManager
        if (cm != null) {
            try {
                cm.registerDefaultNetworkCallback(networkCallback)
            } catch (_: Exception) {
                try {
                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    cm.registerNetworkCallback(request, networkCallback)
                } catch (_: Exception) {
                    // Fallback to polling via computeOnlineNow()
                }
            }
        }
    }

    fun isOnlineNow(): Boolean {
        val live = computeOnlineNow()
        if (_isOnline.value != live) {
            _isOnline.value = live
        }
        return live
    }

    private fun computeOnlineNow(): Boolean {
        val cm = connectivityManager ?: return true
        return try {
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            true
        }
    }

    fun unregister() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) {
            // Already unregistered
        }
    }
}
