package com.emirkuljanin.hnfeed.core.data.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import com.emirkuljanin.hnfeed.core.common.dispatchers.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/** Whether the device has a network connection, so the UI can say "offline" and retry when it comes back. */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}

/**
 * [NetworkMonitor] on `ConnectivityManager` callbacks. "Online" means at least one network that Android has
 * validated as reaching the internet, so a Wi-Fi network behind a captive portal (or still validating) does not
 * trigger retries that would fail. Requests are still made while "offline" and failures are still handled: this
 * is for messaging and for knowing when a retry is worth it.
 */
internal class ConnectivityManagerNetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        if (connectivityManager == null) {
            send(false)
            channel.close()
            return@callbackFlow
        }

        val callback = object : NetworkCallback() {
            // Several networks can be up at once (Wi-Fi handing over to mobile data): offline only when none is.
            private val networks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                networks += network
                trySend(true)
            }

            override fun onLost(network: Network) {
                networks -= network
                trySend(networks.isNotEmpty())
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            .build()
        // The current state first: callbacks arrive on another thread and must have the last word, or a slow
        // initial check could overwrite a newer change.
        send(connectivityManager.isCurrentlyConnected())
        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
        .flowOn(ioDispatcher)
        .conflate()
        .distinctUntilChanged()

    private fun ConnectivityManager.isCurrentlyConnected(): Boolean =
        getNetworkCapabilities(activeNetwork)?.let { capabilities ->
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
}
