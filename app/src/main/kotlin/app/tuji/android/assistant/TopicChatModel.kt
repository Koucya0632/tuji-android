package app.tuji.android.assistant

import android.content.SharedPreferences
import app.tuji.android.core.model.ChatMessage
import app.tuji.android.core.model.ChatSupport
import app.tuji.android.core.model.TopicChatAssistBody
import app.tuji.android.core.model.TopicChatAssistKind
import app.tuji.android.core.model.TopicChatReplyBody
import app.tuji.android.core.model.TopicChatStartBody
import app.tuji.android.core.model.TopicChatState
import app.tuji.android.core.model.TopicChatSummary
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.TopicChatting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** Where 主題對話 was opened from. */
sealed interface TopicChatEntry {
    /** A category page's 「用這個主題聊聊」. */
    data class Topic(val topicId: String) : TopicChatEntry

    /** A saved conversation from 對話紀錄. */
    data class Saved(val chatId: String) : TopicChatEntry

    /** 助手's 主題對話: no topic yet, so the person picks one. */
    data object Choose : TopicChatEntry
}

/** The reading supports, remembered across conversations — iOS keeps them in defaults too. */
interface TopicChatPreferences {
    var showTranslation: Boolean
    var showReading: Boolean
}

class SharedTopicChatPreferences(private val preferences: SharedPreferences) : TopicChatPreferences {
    // A beginner reads the translation by default; either can be turned off.
    override var showTranslation: Boolean
        get() = preferences.getBoolean("tuji.topicChat.showTranslation", true)
        set(value) = preferences.edit().putBoolean("tuji.topicChat.showTranslation", value).apply()
    override var showReading: Boolean
        get() = preferences.getBoolean("tuji.topicChat.showReading", true)
        set(value) = preferences.edit().putBoolean("tuji.topicChat.showReading", value).apply()
}

/**
 * 圖鑑主題對話 — what the screen holds between the server's answers. iOS's
 * `TopicChatModel`.
 *
 * The server owns the conversation ([TopicChatState]) and decides every race;
 * this owns only what has not reached it yet: the draft, the answer on its way
 * ([Outgoing]), which supports were opened for the draft, and the draft kept
 * from the topic before. Every action ends by drawing the state the server sent
 * back, so nothing here has to guess whether a reply landed.
 *
 * Product rules it keeps:
 * - Nothing is ever sent for the person: options and the example only fill
 *   the draft, and a draft that already holds something asks first.
 * - One answer at a time. 停止 stops it, and a stopped or failed answer can be
 *   sent again under the same request id (never charged twice) or taken back.
 * - Switching topic keeps the conversation and sets the draft aside.
 */
class TopicChatModel(
    val entry: TopicChatEntry,
    private val repository: TopicChatting,
    private val preferences: TopicChatPreferences,
    private val uiLanguage: () -> String,
    val direction: String,
    private val scope: CoroutineScope,
    private val makeRequestId: () -> String = { UUID.randomUUID().toString().lowercase() },
    private val pollIntervalMillis: Long = 2_000,
) {
    enum class Failure {
        InsufficientPoints, RateLimited, Busy, Offline, Membership, SignedOut, Unavailable,
        TopicUnavailable, Outdated, AssistLimit, Ended, Failed,
    }

    sealed interface Phase {
        data object Idle : Phase
        data object Loading : Phase
        /** There is a conversation to continue; ask before starting another. */
        data class Choosing(val active: TopicChatSummary) : Phase
        /** No topic yet: the person picks one, and that starts the conversation. */
        data object Picking : Phase
        data object Ready : Phase
        data class Failed(val failure: Failure) : Phase
    }

    /** The answer on its way, kept until the server's state shows it landed. */
    data class Outgoing(
        val requestId: String,
        val text: String,
        val support: List<ChatSupport>,
        val replyTo: String,
        val status: Status,
    ) {
        sealed interface Status {
            data object Sending : Status
            data object Stopped : Status
            data class Failed(val failure: Failure) : Status
        }
    }

    /** What is being generated besides an answer. */
    sealed interface Work {
        data object Starting : Work
        data object Opening : Work
        data class Switching(val topicId: String) : Work
        data object Simplify : Work
        data object Another : Work
        data object Ending : Work
    }

    data class KeptDraft(val topicName: String, val text: String)
    data class PendingFill(val text: String, val support: ChatSupport)
    enum class FillChoice { Replace, Append, Cancel }

    data class State(
        val phase: Phase = Phase.Idle,
        val chatState: TopicChatState? = null,
        val draft: String = "",
        val draftSupport: Set<ChatSupport> = emptySet(),
        val outgoing: Outgoing? = null,
        val work: Work? = null,
        /** Why the last switch, assist, opening or end did not happen. */
        val actionFailure: Failure? = null,
        val keptDraft: KeptDraft? = null,
        val pendingFill: PendingFill? = null,
        /** The latest turn's 示範回答, opened or not. */
        val exampleShown: Boolean = false,
        val exampleTurnId: String? = null,
        val history: List<TopicChatSummary> = emptyList(),
        val showTranslation: Boolean = true,
        val showReading: Boolean = true,
    ) {
        val chat: TopicChatSummary? get() = chatState?.chat
        val isActive: Boolean get() = chat?.isActive ?: false
        val latestTurn: ChatMessage? get() = chatState?.latestTurn
        val isWriting: Boolean get() = outgoing?.status == Outgoing.Status.Sending || chatState?.pending != null
        val canSend: Boolean
            get() = isActive && latestTurn != null && !isWriting && work == null && draft.isNotBlank()

        /** The ceiling, not the floor: the server refuses a balance below what a reply may cost. */
        val canAfford: Boolean get() = chatState?.offer?.let { it.available >= it.maxPoints } ?: false
        val canAssist: Boolean
            get() = isActive && latestTurn != null && !isWriting && work == null && (chatState?.assistsLeft ?: 0) > 0
    }

    private val mutable = MutableStateFlow(State(showTranslation = preferences.showTranslation, showReading = preferences.showReading))
    val state: StateFlow<State> = mutable.asStateFlow()
    private val now get() = mutable.value
    private fun update(change: (State) -> State) {
        mutable.value = change(mutable.value)
    }

    private var replyJob: Job? = null
    /** Bumped by every switch: only the newest one's answer is drawn. */
    private var switchToken = 0

    fun setShowTranslation(value: Boolean) {
        preferences.showTranslation = value
        update { it.copy(showTranslation = value) }
    }

    fun setShowReading(value: Boolean) {
        preferences.showReading = value
        update { it.copy(showReading = value) }
    }

    fun setDraft(value: String) = update { it.copy(draft = value) }

    // MARK: - 進入

    suspend fun load() {
        if (now.phase != Phase.Idle && now.phase !is Phase.Failed) return
        update { it.copy(phase = Phase.Loading) }
        try {
            when (val entry = entry) {
                is TopicChatEntry.Saved -> {
                    apply(repository.read(entry.chatId, uiLanguage()))
                    update { it.copy(phase = Phase.Ready) }
                }
                is TopicChatEntry.Topic -> {
                    val list = repository.list(direction, uiLanguage())
                    val active = list.chats.firstOrNull { it.id == list.active }
                    update { it.copy(history = list.chats) }
                    if (active != null) {
                        update { it.copy(phase = Phase.Choosing(active)) }
                    } else {
                        startNew(entry.topicId)
                    }
                    return
                }
                TopicChatEntry.Choose -> {
                    val list = repository.list(direction, uiLanguage())
                    val active = list.chats.firstOrNull { it.id == list.active }
                    update { it.copy(history = list.chats, phase = active?.let(Phase::Choosing) ?: Phase.Picking) }
                    return
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            update { it.copy(phase = Phase.Failed(failure(e))) }
            return
        }
        pollWhilePending()
    }

    /** 繼續上次. */
    suspend fun continueActive() {
        val active = (now.phase as? Phase.Choosing)?.active ?: return
        update { it.copy(phase = Phase.Loading) }
        try {
            apply(repository.read(active.id, uiLanguage()))
            update { it.copy(phase = Phase.Ready) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            update { it.copy(phase = Phase.Failed(failure(e))) }
            return
        }
        pollWhilePending()
    }

    /** 開始新對話 when no topic came with the entry: pick one first. */
    fun pickNewTopic() {
        if (now.phase is Phase.Choosing) update { it.copy(phase = Phase.Picking) }
    }

    /** A new conversation on [topicId]. The one being continued in this direction ends, without a review. */
    suspend fun startNew(topicId: String) {
        cancelReply()
        update {
            it.copy(
                outgoing = null, draft = "", draftSupport = emptySet(), keptDraft = null, actionFailure = null,
                phase = if (it.chatState == null) Phase.Loading else it.phase, work = Work.Starting,
            )
        }
        try {
            val state = repository.start(TopicChatStartBody(makeRequestId(), uiLanguage(), direction, topicId))
            apply(state)
            update { it.copy(phase = Phase.Ready) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val failure = failure(e)
            update { if (it.chatState == null) it.copy(phase = Phase.Failed(failure)) else it.copy(actionFailure = failure) }
        } finally {
            update { it.copy(work = null) }
        }
    }

    /** Re-reads the conversation, e.g. after a stop. */
    suspend fun refresh() {
        val chat = now.chat ?: return
        try {
            apply(repository.read(chat.id, uiLanguage()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // What is on screen stays; the next action reports.
        }
    }

    /** Something is being written that this screen did not start, or whose answer was lost: ask again until it lands. */
    private suspend fun pollWhilePending() {
        var tries = 0
        while (now.chatState?.pending != null && tries < 60) {
            tries++
            delay(pollIntervalMillis)
            refresh()
        }
        settleOutgoing()
    }

    // MARK: - 回答

    fun send() {
        val s = now
        val turn = s.latestTurn ?: return
        if (!s.canSend) return
        val support = ChatSupport.entries.filter { it in s.draftSupport }
        update {
            it.copy(
                outgoing = Outgoing(makeRequestId(), s.draft.trim(), support, turn.id, Outgoing.Status.Sending),
                draft = "",
                draftSupport = emptySet(),
            )
        }
        runOutgoing()
    }

    /** 再送一次: the same answer under the same request id — never a second message or charge. */
    fun retryOutgoing() {
        val outgoing = now.outgoing ?: return
        if (outgoing.status == Outgoing.Status.Sending || !now.isActive) return
        update { it.copy(outgoing = outgoing.copy(status = Outgoing.Status.Sending)) }
        runOutgoing()
    }

    /** 改一下: the answer goes back into the draft, and the old request is let go. */
    fun editOutgoing() {
        val outgoing = now.outgoing ?: return
        if (outgoing.status == Outgoing.Status.Sending) return
        update { it.copy(draft = outgoing.text, draftSupport = outgoing.support.toSet(), outgoing = null) }
    }

    private fun runOutgoing() {
        val outgoing = now.outgoing ?: return
        val chat = now.chat ?: return
        replyJob?.cancel()
        replyJob = scope.launch {
            try {
                val state = repository.reply(
                    chat.id,
                    TopicChatReplyBody(outgoing.requestId, uiLanguage(), outgoing.text, outgoing.replyTo, outgoing.support.map { it.wire }),
                )
                apply(state)
                pollWhilePending()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                when (val failure = failure(e)) {
                    Failure.Outdated, Failure.Ended -> {
                        // The turn moved on; the answer goes back to the draft rather than nowhere.
                        update { it.copy(draft = it.draft.ifEmpty { outgoing.text }, outgoing = null) }
                        refresh()
                    }
                    Failure.Busy -> {
                        // Another device or an earlier send is writing; wait for it, then retry is offered.
                        refresh()
                        pollWhilePending()
                        markOutgoing(Outgoing.Status.Failed(Failure.Busy))
                    }
                    else -> markOutgoing(Outgoing.Status.Failed(failure))
                }
            }
        }
    }

    private fun markOutgoing(status: Outgoing.Status) {
        val outgoing = now.outgoing ?: return
        if (outgoing.status == Outgoing.Status.Sending || status != Outgoing.Status.Sending) {
            update { it.copy(outgoing = outgoing.copy(status = status)) }
        }
    }

    /** The server's state says what became of the answer: landed, still being written, or not landed. */
    private fun settleOutgoing() {
        val outgoing = now.outgoing ?: return
        val latest = now.latestTurn
        if (latest != null && latest.id != outgoing.replyTo) {
            update { it.copy(outgoing = null) }
        } else if (now.chatState?.pending?.requestId != outgoing.requestId && outgoing.status == Outgoing.Status.Sending) {
            markOutgoing(Outgoing.Status.Stopped)
        }
    }

    /** 停止. Whichever the server sees first wins: a reply that already landed stays. */
    suspend fun stop() {
        val outgoing = now.outgoing ?: return
        val chat = now.chat ?: return
        if (outgoing.status != Outgoing.Status.Sending) return
        cancelReply()
        markOutgoing(Outgoing.Status.Stopped)
        runCatching { repository.stop(chat.id, outgoing.requestId) }
        refresh()
        settleOutgoing()
    }

    private fun cancelReply() {
        replyJob?.cancel()
        replyJob = null
    }

    // MARK: - 草稿

    /** An option or the example. Never sends; a draft that already holds something asks first. */
    fun fill(text: String, support: ChatSupport) {
        val current = now.draft.trim()
        if (current.isEmpty() || current == text) {
            update { it.copy(draft = text, draftSupport = it.draftSupport + support) }
        } else {
            update { it.copy(pendingFill = PendingFill(text, support)) }
        }
    }

    fun resolveFill(choice: FillChoice) {
        val pending = now.pendingFill ?: return
        update { it.copy(pendingFill = null) }
        when (choice) {
            FillChoice.Replace -> update { it.copy(draft = pending.text) }
            FillChoice.Append -> {
                val joiner = if (direction == "zh-ja") "" else " "
                update { it.copy(draft = it.draft.trim() + joiner + pending.text) }
            }
            FillChoice.Cancel -> return
        }
        update { it.copy(draftSupport = it.draftSupport + pending.support) }
    }

    fun restoreKeptDraft() {
        val kept = now.keptDraft ?: return
        fill(kept.text, ChatSupport.Hint)
        // Taking back one's own words is not help.
        update { it.copy(draftSupport = it.draftSupport - ChatSupport.Hint, keptDraft = null) }
    }

    fun discardKeptDraft() = update { it.copy(keptDraft = null) }

    // MARK: - 幫我接話

    /** 示範回答 — what was generated with the turn; no request, no charge. */
    fun toggleExample() {
        syncTurnSupports()
        update {
            val shown = !it.exampleShown
            it.copy(exampleShown = shown, draftSupport = if (shown) it.draftSupport + ChatSupport.Example else it.draftSupport)
        }
    }

    /** A newer turn closes what was opened under the old one. */
    private fun syncTurnSupports() {
        val id = now.latestTurn?.id
        if (id != now.exampleTurnId) update { it.copy(exampleTurnId = id, exampleShown = false) }
    }

    /** 簡單一點 / 換個問題 — free, a couple of times between answers. */
    suspend fun assist(kind: TopicChatAssistKind) {
        if (kind == TopicChatAssistKind.Express || !now.canAssist) return
        val chat = now.chat ?: return
        val turn = now.latestTurn ?: return
        update { it.copy(work = if (kind == TopicChatAssistKind.Simplify) Work.Simplify else Work.Another, actionFailure = null) }
        try {
            apply(repository.assist(chat.id, TopicChatAssistBody(kind, makeRequestId(), uiLanguage(), turn.id)))
            if (kind == TopicChatAssistKind.Simplify) update { it.copy(draftSupport = it.draftSupport + ChatSupport.Simplify) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            update { it.copy(actionFailure = failure(e)) }
            refresh()
        } finally {
            update { it.copy(work = null) }
        }
    }

    // MARK: - 換主題

    /** 更換主題. The conversation stays; the draft is set aside; an answer still being written is let go. */
    suspend fun switchTopic(topicId: String) {
        val chat = now.chat ?: return
        if (!now.isActive || topicId == chat.topicId) return
        now.outgoing?.let { outgoing ->
            cancelReply()
            update { it.copy(draft = it.draft.ifEmpty { outgoing.text }, outgoing = null) }
        }
        val aside = now.draft.trim()
        if (aside.isNotEmpty()) {
            update { it.copy(keptDraft = KeptDraft(chat.topicName, aside), draft = "", draftSupport = emptySet()) }
        }
        val token = ++switchToken
        update { it.copy(work = Work.Switching(topicId), actionFailure = null) }
        try {
            val state = repository.switchTopic(chat.id, makeRequestId(), uiLanguage(), topicId)
            if (token != switchToken) return
            apply(state)
            update { it.copy(work = null) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (token != switchToken) return
            update { it.copy(work = null, actionFailure = failure(e)) }
            refresh()
        }
    }

    /** 重試開場 — after a switch whose opening failed. The topic stays switched. */
    suspend fun retryOpening() {
        val chat = now.chat ?: return
        if (now.chatState?.needsOpening != true || now.work != null) return
        update { it.copy(work = Work.Opening, actionFailure = null) }
        try {
            apply(repository.retryOpening(chat.id, makeRequestId(), uiLanguage()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            update { it.copy(actionFailure = failure(e)) }
        } finally {
            update { it.copy(work = null) }
        }
    }

    // MARK: - 結束

    /** 結束並回顧 (or just 結束). The review is made only when the person answered. */
    suspend fun end(review: Boolean) {
        val chat = now.chat ?: return
        if (now.work != null) return
        cancelReply()
        update { it.copy(outgoing = null, work = Work.Ending, actionFailure = null) }
        try {
            apply(repository.end(chat.id, makeRequestId(), uiLanguage(), review))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            update { it.copy(actionFailure = failure(e)) }
        } finally {
            update { it.copy(work = null) }
        }
    }

    suspend fun loadHistory() {
        runCatching { repository.list(direction, uiLanguage()) }.getOrNull()?.let { list -> update { it.copy(history = list.chats) } }
    }

    suspend fun delete(chatId: String): Boolean = try {
        repository.delete(chatId)
        if (now.chat?.id == chatId) cancelReply()
        update { s ->
            s.copy(
                history = s.history.filter { it.id != chatId },
                chatState = s.chatState?.takeIf { it.chat.id != chatId },
                outgoing = if (s.chat?.id == chatId) null else s.outgoing,
            )
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        update { it.copy(actionFailure = failure(e)) }
        false
    }

    fun clearActionFailure() = update { it.copy(actionFailure = null) }

    /** Leaving the screen: the answer being written is not waited for; it is there to read on the way back. */
    fun leave() = cancelReply()

    private fun apply(state: TopicChatState) {
        update { it.copy(chatState = state) }
        syncTurnSupports()
        settleOutgoing()
    }

    internal companion object {
        fun failure(error: Throwable): Failure = when {
            error is ApiError.Transport -> Failure.Offline
            AssistantErrors.status(error) == 409 -> when (AssistantErrors.reason(error)) {
                "insufficient_credits" -> Failure.InsufficientPoints
                "chat_busy" -> Failure.Busy
                "message_outdated", "request_stale" -> Failure.Outdated
                "assist_limit" -> Failure.AssistLimit
                "chat_ended" -> Failure.Ended
                "topic_unavailable" -> Failure.TopicUnavailable
                else -> Failure.Failed
            }
            AssistantErrors.status(error) == 429 -> Failure.RateLimited
            AssistantErrors.status(error) == 403 -> Failure.Membership
            AssistantErrors.status(error) == 401 -> Failure.SignedOut
            AssistantErrors.status(error) == 404 -> Failure.Unavailable
            else -> Failure.Failed
        }
    }
}
