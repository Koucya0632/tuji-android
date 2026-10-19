package app.tuji.android.core.network

import app.tuji.android.core.model.TopicChatAssistBody
import app.tuji.android.core.model.TopicChatList
import app.tuji.android.core.model.TopicChatReplyBody
import app.tuji.android.core.model.TopicChatStartBody
import app.tuji.android.core.model.TopicChatState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 主題對話's calls — iOS's `TopicChatting`. Every generation carries a request
 * id made by the caller; sending the same id again never makes a second
 * message or a second charge.
 */
interface TopicChatting {
    suspend fun list(learning: String, uiLanguage: String): TopicChatList
    suspend fun start(body: TopicChatStartBody): TopicChatState
    suspend fun read(id: String, uiLanguage: String): TopicChatState
    suspend fun delete(id: String)
    suspend fun switchTopic(id: String, requestId: String, uiLanguage: String, topicId: String): TopicChatState
    suspend fun retryOpening(id: String, requestId: String, uiLanguage: String): TopicChatState
    suspend fun reply(id: String, body: TopicChatReplyBody): TopicChatState
    suspend fun assist(id: String, body: TopicChatAssistBody): TopicChatState
    /** True when the server stopped it; false when it had already landed or failed. */
    suspend fun stop(id: String, requestId: String): Boolean
    suspend fun end(id: String, requestId: String, uiLanguage: String, review: Boolean): TopicChatState
}

class TopicChatRepository(private val api: TujiApiClient) : TopicChatting {
    override suspend fun list(learning: String, uiLanguage: String): TopicChatList =
        api.get(AssistantEndpoint.TopicChats(learning, uiLanguage))

    override suspend fun start(body: TopicChatStartBody): TopicChatState = api.post(AssistantEndpoint.TopicChatStart, body)

    override suspend fun read(id: String, uiLanguage: String): TopicChatState = api.get(AssistantEndpoint.TopicChat(id, uiLanguage))

    override suspend fun delete(id: String) {
        api.delete<JsonElement>(AssistantEndpoint.TopicChat(id, null))
    }

    override suspend fun switchTopic(id: String, requestId: String, uiLanguage: String, topicId: String): TopicChatState =
        api.post(AssistantEndpoint.TopicChatAction(id, "topic"), TopicBody(requestId, uiLanguage, topicId))

    override suspend fun retryOpening(id: String, requestId: String, uiLanguage: String): TopicChatState =
        api.post(AssistantEndpoint.TopicChatAction(id, "opening"), OpeningBody(requestId, uiLanguage))

    override suspend fun reply(id: String, body: TopicChatReplyBody): TopicChatState =
        api.post(AssistantEndpoint.TopicChatAction(id, "reply"), body)

    override suspend fun assist(id: String, body: TopicChatAssistBody): TopicChatState =
        api.post(AssistantEndpoint.TopicChatAction(id, "assist"), body)

    override suspend fun stop(id: String, requestId: String): Boolean =
        api.post<Stopped>(AssistantEndpoint.TopicChatAction(id, "stop"), StopBody(requestId)).stopped

    override suspend fun end(id: String, requestId: String, uiLanguage: String, review: Boolean): TopicChatState =
        api.post(AssistantEndpoint.TopicChatAction(id, "end"), EndBody(requestId, uiLanguage, review))

    @Serializable private data class TopicBody(val requestId: String, val uiLanguage: String, val topicId: String)
    @Serializable private data class OpeningBody(val requestId: String, val uiLanguage: String)
    @Serializable private data class StopBody(val requestId: String)
    @Serializable private data class Stopped(val stopped: Boolean)
    @Serializable private data class EndBody(val requestId: String, val uiLanguage: String, val review: Boolean)
}
