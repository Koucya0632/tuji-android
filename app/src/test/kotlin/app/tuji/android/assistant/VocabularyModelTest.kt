package app.tuji.android.assistant

import app.tuji.android.core.model.VocabularyCandidate
import app.tuji.android.core.model.VocabularyInput
import app.tuji.android.core.model.VocabularyLookup
import app.tuji.android.core.model.VocabularyHeadword
import app.tuji.android.core.model.VocabularyMode
import app.tuji.android.core.model.VocabularyOffer
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.core.model.VocabularyRecords
import app.tuji.android.core.model.VocabularyReply
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.VocabularyStudying
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyModelTest {

    private val offer = VocabularyOffer(
        enabled = true,
        available = 100,
        pricingVersion = "p1",
        modes = VocabularyMode.entries.map { VocabularyOffer.Price(it, 1, 3) },
    )

    private class FakeRepository(var offer: VocabularyOffer) : VocabularyStudying {
        var offers = 0
        val sent = mutableListOf<VocabularyInput>()
        var reply: (VocabularyInput) -> VocabularyRecord = { completed(it) }
        var rejection: Throwable? = null

        override suspend fun offer(): VocabularyOffer = offer.also { offers++ }
        override suspend fun generate(input: VocabularyInput): VocabularyRecord {
            sent += input
            rejection?.let { throw it }
            return reply(input)
        }
        override suspend fun record(id: String): VocabularyRecord = throw ApiError.Http(404, null)
        override suspend fun records(learning: String, favorite: Boolean, cursor: String?) = VocabularyRecords()
        override suspend fun favorite(id: String, value: Boolean): VocabularyRecord = throw UnsupportedOperationException()
        override suspend fun delete(id: String) = Unit
    }

    private class MemoryJournal : VocabularyJournaling {
        val values = mutableListOf<VocabularyInput>()
        override fun pending(owner: String) = values.toList()
        override fun save(input: VocabularyInput, owner: String) { values.removeAll { it.requestId == input.requestId }; values += input }
        override fun remove(id: String, owner: String) { values.removeAll { it.requestId == id } }
    }

    private fun model(
        repository: FakeRepository = FakeRepository(offer),
        journal: MemoryJournal = MemoryJournal(),
        cache: AssistantOfferCache<VocabularyOffer> = AssistantOfferCache("k", null, VocabularyOffer.serializer()),
        owner: String? = "u1",
        words: List<Word> = emptyList(),
    ) = VocabularyModel(
        repository = repository,
        offerCache = cache,
        historyCache = AssistantHistoryCache(),
        journal = journal,
        owner = owner,
        uiLanguage = { "zh-Hant" },
        learning = "zh-ja",
        catalog = { words },
        pollIntervalMillis = 1,
    )

    @Test fun `typing never spends — only 送出 sends a request`() = runTest {
        val repository = FakeRepository(offer)
        val model = model(repository)
        model.load()
        model.setQuery("猫")
        model.choose(VocabularyCandidate("ねこ", "貓"), VocabularyMode.Analysis)
        assertTrue(repository.sent.isEmpty())

        model.generate()
        val sent = repository.sent.single()
        assertEquals(VocabularyMode.Analysis, sent.mode)
        assertEquals("ねこ", sent.query)
        assertEquals("貓", sent.selectedSense)
        assertEquals(3, sent.maxPoints)
        // The draft is cleared; the turn keeps what was sent.
        assertEquals("", model.state.value.query)
        assertEquals(VocabularyRecord.Status.Completed, model.state.value.turns.single().record?.status)
    }

    @Test fun `an offer kept from an earlier launch is shown but cannot pay`() = runTest {
        val cache = AssistantOfferCache("k", null, VocabularyOffer.serializer())
        val repository = FakeRepository(offer)
        // Read once this launch, then the kept balance stops being trusted.
        cache.load("u1") { offer }
        cache.balanceChanged()
        val model = model(repository, cache = cache)
        model.setQuery("猫")
        assertFalse(model.canGenerate())
        model.load()
        assertEquals(1, repository.offers)
        assertTrue(model.canGenerate())
    }

    @Test fun `a refused request is not pending and says why`() = runTest {
        val repository = FakeRepository(offer).apply {
            rejection = ApiError.Http(409, """{"error":"insufficient_credits"}""")
        }
        val journal = MemoryJournal()
        val model = model(repository, journal)
        model.load()
        model.setQuery("猫")
        model.generate()
        val state = model.state.value
        assertNull(state.pending)
        assertTrue(journal.values.isEmpty())
        assertEquals(VocabularyModel.Blocker.InsufficientPoints, state.blocker)
        assertEquals(
            AssistantNote.Known(AssistantNote.Kind.InsufficientCredits),
            state.turns.single().failureNote,
        )
    }

    @Test fun `a request the server may have taken stays in the journal`() = runTest {
        val repository = FakeRepository(offer).apply { rejection = ApiError.Transport(RuntimeException("offline")) }
        val journal = MemoryJournal()
        val model = model(repository, journal)
        model.load()
        model.setQuery("猫")
        model.generate()
        assertEquals(1, journal.values.size)
        assertEquals(AssistantNote.Known(AssistantNote.Kind.Confirming), model.state.value.message)
    }

    @Test fun `助記 offers another card as the main action, the others quietly`() = runTest {
        val model = model()
        model.load()
        model.setMode(VocabularyMode.Mnemonic)
        model.setQuery("猫")
        model.generate()
        val turn = model.state.value.turns.last()
        assertEquals(VocabularyModel.Regeneration.Main(3), model.regeneration(turn))

        model.setMode(VocabularyMode.Lookup)
        model.setQuery("犬")
        model.generate()
        val turns = model.state.value.turns
        // Only the newest card offers another.
        assertNull(model.regeneration(turns.first()))
        assertEquals(VocabularyModel.Regeneration.Secondary(3), model.regeneration(turns.last()))
    }

    @Test fun `a membership offer blocks sending with its own reason`() = runTest {
        val model = model(FakeRepository(offer.copy(enabled = false, reason = VocabularyOffer.Reason.Membership)))
        model.load()
        assertEquals(VocabularyModel.Blocker.Membership, model.state.value.blocker)
    }

    @Test fun `圖鑑 matches are exact, on the word or its meaning`() {
        val model = model(
            words = listOf(
                Word(id = "w1", word = "Cat", chinese = "貓"),
                Word(id = "w2", word = "catalog", chinese = "目錄"),
            ),
        )
        assertEquals(listOf("w1"), model.catalogMatches("cat ").map { it.id })
        assertEquals(listOf("w1"), model.catalogMatches("貓").map { it.id })
        assertTrue(model.catalogMatches("").isEmpty())
    }

    private companion object {
        fun completed(input: VocabularyInput) = VocabularyRecord(
            id = input.requestId,
            status = VocabularyRecord.Status.Completed,
            input = input,
            result = VocabularyReply(content = VocabularyLookup(VocabularyHeadword(input.query))),
        )
    }
}
