package app.tuji.android.core.study

import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelPoint
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.model.Word

/*
 * What the travel screens show, decided from plain values so each rule can be
 * asserted by calling it. iOS's `TravelDecisions`.
 */

/** Whether this account may study a spot. */
enum class TravelSpotAccess {
    Open,

    /** Opens the paywall instead of a session. */
    NeedsMembership,
}

/** One 景點 as its page shows it. */
data class TravelSpotState(
    /** The spot's words the dictionary has, in the spot's order. */
    val words: List<Word>,
    val studied: Int,
    val due: Int,
    val access: TravelSpotAccess,
) {
    val total: Int get() = words.size

    /** 0–1, for the bar. */
    val progress: Double get() = if (total == 0) 0.0 else studied.toDouble() / total

    /**
     * Which study buttons to offer. A mode with nothing in it is not offered:
     * an empty session is a dead end. A locked spot offers none — its one
     * button opens the paywall.
     */
    val studyModes: List<StudyMode>
        get() {
            if (access != TravelSpotAccess.Open) return emptyList()
            return buildList {
                if (studied < total) add(StudyMode.New)
                if (due > 0) add(StudyMode.Review)
            }
        }
}

object TravelDecisions {
    /**
     * A spot is a handful of words, learned in one sitting — the daily goal
     * that paces 今天 would split eight words over several visits.
     */
    const val SPOT_QUEUE_LIMIT = 30

    /** [studyable] is `Membership.studyableCategories`; null = not gated, so every spot is open. */
    fun access(spot: TravelSpot, studyable: List<String>?): TravelSpotAccess =
        if (studyable == null || spot.free) TravelSpotAccess.Open else TravelSpotAccess.NeedsMembership

    /**
     * [scores]: a word with a score has been studied. [nextReview]: word id →
     * epoch millis, as `MasteryStore` keeps it.
     */
    fun state(
        spot: TravelSpot,
        words: List<Word>,
        scores: Map<String, Int>,
        nextReview: Map<String, Long>,
        studyable: List<String>?,
        nowMs: Long = System.currentTimeMillis(),
    ): TravelSpotState {
        val byId = HashMap<String, Word>(words.size)
        words.forEach { byId.putIfAbsent(it.id, it) }
        val resolved = spot.wordIds.mapNotNull { byId[it] }
        return TravelSpotState(
            words = resolved,
            studied = resolved.count { scores[it.id] != null },
            due = resolved.count { w -> nextReview[w.id]?.let { it <= nowMs } ?: false },
            access = access(spot, studyable),
        )
    }

    /** How many of a spot's words have been studied, without resolving them — for the city list. */
    fun studiedCount(spot: TravelSpot, scores: Map<String, Int>): Int = spot.wordIds.count { scores[it] != null }

    /** The entry in 物見 is drawn only when there is somewhere to go. */
    fun showsEntry(destinations: List<TravelDestination>): Boolean = destinations.isNotEmpty()

    /** Spots that can be opened, across a destination — the number on the entry. */
    fun openSpotCount(destination: TravelDestination): Int = destination.cities.sumOf { it.openSpots.size }

    /** The side of one grid cell that fits [columns] cells and the gaps between them into [width]. */
    fun cellSide(width: Float, columns: Int, gap: Float): Float {
        if (columns <= 0) return 0f
        return ((width - gap * (columns - 1)) / columns).coerceAtLeast(0f)
    }

    /** Top-left corner of the cell at [tile], as (x, y). */
    fun cellOrigin(tile: TravelPoint, side: Float, gap: Float): Pair<Float, Float> =
        (tile.x * (side + gap)).toFloat() to (tile.y * (side + gap)).toFloat()
}
