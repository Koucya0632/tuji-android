package app.tuji.android.assistant

import app.tuji.android.core.model.ChatAITurn
import app.tuji.android.core.model.ChatMessage
import app.tuji.android.core.model.ChatSupport
import app.tuji.android.core.model.ChatTargetText
import app.tuji.android.core.model.TopicChatAssistBody
import app.tuji.android.core.model.TopicChatList
import app.tuji.android.core.model.TopicChatOffer
import app.tuji.android.core.model.TopicChatReplyBody
import app.tuji.android.core.model.TopicChatStartBody
import app.tuji.android.core.model.TopicChatState
import app.tuji.android.core.model.TopicChatSummary
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.TopicChatting
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicChatModelTest {

    private class Prefs : TopicChatPreferences {
        override var showTranslation = true
        override var showReading = true
    }

    private fun summary(id: String = "c1", topic: String = "kitchen", active: Boolean = true) =
        TopicChatSummary(id = id, topicId = topic, topicName = topic, status = if (active) "active" else "ended")

    private fun ai(id: String) = ChatMessage(id, ChatMessage.Kind.Ai, ai = ChatAITurn(line = ChatTargetText("q$id")))

    private fun state(vararg messages: ChatMessage, chat: TopicChatSummary = summary()) =
        TopicChatState(chat = chat, messages = messages.toList(), offer = TopicChatOffer(1, 3, 50), assistsLeft = 2)

    private class FakeRepository : TopicChatting {
        var list = TopicChatList()
        var current: TopicChatState? = null
        val replies = mutableListOf<TopicChatReplyBody>()
        var replyResult: (TopicChatReplyBody) -> TopicChatState = { throw ApiError.Transport(RuntimeException("offline")) }
        var started = mutableListOf<String>()
        var switched: TopicChatState? = null
        override suspend fun list(learning: String, uiLanguage: String) = list
        override suspend fun start(body: TopicChatStartBody): TopicChatState { started += body.topicId; return current!! }
        override suspend fun read(id: String, uiLanguage: String) = current!!
        override suspend fun delete(id: String) = Unit
        override suspend fun switchTopic(id: String, requestId: String, uiLanguage: String, topicId: String) = switched!!
        override suspend fun retryOpening(id: String, requestId: String, uiLanguage: String) = current!!
        override suspend fun reply(id: String, body: TopicChatReplyBody): TopicChatState { replies += body; return replyResult(body) }
        override suspend fun assist(id: String, body: TopicChatAssistBody) = current!!
        override suspend fun stop(id: String, requestId: String) = true
        override suspend fun end(id: String, requestId: String, uiLanguage: String, review: Boolean) = current!!
    }

    private fun TestScope.model(entry: TopicChatEntry, repository: FakeRepository): TopicChatModel {
        var ids = 0
        return TopicChatModel(
            entry = entry,
            repository = repository,
            preferences = Prefs(),
            uiLanguage = { "zh-Hant" },
            direction = "zh-en",
            scope = this,
            makeRequestId = { "req-${++ids}" },
            pollIntervalMillis = 1,
        )
    }

    @Test fun `a conversation to continue is asked about first`() = runTest {
        val repository = FakeRepository().apply { list = TopicChatList(chats = listOf(summary()), active = "c1") }
        val model = model(TopicChatEntry.Topic("bath"), repository)
        model.load()
        assertTrue(model.state.value.phase is TopicChatModel.Phase.Choosing)
        assertTrue(repository.started.isEmpty())
    }

    @Test fun `a topic with nothing to continue starts at once`() = runTest {
        val repository = FakeRepository().apply { current = state(ai("m1")) }
        val model = model(TopicChatEntry.Topic("kitchen"), repository)
        model.load()
        assertEquals(listOf("kitchen"), repository.started)
        assertEquals(TopicChatModel.Phase.Ready, model.state.value.phase)
    }

    @Test fun `an option never replaces the draft without asking`() = runTest {
        val repository = FakeRepository().apply { current = state(ai("m1")) }
        val model = model(TopicChatEntry.Saved("c1"), repository)
        model.load()
        model.setDraft("I like")
        model.fill("I like apples", ChatSupport.Option)
        assertEquals("I like", model.state.value.draft)
        model.resolveFill(TopicChatModel.FillChoice.Append)
        assertEquals("I like I like apples", model.state.value.draft)
        assertTrue(ChatSupport.Option in model.state.value.draftSupport)
    }

    @Test fun `a failed answer is sent again under the same request id`() = runTest {
        val repository = FakeRepository().apply { current = state(ai("m1")) }
        val model = model(TopicChatEntry.Saved("c1"), repository)
        model.load()
        model.setDraft("hello")
        model.send()
        advanceUntilIdle()
        assertEquals(TopicChatModel.Outgoing.Status.Failed(TopicChatModel.Failure.Offline), model.state.value.outgoing?.status)

        // This time it lands: a newer turn answers it, and the outgoing row goes.
        repository.replyResult = { state(ai("m1"), ChatMessage("u1", ChatMessage.Kind.User, user = ChatMessage.UserTurn("hello")), ai("m2")) }
        model.retryOutgoing()
        advanceUntilIdle()
        assertEquals(listOf("req-1", "req-1"), repository.replies.map { it.requestId })
        assertNull(model.state.value.outgoing)
        assertEquals("m2", model.state.value.latestTurn?.id)
    }

    @Test fun `an answer to a turn that moved on goes back to the draft`() = runTest {
        val repository = FakeRepository().apply {
            current = state(ai("m1"))
            replyResult = { throw ApiError.Http(409, """{"error":"message_outdated"}""") }
        }
        val model = model(TopicChatEntry.Saved("c1"), repository)
        model.load()
        model.setDraft("hello")
        model.send()
        advanceUntilIdle()
        assertNull(model.state.value.outgoing)
        assertEquals("hello", model.state.value.draft)
    }

    @Test fun `switching topic sets the draft aside`() = runTest {
        val repository = FakeRepository().apply {
            current = state(ai("m1"))
            switched = state(ai("m1"), chat = summary(topic = "bath"))
        }
        val model = model(TopicChatEntry.Saved("c1"), repository)
        model.load()
        model.setDraft("about the kitchen")
        model.switchTopic("bath")
        val s = model.state.value
        assertEquals("", s.draft)
        assertEquals(TopicChatModel.KeptDraft("kitchen", "about the kitchen"), s.keptDraft)
        model.restoreKeptDraft()
        assertEquals("about the kitchen", model.state.value.draft)
        // Taking back one's own words is not help.
        assertTrue(model.state.value.draftSupport.isEmpty())
    }

    @Test fun `refusals map to what the screen says`() {
        assertEquals(TopicChatModel.Failure.InsufficientPoints, TopicChatModel.failure(ApiError.Http(409, """{"error":"insufficient_credits"}""")))
        assertEquals(TopicChatModel.Failure.AssistLimit, TopicChatModel.failure(ApiError.Http(409, """{"error":"assist_limit"}""")))
        assertEquals(TopicChatModel.Failure.Membership, TopicChatModel.failure(ApiError.Http(403, null)))
        assertEquals(TopicChatModel.Failure.RateLimited, TopicChatModel.failure(ApiError.Http(429, null)))
        assertEquals(TopicChatModel.Failure.Offline, TopicChatModel.failure(ApiError.Transport(RuntimeException())))
    }
}
