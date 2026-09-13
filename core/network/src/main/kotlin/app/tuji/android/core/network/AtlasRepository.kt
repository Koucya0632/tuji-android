package app.tuji.android.core.network

import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.model.AtlasAuthorPage
import app.tuji.android.core.model.AtlasCollectionDetail
import app.tuji.android.core.model.AtlasCollectionsResponse
import app.tuji.android.core.model.AtlasCollectionLearnResult
import app.tuji.android.core.model.AtlasSaveState
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

/**
 * 收藏 a whole collection — which unlocks browsing it and counts toward the
 * author, and puts **nothing** in the reader's 圖鑑. That is [CollectionLearning].
 */
interface CollectionBookmarking {
    suspend fun savedCollections(lang: String): List<AtlasPublicCollection>
    suspend fun collectionSaveState(slug: String): AtlasSaveState
    suspend fun saveCollection(slug: String): AtlasSaveState
    suspend fun unsaveCollection(slug: String): AtlasSaveState
}

/** 全部加入學習 — put a saved collection's remaining items into the study queue. */
fun interface CollectionLearning {
    suspend fun learnCollection(slug: String): AtlasCollectionLearnResult
}

/**
 * 加入學習 for one 物見 word: it goes into the reader's own 圖鑑 as a card with
 * its own SRS history. All three verbs answer where it stands now and how many
 * people are learning it, so the page never has to guess either.
 */
interface AtlasSaving {
    suspend fun saveState(slug: String): AtlasSaveState
    suspend fun save(slug: String): AtlasSaveState
    suspend fun unsave(slug: String): AtlasSaveState
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
    suspend fun block(handle: String)
    suspend fun unblock(handle: String)
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

@Serializable
private data class BlockBody(val handle: String)

class AtlasRepository(private val api: TujiApiClient) :
    AtlasReading, AtlasSaving, ReportSubmitting, BlockListing,
    EntitlementReading, AccountReading, AtlasAuthoring, AtlasItemReading,
    CollectionBookmarking, CollectionLearning {

    override suspend fun savedCollections(lang: String): List<AtlasPublicCollection> =
        api.get<AtlasCollectionsResponse>(Endpoint.AtlasSavedCollections(lang = lang, limit = 100)).collections

    override suspend fun collectionSaveState(slug: String): AtlasSaveState =
        api.get(Endpoint.AtlasCollectionSave(slug))

    override suspend fun saveCollection(slug: String): AtlasSaveState =
        api.post(Endpoint.AtlasCollectionSave(slug), Empty())

    override suspend fun unsaveCollection(slug: String): AtlasSaveState =
        api.delete(Endpoint.AtlasCollectionSave(slug))

    override suspend fun learnCollection(slug: String): AtlasCollectionLearnResult =
        api.post(Endpoint.AtlasCollectionLearn(slug), Empty())

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

    override suspend fun saveState(slug: String): AtlasSaveState =
        api.get(Endpoint.AtlasSave(slug = slug))

    override suspend fun save(slug: String): AtlasSaveState =
        api.post(Endpoint.AtlasSave(slug = slug), Empty())

    override suspend fun unsave(slug: String): AtlasSaveState =
        api.delete(Endpoint.AtlasSave(slug = slug))

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

    override suspend fun block(handle: String) {
        api.post<Empty>(Endpoint.Blocks, BlockBody(handle))
    }

    override suspend fun unblock(handle: String) {
        api.delete<Empty>(Endpoint.Block(handle))
    }
}
