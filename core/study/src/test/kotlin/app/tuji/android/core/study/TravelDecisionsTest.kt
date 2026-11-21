package app.tuji.android.core.study

import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.TravelCity
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelGrid
import app.tuji.android.core.model.TravelPoint
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * THE RED LINES (iOS `TravelTests`):
 *  - A non-member studies a free spot and meets the paywall at any other; an
 *    account that is not gated meets it nowhere.
 *  - A study mode with nothing in it is never offered.
 *  - The entry is drawn exactly when the server names somewhere to go.
 */
class TravelDecisionsTest {
    private val akihabara = TravelSpot(
        id = "akihabara", name = "秋葉原", at = TravelPoint(0.5, 0.5),
        wordIds = listOf("claw-machine", "pin-badge", "trading-card"), open = true, free = true,
    )
    private val asakusa = TravelSpot(
        id = "asakusa", name = "淺草", at = TravelPoint(0.5, 0.5),
        wordIds = listOf("paper-lantern", "fortune-slip"), open = false, free = false,
    )

    private fun word(id: String, category: String = "collecting-hobbies") = Word(id = id, word = id, category = category)

    @Test
    fun `a non-member studies the free spot only`() {
        val gated = listOf("fruits", "bedroom")
        assertEquals(TravelSpotAccess.Open, TravelDecisions.access(akihabara, gated))
        assertEquals(TravelSpotAccess.NeedsMembership, TravelDecisions.access(asakusa, gated))
    }

    @Test
    fun `an account that is not gated studies every spot`() {
        assertEquals(TravelSpotAccess.Open, TravelDecisions.access(asakusa, null))
    }

    @Test
    fun `counts studied and due among the words the dictionary has`() {
        val now = 1_000_000_000L
        val state = TravelDecisions.state(
            akihabara,
            // `trading-card` has not loaded; `banana` is not the spot's.
            words = listOf(word("pin-badge"), word("banana", "fruits"), word("claw-machine")),
            scores = mapOf("claw-machine" to 40, "pin-badge" to 10, "trading-card" to 90, "banana" to 5),
            nextReview = mapOf("claw-machine" to now - 60_000, "pin-badge" to now + 60_000),
            studyable = null,
            nowMs = now,
        )
        // The spot's order, not the dictionary's.
        assertEquals(listOf("claw-machine", "pin-badge"), state.words.map { it.id })
        assertEquals(2, state.studied)
        assertEquals(1, state.due)
        assertEquals(1.0, state.progress, 0.0)
        assertEquals(listOf(StudyMode.Review), state.studyModes)
    }

    @Test
    fun `offers only modes with something in them`() {
        val words = akihabara.wordIds.map { word(it) }
        fun modes(scores: Map<String, Int>, due: Map<String, Long> = emptyMap()) =
            TravelDecisions.state(akihabara, words, scores, due, null, nowMs = 100).studyModes
        assertEquals(listOf(StudyMode.New), modes(emptyMap()))
        assertEquals(listOf(StudyMode.New, StudyMode.Review), modes(mapOf("pin-badge" to 1), mapOf("pin-badge" to 0L)))
        assertTrue(modes(akihabara.wordIds.associateWith { 50 }).isEmpty())
    }

    @Test
    fun `a locked spot offers no mode however much is left`() {
        val state = TravelDecisions.state(
            asakusa, asakusa.wordIds.map { word(it, "festivals") }, emptyMap(), emptyMap(), listOf("fruits"),
        )
        assertEquals(TravelSpotAccess.NeedsMembership, state.access)
        assertTrue(state.studyModes.isEmpty())
    }

    @Test
    fun `a spot with no words loaded is empty, not complete`() {
        val state = TravelDecisions.state(akihabara, emptyList(), emptyMap(), emptyMap(), null)
        assertEquals(0, state.total)
        assertEquals(0.0, state.progress, 0.0)
        assertTrue(state.studyModes.isEmpty())
    }

    @Test
    fun `the entry shows only when there is somewhere to go, and counts open spots`() {
        val japan = TravelDestination(
            id = "japan", name = "日本", grid = TravelGrid(14, 16),
            cities = listOf(TravelCity(id = "tokyo", name = "東京", regionId = "kanto", spots = listOf(akihabara, asakusa))),
        )
        assertFalse(TravelDecisions.showsEntry(emptyList()))
        assertTrue(TravelDecisions.showsEntry(listOf(japan)))
        assertEquals(1, TravelDecisions.openSpotCount(japan))
    }

    @Test
    fun `cells fill the width with gaps between`() {
        val side = TravelDecisions.cellSide(362f, 14, 2f)
        assertEquals(24f, side, 0.0001f)
        assertEquals(338f to 52f, TravelDecisions.cellOrigin(TravelPoint(13.0, 2.0), side, 2f))
        assertEquals(0f, TravelDecisions.cellSide(100f, 0, 2f), 0f)
    }

    @Test
    fun `counts studied words without resolving them`() {
        assertEquals(2, TravelDecisions.studiedCount(akihabara, mapOf("claw-machine" to 1, "trading-card" to 3)))
    }
}
