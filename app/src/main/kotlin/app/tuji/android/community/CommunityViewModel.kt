package app.tuji.android.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.BlockList
import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.AtlasReading
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
 *
 * It also holds the 封鎖 list, because every 物見 screen reads the same one and
 * a block made on a word's page has to empty the shelves here too.
 */
class CommunityViewModel(
    private val atlas: AtlasReading,
    private val bookmarks: CollectionBookmarking,
    private val reporter: ReportSubmitting,
    private val blocks: BlockListing,
    private val direction: LearningDirection,
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

    private val _blocked = MutableStateFlow(BlockList.none)

    /**
     * Hidden authors.
     *
     * Held rather than re-fetched per screen: it is small, it changes rarely,
     * and every 物見 list has to apply the same one.
     */
    val blocked: StateFlow<BlockList> = _blocked.asStateFlow()

    /** The shelves as the server sent them, so a block or an unblock re-filters without a fetch. */
    private var exploreRaw: List<AtlasPublicCollection> = emptyList()
    private var savedRaw: List<AtlasPublicCollection> = emptyList()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    /** The wire's language scope for the collections feed. */
    private val lang: String
        get() = if (direction.targetLanguage == TargetLanguage.JA) "ja" else "en"

    /** The block list the other 物見 screens filter by. */
    val blockList: BlockList get() = _blocked.value

    fun load() {
        work.launch { reload() }
    }

    /**
     * The same read, awaited — what a pull-to-refresh needs so the rule stays
     * on screen until the shelves land rather than flashing off with the
     * finger. 已收藏 comes along when there is an account to ask for it: iOS
     * refreshes only the shelf on screen, and the other one is one tap away
     * from a reader who has just asked for fresh answers.
     */
    suspend fun reload(withSaved: Boolean = false) {
        run {
            // The block list first, so nothing blocked is ever drawn and then
            // removed — a hidden author flashing on screen is the one thing
            // this feature exists to prevent.
            _blocked.value = runCatching { BlockList.of(blocks.blockedHandles()) }
                .getOrElse {
                    // Fail open: see BlockList.none. One failed request must not
                    // take 物見 away from everyone.
                    Log.w(TAG, "block list unavailable — showing everything", it)
                    BlockList.none
                }
            val cols = runCatching { atlas.collections(lang) }.getOrElse {
                Log.e(TAG, "物見 collections failed", it)
                _explore.value = Shelf(loading = false, failed = true)
                return@run
            }
            exploreRaw = cols
            _explore.value = Shelf(collections = blockList.filter(cols) { it.author }, loading = false)
        }
        if (withSaved) reloadSaved()
    }

    /** 已收藏. Only for a signed-in account; the screen shows guests a way to sign in instead. */
    fun loadSaved() {
        work.launch { reloadSaved() }
    }

    private suspend fun reloadSaved() {
        run {
            val cols = runCatching { bookmarks.savedCollections(lang) }.getOrElse {
                Log.e(TAG, "saved collections failed", it)
                _saved.value = _saved.value.copy(loading = false, failed = true)
                return@run
            }
            savedRaw = cols
            _saved.value = Shelf(collections = blockList.filter(cols) { it.author }, loading = false)
        }
    }

    /**
     * The account's own public page, for the row above the shelves.
     *
     * @param force after 編輯個人資料, when the row already on screen is the old one.
     */
    fun loadMe(uid: String, force: Boolean = false) {
        if (!force && _me.value?.handle == uid) return
        work.launch {
            runCatching { atlas.author(uid).author }
                .onSuccess { _me.value = it }
                .onFailure { Log.w(TAG, "my page row failed", it) }
        }
    }

    // 封鎖

    /**
     * 封鎖 — optimistic: their collections leave the shelves at once, and only a
     * server failure puts them back. Blocking is reversible, so an over-eager
     * hide is cheap; a block that visibly does nothing is not.
     *
     * @param onBlocked runs once the server has it. The screen that asked is
     *   showing work the reader just said they never want to see, so it leaves.
     */
    fun block(handle: String, onBlocked: () -> Unit = {}) {
        _blocked.value = blockList.adding(handle)
        refilter()
        work.launch {
            runCatching { blocks.block(handle) }
                .onSuccess { onBlocked() }
                .onFailure {
                    Log.e(TAG, "block failed", it)
                    _blocked.value = blockList.removing(handle)
                    refilter()
                }
        }
    }

    /**
     * 解除封鎖 — optimistic the same way, restored if the server refuses.
     *
     * @param onDone runs once the server has answered either way, for a list
     *   that holds its other rows until this one settles.
     */
    fun unblock(handle: String, onDone: () -> Unit = {}) {
        val wasBlocked = blockList.hides(handle)
        _blocked.value = blockList.removing(handle)
        refilter()
        work.launch {
            runCatching { blocks.unblock(handle) }
                .onFailure {
                    Log.e(TAG, "unblock failed", it)
                    if (wasBlocked) {
                        _blocked.value = blockList.adding(handle)
                        refilter()
                    }
                }
            onDone()
        }
    }

    private fun refilter() {
        _explore.value = _explore.value.copy(collections = blockList.filter(exploreRaw) { it.author })
        _saved.value = _saved.value.copy(collections = blockList.filter(savedRaw) { it.author })
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
