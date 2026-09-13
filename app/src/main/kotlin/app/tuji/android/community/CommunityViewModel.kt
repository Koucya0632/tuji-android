package app.tuji.android.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.BlockList
import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.AtlasAuthorPage
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicDetail
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.AtlasReading
import app.tuji.android.core.network.AtlasSaving
import app.tuji.android.core.network.BlockListing
import app.tuji.android.core.network.CollectionBookmarking
import app.tuji.android.core.network.ReportSubmitting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 物見 — reading other people's published words.
 *
 * The tab itself is two shelves of **合集**, as on iOS: 探索, everything
 * published in the language being learned, and 已收藏, the ones this account
 * bookmarked. Single words are reached through a collection or an author, not
 * listed loose on the tab.
 */
class CommunityViewModel(
    private val atlas: AtlasReading,
    private val saver: AtlasSaving,
    private val bookmarks: CollectionBookmarking,
    private val reporter: ReportSubmitting,
    private val blocks: BlockListing,
    private val direction: LearningDirection,
    private val uiLang: String,
    /**
     * Called after a save actually lands.
     *
     * 收藏 writes to a shelf **another tab draws** — 圖鑑's 已收進 — and nothing
     * over there can know it happened. Without this, someone saves a word, taps
     * 圖鑑, and finds the shelf they just added to unchanged; the same shape as
     * finishing a session and seeing the tiers you had before you started.
     */
    private val onSaved: () -> Unit = {},
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    /** One shelf of 合集. */
    data class Shelf(
        val collections: List<AtlasPublicCollection> = emptyList(),
        val loading: Boolean = true,
        val failed: Boolean = false,
    )

    private val _explore = MutableStateFlow(Shelf())

    /** 探索 — published collections in the language being learned. */
    val explore: StateFlow<Shelf> = _explore.asStateFlow()

    private val _saved = MutableStateFlow(Shelf())

    /** 已收藏 — this account's bookmarked collections, same language scope. */
    val saved: StateFlow<Shelf> = _saved.asStateFlow()

    private val _me = MutableStateFlow<AtlasAuthor?>(null)

    /**
     * The row at the top of the tab: this account as other people see it. Null
     * while loading and after a failure alike — a network problem there must
     * not cost the list under it, and an error where a name should be is worse
     * than no row.
     */
    val me: StateFlow<AtlasAuthor?> = _me.asStateFlow()

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

    /** The block list the other 物見 screens filter by. */
    val blockList: BlockList get() = blocked

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
            val cols = runCatching { atlas.collections(lang) }.getOrElse {
                Log.e(TAG, "物見 collections failed", it)
                _explore.value = Shelf(loading = false, failed = true)
                return@launch
            }
            _explore.value = Shelf(collections = blocked.filter(cols) { it.author }, loading = false)
        }
    }

    /** 已收藏. Only for a signed-in account; the screen shows guests a way to sign in instead. */
    fun loadSaved() {
        work.launch {
            val cols = runCatching { bookmarks.savedCollections(lang) }.getOrElse {
                Log.e(TAG, "saved collections failed", it)
                _saved.value = _saved.value.copy(loading = false, failed = true)
                return@launch
            }
            _saved.value = Shelf(collections = blocked.filter(cols) { it.author }, loading = false)
        }
    }

    /** The account's own public page, for the row above the shelves. */
    fun loadMe(uid: String) {
        if (_me.value?.handle == uid) return
        work.launch {
            runCatching { atlas.author(uid).author }
                .onSuccess { _me.value = it }
                .onFailure { Log.w(TAG, "my page row failed", it) }
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
            if (ok) onSaved()
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
