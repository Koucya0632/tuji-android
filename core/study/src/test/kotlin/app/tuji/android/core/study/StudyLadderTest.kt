package app.tuji.android.core.study

import app.tuji.android.core.model.SRSRating
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The same cases the iOS suite pins.
 *
 * The ladder is the one place a session's *shape* is decided, and a divergence
 * here means the two apps teach the same word in a different order — invisible
 * from either side alone.
 */
class StudyLadderTest {

    private fun item(id: String, term: String = "cat", reading: String? = null) = StudyQueueItem(
        card = StudyCard(id = "c-$id"),
        word = StudyQueueWord(
            id = id,
            word = term,
            chinese = "貓",
            imageUrl = "https://img.test/$id.webp",
            pronunciation = "/kæt/",
            reading = reading,
            category = "animals",
        ),
    )

    private fun kinds(ladder: StudyLadder) = ladder.tasks.map { it.item.word.id to it.kind }

    @Test
    fun `the initial schedule interleaves words and keeps each ladder ordered`() {
        val ladder = StudyLadder(listOf(item("a"), item("b")))
        val order = kinds(ladder)

        // Each word's own stages stay in order…
        for (id in listOf("a", "b")) {
            val mine = order.filter { it.first == id }.map { it.second }
            assertEquals(
                listOf(NewTaskKind.Recognize, NewTaskKind.Identify, NewTaskKind.SpellTiles),
                mine,
            )
        }
        // …and the two words interleave rather than running as blocks.
        val ids = order.map { it.first }
        assertTrue("expected interleaving, got $ids", ids != ids.sorted())
    }

    @Test
    fun `a single-unit subject carries no spell stage`() {
        // One tile is a free answer, so the word finishes after 選字.
        val ladder = StudyLadder(listOf(item("a", term = "字")))
        assertEquals(listOf(NewTaskKind.Recognize, NewTaskKind.Identify), ladder.tasks.map { it.kind })
        assertEquals(2, ladder.totalStages)
    }

    @Test
    fun `completing every stage of a word reports it once`() {
        var ladder = StudyLadder(listOf(item("a", term = "字")))
        val first = ladder.completeCurrent()
        assertNull("認識 is not the end of the word", first.finishedWord)
        ladder = first.ladder

        val second = ladder.completeCurrent()
        assertEquals("a", second.finishedWord?.word?.id)
        assertEquals(1, second.ladder.clearedWords)
        assertTrue(second.ladder.finished)
    }

    @Test
    fun `completing a non-final stage reports nothing`() {
        val ladder = StudyLadder(listOf(item("a")))
        val step = ladder.completeCurrent()
        assertNull(step.finishedWord)
        assertEquals(0, step.ladder.clearedWords)
        assertEquals(1, step.ladder.stageClears)
    }

    @Test
    fun `requeue puts the head back a few positions later`() {
        val ladder = StudyLadder(listOf(item("a"), item("b"), item("c")))
        val head = ladder.current!!
        val after = ladder.requeueCurrent()

        assertEquals(ladder.tasks.size, after.tasks.size)
        assertEquals(StudyLadder.REQUEUE_GAP, after.tasks.indexOfFirst { it.id == head.id })
        // A retry must not inflate the denominator, or the bar walks backwards.
        assertEquals(ladder.totalStages, after.totalStages)
        assertEquals(0, after.stageClears)
    }

    @Test
    fun `a spell task never surfaces before its word cleared identify`() {
        // Spelling a word the user just failed to recognise breaks the ladder.
        var ladder = StudyLadder(listOf(item("a")))
        // 認識 done, 選字 wrong and requeued.
        ladder = ladder.completeCurrent().ladder
        ladder = ladder.requeueCurrent()

        assertTrue(
            "head was ${ladder.current?.kind}",
            ladder.current?.kind != NewTaskKind.SpellTiles,
        )
    }

    @Test
    fun `the fast path drops identify and shrinks the denominator`() {
        val subject = item("a")
        val ladder = StudyLadder(listOf(subject))
        val before = ladder.totalStages

        val after = ladder.skipIdentify(subject)

        assertEquals(before - 1, after.totalStages)
        assertTrue(after.tasks.none { it.kind == NewTaskKind.Identify })
        // Load-bearing: without this the word's tiles would be deferred forever.
        assertTrue("a" in after.identifyCleared)
        assertTrue("a" in after.skippedIdentify)
    }

    @Test
    fun `skipping identify for a word with no pending identify changes nothing`() {
        val subject = item("a")
        val ladder = StudyLadder(listOf(subject)).skipIdentify(subject)
        assertSame(ladder, ladder.skipIdentify(subject))
    }

    @Test
    fun `an empty queue is immediately finished and scores zero`() {
        val ladder = StudyLadder(emptyList())
        assertTrue(ladder.finished)
        assertNull(ladder.current)
        assertEquals(0.0, ladder.progress, 0.0001)
        assertNull(ladder.completeCurrent().finishedWord)
    }

    @Test
    fun `progress counts cleared stages against scheduled ones`() {
        var ladder = StudyLadder(listOf(item("a"), item("b")))
        val total = ladder.totalStages
        ladder = ladder.completeCurrent().ladder
        assertEquals(1.0 / total, ladder.progress, 0.0001)
    }

    @Test
    fun `a wrong answer downgrades one step and bottoms out at again`() {
        assertEquals(SRSRating.Good, SRSRating.Easy.downgraded)
        assertEquals(SRSRating.Hard, SRSRating.Good.downgraded)
        assertEquals(SRSRating.Again, SRSRating.Hard.downgraded)
        assertEquals(SRSRating.Again, SRSRating.Again.downgraded)
    }
}
