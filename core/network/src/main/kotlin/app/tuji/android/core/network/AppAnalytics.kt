package app.tuji.android.core.network

import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** One anonymous session per process that actually opens an Activity. No account or device id. */
class AppAnalytics(private val api: TujiApiClient, private val scope: CoroutineScope) {
    private val opened = AtomicBoolean(false)
    private val sessionId = UUID.randomUUID().toString()

    /** Activity recreation and sign-in returns keep the same session. Background workers don't count. */
    fun appOpened() {
        if (!opened.compareAndSet(false, true)) return
        scope.launch {
            try {
                api.post<Map<String, Boolean>>(Endpoint.Events, AppOpenEvent(sessionId, type = "app_open", platform = "android"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: a rejected event or offline launch must never block the app.
            }
        }
    }
}

@Serializable
internal data class AppOpenEvent(
    val sessionId: String,
    val type: String,
    val platform: String,
)
