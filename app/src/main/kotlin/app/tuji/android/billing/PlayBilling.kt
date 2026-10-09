package app.tuji.android.billing

import android.app.Activity
import android.content.Context
import app.tuji.android.core.model.CreditCatalog
import app.tuji.android.core.model.CreditPack
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Google Play Billing for 永久會員 and the 罐頭點數 packs — iOS's
 * `StoreKitService`, minus Pro (not sold on Android).
 *
 * Application-lifetime, like iOS's singleton: Play reports a purchase to the
 * listener given here, and a purchase that clears while the membership page is
 * closed (a pending payment) still has to reach the server.
 *
 * Nothing here grants anything. A purchase is handed to the server
 * ([PlayDelivery]); the server asks Google, writes the membership or points,
 * then acknowledges or consumes. Until it does, Play keeps listing the purchase
 * and [reconcile] sends it again — and if it never does, Google refunds it
 * after three days. Money cannot be kept for nothing.
 */
class PlayBilling(
    context: Context,
    private val deliver: suspend (productId: String, token: String) -> DeliveryOutcome,
    private val catalog: suspend () -> CreditCatalog,
    private val currentUser: () -> String?,
    private val scope: CoroutineScope,
) {
    /** A product Play will sell, with its price as Play formats it for this account. */
    data class Offer(val productId: String, val price: String, internal val details: ProductDetails)

    enum class Notice {
        /** Paid and granted. */
        Delivered,

        /** Waiting for a slow payment method to clear. */
        Pending,

        /** Already a 永久會員 — the second purchase is refunded by Google. */
        AlreadyOwned,

        /** Paid, but not granted yet: it will be retried, never charged twice. */
        SyncPending,

        /** Play could not start the purchase. */
        Failed,

        /** The server stopped selling this between the page opening and the tap. */
        Unavailable,
    }

    data class State(
        val offers: Map<String, Offer> = emptyMap(),
        /** The point packs the server sells this account right now; empty when it sells none. */
        val packs: List<CreditPack> = emptyList(),
        val loading: Boolean = true,
        /** Play could not be reached, or did not know the products. */
        val loadFailed: Boolean = false,
        val purchasing: String? = null,
        val notice: Notice? = null,
        /** Bumped on every grant, so the entitlement and the wallet re-read. */
        val deliveries: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connecting = Mutex()

    /** One delivery per token at a time: the listener and [reconcile] can both see a purchase. */
    private val delivering = Mutex()
    private val inFlight = mutableSetOf<String>()

    private suspend fun connected(): Boolean = connecting.withLock {
        if (client.isReady) return@withLock true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
                }

                override fun onBillingServiceDisconnected() {
                    if (cont.isActive) cont.resume(false)
                }
            })
        }
    }

    /** What is for sale: 永久會員 always, the packs when the server's catalog sells them. */
    suspend fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        val packs = runCatching { catalog() }.getOrNull()?.takeIf { it.purchaseEnabled }?.packs.orEmpty()
        val ids = listOf(PlayDelivery.LIFETIME) + packs.map { it.productId }
        val offers = if (connected()) query(ids) else null
        _state.update {
            it.copy(
                loading = false,
                loadFailed = offers?.containsKey(PlayDelivery.LIFETIME) != true,
                offers = offers.orEmpty(),
                packs = packs.filter { pack -> offers?.containsKey(pack.productId) == true },
            )
        }
    }

    private suspend fun query(ids: List<String>): Map<String, Offer>? {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            ids.map {
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(it)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            },
        ).build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingResponseCode.OK) return null
        return result.productDetailsList.orEmpty().mapNotNull { details ->
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: return@mapNotNull null
            Offer(details.productId, price, details)
        }.associateBy { it.productId }
    }

    fun purchase(activity: Activity, productId: String) {
        val user = currentUser() ?: return
        val offer = _state.value.offers[productId] ?: return
        if (_state.value.purchasing != null) return
        _state.update { it.copy(purchasing = productId, notice = null) }
        scope.launch {
            // Like iOS: the catalog is asked again at the tap, so a pack the
            // server stopped selling a minute ago is not bought from a stale page.
            if (productId != PlayDelivery.LIFETIME) {
                val now = runCatching { catalog() }.getOrNull()
                if (now?.purchaseEnabled != true || now.packs.none { it.productId == productId }) {
                    _state.update { it.copy(purchasing = null, notice = Notice.Unavailable) }
                    return@launch
                }
            }
            val product = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(offer.details)
                .apply { offer.details.oneTimePurchaseOfferDetails?.offerToken?.let(::setOfferToken) }
                .build()
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(product))
                // ADR-0005's appAccountToken: the server grants only to this id.
                .setObfuscatedAccountId(user)
                .build()
            val result = withContext(Dispatchers.Main) { client.launchBillingFlow(activity, params) }
            when (result.responseCode) {
                BillingResponseCode.OK -> Unit // the listener takes it from here
                BillingResponseCode.ITEM_ALREADY_OWNED -> {
                    _state.update { it.copy(purchasing = null) }
                    reconcile()
                }
                else -> _state.update { it.copy(purchasing = null, notice = Notice.Failed) }
            }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingResponseCode.OK -> scope.launch {
                purchases.orEmpty().forEach { handle(it) }
                _state.update { it.copy(purchasing = null) }
            }
            BillingResponseCode.USER_CANCELED -> _state.update { it.copy(purchasing = null) }
            BillingResponseCode.ITEM_ALREADY_OWNED -> scope.launch {
                _state.update { it.copy(purchasing = null) }
                reconcile()
            }
            else -> _state.update { it.copy(purchasing = null, notice = Notice.Failed) }
        }
    }

    /**
     * Send every purchase Play still lists as not delivered. On launch, on
     * sign-in, on opening 會員方案, and from 重試同步 — a crash between paying
     * and granting heals here.
     */
    suspend fun reconcile(quiet: Boolean = true) {
        currentUser() ?: return
        if (!connected()) return
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
        )
        if (result.billingResult.responseCode != BillingResponseCode.OK) return
        result.purchasesList.forEach { handle(it, quiet) }
    }

    private suspend fun handle(purchase: Purchase, quiet: Boolean = false) {
        if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
            if (!quiet) _state.update { it.copy(notice = Notice.Pending) }
            return
        }
        val user = currentUser() ?: return
        val purchased = purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        val account = purchase.accountIdentifiers?.obfuscatedAccountId
        if (!PlayDelivery.needsDelivery(purchased, purchase.isAcknowledged, account, user)) return

        val token = purchase.purchaseToken
        if (!delivering.withLock { inFlight.add(token) }) return
        try {
            var outcome = DeliveryOutcome.Delivered
            for (productId in purchase.products) {
                outcome = deliver(productId, token)
                if (outcome != DeliveryOutcome.Delivered) break
            }
            val notice = when (outcome) {
                DeliveryOutcome.Delivered -> Notice.Delivered
                DeliveryOutcome.Pending -> Notice.Pending
                DeliveryOutcome.AlreadyOwned -> Notice.AlreadyOwned
                DeliveryOutcome.RetryLater -> Notice.SyncPending
                // Not this account's, or not a purchase: nothing to tell.
                DeliveryOutcome.WrongAccount, DeliveryOutcome.Rejected -> null
            }
            _state.update {
                it.copy(
                    notice = if (quiet && notice != Notice.Delivered) it.notice else notice ?: it.notice,
                    deliveries = it.deliveries + if (outcome == DeliveryOutcome.Delivered) 1 else 0,
                )
            }
        } finally {
            delivering.withLock { inFlight.remove(token) }
        }
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }
}
