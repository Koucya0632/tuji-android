package app.tuji.android.core.network

import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.model.AtlasAuthorPage
import app.tuji.android.core.model.AtlasCollectionDetail
import app.tuji.android.core.model.AtlasCollectionsResponse
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicDetail
import app.tuji.android.core.model.AtlasPublicDetailResponse
import app.tuji.android.core.model.AtlasPublicFeed
import app.tuji.android.core.model.AtlasCard
import app.tuji.android.core.model.AtlasCardsResponse
import app.tuji.android.core.model.AtlasConfirmPayload
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.AtlasPublishResult
import app.tuji.android.core.model.AtlasRecognitionResponse
import app.tuji.android.core.model.AtlasUploadResponse
import app.tuji.android.core.model.BlockedHandles
import app.tuji.android.core.model.RecognitionMode
import app.tuji.android.core.model.TargetLanguage
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.UserMe
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.UserMeResponse
import kotlinx.serialization.Serializable

/** Reading 物見, as a role. */
interface AtlasReading {
    suspend fun feed(limit: Int = 40): AtlasPublicFeed
    suspend fun item(slug: String, lang: String): AtlasPublicDetail?
    suspend fun author(handle: String): AtlasAuthorPage
    suspend fun collections(lang: String, limit: Int = 40): List<AtlasPublicCollection>
    suspend fun collection(slug: String): AtlasCollectionDetail
}

/** Saving someone else's word into your own 圖鑑. */
interface AtlasSaving {
    suspend fun save(slug: String)
}

/**
 * One seam for submitting a 檢舉, whatever the target.
 *
 * Without it a screen has to pick between three near-identical calls, and the
 * two screens that report are the ones least able to be tested if they reach
 * for a singleton to do it.
 */
interface ReportSubmitting {
    suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?)
}

/**
 * Making a 自製圖鑑 entry: photo → 辨識 → 確認 → 卡片.
 *
 * A role of its own rather than more methods on the reading seam. Consuming
 * 物見 and producing it are different milestones with different failure modes,
 * and a fake for one should not have to stub the other.
 */
interface AtlasAuthoring {
    /**
     * Upload one photo.
     *
     * The primary recognition runs server-side **in this same request**, so the
     * response already carries candidates. That is why it uses the slow policy:
     * it is an upload plus a vision pass, and it is not retriable for free.
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        filename: String,
        mimeType: String,
        targetLanguage: TargetLanguage?,
    ): AtlasUploadResponse

    /** A second, explicit pass. Another AI call — `CaptureDraft` decides when. */
    suspend fun recognize(imageId: String, mode: RecognitionMode): AtlasRecognitionResponse

    suspend fun confirm(imageId: String, payload: AtlasConfirmPayload): AtlasItem

    /** The study cards. An item is not reviewable until these exist. */
    suspend fun createCards(itemId: String, cardTypes: List<String>): List<AtlasCard>

    /**
     * Offer it to 物見.
     *
     * The result says whether it actually went live: a machine gate publishes
     * clean submissions immediately and queues risky ones for a human.
     */
    suspend fun publish(itemId: String): AtlasPublishResult
}

/** Reading the account's tier, limits and usage. */
/**
 * One 自製圖鑑 card in full.
 *
 * Its own role, and the payload is the **same [WordDetail]** the dictionary
 * sends: the server says so in the route's own comment, and it is why 單字詳情
 * can draw a card the user photographed without a second screen or a second
 * model. The only thing the caller has to know is that the id it holds is
 * prefixed — `atlas:<uuid>` — and this takes the bare one.
 */
interface AtlasItemReading {
    suspend fun itemDetail(itemId: String, lang: String): WordDetail
}

interface EntitlementReading {
    suspend fun entitlement(): Entitlement
}

/** Who is signed in. */
interface AccountReading {
    suspend fun me(): UserMe?
}

/** The 封鎖 list. Stored on the server so it follows the account. */
interface BlockListing {
    suspend fun blockedHandles(): List<String>
}

@Serializable
private data class RecognizeBody(val mode: String)

@Serializable
private data class CardsBody(val cardTypes: List<String>)

@Serializable
private data class AtlasConfirmResponse(val item: AtlasItem)

@Serializable
private data class ReportBody(val reason: String, val detail: String? = null)

@Serializable
private data class Empty(val ok: Boolean? = null)

class AtlasRepository(private val api: TujiApiClient) :
    AtlasReading, AtlasSaving, ReportSubmitting, BlockListing,
    EntitlementReading, AccountReading, AtlasAuthoring, AtlasItemReading {

    override suspend fun itemDetail(itemId: String, lang: String): WordDetail =
        api.get(Endpoint.AtlasItemDetail(itemId = itemId, lang = lang))

    override suspend fun uploadImage(
        bytes: ByteArray,
        filename: String,
        mimeType: String,
        targetLanguage: TargetLanguage?,
    ): AtlasUploadResponse = api.post(
        Endpoint.AtlasImages,
        MultiPartFormDataContent(
            formData {
                append(
                    "file", bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, mimeType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$filename\"")
                    },
                )
                targetLanguage?.let { append("targetLanguage", if (it == TargetLanguage.JA) "ja" else "en") }
            },
        ),
    )

    override suspend fun recognize(
        imageId: String,
        mode: RecognitionMode,
    ): AtlasRecognitionResponse =
        api.post(Endpoint.AtlasRecognize(imageId), RecognizeBody(mode.wire))

    override suspend fun confirm(imageId: String, payload: AtlasConfirmPayload): AtlasItem =
        api.post<AtlasConfirmResponse>(Endpoint.AtlasConfirm(imageId), payload).item

    override suspend fun createCards(itemId: String, cardTypes: List<String>): List<AtlasCard> =
        api.post<AtlasCardsResponse>(Endpoint.AtlasCards(itemId), CardsBody(cardTypes)).cards

    override suspend fun publish(itemId: String): AtlasPublishResult =
        api.post(Endpoint.AtlasPublish(itemId), Empty())


    override suspend fun entitlement(): Entitlement = api.get(Endpoint.Entitlement)

    override suspend fun me(): UserMe? = api.get<UserMeResponse>(Endpoint.Me).user


    override suspend fun feed(limit: Int): AtlasPublicFeed =
        api.get(Endpoint.AtlasFeed(limit = limit))

    override suspend fun item(slug: String, lang: String): AtlasPublicDetail? =
        api.get<AtlasPublicDetailResponse>(Endpoint.AtlasItem(slug = slug, lang = lang)).item

    override suspend fun author(handle: String): AtlasAuthorPage =
        api.get(Endpoint.AtlasAuthorPage(handle = handle))

    override suspend fun collections(lang: String, limit: Int): List<AtlasPublicCollection> =
        api.get<AtlasCollectionsResponse>(
            Endpoint.AtlasCollections(lang = lang, limit = limit),
        ).collections

    override suspend fun collection(slug: String): AtlasCollectionDetail =
        api.get(Endpoint.AtlasCollection(slug = slug))

    override suspend fun save(slug: String) {
        api.post<Empty>(Endpoint.AtlasSave(slug = slug), Empty())
    }

    override suspend fun report(target: ReportTarget, reason: ReportReason, detail: String?) {
        val endpoint = when (target) {
            is ReportTarget.Item -> Endpoint.AtlasItemReport(target.slug)
            is ReportTarget.Collection -> Endpoint.AtlasCollectionReport(target.slug)
            is ReportTarget.Author -> Endpoint.AtlasAuthorReport(target.handle)
        }
        api.post<Empty>(endpoint, ReportBody(reason = reason.wire, detail = detail))
    }

    override suspend fun blockedHandles(): List<String> =
        api.get<BlockedHandles>(Endpoint.Blocks).handles
}
