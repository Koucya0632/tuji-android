package app.tuji.android.study

import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a 學新字 報錯 says about the stage on screen — iOS's `NewFlowCoordinator.reportSubject`. */
class NewFlowReportSubjectTest {

    private val item = StudyQueueItem(
        card = StudyCard(id = "7"),
        word = StudyQueueWord(
            id = "w", word = "cat", chinese = "貓",
            imageUrl = "https://img.test/w.webp", pronunciation = "/kæt/",
            category = "animals",
        ),
    )

    @Test
    fun `recognize reports the self-rating once given`() {
        val open = NewFlowViewModel.Stage.Recognize(item).reportSubject()
        assertEquals("recognize", open.phase)
        assertNull(open.selectedAnswer)

        val rated = NewFlowViewModel.Stage.Recognize(item, rated = SRSRating.Good).reportSubject()
        assertEquals(SRSRating.Good.wire, rated.selectedAnswer)
    }

    @Test
    fun `identify reports the pick`() {
        val s = NewFlowViewModel.Stage.Identify(item, choices = listOf("cat", "dog"), picked = "dog").reportSubject()
        assertEquals("identify", s.phase)
        assertEquals("dog", s.selectedAnswer)
    }
}
