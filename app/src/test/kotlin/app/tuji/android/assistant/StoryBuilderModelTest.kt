package app.tuji.android.assistant

import app.tuji.android.core.model.Story
import app.tuji.android.core.model.StoryEvent
import app.tuji.android.core.model.StoryOffer
import app.tuji.android.core.model.StoryPayload
import app.tuji.android.core.model.StoryResult
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.StoryWriting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryBuilderModelTest {

    private val offer = StoryOffer(
        minPoints = 2,
        maxPoints = 8,
        available = 50,
        scenes = listOf(
            StoryOffer.Scene("cafe", "咖啡店", listOf(StoryOffer.Situation("order", "點餐"), StoryOffer.Situation("chat", "聊天"))),
            StoryOffer.Scene("park", "公園", listOf(StoryOffer.Situation("chat", "聊天"))),
        ),
        levels = listOf(StoryOffer.Level("easy", "簡單")),
    )

    private class FakeRepository(val offer: StoryOffer) : StoryWriting {
        val payloads = mutableListOf<StoryPayload>()
        var events: List<StoryEvent> = emptyList()
        var failure: Throwable? = null
        var offers = 0
        override suspend fun storyOffer(uiLanguage: String): StoryOffer = offer.also { offers++ }
        override fun writeStory(payload: StoryPayload): Flow<StoryEvent> = flow {
            payloads += payload
            events.forEach { emit(it) }
            failure?.let { throw it }
        }
    }

    private val words = listOf(Word("w1", "猫"), Word("w2", "犬"), Word("w3", "鳥"), Word("w4", "魚"))

    private fun model(repository: FakeRepository, cache: StoryOfferCache = StoryOfferCache(null)): StoryBuilderModel {
        var ids = 0
        return StoryBuilderModel(
            repository = repository,
            offerCache = cache,
            candidates = { words },
            uiLanguage = { "zh-Hant" },
            learning = "zh-ja",
            language = TargetLanguage.JA,
            makeRequestId = { "req-${++ids}" },
        )
    }

    private suspend fun StoryBuilderModel.complete() {
        loadOffer()
        toggle(words[0])
        chooseScene("cafe")
        chooseSituation("order")
        chooseLevel("easy")
    }

    @Test fun `three words at most`() {
        val model = model(FakeRepository(offer))
        words.forEach(model::toggle)
        assertEquals(3, model.state.value.picked.size)
        assertFalse(model.state.value.canPickMore)
    }

    @Test fun `a place without the chosen situation clears it`() = runTest {
        val model = model(FakeRepository(offer))
        model.loadOffer()
        // Before a place, every situation any place offers, once each.
        assertEquals(listOf("order", "chat"), model.state.value.situations.map { it.id })
        model.chooseSituation("order")
        model.chooseScene("park")
        assertNull(model.state.value.situationId)
    }

    @Test fun `the story arrives in pieces and brings the balance back`() = runTest {
        val repository = FakeRepository(offer).apply {
            val segment = Story.Segment(Story.Speaker.Cat, "にゃ")
            events = listOf(
                StoryEvent.Opening("T", "I"),
                StoryEvent.Segment(segment),
                StoryEvent.Done(StoryResult(Story("T", "I", listOf(segment)), charged = 5, available = 45, minPoints = 2, maxPoints = 8)),
            )
        }
        val cache = StoryOfferCache(null)
        val model = model(repository, cache)
        model.complete()
        model.write()
        val state = model.state.value
        assertEquals(1, state.story?.segments?.size)
        assertEquals(StoryBuilderModel.Writing.Idle, state.writing)
        assertEquals(45, state.offer?.available)
        assertEquals(45, cache.entry("zh-Hant")?.offer?.available)
    }

    @Test fun `the same choices sent again keep their request id`() = runTest {
        val repository = FakeRepository(offer).apply { failure = ApiError.Transport(RuntimeException("lost")) }
        val model = model(repository)
        model.complete()
        model.write()
        assertEquals(StoryBuilderModel.Writing.Failed(StoryBuilderModel.Failure.Offline), model.state.value.writing)
        model.write()
        assertEquals(listOf("req-1", "req-1"), repository.payloads.map { it.requestId })

        // A new set of choices is a new request.
        model.chooseLevel("easy")
        model.toggle(words[1])
        model.write()
        assertEquals("req-2", repository.payloads.last().requestId)
    }

    @Test fun `refusals say why`() {
        val model = model(FakeRepository(offer))
        assertEquals(StoryBuilderModel.Failure.InsufficientPoints, model.failure(ApiError.Http(409, """{"error":"insufficient_credits"}""")))
        assertEquals(StoryBuilderModel.Failure.Busy, model.failure(ApiError.Http(429, null)))
        assertEquals(StoryBuilderModel.Failure.ItemsChanged, model.failure(ApiError.Http(400, null)))
        assertEquals(StoryBuilderModel.Failure.Unavailable, model.failure(ApiError.Http(403, null)))
        assertEquals(StoryBuilderModel.Failure.Busy, model.failure(app.tuji.android.core.model.StoryInterrupted("rate_limited")))
    }

    @Test fun `a remembered offer shows the choices but cannot pay`() = runTest {
        val cache = StoryOfferCache(null)
        cache.store(offer, "zh-Hant")
        cache.balanceChanged()
        val repository = FakeRepository(offer)
        val model = model(repository, cache)
        model.toggle(words[0])
        assertFalse(model.state.value.offerReady)
        model.loadOffer()
        assertTrue(model.state.value.offerReady)
        assertEquals(1, repository.offers)
    }
}
