package app.tuji.android.assistant

import app.tuji.android.core.model.Story
import app.tuji.android.core.model.StoryEvent
import app.tuji.android.core.model.StoryInterrupted
import app.tuji.android.core.model.StoryOffer
import app.tuji.android.core.model.StoryPayload
import app.tuji.android.core.model.StoryResult
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.StoryWriting
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 情境故事 — what the person has picked so far, and the one paid step at the
 * end. iOS's `StoryBuilderModel`.
 *
 * The order on screen is the order of the decision: 單字 → 場地 → 情景 → 難度 →
 * 生成. A situation can be picked before a place; a place that lacks the picked
 * one clears it. A story is written only when the person taps the button that
 * says what it costs.
 *
 * The story arrives in pieces: the page is the story's from the tap, [State.story]
 * fills in once its title and intro exist, and [State.writing] stays
 * [Writing.InProgress] while the segments come in. A failure leaves what arrived on
 * the page — or, when nothing did, the choices with the reason — charged nothing.
 */
class StoryBuilderModel(
    private val repository: StoryWriting,
    private val offerCache: StoryOfferCache,
    /** Items that can go into a story: the dictionary, the person's own cards, and the cards taken in. */
    private val candidates: () -> List<Word>,
    private val uiLanguage: () -> String,
    private val learning: String,
    private val language: TargetLanguage,
    private val makeRequestId: () -> String = { UUID.randomUUID().toString().lowercase() },
) {
    /** Why the last attempt did not produce a story. Every case charged nothing. */
    enum class Failure { InsufficientPoints, Busy, Offline, Unavailable, ItemsChanged, Failed }

    sealed interface Writing {
        data object Idle : Writing
        data object InProgress : Writing
        data class Failed(val failure: Failure) : Writing
    }

    enum class OfferPhase { Loading, Loaded, Failed }

    data class State(
        val offerPhase: OfferPhase = OfferPhase.Loading,
        val offer: StoryOffer? = null,
        /** Read for this account since launch: a remembered one shows the choices but cannot pay. */
        val balanceKnown: Boolean = false,
        val picked: List<Word> = emptyList(),
        val sceneId: String? = null,
        val situationId: String? = null,
        val levelId: String? = null,
        val writing: Writing = Writing.Idle,
        val story: Story? = null,
        /** The language the story's spoken lines are in, for their voice. */
        val storyLanguage: TargetLanguage = TargetLanguage.EN,
    ) {
        val canPickMore: Boolean get() = picked.size < MAX_ITEMS
        fun isPicked(word: Word): Boolean = picked.any { it.id == word.id }
        val scene: StoryOffer.Scene? get() = offer?.scenes?.firstOrNull { it.id == sceneId }

        /** What can happen at the chosen place, or before one is chosen every situation any place offers. */
        val situations: List<StoryOffer.Situation>
            get() = scene?.situations ?: offer?.scenes.orEmpty().flatMap { it.situations }.distinctBy { it.id }

        val isComplete: Boolean get() = picked.isNotEmpty() && sceneId != null && situationId != null && levelId != null
        val offerReady: Boolean get() = balanceKnown && offer != null
        val canWrite: Boolean get() = isComplete && offerReady && writing != Writing.InProgress
        val reading: Boolean get() = story != null || writing == Writing.InProgress
    }

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    private val now get() = mutable.value
    private fun update(change: (State) -> State) {
        mutable.value = change(mutable.value)
    }

    /** One id per set of choices, reused when the same choices are sent again. */
    private var pending: Pair<StoryPayload, String>? = null
    private var writeJob: Job? = null

    val candidateWords: List<Word> get() = candidates()

    /** Shows the kept offer at once, when there is one. */
    suspend fun loadOffer() {
        val language = uiLanguage()
        offerCache.entry(language)?.let { entry ->
            update { it.copy(offer = entry.offer, balanceKnown = entry.current, offerPhase = OfferPhase.Loaded) }
            if (entry.current) return
        }
        try {
            val offer = offerCache.load(language) { repository.storyOffer(language) }
            update { it.copy(offer = offer, balanceKnown = true, offerPhase = OfferPhase.Loaded) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Choices without a balance cannot be acted on: say it failed.
            update { it.copy(offerPhase = if (it.balanceKnown) OfferPhase.Loaded else OfferPhase.Failed) }
        }
    }

    fun toggle(word: Word) {
        update { s ->
            when {
                s.isPicked(word) -> s.copy(picked = s.picked.filter { it.id != word.id })
                s.canPickMore -> s.copy(picked = s.picked + word)
                else -> s
            }
        }
        choicesChanged()
    }

    fun chooseScene(id: String) {
        if (id == now.sceneId) return
        update { s ->
            val next = s.copy(sceneId = id)
            if (next.situationId != null && next.situations.none { it.id == next.situationId }) next.copy(situationId = null) else next
        }
        choicesChanged()
    }

    fun chooseSituation(id: String) {
        if (now.situations.none { it.id == id }) return
        update { it.copy(situationId = id) }
        choicesChanged()
    }

    fun chooseLevel(id: String) {
        update { it.copy(levelId = id) }
        choicesChanged()
    }

    /** The button's way in: a write the page can stop by leaving. */
    fun start(scope: CoroutineScope) {
        if (!now.canWrite) return
        writeJob = scope.launch { write() }
    }

    suspend fun write() {
        if (!now.canWrite) return
        val payload = payload() ?: return
        update { it.copy(writing = Writing.InProgress) }
        try {
            var finished = false
            repository.writeStory(payload).collect { event ->
                when (event) {
                    is StoryEvent.Opening -> update {
                        it.copy(storyLanguage = language, story = Story(event.title, event.intro))
                    }
                    is StoryEvent.Segment -> update { s ->
                        s.copy(story = s.story?.let { it.copy(segments = it.segments + event.segment) })
                    }
                    is StoryEvent.Done -> {
                        finish(event.result)
                        finished = true
                    }
                }
            }
            if (finished) return
            // The answer ended without the whole story.
            update { it.copy(writing = Writing.Failed(Failure.Failed)) }
            refreshBalance()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            update { it.copy(writing = Writing.Failed(failure(e))) }
            refreshBalance()
        }
    }

    /** A failure does not say what is left, and some are charged. */
    private suspend fun refreshBalance() {
        val language = uiLanguage()
        val offer = runCatching { offerCache.load(language) { repository.storyOffer(language) } }.getOrNull() ?: return
        update { it.copy(offer = offer, balanceKnown = true) }
    }

    private fun finish(result: StoryResult) {
        pending = null
        val offer = now.offer?.copy(minPoints = result.minPoints, maxPoints = result.maxPoints, available = result.available)
        update { it.copy(story = result.story, writing = Writing.Idle, offer = offer, balanceKnown = it.balanceKnown || offer != null) }
        offer?.let { offerCache.store(it, uiLanguage()) }
    }

    /** Back to the choices, kept as they were, for another story. */
    fun closeStory() {
        stop()
        update { it.copy(story = null) }
    }

    /**
     * Leaving the page: drop a write in flight. The server finishes and charges
     * it as it would a lost answer, so the kept balance is behind and the next
     * tap is a new request.
     */
    fun stop() {
        val job = writeJob ?: return
        job.cancel()
        writeJob = null
        if (now.writing == Writing.InProgress) {
            update { it.copy(writing = Writing.Idle) }
            pending = null
            offerCache.balanceChanged()
        }
    }

    private fun payload(): StoryPayload? {
        val s = now
        val base = StoryPayload(
            requestId = "",
            uiLanguage = uiLanguage(),
            learningDirection = learning,
            wordIds = s.picked.map { it.id },
            sceneId = s.sceneId ?: return null,
            situationId = s.situationId ?: return null,
            levelId = s.levelId ?: return null,
        )
        val requestId = pending?.takeIf { it.first == base }?.second ?: makeRequestId().also { pending = base to it }
        return base.copy(requestId = requestId)
    }

    /** A new set of choices is a new request; an old failure no longer applies. */
    private fun choicesChanged() {
        if (now.writing is Writing.Failed) update { it.copy(writing = Writing.Idle) }
    }

    internal fun failure(error: Throwable): Failure = when {
        error is StoryInterrupted -> if (error.code == "rate_limited") Failure.Busy else Failure.Failed
        error is ApiError.Transport -> Failure.Offline
        else -> when (AssistantErrors.status(error)) {
            409 -> if (AssistantErrors.reason(error) == "insufficient_credits") Failure.InsufficientPoints else Failure.Failed
            429 -> Failure.Busy
            401, 403, 404 -> Failure.Unavailable
            400 -> Failure.ItemsChanged
            else -> Failure.Failed
        }
    }

    companion object {
        /** The server's limit. */
        const val MAX_ITEMS = 3
    }
}
