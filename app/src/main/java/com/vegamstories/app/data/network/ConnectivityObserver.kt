package com.vegamstories.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the phone can reach the internet: connected, and the connection checked by Android to
 * actually work (not a Wi-Fi that still wants a sign-in page).
 */
@Singleton
class ConnectivityObserver @Inject constructor(@ApplicationContext context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    fun isOnlineNow(): Boolean = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }.reachesInternet()

    /** The current state, then every change. */
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.reachesInternet())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(isOnlineNow())
        manager?.registerDefaultNetworkCallback(callback)
        awaitClose { manager?.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}

private fun NetworkCapabilities?.reachesInternet(): Boolean =
    this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
