package app.tuji.android.core.catalog

import app.tuji.android.core.model.Word

/**
 * Which batch of words 圖鑑 is showing.
 *
 * **Source, not theme.** They are different axes and mixing them is what iOS
 * did first: 自製圖鑑 and 物見 sat in the theme row as if they were rooms like
 * 廚房, and the user could never ask "show me only the ones I took in". A theme
 * says what a word is about; a source says where it came from.
 *
 * **There is no 全部.** One source is always in effect, and tapping the lit chip
 * does nothing. A filter row that can end up with nothing lit is
 * indistinguishable from a broken one, and merging the sources answers a
 * question nobody asked: the dictionary and the words you saved are different
 * kinds of thing, not two halves of a list.
 *
 * **我做的 is missing on purpose.** 自製圖鑑 rows carry `atlas:` ids, and the
 * screen behind them — the item's own page, where it can be re-enriched or
 * withdrawn — does not exist on Android yet. A tile that does nothing when
 * tapped is worse than a tile that is not there, so the chip arrives with the
 * screen rather than before it.
 */
enum class CardsSource {
    /** The published dictionary. What the tab is for, so it opens here. */
    Official,

    /** 書籤 — dictionary words marked to look at again. Passive: marking one
     *  changes nothing about what is scheduled for review. */
    Bookmarked,

    /** 已收進 — words taken in from someone else's 圖鑑. */
    Taken,
}

/**
 * What each source shows.
 *
 * A value, not a `when` inside the screen, because one of the three rules is
 * not obvious: see [words] on why a bookmark can name a word that is not there.
 */
object CardsSourceRules {

    /** The `saved:` prefix routes a tap to 物見's page rather than the
     *  dictionary's — these belong to someone else, and their page has an
     *  author, a 取消收藏 and a 檢舉 that a dictionary entry has none of. */
    const val SAVED_PREFIX = "saved:"

    /**
     * @param official the catalogue for the current deck.
     * @param taken 已收進, already in word shape and already deck-scoped.
     * @param bookmarked the ids the account has marked — **across both decks**,
     *   because a bookmark is not re-made when the learner switches to
     *   中文→英文. So an id here can name a word this catalogue does not
     *   contain, and the only correct thing to do with it is leave it out:
     *   showing a blank tile would say the entry is broken rather than that it
     *   belongs to the other deck.
     */
    fun words(
        source: CardsSource,
        official: List<Word>,
        taken: List<Word>,
        bookmarked: Set<String>,
    ): List<Word> = when (source) {
        CardsSource.Official -> official
        // Catalogue order, not the order the marks were made in: the grid is
        // the same grid, with rows taken out.
        CardsSource.Bookmarked -> official.filter { it.id in bookmarked }
        CardsSource.Taken -> taken
    }

    /** Whether a tap on [wordId] belongs to 物見 rather than the dictionary. */
    fun isSaved(wordId: String): Boolean = wordId.startsWith(SAVED_PREFIX)

    /** The 物見 slug inside a `saved:` id, or null for anything else. */
    fun savedSlug(wordId: String): String? =
        wordId.removePrefix(SAVED_PREFIX).takeIf { it != wordId && it.isNotEmpty() }
}
