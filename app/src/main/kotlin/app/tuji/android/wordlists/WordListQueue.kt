package app.tuji.android.wordlists

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.network.WordListsAccess

/**
 * A study queue drawn from one 個人詞表 — iOS's `WordListStudyQueue`.
 *
 * Shaped as the same [StudyQueueReading] 今日's sessions read, so 學新字 and
 * 複習 run unchanged and only where the cards come from differs. The theme
 * filter is ignored: a list is its own selection.
 */
class WordListQueue(private val remote: WordListsAccess, private val listId: String) : StudyQueueReading {
    override suspend fun queue(
        mode: StudyMode,
        limit: Int,
        new: Int,
        categories: List<String>,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse {
        val response = remote.wordListQueue(listId, mode, limit.coerceAtLeast(1), lang, learning)
        // One item per word, the same rule 今日's queue keeps.
        return response.copy(queue = response.queue.distinctBy { it.word.id })
    }

    companion object {
        /** A review batch is capped at the size iOS uses for a list. */
        const val REVIEW_LIMIT = 30
    }
}
