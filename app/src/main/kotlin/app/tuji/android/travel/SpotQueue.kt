package app.tuji.android.travel

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.network.TravelAccess

/**
 * A study queue drawn from one 景點 — iOS's `SpotStudyQueue`.
 *
 * The same [StudyQueueReading] 今日's sessions read, so 學新字 and 複習 run
 * unchanged. The theme filter is ignored: the spot's words are the selection.
 */
class SpotQueue(private val remote: TravelAccess, private val spotId: String) : StudyQueueReading {
    override suspend fun queue(
        mode: StudyMode,
        limit: Int,
        new: Int,
        categories: List<String>,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse {
        val response = remote.spotQueue(spotId, mode, limit.coerceAtLeast(1), lang, learning)
        // One item per word, the same rule 今日's queue keeps.
        return response.copy(queue = response.queue.distinctBy { it.word.id })
    }

    companion object {
        /**
         * A spot is a handful of words, learned in one sitting — the daily
         * goal that paces 今日 would split eight words over several visits.
         */
        const val LIMIT = 30
    }
}
