package app.tuji.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Whether the device is online — iOS's `NetworkMonitor`, for the banner and for
 * asking again when the network comes back.
 *
 * Answered at construction from the current network, so the banner never
 * flashes at launch for a phone that is in fact connected. Null only until
 * then.
 */
class ConnectivityMonitor(context: Context) {

    private val _online = MutableStateFlow<Boolean?>(null)
    val online: StateFlow<Boolean?> = _online.asStateFlow()

    private val _reconnects = MutableStateFlow(0)

    /**
     * How many times the device has come back online since launch. A key for
     * "ask again": whatever a screen read while offline failed, and nothing
     * else asks a second time.
     */
    val reconnects: StateFlow<Int> = _reconnects.asStateFlow()

    init {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        if (manager == null) {
            _online.value = true
        } else {
            _online.value = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }?.usable() ?: false
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    publish(caps.usable())
                }

                override fun onLost(network: Network) {
                    publish(false)
                }
            })
        }
    }

    private fun publish(online: Boolean) {
        if (_online.value == false && online) _reconnects.update { it + 1 }
        _online.value = online
    }

    /**
     * Attached, and not held at a sign-in page. Deliberately not VALIDATED:
     * validation is a request to Google, which some networks block — mainland
     * China among them — and there the banner would say "offline" on every
     * screen of an app that is working. iOS's path monitor does not ask either.
     */
    private fun NetworkCapabilities.usable(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            !hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)
}
