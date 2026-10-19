package app.tuji.android.assistant

import app.tuji.android.core.model.VocabularyCandidate
import app.tuji.android.core.model.VocabularyInput
import app.tuji.android.core.model.VocabularyMode
import app.tuji.android.core.model.VocabularyOffer
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.core.model.Word
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.TujiJson
import app.tuji.android.core.network.VocabularyStudying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/** A sentence the page can say about a request, before it is put into words. */
sealed interface VocabularyNote {
    /** The server's own sentence, already in the interface language. */
    data class Server(val text: String) : VocabularyNote
    data class Known(val kind: Kind) : VocabularyNote

    enum class Kind {
        InsufficientCredits, NoContent, SignIn, PriceUnavailable, Confirming, HistoryFailed, BookmarkFailed,
        DeleteFailed, PriceChanged, RateLimited, Unavailable,
    }
}

/**
 * One exchange on the AI 查詞 screen: what was asked, and Moira's card once it
 * is back. The conversation is the screen's memory only; 查詞紀錄 is what is kept.
 */
data class VocabularyTurn(
    val input: VocabularyInput,
    val record: VocabularyRecord? = null,
    /** The server refused the request outright; nothing was charged. */
    val rejection: VocabularyNote? = null,
) {
    val id: String get() = input.requestId

    val failureNote: VocabularyNote?
        get() {
            rejection?.let { return it }
            if (record?.status != VocabularyRecord.Status.Failed) return null
            record.result?.message?.takeIf { it.isNotEmpty() }?.let { return VocabularyNote.Server(it) }
            return VocabularyNote.Known(
                if (record.error == "insufficient_credits") VocabularyNote.Kind.InsufficientCredits else VocabularyNote.Kind.NoContent,
            )
        }
}

/**
 * AI 查詞 — iOS's `VocabularyModel`.
 *
 * Built per account and learning direction (the page remembers it under that
 * pair), so a switch starts a new conversation rather than carrying one across.
 * Only [generate] and [regenerate] spend points; a candidate or a next step
 * only fills the draft.
 */
class VocabularyModel(
    private val repository: VocabularyStudying,
    private val offerCache: AssistantOfferCache<VocabularyOffer>,
    private val historyCache: AssistantHistoryCache<VocabularyRecord>,
    private val journal: VocabularyJournaling,
    private val owner: String?,
    /** The interface language's wire code, read at each send. */
    private val uiLanguage: () -> String,
    val learning: String,
    private val catalog: () -> List<Word>,
    query: String = "",
    private val pollIntervalMillis: Long = 2_000,
) {
    data class Spotlight(val id: String, val tick: Int)

    /** Another card for the newest turn: 助記 offers it as the main action, the others quietly. */
    sealed interface Regeneration {
        val maxPoints: Int
        data class Main(override val maxPoints: Int) : Regeneration
        data class Secondary(override val maxPoints: Int) : Regeneration
    }

    /** Why 送出 cannot be pressed for a reason the person can act on. */
    enum class Blocker { Membership, Disabled, InsufficientPoints }

    data class State(
        val query: String = "",
        val context: String = "",
        val selectedSense: String = "",
        val mode: VocabularyMode = VocabularyMode.Lookup,
        val offer: VocabularyOffer? = null,
        /** Read from the server since launch, so its balance can decide spending. */
        val offerCurrent: Boolean = false,
        /** The server said the points are short. */
        val outOfPoints: Boolean = false,
        val turns: List<VocabularyTurn> = emptyList(),
        val spotlight: Spotlight? = null,
        val history: List<VocabularyRecord> = emptyList(),
        val nextCursor: String? = null,
        val pending: VocabularyInput? = null,
        val busy: Boolean = false,
        val loadingHistory: Boolean = false,
        val message: VocabularyNote? = null,
        val historyMessage: VocabularyNote? = null,
    ) {
        fun price(mode: VocabularyMode): VocabularyOffer.Price? = offer?.modes?.firstOrNull { it.mode == mode }
        val price: VocabularyOffer.Price? get() = price(mode)

        val blocker: Blocker?
            get() {
                val offer = offer ?: return null
                return when (offer.reason) {
                    VocabularyOffer.Reason.Disabled -> Blocker.Disabled
                    VocabularyOffer.Reason.Membership -> Blocker.Membership
                    null -> if (!offer.enabled) Blocker.Disabled else if (outOfPoints) Blocker.InsufficientPoints else null
                }
            }
    }

    private val mutable = MutableStateFlow(State(query = query))
    val state: StateFlow<State> = mutable.asStateFlow()

    private var historyFavorites = false
    private var historyLoaded = false
    private var historyRequest = 0

    private fun update(change: (State) -> State) {
        mutable.value = change(mutable.value)
    }

    private val now: State get() = mutable.value

    fun setQuery(value: String) = update { it.copy(query = value, selectedSense = "", message = null) }
    fun setContext(value: String) = update { it.copy(context = value) }
    fun setMode(value: VocabularyMode) = update { it.copy(mode = value) }
    fun clearSense() = update { it.copy(selectedSense = "") }

    /** Official 詞條 whose word or Chinese meaning is exactly what was typed — the free path. */
    fun catalogMatches(query: String = now.query): List<Word> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return catalog()
            .filter { it.word.lowercase() == needle || it.chinese?.lowercase() == needle }
            .distinctBy { it.id }
    }

    fun canGenerate(s: State = now): Boolean {
        val word = s.query.trim()
        return canSpend(s.mode, s) && word.isNotEmpty() && word.length <= 120 &&
            word.any { it.isLetterOrDigit() } && s.context.length <= 800 && s.selectedSense.length <= 300
    }

    private fun canSpend(mode: VocabularyMode, s: State = now): Boolean =
        !s.busy && s.pending == null && owner != null && s.offerCurrent && s.offer?.enabled == true && s.price(mode) != null

    /** Only the newest completed card offers another, and only when it can be paid for. */
    fun regeneration(turn: VocabularyTurn, s: State = now): Regeneration? {
        if (turn.id != s.turns.lastOrNull()?.id || turn.record?.status != VocabularyRecord.Status.Completed) return null
        if (!canSpend(turn.input.mode, s)) return null
        val price = s.price(turn.input.mode) ?: return null
        return if (turn.input.mode == VocabularyMode.Mnemonic) Regeneration.Main(price.maxPoints) else Regeneration.Secondary(price.maxPoints)
    }

    suspend fun load() {
        val owner = owner ?: run {
            update { it.copy(message = VocabularyNote.Known(VocabularyNote.Kind.SignIn)) }
            return
        }
        // Entering does not read the balance again when this launch already has it.
        offerCache.entry(owner)?.let { entry -> update { it.copy(offer = entry.offer, offerCurrent = entry.current) } }
        if (!now.offerCurrent) {
            // A first read, or one after a purchase: a shortfall said before is over.
            update { it.copy(outOfPoints = false) }
            refreshOffer()
        }
        val input = journal.pending(owner).firstOrNull { it.learningDirection == learning } ?: return
        if (now.turns.none { it.id == input.requestId }) append(VocabularyTurn(input))
        update { it.copy(pending = input) }
        reconcile()
    }

    /** Also how the balance comes back after a card or a refusal, since neither carries it. */
    private suspend fun refreshOffer() {
        val owner = owner ?: return
        try {
            val offer = offerCache.load(owner) { repository.offer() }
            update { it.copy(offer = offer, offerCurrent = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            update { it.copy(message = VocabularyNote.Known(VocabularyNote.Kind.PriceUnavailable)) }
        }
    }

    /** The only fresh paid request from the composer. */
    suspend fun generate() {
        if (!canGenerate()) return
        val s = now
        val query = s.query.trim()
        val context = s.context.trim()
        val sense = s.selectedSense
        update { it.copy(query = "", context = "", selectedSense = "") }
        start(s.mode, query, context, sense)
    }

    /** Another card for what the newest turn asked; a new request, charged again. */
    suspend fun regenerate(turn: VocabularyTurn) {
        if (regeneration(turn) == null) return
        start(turn.input.mode, turn.input.query, turn.input.context, turn.input.selectedSense)
    }

    private suspend fun start(mode: VocabularyMode, query: String, context: String, sense: String) {
        val price = now.price(mode) ?: return
        val offer = now.offer ?: return
        val owner = owner ?: return
        update { it.copy(outOfPoints = false) }
        val input = VocabularyInput(
            requestId = UUID.randomUUID().toString().lowercase(),
            mode = mode,
            query = query,
            context = context,
            selectedSense = sense,
            uiLanguage = uiLanguage(),
            learningDirection = learning,
            pricingVersion = offer.pricingVersion,
            maxPoints = price.maxPoints,
        )
        journal.save(input, owner)
        update { it.copy(pending = input) }
        append(VocabularyTurn(input))
        send(input, owner)
    }

    private suspend fun send(input: VocabularyInput, owner: String) {
        update { it.copy(busy = true, message = null) }
        try {
            val record = repository.generate(input)
            accept(record, owner)
            if (record.status == VocabularyRecord.Status.Pending) poll(input, owner)
            refreshOffer()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (definitelyRejected(e)) {
                journal.remove(input.requestId, owner)
                update { s ->
                    s.copy(
                        pending = null,
                        turns = s.turns.map { if (it.id == input.requestId) it.copy(rejection = failureMessage(e)) else it },
                        outOfPoints = s.outOfPoints || (status(e) == 409 && reason(e) == "insufficient_credits"),
                    )
                }
                refreshOffer()
            } else {
                update { it.copy(message = VocabularyNote.Known(VocabularyNote.Kind.Confirming)) }
            }
        } finally {
            update { it.copy(busy = false) }
        }
    }

    suspend fun reconcile() {
        val input = now.pending ?: return
        val owner = owner ?: return
        if (now.busy) return
        update { it.copy(busy = true) }
        try {
            val record = repository.record(input.requestId)
            accept(record, owner)
            if (record.status == VocabularyRecord.Status.Pending) poll(input, owner)
            refreshOffer()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (status(e) == 404) {
                // The original POST may never have arrived; this is still the user's authorized request.
                update { it.copy(busy = false) }
                send(input, owner)
            } else {
                update { it.copy(message = VocabularyNote.Known(VocabularyNote.Kind.Confirming)) }
            }
        } finally {
            update { it.copy(busy = false) }
        }
    }

    private suspend fun poll(input: VocabularyInput, owner: String) {
        repeat(65) {
            try {
                delay(pollIntervalMillis)
                val record = repository.record(input.requestId)
                accept(record, owner)
                if (record.status != VocabularyRecord.Status.Pending) return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return@repeat
            }
        }
        if (now.pending != null) update { it.copy(message = VocabularyNote.Known(VocabularyNote.Kind.Confirming)) }
    }

    private fun accept(record: VocabularyRecord, owner: String) {
        if (record.status == VocabularyRecord.Status.Pending) return
        historyCache.invalidate(owner)
        journal.remove(record.id, owner)
        update { s ->
            val turns = when {
                record.status == VocabularyRecord.Status.Deleted -> s.turns.filter { it.id != record.id }
                else -> s.turns.map { if (it.id == record.id) it.copy(record = record) else it }
            }
            s.copy(
                outOfPoints = s.outOfPoints ||
                    (record.status == VocabularyRecord.Status.Failed && record.error == "insufficient_credits"),
                pending = s.pending?.takeIf { it.requestId != record.id },
                turns = turns,
                message = null,
            )
        }
        // The card is far taller than the wait it replaces: bring its turn up again.
        if (record.status != VocabularyRecord.Status.Deleted && now.turns.any { it.id == record.id }) focus(record.id)
    }

    /** Puts a candidate into the composer. Spending stays with 送出. */
    fun choose(candidate: VocabularyCandidate, mode: VocabularyMode) =
        update { it.copy(query = candidate.term, selectedSense = candidate.meaning, mode = mode, message = null) }

    /** 改一下: what a turn asked goes back into the composer. */
    fun edit(turn: VocabularyTurn) = update {
        it.copy(
            query = turn.input.query,
            context = turn.input.context,
            selectedSense = turn.input.selectedSense,
            mode = turn.input.mode,
            message = null,
        )
    }

    /** A card from 查詞紀錄 joins the conversation once and is brought into view. */
    fun open(record: VocabularyRecord) {
        val input = record.input ?: return
        if (now.turns.any { it.id == record.id }) {
            update { s -> s.copy(turns = s.turns.map { if (it.id == record.id) it.copy(record = record) else it }) }
            focus(record.id)
        } else {
            append(VocabularyTurn(input, record))
        }
        update { it.copy(message = null) }
    }

    /** Opening 查詞紀錄 again shows the list this launch already read; [force] reads it anyway. */
    suspend fun loadHistory(favorite: Boolean, more: Boolean = false, force: Boolean = false) {
        if (more && now.loadingHistory) return
        val filter = "$learning|$favorite"
        if (!more && !force && owner != null) {
            historyCache.page(owner, filter)?.let { kept ->
                historyRequest++
                historyFavorites = favorite
                historyLoaded = true
                update { it.copy(loadingHistory = false, historyMessage = null, history = kept.records, nextCursor = kept.nextCursor) }
                return
            }
        }
        val request = ++historyRequest
        update { it.copy(loadingHistory = true, historyMessage = null) }
        try {
            val list = repository.records(learning, favorite, if (more) now.nextCursor else null)
            if (request != historyRequest) return
            historyFavorites = favorite
            historyLoaded = true
            update { it.copy(history = if (more) it.history + list.records else list.records, nextCursor = list.nextCursor) }
            keepHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            if (request == historyRequest) {
                update { it.copy(historyMessage = VocabularyNote.Known(VocabularyNote.Kind.HistoryFailed)) }
            }
        } finally {
            if (request == historyRequest) update { it.copy(loadingHistory = false) }
        }
    }

    suspend fun toggleFavorite(record: VocabularyRecord) {
        try {
            val updated = repository.favorite(record.id, !record.favorite)
            update { s ->
                s.copy(
                    turns = s.turns.map { if (it.id == updated.id) it.copy(record = updated) else it },
                    history = s.history.mapNotNull { row ->
                        if (row.id != updated.id) row else if (historyFavorites && !updated.favorite) null else updated
                    },
                )
            }
            // The other filter's list holds this row too: only the open one is right.
            owner?.let { historyCache.invalidate(it) }
            keepHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            val note = VocabularyNote.Known(VocabularyNote.Kind.BookmarkFailed)
            update { it.copy(message = note, historyMessage = note) }
        }
    }

    suspend fun delete(record: VocabularyRecord) {
        try {
            repository.delete(record.id)
            update { s -> s.copy(history = s.history.filter { it.id != record.id }, turns = s.turns.filter { it.id != record.id }) }
            owner?.let { historyCache.invalidate(it) }
            keepHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            update { it.copy(historyMessage = VocabularyNote.Known(VocabularyNote.Kind.DeleteFailed)) }
        }
    }

    /** The list on screen, as the kept one for its filter. */
    private fun keepHistory() {
        val owner = owner ?: return
        if (!historyLoaded) return
        historyCache.store(AssistantHistoryCache.Page(now.history, now.nextCursor), owner, "$learning|$historyFavorites")
    }

    private fun append(turn: VocabularyTurn) {
        update { it.copy(turns = it.turns + turn) }
        focus(turn.id)
    }

    private fun focus(id: String) = update { it.copy(spotlight = Spotlight(id, (it.spotlight?.tick ?: 0) + 1)) }

    internal companion object {
        fun status(error: Throwable): Int? = when (error) {
            is ApiError.Http -> error.status
            ApiError.NotAuthenticated -> 401
            else -> null
        }

        fun reason(error: Throwable): String? = (error as? ApiError.Http)?.body?.let { body ->
            runCatching { TujiJson.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        }

        /** The server certainly did not take the request: nothing was charged and nothing is pending. */
        fun definitelyRejected(error: Throwable): Boolean = when (status(error)) {
            401, 403, 409, 400, 413 -> true
            429 -> reason(error) != "save_limit"
            else -> false
        }

        fun failureMessage(error: Throwable): VocabularyNote.Known = VocabularyNote.Known(
            when {
                status(error) == 409 && reason(error) == "price_changed" -> VocabularyNote.Kind.PriceChanged
                status(error) == 409 && reason(error) == "insufficient_credits" -> VocabularyNote.Kind.InsufficientCredits
                status(error) == 429 -> VocabularyNote.Kind.RateLimited
                status(error) == 401 -> VocabularyNote.Kind.SignIn
                else -> VocabularyNote.Kind.Unavailable
            },
        )
    }
}
