package app.tuji.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the device is online — iOS's `NetworkMonitor`, for the banner.
 *
 * Null until the system has answered once, so the banner never flashes at
 * launch for a phone that is in fact connected.
 */
class ConnectivityMonitor(context: Context) {

    private val _online = MutableStateFlow<Boolean?>(null)
    val online: StateFlow<Boolean?> = _online.asStateFlow()

    init {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            _online.value = true
        } else {
            _online.value = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }?.usable() ?: false
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = caps.usable()
                }

                override fun onLost(network: Network) {
                    _online.value = false
                }
            })
        }
    }

    /** Validated, not merely attached: a captive portal is not a connection. */
    private fun NetworkCapabilities.usable(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
