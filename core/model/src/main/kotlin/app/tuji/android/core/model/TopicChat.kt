package app.tuji.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 圖鑑主題對話 — iOS's `TopicChatting.swift`, the wire half (tuji-web `/api/topic-chat`).
 *
 * The person picks a 圖鑑 category as the topic; Moira opens, takes up what
 * they say, and shows a sentence they can copy with one part swapped. The
 * server keeps the conversation and decides every race, so every action
 * answers with the whole conversation as the server now holds it.
 */

/** A sentence in the language being learned, with what helps read it. */
@Serializable
data class ChatTargetText(
    val text: String,
    /** Japanese only: kana over each kanji run. */
    val furigana: List<FuriganaSegment>? = null,
    /** Japanese only, when there is no `furigana`. */
    val reading: String? = null,
    /** In the interface language; null when that is the language being learned. */
    val translation: String? = null,
)

/** 「跟著說」: the structure behind a turn, with one blank (＿＿ or ___). */
@Serializable
data class ChatPattern(val key: String = "", val template: String, val translation: String? = null)

/** An answer the person can drop into their draft. */
@Serializable
data class ChatOption(val label: String = "", val answer: ChatTargetText)

@Serializable
data class ChatKeyword(val text: String, val reading: String? = null, val meaning: String = "", val wordId: String? = null)

@Serializable
data class ChatAITurn(
    /** opening / share / follow_up / answer / clarify / role_invite / another. */
    val intent: String = "",
    /** Opening only: one line naming the imagined situation. */
    val scene: String? = null,
    val line: ChatTargetText,
    val awaitsAnswer: Boolean = true,
    val pattern: ChatPattern? = null,
    /** 「示範回答」: one whole answer the person could send. */
    val example: ChatTargetText? = null,
    val options: List<ChatOption> = emptyList(),
    val keywords: List<ChatKeyword> = emptyList(),
    val wordIds: List<String> = emptyList(),
    val simpler: Simpler? = null,
) {
    /** 「簡單一點」: the same question again, easier, added under the turn. */
    @Serializable
    data class Simpler(val note: String = "", val line: ChatTargetText? = null, val options: List<ChatOption> = emptyList())
}

/** What the app can say it helped with. The wire values are the server's. */
@Serializable
enum class ChatSupport(val wire: String) {
    @SerialName("hint") Hint("hint"),
    @SerialName("example") Example("example"),
    @SerialName("express") Express("express"),
    @SerialName("option") Option("option"),
    @SerialName("simplify") Simplify("simplify"),
}

@Serializable
data class ChatMessage(
    val id: String,
    val kind: Kind,
    val topicId: String = "",
    val topicVersion: Int = 0,
    val ai: ChatAITurn? = null,
    val user: UserTurn? = null,
    val topic: TopicMark? = null,
) {
    @Serializable
    enum class Kind {
        @SerialName("ai") Ai,
        @SerialName("user") User,
        @SerialName("topic") Topic,
    }

    @Serializable
    data class UserTurn(val text: String, val support: List<String> = emptyList())

    @Serializable
    data class TopicMark(val name: String)
}

@Serializable
data class ChatReview(
    val topics: List<Topic> = emptyList(),
    val sentences: List<Sentence> = emptyList(),
    val answerTypes: List<AnswerType> = emptyList(),
    val tryNext: TryNext? = null,
) {
    @Serializable
    data class Topic(val id: String, val name: String)

    /** 「你說過的」. kind: as_said / corrected / expressed. */
    @Serializable
    data class Sentence(val kind: String, val text: ChatTargetText, val original: String? = null, val messageId: String)

    /** 「練習過的答法」. */
    @Serializable
    data class AnswerType(val type: String, val pattern: String, val answer: String, val messageId: String)

    /** 「下次可以試」 — a suggestion, not something the person did. */
    @Serializable
    data class TryNext(val answerType: String, val text: ChatTargetText)
}

@Serializable
data class TopicChatSummary(
    val id: String,
    val learningDirection: String = "",
    val topicId: String,
    val topicName: String,
    val topicVersion: Int = 0,
    /** "active" or "ended". */
    val status: String,
    val updatedAt: String = "",
    /** How many times the person answered. Zero means no review is offered. */
    val answers: Int = 0,
    val spent: Int? = null,
    val review: ChatReview? = null,
) {
    val isActive: Boolean get() = status == "active"
}

@Serializable
data class TopicChatOffer(val minPoints: Int = 0, val maxPoints: Int = 0, val available: Int = 0)

@Serializable
data class TopicChatPending(val requestId: String, val kind: String = "", val text: String? = null)

/** The conversation as the server holds it, and what the last action added. */
@Serializable
data class TopicChatState(
    val chat: TopicChatSummary,
    val messages: List<ChatMessage> = emptyList(),
    val pending: TopicChatPending? = null,
    /** The current topic has no opening yet: the last one failed. */
    val needsOpening: Boolean = false,
    /** Free 簡單一點／換個問題 left before the next answer. */
    val assistsLeft: Int = 0,
    val offer: TopicChatOffer = TopicChatOffer(),
    val openingError: String? = null,
    val reviewError: String? = null,
    val express: ChatTargetText? = null,
    val charged: Int? = null,
) {
    /** The turn the person is answering: the newest AI turn of the current topic. */
    val latestTurn: ChatMessage?
        get() {
            val last = messages.lastOrNull { it.kind != ChatMessage.Kind.User } ?: return null
            return last.takeIf { it.kind == ChatMessage.Kind.Ai && it.topicVersion == chat.topicVersion }
        }
}

@Serializable
data class TopicChatList(val chats: List<TopicChatSummary> = emptyList(), val active: String? = null, val offer: TopicChatOffer = TopicChatOffer())

@Serializable
data class TopicChatStartBody(val requestId: String, val uiLanguage: String, val learningDirection: String, val topicId: String)

@Serializable
data class TopicChatReplyBody(
    val requestId: String,
    val uiLanguage: String,
    val text: String,
    val replyTo: String,
    val support: List<String>,
)

@Serializable
enum class TopicChatAssistKind {
    @SerialName("express") Express,
    @SerialName("simplify") Simplify,
    @SerialName("another") Another,
}

@Serializable
data class TopicChatAssistBody(
    val kind: TopicChatAssistKind,
    val requestId: String,
    val uiLanguage: String,
    val messageId: String,
    val text: String? = null,
)
