package app.tuji.android.study

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.TravelResponse
import app.tuji.android.core.model.WordList
import app.tuji.android.core.model.WordListDetailResponse
import app.tuji.android.core.model.WordListsResponse
import app.tuji.android.core.network.StudyQueueReading
import app.tuji.android.core.network.TravelAccess
import app.tuji.android.core.network.WordListsAccess
import app.tuji.android.core.study.StudyQuotas
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySourceTest {

    private val learning = LearningDirection.entries.first()
    private val themes = listOf("kitchen", "station")

    private object Today : StudyQueueReading {
        override suspend fun queue(
            mode: StudyMode, limit: Int, new: Int, categories: List<String>, lang: String, learning: LearningDirection,
        ) = StudyQueueResponse(queue = emptyList())
    }

    private class Lists : WordListsAccess {
        var asked: Triple<String, StudyMode, Int>? = null
        override suspend fun wordListQueue(listId: String, mode: StudyMode, limit: Int, lang: String, learning: LearningDirection): StudyQueueResponse {
            asked = Triple(listId, mode, limit)
            return StudyQueueResponse(queue = emptyList())
        }
        override suspend fun wordLists(learning: LearningDirection, containing: String?): WordListsResponse = error("unused")
        override suspend fun wordList(id: String): WordListDetailResponse = error("unused")
        override suspend fun createWordList(learning: LearningDirection, name: String): WordList = error("unused")
        override suspend fun renameWordList(id: String, name: String) = error("unused")
        override suspend fun deleteWordList(id: String) = error("unused")
        override suspend fun reorderWordLists(learning: LearningDirection, ids: List<String>) = error("unused")
        override suspend fun setWordInList(listId: String, wordId: String, present: Boolean) = error("unused")
    }

    private class Travel : TravelAccess {
        var asked: Triple<String, StudyMode, Int>? = null
        override suspend fun spotQueue(spotId: String, mode: StudyMode, limit: Int, lang: String, learning: LearningDirection): StudyQueueResponse {
            asked = Triple(spotId, mode, limit)
            return StudyQueueResponse(queue = emptyList())
        }
        override suspend fun destinations(lang: String, learning: LearningDirection): TravelResponse = error("unused")
    }

    // ── queue ──

    @Test fun `今日's themes read 今日's own queue`() {
        assertSame(Today, StudySource.Themes.queue(Today, Lists(), Travel()))
    }

    @Test fun `a list reads its own endpoint, by its id`() = runTest {
        val lists = Lists()
        StudySource.WordList("l1").queue(Today, lists, Travel())
            .queue(StudyMode.Review, 30, 0, themes, "zh-TW", learning)
        assertEquals(Triple("l1", StudyMode.Review, 30), lists.asked)
    }

    @Test fun `a spot reads its own endpoint, by its id`() = runTest {
        val travel = Travel()
        StudySource.Spot("asakusa").queue(Today, Lists(), travel)
            .queue(StudyMode.New, 30, 30, themes, "zh-TW", learning)
        assertEquals(Triple("asakusa", StudyMode.New, 30), travel.asked)
    }

    // ── 學新字 ──

    @Test fun `今日's new words are the goal tapered by the backlog, over the picked themes`() {
        val asked = StudySource.Themes.newQueue(goal = 10, due = 60, themes = themes)
        assertEquals(StudyQuotas.newQueue(goal = 10, due = 60, categories = themes), asked)
        assertEquals(themes, asked.categories)
    }

    @Test fun `a list follows the daily goal untapered, with no themes`() {
        assertEquals(StudyQuotas.NewQueue(limit = 10, categories = emptyList()), StudySource.WordList("l1").newQueue(goal = 10, due = 500, themes = themes))
    }

    @Test fun `a list never opens an empty session on a goal of zero`() {
        assertEquals(1, StudySource.WordList("l1").newQueue(goal = 0, due = 0, themes = themes).limit)
    }

    @Test fun `a spot is learned in one sitting whatever the goal`() {
        val asked = StudySource.Spot("asakusa").newQueue(goal = 5, due = 500, themes = themes)
        assertEquals(30, asked.limit)
        assertTrue(asked.categories.isEmpty())
    }

    // ── 複習 ──

    @Test fun `each source has its own review batch`() {
        assertEquals(20, StudySource.Themes.reviewLimit)
        assertEquals(30, StudySource.WordList("l1").reviewLimit)
        // Its own number, not the list's it used to borrow — iOS's SpotStudyQueue.limit.
        assertEquals(30, StudySource.Spot("asakusa").reviewLimit)
    }

    @Test fun `only 今日's themes offer another round`() {
        assertTrue(StudySource.Themes.offersAnotherRound)
        assertFalse(StudySource.WordList("l1").offersAnotherRound)
        assertFalse(StudySource.Spot("asakusa").offersAnotherRound)
    }
}
