package app.tuji.android.study

import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.network.TravelAccess
import app.tuji.android.core.network.WordListsAccess
import app.tuji.android.core.study.StudyQuotas
import app.tuji.android.travel.SpotQueue
import app.tuji.android.wordlists.WordListQueue

/**
 * Where a 學新字 or 複習 session draws its cards from.
 *
 * 今日's themes, one 個人詞表, or one 景點. The two flows are the same screens
 * whichever it is; what differs is the queue, how many new words a sitting
 * asks for, how big a review batch is, and whether the summary offers
 * 再來一輪. Each of those used to be an `if` on the route in the shell, and
 * each feature that added a source had to find and rewrite all of them — 詞表
 * did, then 景點 did again, and 景點's review borrowed the 詞表's limit
 * because that was the branch it was pasted into.
 */
sealed interface StudySource {

    /** 今日's 學習主題 — the session the daily goal is about. */
    data object Themes : StudySource

    /** One 個人詞表. Its own selection: no themes, no backlog taper. */
    data class WordList(val listId: String) : StudySource

    /** One 景點's words. A handful, learned in one sitting. */
    data class Spot(val spotId: String) : StudySource

    /**
     * The queue a session reads. [themes] is 今日's own; a list or a spot
     * wraps the same [StudyQueueReading] shape around its own endpoint, so the
     * flows run unchanged.
     */
    fun queue(themes: StudyQueueReading, lists: WordListsAccess, travel: TravelAccess): StudyQueueReading =
        when (this) {
            Themes -> themes
            is WordList -> WordListQueue(lists, listId)
            is Spot -> SpotQueue(travel, spotId)
        }

    /**
     * What 學新字 asks for.
     *
     * [goal] is 設定's daily goal, [due] today's review backlog and [themes]
     * the studied selection — the same three numbers 今日 printed on the
     * button that opened the session, so the two cannot disagree.
     */
    fun newQueue(goal: Int, due: Int, themes: List<String>): StudyQuotas.NewQueue = when (this) {
        Themes -> StudyQuotas.newQueue(goal = goal, due = due, categories = themes)
        // The daily goal, as on iOS, but not tapered: the backlog is today's,
        // not the list's. At least one, or a goal of zero opens an empty session.
        is WordList -> StudyQuotas.NewQueue(limit = goal.coerceAtLeast(1), categories = emptyList())
        // The whole spot: the goal that paces 今日 would split eight words
        // over several visits.
        is Spot -> StudyQuotas.NewQueue(limit = SpotQueue.LIMIT, categories = emptyList())
    }

    /** How many cards one 複習 batch asks for. */
    val reviewLimit: Int
        get() = when (this) {
            Themes -> THEMES_REVIEW_LIMIT
            is WordList -> WordListQueue.REVIEW_LIMIT
            is Spot -> SpotQueue.LIMIT
        }

    /**
     * Whether 複習's summary offers 再來一輪 and counts what is still due.
     *
     * Only for 今日's themes: the count is every due word on the account, so on
     * a list's or a spot's summary it would be a number about something else.
     */
    val offersAnotherRound: Boolean get() = this == Themes

    companion object {
        /** iOS's `StudyQueueStore` batch; also [ReviewViewModel.load]'s default. */
        const val THEMES_REVIEW_LIMIT = 20
    }
}
