package app.tuji.android.assistant

import app.tuji.android.core.model.GrammarComparison
import app.tuji.android.core.model.GrammarExplanation
import app.tuji.android.core.model.GrammarInput
import app.tuji.android.core.model.GrammarMode
import app.tuji.android.core.model.GrammarOffer
import app.tuji.android.core.model.GrammarPracticeProgress
import app.tuji.android.core.model.GrammarRecord
import app.tuji.android.core.model.GrammarRecords
import app.tuji.android.core.model.GrammarReply
import app.tuji.android.core.model.GrammarVerdict
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.GrammarStudying
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarModelTest {

    private val offer = GrammarOffer(
        enabled = true,
        pricingVersion = "p1",
        modes = GrammarMode.entries.map { GrammarOffer.Price(it, 2, 6) },
    )

    private class FakeRepository(val offer: GrammarOffer) : GrammarStudying {
        val sent = mutableListOf<GrammarInput>()
        var content: (GrammarInput) -> app.tuji.android.core.model.GrammarContent = { explanation(it.query) }
        var deleted = mutableListOf<String>()
        override suspend fun offer() = offer
        override suspend fun generate(input: GrammarInput): GrammarRecord {
            sent += input
            return GrammarRecord(input.requestId, VocabularyRecord.Status.Completed, input, GrammarReply(content = content(input)))
        }
        override suspend fun record(id: String): GrammarRecord = throw ApiError.Http(404, null)
        override suspend fun records(learning: String, favorite: Boolean, mode: GrammarMode?, cursor: String?) = GrammarRecords()
        override suspend fun favorite(id: String, value: Boolean): GrammarRecord = throw UnsupportedOperationException()
        override suspend fun delete(id: String) { deleted += id }
    }

    private class MemoryJournal : GrammarJournaling {
        val values = mutableListOf<GrammarInput>()
        override fun pending(owner: String) = values.toList()
        override fun save(input: GrammarInput, owner: String) { values += input }
        override fun remove(id: String, owner: String) { values.removeAll { it.requestId == id } }
    }

    private class MemoryProgress : GrammarProgressStoring {
        val removed = mutableListOf<String>()
        override fun progress(owner: String, learning: String, setId: String) = GrammarPracticeProgress()
        override fun save(progress: GrammarPracticeProgress, owner: String, learning: String, setId: String) = Unit
        override fun remove(owner: String, learning: String, setId: String) { removed += setId }
    }

    private fun model(repository: FakeRepository, progress: MemoryProgress = MemoryProgress()) = GrammarModel(
        repository = repository,
        offerCache = AssistantOfferCache("g", null, GrammarOffer.serializer()),
        historyCache = AssistantHistoryCache(),
        journal = MemoryJournal(),
        progress = progress,
        owner = "u1",
        uiLanguage = { "zh-Hant" },
        learning = "zh-ja",
        pollIntervalMillis = 1,
    )

    @Test fun `only what the template reads is sent`() = runTest {
        val repository = FakeRepository(offer)
        val model = model(repository)
        model.load()
        model.setMode(GrammarMode.Compare)
        model.setCompareWith("〜ところだ")
        model.setMode(GrammarMode.Explain)
        model.setQuery("〜ばかり")
        model.generate()
        val sent = repository.sent.single()
        assertEquals("", sent.compareWith)
        assertEquals(6, sent.maxPoints)
        // The draft is cleared after sending.
        assertEquals("", model.state.value.compareWith)
    }

    @Test fun `a next step fills the draft and spends nothing`() = runTest {
        val repository = FakeRepository(offer)
        val model = model(repository)
        model.load()
        model.setQuery("〜ばかり")
        model.generate()
        val record = model.state.value.turns.single().record!!
        model.next(GrammarMode.Compare, record)
        val state = model.state.value
        assertEquals(GrammarMode.Compare, state.mode)
        assertEquals("〜ばかり", state.query)
        assertEquals(record.id, state.sourceRecordId)
        assertEquals(1, state.draftTick)
        assertEquals(1, repository.sent.size)

        // Typing over it makes it the person's own question.
        model.setQuery("〜ばかりか")
        assertNull(model.state.value.sourceRecordId)
    }

    @Test fun `a comparison's practice step carries the other point`() = runTest {
        val repository = FakeRepository(offer).apply { content = { comparison() } }
        val model = model(repository)
        model.load()
        model.setMode(GrammarMode.Compare)
        model.setQuery("〜ばかり")
        model.generate()
        model.follow(GrammarModel.ComparisonStep.Practice, model.state.value.turns.single().record!!)
        val state = model.state.value
        assertEquals(GrammarMode.Practice, state.mode)
        assertEquals("A", state.query)
        assertEquals("B", state.compareWith)
    }

    @Test fun `deleting a set forgets its practice on this device`() = runTest {
        val progress = MemoryProgress()
        val repository = FakeRepository(offer)
        val model = model(repository, progress)
        model.load()
        model.setQuery("〜ばかり")
        model.generate()
        val record = model.state.value.turns.single().record!!
        model.delete(record)
        assertEquals(listOf(record.id), progress.removed)
        assertTrue(model.state.value.turns.isEmpty())
    }

    private companion object {
        fun explanation(name: String) = GrammarExplanation(
            summary = GrammarExplanation.Summary(name),
            structure = GrammarExplanation.Structure(),
        )

        fun comparison() = GrammarComparison(
            a = GrammarComparison.Point("A"),
            b = GrammarComparison.Point("B"),
            similarity = GrammarComparison.Similarity(interchangeable = GrammarVerdict.Conditional),
        )
    }
}
