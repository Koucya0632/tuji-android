package app.tuji.android.core.network

sealed class CreditEndpoint(path: String) : Endpoint {
    override val descriptor = EndpointDescriptor(path = path, policy = EndpointPolicy.PrivateFresh)
    data object Wallet : CreditEndpoint("/api/credits/wallet")
    data object Catalog : CreditEndpoint("/api/credits/catalog")
    data object Monthly : CreditEndpoint("/api/credits/benefits/monthly/claim")
    data object CheckIn : CreditEndpoint("/api/credits/check-in")
    data object Images : CreditEndpoint("/api/ai/images")
    data object Quotes : CreditEndpoint("/api/ai/quotes")
    data object Operations : CreditEndpoint("/api/ai/operations")
    data class Operation(val id: String) : CreditEndpoint("/api/ai/operations/$id")
    data class Cancel(val id: String) : CreditEndpoint("/api/ai/operations/$id/cancel")
    data class Confirm(val id: String) : CreditEndpoint("/api/ai/operations/$id/confirm")
}
