package app.tuji.android.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.BlockList
import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.model.AtlasAuthorPage
import app.tuji.android.core.model.AtlasCollectionDetail
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicDetail
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.AtlasReading
import app.tuji.android.core.network.AtlasSaving
import app.tuji.android.core.network.BlockListing
import app.tuji.android.core.network.ReportSubmitting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 物見 — reading other people's published words.
 *
 * **Consumption only.** M3 ships the half of 物見 that reads; publishing and
 * the camera are M5. That is not a temporary gap to be filled in with a
 * disabled button — a greyed 拍照 that never enables is worse than no entry at
 * all, because it promises something this build cannot do.
 */
class CommunityViewModel(
    private val atlas: AtlasReading,
    private val saver: AtlasSaving,
    private val reporter: ReportSubmitting,
    private val blocks: BlockListing,
    private val direction: LearningDirection,
    private val uiLang: String,
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    data class Feed(
        val items: List<AtlasPublicItem> = emptyList(),
        val collections: List<AtlasPublicCollection> = emptyList(),
        val loading: Boolean = true,
        val failed: Boolean = false,
    )

    private val _feed = MutableStateFlow(Feed())
    val feed: StateFlow<Feed> = _feed.asStateFlow()

    /**
     * Hidden authors.
     *
     * Held rather than re-fetched per screen: it is small, it changes rarely,
     * and every 物見 list has to apply the same one.
     */
    private var blocked: BlockList = BlockList.none

    private val work: CoroutineScope get() = scope ?: viewModelScope

    /** The wire's language scope for the collections feed. */
    private val lang: String
        get() = if (direction.targetLanguage == TargetLanguage.JA) "ja" else "en"

    fun load() {
        work.launch {
            // The block list first, so nothing blocked is ever drawn and then
            // removed — a hidden author flashing on screen is the one thing
            // this feature exists to prevent.
            blocked = runCatching { BlockList.of(blocks.blockedHandles()) }
                .getOrElse {
                    // Fail open: see BlockList.none. One failed request must not
                    // take 物見 away from everyone.
                    Log.w(TAG, "block list unavailable — showing everything", it)
                    BlockList.none
                }

            val items = runCatching { atlas.feed().items }.getOrElse {
                Log.e(TAG, "物見 feed failed", it)
                _feed.value = Feed(loading = false, failed = true)
                return@launch
            }
            // Collections are a smaller, separate failure: without them the
            // feed still works, so they do not fail the screen.
            val cols = runCatching { atlas.collections(lang) }.getOrElse {
                Log.w(TAG, "collections failed", it)
                emptyList()
            }

            _feed.value = Feed(
                items = blocked.filter(items) { it.author },
                collections = blocked.filter(cols) { it.author },
                loading = false,
            )
        }
    }

    // One item

    sealed interface ItemState {
        data object Loading : ItemState
        data object Failed : ItemState
        data class Loaded(
            val item: AtlasPublicDetail,
            val saving: Boolean = false,
            val saved: Boolean = false,
        ) : ItemState
    }

    private val _item = MutableStateFlow<ItemState>(ItemState.Loading)
    val item: StateFlow<ItemState> = _item.asStateFlow()

    fun openItem(slug: String) {
        _item.value = ItemState.Loading
        work.launch {
            runCatching { atlas.item(slug, uiLang) }
                .onSuccess { d ->
                    _item.value = if (d == null) ItemState.Failed else ItemState.Loaded(d)
                }
                .onFailure {
                    Log.e(TAG, "物見 item failed: $slug", it)
                    _item.value = ItemState.Failed
                }
        }
    }

    /**
     * 收藏 — put someone else's word into your own 圖鑑.
     *
     * The flag stays set on failure being *false*, not on optimism: a card the
     * user believes they saved and did not is worse than a second tap.
     */
    fun save() {
        val loaded = _item.value as? ItemState.Loaded ?: return
        if (loaded.saving || loaded.saved) return
        _item.value = loaded.copy(saving = true)
        work.launch {
            val ok = runCatching { saver.save(loaded.item.slug) }
                .onFailure { Log.e(TAG, "save failed: ${loaded.item.slug}", it) }
                .isSuccess
            (_item.value as? ItemState.Loaded)?.let {
                _item.value = it.copy(saving = false, saved = ok)
            }
        }
    }

    // Author

    private val _author = MutableStateFlow<AtlasAuthorPage?>(null)
    val author: StateFlow<AtlasAuthorPage?> = _author.asStateFlow()

    fun openAuthor(handle: String) {
        _author.value = null
        work.launch {
            runCatching { atlas.author(handle) }
                .onSuccess { page ->
                    _author.value = page.copy(
                        items = blocked.filter(page.items) { it.author },
                        collections = blocked.filter(page.collections) { it.author },
                    )
                }
                .onFailure { Log.e(TAG, "author page failed: $handle", it) }
        }
    }

    // 合集

    private val _collection = MutableStateFlow<AtlasCollectionDetail?>(null)
    val collection: StateFlow<AtlasCollectionDetail?> = _collection.asStateFlow()

    fun openCollection(slug: String) {
        _collection.value = null
        work.launch {
            runCatching { atlas.collection(slug) }
                .onSuccess { detail ->
                    _collection.value = detail.copy(
                        items = blocked.filter(detail.items) { it.author },
                    )
                }
                .onFailure { Log.e(TAG, "collection failed: $slug", it) }
        }
    }

    // 檢舉

    private val _reported = MutableStateFlow<ReportTarget?>(null)

    /** Non-null once a report for this target has been accepted. */
    val reported: StateFlow<ReportTarget?> = _reported.asStateFlow()

    fun report(target: ReportTarget, reason: ReportReason, detail: String? = null) {
        work.launch {
            runCatching { reporter.report(target, reason, detail) }
                .onSuccess { _reported.value = target }
                .onFailure { Log.e(TAG, "report failed", it) }
        }
    }

    fun clearReported() {
        _reported.value = null
    }

    private companion object {
        const val TAG = "TujiCommunity"
    }
}
