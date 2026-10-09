package app.tuji.android.core.network

import kotlinx.serialization.Serializable

/**
 * `/api/billing/play/verify` — a Google Play purchase handed to the server,
 * which asks Google about the token and grants it. iOS's `BillingRepository`.
 *
 * The wire types live here, in the module that runs the serialization plugin:
 * a `@Serializable` class in `:app` gets no serializer generated, and the first
 * send fails in the client before it reaches the network.
 */
class BillingRepository(private val api: TujiApiClient) {
    /**
     * Returns the server's `state`: `pending` while a slow payment clears, or
     * how the purchase was written. A refusal arrives as [ApiError.Http].
     */
    suspend fun verifyPlayPurchase(productId: String, purchaseToken: String): String? =
        api.post<Reply>(Endpoint.PlayPurchaseVerify, Request(productId, purchaseToken)).state

    @Serializable private data class Request(val productId: String, val purchaseToken: String)

    @Serializable private data class Reply(val state: String? = null)
}
