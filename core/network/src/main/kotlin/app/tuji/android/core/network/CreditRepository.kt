package app.tuji.android.core.network

import app.tuji.android.core.model.*
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

class CreditRepository(private val api: TujiApiClient) {
    suspend fun wallet(): CreditWallet = api.get(CreditEndpoint.Wallet)
    suspend fun catalog(): CreditCatalog = api.get(CreditEndpoint.Catalog)
    suspend fun claim(monthly: Boolean): CreditWallet = api.post<Claim>(if (monthly) CreditEndpoint.Monthly else CreditEndpoint.CheckIn, Empty()).wallet
    suspend fun history(): List<CreditOperation> = api.get<Listing>(CreditEndpoint.Operations).operations
    suspend fun read(id: String): CreditOperation = api.get(CreditEndpoint.Operation(id))
    suspend fun cancel(id: String): CreditOperation = api.post(CreditEndpoint.Cancel(id), Empty())
    suspend fun quote(imageId: String, precision: Boolean, language: String, gloss: String?): CreditQuote = api.post(
        CreditEndpoint.Quotes, QuoteRequest(imageId, if (precision) "atlas.recognize.precision" else "atlas.recognize.primary", language, gloss),
    )
    suspend fun accept(pending: PendingCreditAcceptance): CreditOperation = api.postIdempotent(CreditEndpoint.Operations, AcceptRequest(pending.quoteId), pending.key)
    /** Idempotent per operation and candidate — see [CreditConfirmRequest]. */
    suspend fun confirm(request: CreditConfirmRequest): Confirmed = api.post(
        CreditEndpoint.Confirm(request.operationId),
        ConfirmRequest(request.candidateId, request.lemma, request.displayZhHant, request.displayGloss),
    )
    suspend fun upload(bytes: ByteArray): AtlasImageSummary = api.post<Uploaded>(CreditEndpoint.Images, MultiPartFormDataContent(formData {
        append("file", bytes, Headers.build {
            append(HttpHeaders.ContentType, "image/jpeg")
            append(HttpHeaders.ContentDisposition, "filename=\"atlas-photo.jpg\"")
        })
    })).image

    /** An uploaded photo, for a result reopened in a later session whose local crop is gone. */
    suspend fun image(id: String): AtlasImageSummary = api.get<ImageDetail>(Endpoint.AtlasImage(id)).image

    @Serializable private class Empty
    @Serializable private data class ImageDetail(val image: AtlasImageSummary)
    @Serializable private data class Claim(val wallet: CreditWallet)
    @Serializable private data class Listing(val operations: List<CreditOperation>)
    @Serializable private data class Uploaded(val image: AtlasImageSummary)
    @Serializable private data class QuoteRequest(val imageId: String, val feature: String, val targetLanguage: String, val glossLanguage: String?)
    @Serializable private data class AcceptRequest(val quoteId: String)
    @Serializable private data class ConfirmRequest(val candidateId: String, val lemma: String, val displayZhHant: String, val displayGloss: String?)
    @Serializable data class Item(val id: String)
    @Serializable data class Confirmed(val item: Item, val operation: CreditOperation)
}
