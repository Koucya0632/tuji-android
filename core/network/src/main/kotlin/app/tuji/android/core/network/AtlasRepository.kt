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
import app.tuji.android.core.model.BlockedHandles
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

/** The 封鎖 list. Stored on the server so it follows the account. */
interface BlockListing {
    suspend fun blockedHandles(): List<String>
}

@Serializable
private data class ReportBody(val reason: String, val detail: String? = null)

@Serializable
private data class Empty(val ok: Boolean? = null)

class AtlasRepository(private val api: TujiApiClient) :
    AtlasReading, AtlasSaving, ReportSubmitting, BlockListing {

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
