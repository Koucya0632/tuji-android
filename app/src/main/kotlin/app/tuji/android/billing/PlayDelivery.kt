package app.tuji.android.billing

import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.Endpoint
import app.tuji.android.core.network.TujiApiClient
import kotlinx.serialization.Serializable

/**
 * What became of one purchase handed to `/api/billing/play/verify`.
 *
 * The server is the only thing that grants: it asks Google about the token,
 * writes 永久會員 or the points, and only then acknowledges or consumes. The
 * app's part is to hand the token over — after the purchase, and again on every
 * launch while Play still lists it as unacknowledged — and to say what happened.
 */
enum class DeliveryOutcome {
    Delivered,

    /** Paid with a slow method: nothing to grant until Play says it cleared. */
    Pending,

    /**
     * This account already holds a paid 永久會員. The server leaves the second
     * one unacknowledged, and Google refunds it on its own within three days.
     */
    AlreadyOwned,

    /** Bought while another Tuji account was signed in. Never granted here. */
    WrongAccount,

    /** Google says the purchase is not one: canceled, refunded, or unknown. */
    Rejected,

    /** Offline, or the server could not finish. The token stays with Play; retrying is safe. */
    RetryLater,
}

object PlayDelivery {
    /** The one-time product. The point packs come from the server's catalog. */
    const val LIFETIME = "app.tuji.lifetime"

    /** The server's answer, by status and the `error` it names. */
    fun outcome(status: Int, body: String?): DeliveryOutcome = when {
        status == 202 -> DeliveryOutcome.Pending
        status in 200..299 -> DeliveryOutcome.Delivered
        status == 401 -> DeliveryOutcome.RetryLater
        status == 403 -> DeliveryOutcome.WrongAccount
        status == 409 && body?.contains("lifetime already owned") == true -> DeliveryOutcome.AlreadyOwned
        // Already linked to this purchase on a previous delivery: nothing left to do.
        status == 409 && body?.contains("already linked") == true -> DeliveryOutcome.Delivered
        status == 400 || status == 409 -> DeliveryOutcome.Rejected
        else -> DeliveryOutcome.RetryLater
    }

    /**
     * Whether a purchase Play still lists should be sent. An acknowledged one
     * is done (a consumed one is not listed at all), and one made for another
     * Tuji account is that account's to deliver — sending it from here would
     * only earn a 403.
     */
    fun needsDelivery(purchased: Boolean, acknowledged: Boolean, accountId: String?, currentUser: String): Boolean =
        purchased && !acknowledged && accountId.equals(currentUser, ignoreCase = true)

    suspend fun verify(api: TujiApiClient, productId: String, token: String): DeliveryOutcome = try {
        // A 202 is a success status to the client, so the pending case is
        // read off the reply rather than the status.
        val reply = api.post<Reply>(Endpoint.PlayPurchaseVerify, Request(productId, token))
        if (reply.state == "pending") DeliveryOutcome.Pending else DeliveryOutcome.Delivered
    } catch (e: ApiError.Http) {
        outcome(e.status, e.body)
    } catch (e: ApiError) {
        DeliveryOutcome.RetryLater
    }

    @Serializable private data class Request(val productId: String, val purchaseToken: String)

    @Serializable private data class Reply(val state: String? = null)
}
