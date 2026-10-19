package app.tuji.android.assistant

import app.tuji.android.core.model.GrammarCandidate
import app.tuji.android.core.model.GrammarComparison
import app.tuji.android.core.model.GrammarDifficulty
import app.tuji.android.core.model.GrammarExplanation
import app.tuji.android.core.model.GrammarInput
import app.tuji.android.core.model.GrammarMode
import app.tuji.android.core.model.GrammarOffer
import app.tuji.android.core.model.GrammarPracticeType
import app.tuji.android.core.model.GrammarRecord
import app.tuji.android.core.model.VocabularyOffer
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.core.network.GrammarStudying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * One exchange on the AI 語法 screen: what was asked, and Moira's card once it
 * is back. The conversation is the screen's memory only; 語法紀錄 is what is kept.
 */
data class GrammarTurn(
    val input: GrammarInput,
    val record: GrammarRecord? = null,
    /** The server refused the request outright; nothing was charged. */
    val rejection: AssistantNote? = null,
) {
    val id: String get() = input.requestId

    val failureNote: AssistantNote?
        get() {
            rejection?.let { return it }
            if (record?.status != VocabularyRecord.Status.Failed) return null
            record.result?.message?.takeIf { it.isNotEmpty() }?.let { return AssistantNote.Server(it) }
            return AssistantNote.Known(
                if (record.error == "insufficient_credits") AssistantNote.Kind.InsufficientCredits else AssistantNote.Kind.NoContent,
            )
        }
}

/**
 * AI 語法 — iOS's `GrammarModel`: explain, compare and practise grammar with
 * Moira. Built per account and learning direction. Only [generate] and
 * [regenerate] spend points; a candidate or a next step only fills the draft.
 */
class GrammarModel(
    private val repository: GrammarStudying,
    private val offerCache: AssistantOfferCache<GrammarOffer>,
    private val historyCache: AssistantHistoryCache<GrammarRecord>,
    private val journal: GrammarJournaling,
    val progress: GrammarProgressStoring,
    val owner: String?,
    private val uiLanguage: () -> String,
    val learning: String,
    private val pollIntervalMillis: Long = 2_000,
) {
    data class Spotlight(val id: String, val tick: Int)

    data class HistoryFilter(val favorite: Boolean = false, val mode: GrammarMode? = null)

    sealed interface Regeneration {
        val maxPoints: Int
        data class Main(override val maxPoints: Int) : Regeneration
        data class Secondary(override val maxPoints: Int) : Regeneration
    }

    enum class ComparisonStep { ExplainA, ExplainB, Practice }

    data class State(
        val query: String = "",
        val context: String = "",
        val compareWith: String = "",
        /** A grammar point chosen from a clarification. */
        val focus: String = "",
        val difficulty: GrammarDifficulty = GrammarDifficulty.Basic,
        val practiceType: GrammarPracticeType = GrammarPracticeType.Mixed,
        val mode: GrammarMode = GrammarMode.Explain,
        /** The card a next step was drafted from. Cleared when the draft is rewritten. */
        val sourceRecordId: String? = null,
        val offer: GrammarOffer? = null,
        val offerCurrent: Boolean = false,
        val outOfPoints: Boolean = false,
        val turns: List<GrammarTurn> = emptyList(),
        val spotlight: Spotlight? = null,
        val history: List<GrammarRecord> = emptyList(),
        val nextCursor: String? = null,
        val pending: GrammarInput? = null,
        val busy: Boolean = false,
        val loadingHistory: Boolean = false,
        val message: AssistantNote? = null,
        val historyMessage: AssistantNote? = null,
        /** A next step, a candidate or 改一下 filled the draft: the page opens its fields and focuses the composer. */
        val draftTick: Int = 0,
    ) {
        fun price(mode: GrammarMode): GrammarOffer.Price? = offer?.modes?.firstOrNull { it.mode == mode }
        val price: GrammarOffer.Price? get() = price(mode)

        val blocker: VocabularyModel.Blocker?
            get() {
                val offer = offer ?: return null
                return when (offer.reason) {
                    VocabularyOffer.Reason.Disabled -> VocabularyModel.Blocker.Disabled
                    VocabularyOffer.Reason.Membership -> VocabularyModel.Blocker.Membership
                    null -> when {
                        !offer.enabled -> VocabularyModel.Blocker.Disabled
                        outOfPoints -> VocabularyModel.Blocker.InsufficientPoints
                        else -> null
                    }
                }
            }

        /** Input problems are said beside the field, not by a disabled button alone. */
        val tooLong: Boolean
            get() = query.length > QUERY_LIMIT || context.length > CONTEXT_LIMIT || compareWith.length > COMPARE_LIMIT
    }

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()
    private val now: State get() = mutable.value
    private fun update(change: (State) -> State) {
        mutable.value = change(mutable.value)
    }

    private var historyFilter = HistoryFilter()
    private var historyLoaded = false
    private var historyRequest = 0

    /** Typing over the draft makes it the person's own question, no longer the next step of an earlier card. */
    fun setQuery(value: String) = update { it.copy(query = value, focus = "", sourceRecordId = null, message = null) }
    fun setContext(value: String) = update { it.copy(context = value) }
    fun setCompareWith(value: String) = update { it.copy(compareWith = value) }
    fun setMode(value: GrammarMode) = update { it.copy(mode = value) }
    fun setDifficulty(value: GrammarDifficulty) = update { it.copy(difficulty = value) }
    fun setPracticeType(value: GrammarPracticeType) = update { it.copy(practiceType = value) }
    fun clearFocus() = update { it.copy(focus = "") }

    fun canGenerate(s: State = now): Boolean {
        val text = s.query.trim()
        return canSpend(s.mode, s) && text.isNotEmpty() && !s.tooLong && text.any { it.isLetterOrDigit() }
    }

    private fun canSpend(mode: GrammarMode, s: State = now): Boolean =
        !s.busy && s.pending == null && owner != null && s.offerCurrent && s.offer?.enabled == true && s.price(mode) != null

    /** Only the newest completed card offers another: 換一組題目 for a set, quietly for the others. */
    fun regeneration(turn: GrammarTurn, s: State = now): Regeneration? {
        if (turn.id != s.turns.lastOrNull()?.id || turn.record?.status != VocabularyRecord.Status.Completed) return null
        if (!canSpend(turn.input.mode, s)) return null
        val price = s.price(turn.input.mode) ?: return null
        return if (turn.input.mode == GrammarMode.Practice) Regeneration.Main(price.maxPoints) else Regeneration.Secondary(price.maxPoints)
    }

    suspend fun load() {
        val owner = owner ?: run {
            update { it.copy(message = AssistantNote.Known(AssistantNote.Kind.GrammarSignIn)) }
            return
        }
        offerCache.entry(owner)?.let { entry -> update { it.copy(offer = entry.offer, offerCurrent = entry.current) } }
        if (!now.offerCurrent) {
            update { it.copy(outOfPoints = false) }
            refreshOffer()
        }
        val input = journal.pending(owner).firstOrNull { it.learningDirection == learning } ?: return
        if (now.turns.none { it.id == input.requestId }) append(GrammarTurn(input))
        update { it.copy(pending = input) }
        reconcile()
    }

    private suspend fun refreshOffer() {
        val owner = owner ?: return
        try {
            val offer = offerCache.load(owner) { repository.offer() }
            update { it.copy(offer = offer, offerCurrent = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            update { it.copy(message = AssistantNote.Known(AssistantNote.Kind.PriceUnavailable)) }
        }
    }

    /** The only fresh paid request from the composer. */
    suspend fun generate() {
        if (!canGenerate()) return
        val s = now
        // Only what the mode reads goes out: a 比較對象 typed for 近似語法 does
        // not ride along on a 語法解釋 the person switched to.
        val draft = Draft(
            mode = s.mode,
            query = s.query.trim(),
            context = s.context.trim(),
            compareWith = if (s.mode != GrammarMode.Explain) s.compareWith.trim() else "",
            focus = s.focus,
            difficulty = if (s.mode == GrammarMode.Practice) s.difficulty else GrammarDifficulty.Basic,
            practiceType = if (s.mode == GrammarMode.Practice) s.practiceType else GrammarPracticeType.Mixed,
            sourceRecordId = s.sourceRecordId,
        )
        update { it.copy(query = "", context = "", compareWith = "", focus = "", sourceRecordId = null) }
        start(draft)
    }

    suspend fun regenerate(turn: GrammarTurn) {
        if (regeneration(turn) == null) return
        start(Draft.of(turn.input))
    }

    private data class Draft(
        val mode: GrammarMode,
        val query: String,
        val context: String,
        val compareWith: String,
        val focus: String,
        val difficulty: GrammarDifficulty,
        val practiceType: GrammarPracticeType,
        val sourceRecordId: String?,
    ) {
        companion object {
            fun of(input: GrammarInput) = Draft(
                input.mode, input.query, input.context, input.compareWith, input.focus,
                input.difficulty, input.practiceType, input.sourceRecordId,
            )
        }
    }

    private suspend fun start(draft: Draft) {
        val price = now.price(draft.mode) ?: return
        val offer = now.offer ?: return
        val owner = owner ?: return
        update { it.copy(outOfPoints = false) }
        val input = GrammarInput(
            requestId = UUID.randomUUID().toString().lowercase(),
            mode = draft.mode,
            query = draft.query,
            context = draft.context,
            compareWith = draft.compareWith,
            focus = draft.focus,
            difficulty = draft.difficulty,
            practiceType = draft.practiceType,
            sourceRecordId = draft.sourceRecordId,
            uiLanguage = uiLanguage(),
            learningDirection = learning,
            pricingVersion = offer.pricingVersion,
            maxPoints = price.maxPoints,
        )
        journal.save(input, owner)
        update { it.copy(pending = input) }
        append(GrammarTurn(input))
        send(input, owner)
    }

    private suspend fun send(input: GrammarInput, owner: String) {
        update { it.copy(busy = true, message = null) }
        try {
            val record = repository.generate(input)
            accept(record, owner)
            if (record.status == VocabularyRecord.Status.Pending) poll(input, owner)
            refreshOffer()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (AssistantErrors.definitelyRejected(e)) {
                journal.remove(input.requestId, owner)
                val note = AssistantErrors.failureMessage(e, AssistantNote.Kind.GrammarSignIn)
                update { s ->
                    s.copy(
                        pending = null,
                        turns = s.turns.map { if (it.id == input.requestId) it.copy(rejection = note) else it },
                        outOfPoints = s.outOfPoints ||
                            (AssistantErrors.status(e) == 409 && AssistantErrors.reason(e) == "insufficient_credits"),
                    )
                }
                refreshOffer()
            } else {
                // The answer may only be late: ask after the same request rather than starting another.
                poll(input, owner)
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
            if (AssistantErrors.status(e) == 404) {
                // The original POST may never have arrived; this is still the user's authorized request.
                update { it.copy(busy = false) }
                send(input, owner)
            } else {
                update { it.copy(message = AssistantNote.Known(AssistantNote.Kind.Confirming)) }
            }
        } finally {
            update { it.copy(busy = false) }
        }
    }

    private suspend fun poll(input: GrammarInput, owner: String) {
        repeat(80) {
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
        if (now.pending != null) update { it.copy(message = AssistantNote.Known(AssistantNote.Kind.Confirming)) }
    }

    private fun accept(record: GrammarRecord, owner: String) {
        if (record.status == VocabularyRecord.Status.Pending) return
        historyCache.invalidate(owner)
        journal.remove(record.id, owner)
        update { s ->
            s.copy(
                outOfPoints = s.outOfPoints ||
                    (record.status == VocabularyRecord.Status.Failed && record.error == "insufficient_credits"),
                pending = s.pending?.takeIf { it.requestId != record.id },
                turns = if (record.status == VocabularyRecord.Status.Deleted) {
                    s.turns.filter { it.id != record.id }
                } else {
                    s.turns.map { if (it.id == record.id) it.copy(record = record) else it }
                },
                message = null,
            )
        }
        if (record.status != VocabularyRecord.Status.Deleted && now.turns.any { it.id == record.id }) focus(record.id)
    }

    // MARK: - Drafts

    /** A clarification's grammar point goes into the draft; spending stays with 送出. */
    fun choose(candidate: GrammarCandidate, turn: GrammarTurn) {
        fill(Draft.of(turn.input).copy(focus = candidate.focus))
    }

    /** 改一下: what a turn asked goes back into the composer. */
    fun edit(turn: GrammarTurn) = fill(Draft.of(turn.input))

    /** 比較近似語法 / 練習這個語法 under an explanation. */
    fun next(mode: GrammarMode, record: GrammarRecord) {
        val input = record.input ?: return
        val value = record.result?.content as? GrammarExplanation ?: return
        fill(Draft(mode, value.summary.name, input.context, "", "", now.difficulty, now.practiceType, record.id))
    }

    /** 解釋 A / 解釋 B / 練習分辨 under a comparison. */
    fun follow(step: ComparisonStep, record: GrammarRecord) {
        val input = record.input ?: return
        val value = record.result?.content as? GrammarComparison ?: return
        val mode = if (step == ComparisonStep.Practice) GrammarMode.Practice else GrammarMode.Explain
        fill(
            Draft(
                mode = mode,
                query = if (step == ComparisonStep.ExplainB) value.b.name else value.a.name,
                context = input.context,
                compareWith = if (step == ComparisonStep.Practice) value.b.name else "",
                focus = "",
                difficulty = now.difficulty,
                practiceType = now.practiceType,
                sourceRecordId = record.id,
            ),
        )
    }

    private fun fill(draft: Draft) = update {
        it.copy(
            mode = draft.mode,
            query = draft.query,
            context = draft.context,
            compareWith = draft.compareWith,
            focus = draft.focus,
            difficulty = draft.difficulty,
            practiceType = draft.practiceType,
            sourceRecordId = draft.sourceRecordId,
            message = null,
            draftTick = it.draftTick + 1,
        )
    }

    /** A card from 語法紀錄 joins the conversation once and is brought into view. */
    fun open(record: GrammarRecord) {
        val input = record.input ?: return
        if (now.turns.any { it.id == record.id }) {
            update { s -> s.copy(turns = s.turns.map { if (it.id == record.id) it.copy(record = record) else it }) }
            focus(record.id)
        } else {
            append(GrammarTurn(input, record))
        }
        update { it.copy(message = null) }
    }

    // MARK: - 語法紀錄

    suspend fun loadHistory(filter: HistoryFilter, more: Boolean = false, force: Boolean = false) {
        if (more && now.loadingHistory) return
        if (!more && !force && owner != null) {
            historyCache.page(owner, filterKey(filter))?.let { kept ->
                historyRequest++
                historyFilter = filter
                historyLoaded = true
                update { it.copy(loadingHistory = false, historyMessage = null, history = kept.records, nextCursor = kept.nextCursor) }
                return
            }
        }
        val request = ++historyRequest
        update { it.copy(loadingHistory = true, historyMessage = null) }
        try {
            val list = repository.records(learning, filter.favorite, filter.mode, if (more) now.nextCursor else null)
            if (request != historyRequest) return
            historyFilter = filter
            historyLoaded = true
            update { it.copy(history = if (more) it.history + list.records else list.records, nextCursor = list.nextCursor) }
            keepHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            if (request == historyRequest) {
                update { it.copy(historyMessage = AssistantNote.Known(AssistantNote.Kind.GrammarHistoryFailed)) }
            }
        } finally {
            if (request == historyRequest) update { it.copy(loadingHistory = false) }
        }
    }

    suspend fun toggleFavorite(record: GrammarRecord) {
        try {
            val updated = repository.favorite(record.id, !record.favorite)
            update { s ->
                s.copy(
                    turns = s.turns.map { if (it.id == updated.id) it.copy(record = updated) else it },
                    history = s.history.mapNotNull { row ->
                        if (row.id != updated.id) row else if (historyFilter.favorite && !updated.favorite) null else updated
                    },
                )
            }
            owner?.let { historyCache.invalidate(it) }
            keepHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            val note = AssistantNote.Known(AssistantNote.Kind.BookmarkFailed)
            update { it.copy(message = note, historyMessage = note) }
        }
    }

    /** Deleting a set also forgets how far this device got through it. */
    suspend fun delete(record: GrammarRecord) {
        try {
            repository.delete(record.id)
            owner?.let { progress.remove(it, learning, record.id) }
            update { s -> s.copy(history = s.history.filter { it.id != record.id }, turns = s.turns.filter { it.id != record.id }) }
            owner?.let { historyCache.invalidate(it) }
            keepHistory()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            update { it.copy(historyMessage = AssistantNote.Known(AssistantNote.Kind.DeleteFailed)) }
        }
    }

    private fun filterKey(filter: HistoryFilter) = "$learning|${filter.favorite}|${filter.mode?.name.orEmpty()}"

    private fun keepHistory() {
        val owner = owner ?: return
        if (!historyLoaded) return
        historyCache.store(AssistantHistoryCache.Page(now.history, now.nextCursor), owner, filterKey(historyFilter))
    }

    private fun append(turn: GrammarTurn) {
        update { it.copy(turns = it.turns + turn) }
        focus(turn.id)
    }

    private fun focus(id: String) = update { it.copy(spotlight = Spotlight(id, (it.spotlight?.tick ?: 0) + 1)) }

    companion object {
        const val QUERY_LIMIT = 800
        const val CONTEXT_LIMIT = 800
        const val COMPARE_LIMIT = 120
    }
}
