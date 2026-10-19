package app.tuji.android.core.network

import app.tuji.android.core.model.Story
import app.tuji.android.core.model.StoryEvent
import app.tuji.android.core.model.StoryInterrupted
import app.tuji.android.core.model.StoryOffer
import app.tuji.android.core.model.StoryPayload
import app.tuji.android.core.model.StoryResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.Serializable

/** 情境故事's calls — iOS's `AssistantStoryWriting`. */
interface StoryWriting {
    suspend fun storyOffer(uiLanguage: String): StoryOffer

    /**
     * The story as it is written. Fails before the first event the way any
     * request does; a failure after it ends the flow with [StoryInterrupted].
     */
    fun writeStory(payload: StoryPayload): Flow<StoryEvent>
}

class StoryRepository(private val api: TujiApiClient) : StoryWriting {
    override suspend fun storyOffer(uiLanguage: String): StoryOffer = api.get(AssistantEndpoint.StoryOffer(uiLanguage))

    override fun writeStory(payload: StoryPayload): Flow<StoryEvent> =
        api.postLines(AssistantEndpoint.Story, payload).mapNotNull(::event)

    @Serializable
    private data class Line(
        val type: String,
        val title: String? = null,
        val intro: String? = null,
        val segment: Story.Segment? = null,
        val error: String? = null,
    )

    companion object {
        /**
         * Null for a line this version does not know, so a new kind of line is
         * skipped rather than fatal. A server that does not stream answers with
         * the whole result in one body; that is the finished story.
         */
        fun event(text: String): StoryEvent? {
            val line = runCatching { TujiJson.decodeFromString(Line.serializer(), text) }.getOrNull()
                ?: return runCatching { TujiJson.decodeFromString(StoryResult.serializer(), text) }.getOrNull()
                    ?.let(StoryEvent::Done)
            return when (line.type) {
                "opening" -> if (line.title != null && line.intro != null) StoryEvent.Opening(line.title, line.intro) else null
                "segment" -> line.segment?.let(StoryEvent::Segment)
                "done" -> try {
                    StoryEvent.Done(TujiJson.decodeFromString(StoryResult.serializer(), text))
                } catch (e: Throwable) {
                    throw ApiError.Decoding(e)
                }
                "error" -> throw StoryInterrupted(line.error ?: "assistant_unavailable")
                else -> null
            }
        }
    }
}
