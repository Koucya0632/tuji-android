package app.tuji.android.atlas

import android.util.Log
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.model.Category
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.CatalogReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The catalogue, once, for everyone who reads it.
 *
 * Three screens want these 557 rows — 圖鑑 draws them, 搜尋 filters them, and
 * 複習's 聽句 draws its second picture from them — and they want the *same*
 * rows: a count on a shelf that disagrees with what opening it shows is the
 * kind of bug that looks like a data problem for a week.
 *
 * It replaces the plain `catalogPool` field, which M1 left with a comment
 * saying the real store arrives with 圖鑑. It has.
 *
 * **A failed load keeps what it had.** The catalogue does not change during a
 * session, so rows fetched a minute ago are still right; emptying them on a
 * dropped connection would blank 圖鑑 and silently delete 聽句 (see
 * `ReviewViewModel`), which is exactly the failure the pool taught us.
 */
class CatalogStore(
    private val catalog: CatalogReading,
    /**
     * The language the server should write in. Set from the device — see
     * [app.tuji.android.core.model.UiLanguage].
     */
    private var uiLang: String = "zh-Hant",
) {
    /** Change the language and drop what was fetched in the old one. */
    fun retune(lang: String) {
        if (lang == uiLang) return
        uiLang = lang
        _contents.value = Contents()
    }

    data class Contents(
        val words: List<Word> = emptyList(),
        val categories: List<Category> = emptyList(),
    ) {
        val shelves: List<CategoryShelf.Shelf> get() = CategoryShelf.shelves(categories, words)
        val loaded: Boolean get() = words.isNotEmpty()
    }

    private val _contents = MutableStateFlow(Contents())
    val contents: StateFlow<Contents> = _contents.asStateFlow()

    /** Words, for the callers that only want the pool. */
    val words: List<Word> get() = _contents.value.words

    private val gate = Mutex()

    /**
     * Fill it, once per direction.
     *
     * Guarded rather than merely checked: 今日 asks on appearance and 圖鑑 asks
     * on its first draw, and without the lock a cold start fetches 557 rows
     * twice.
     */
    suspend fun load(learning: LearningDirection, force: Boolean = false) {
        gate.withLock {
            if (_contents.value.loaded && !force) return
            // Not `runCatching`: it catches `CancellationException` as well,
            // so a torn-down load is reported as a failed one and the caller's
            // cancellation never propagates. See MasteryStore, where that
            // swallow hid a refresh that never ran.
            val words = try {
                catalog.words(uiLang, learning).words
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "catalogue load failed", failure)
                return
            }
            // Categories are a smaller, separate failure: without them 圖鑑 has
            // no shelves, but 聽句 and 搜尋 still work off the words. So the
            // words are kept even when this half fails.
            val categories = try {
                catalog.categories(uiLang).categories
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "categories load failed", failure)
                emptyList()
            }
            _contents.value = Contents(words = words, categories = categories)
        }
    }

    private companion object {
        const val TAG = "TujiCatalog"
    }
}
