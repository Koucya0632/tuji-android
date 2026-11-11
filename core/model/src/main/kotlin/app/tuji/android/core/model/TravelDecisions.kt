package app.tuji.android.core.model

/** Whether this account may study a spot. */
enum class TravelSpotAccess {
    Open,

    /** Opens 會員方案 instead of a session. */
    NeedsMembership,
}

/** One 景點 as its page shows it. */
data class TravelSpotState(
    /** The spot's words the catalogue has, in the spot's order. */
    val words: List<Word>,
    val studied: Int,
    val due: Int,
    val access: TravelSpotAccess,
) {
    val total: Int get() = words.size

    /** 0–1, for the bar. */
    val progress: Float get() = if (total == 0) 0f else studied.toFloat() / total

    /**
     * Which study buttons to offer. A mode with nothing in it is not offered:
     * an empty session is a dead end. A locked spot offers none — its one
     * button opens 會員方案.
     */
    val studyModes: List<StudyMode>
        get() = if (access != TravelSpotAccess.Open) {
            emptyList()
        } else {
            listOfNotNull(
                StudyMode.New.takeIf { studied < total },
                StudyMode.Review.takeIf { due > 0 },
            )
        }
}

/** What the travel screens show, decided from plain values so each rule can be asserted by calling it. */
object TravelDecisions {

    /** `studyable` is `Membership.studyableCategories`; null means this account is not gated. */
    fun access(spot: TravelSpot, studyable: List<String>?): TravelSpotAccess =
        if (studyable == null || spot.free) TravelSpotAccess.Open else TravelSpotAccess.NeedsMembership

    /**
     * @param scores word id → mastery; a word with a score has been studied.
     * @param nextReview word id → next review, epoch millis.
     */
    fun state(
        spot: TravelSpot,
        words: List<Word>,
        scores: Map<String, Int>,
        nextReview: Map<String, Long>,
        studyable: List<String>?,
        now: Long,
    ): TravelSpotState {
        val byId = words.associateBy { it.id }
        val resolved = spot.wordIds.mapNotNull { byId[it] }
        return TravelSpotState(
            words = resolved,
            studied = resolved.count { it.id in scores },
            due = resolved.count { word -> nextReview[word.id]?.let { it <= now } ?: false },
            access = access(spot, studyable),
        )
    }

    /** How many of a spot's words have been studied, without resolving them — for the city list. */
    fun studiedCount(spot: TravelSpot, scores: Map<String, Int>): Int = spot.wordIds.count { it in scores }

    /** The entry in 物見 is drawn only when there is somewhere to go. */
    fun showsEntry(destinations: List<TravelDestination>): Boolean = destinations.isNotEmpty()

    /** Spots that can be opened, across a destination — the number on the entry. */
    fun openSpotCount(destination: TravelDestination): Int = destination.cities.sumOf { it.openSpots.size }

    /** The side of one grid cell that fits `columns` cells and the gaps between them into `width`. */
    fun cellSide(width: Float, columns: Int, gap: Float): Float =
        if (columns <= 0) 0f else ((width - gap * (columns - 1)) / columns).coerceAtLeast(0f)

    /** Left or top edge of the cell at `index` along one axis. */
    fun cellOffset(index: Float, side: Float, gap: Float): Float = index * (side + gap)
}
