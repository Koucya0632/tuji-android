package app.tuji.android.credits

import android.content.SharedPreferences
import android.util.Log
import app.tuji.android.core.model.*
import app.tuji.android.core.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

/**
 * 點數版拍照新增 — iOS's `CreditCaptureModel`: 選模式 → 拍照/裁切（只留在本機）→
 * 開始識別（上傳、報價、扣點一次做完）→ 選候選建卡。普通識別完成後，同一張照片
 * 可補差價升級成高精度；價格由伺服器決定，報價和畫面上的價格不符就停下來讓使用者確認。
 *
 * Also the 罐頭點數 wallet screen's model, which only reads the wallet and claims.
 */
class CreditCaptureModel(
    private val repository: CreditRepository,
    private val atlas: AtlasAuthoring,
    private val owner: String,
    private val currentOwner: () -> String?,
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
    /** 生成佇列's 罐頭點數 entry. 確認並生成卡片 hands the rest of the work here. */
    private val enqueue: (CreditConfirmRequest, imageId: String, thumbUrl: String?) -> Unit = { _, _, _ -> },
    /** Operations 生成佇列 is already confirming, which must not reopen as results to pick from. */
    private val queuedOperations: () -> Set<String> = { emptySet() },
) {
    enum class Mode(val feature: String) {
        Primary("atlas.recognize.primary"),
        Precision("atlas.recognize.precision"),
    }

    /** The second field on the correction form — the one that carries the meaning. */
    enum class SecondField { ChineseName, Gloss, Hidden }

    data class Suggestion(val lemma: String, val zhHant: String, val gloss: String)

    data class State(
        val mode: Mode = Mode.Primary,
        /** The cropped photo, still on the phone. Nothing is uploaded until 開始識別. */
        val photo: ByteArray? = null,
        val image: AtlasImageSummary? = null,
        val wallet: CreditWallet? = null,
        val catalog: CreditCatalog? = null,
        /** Only set when the server's price differs from the one on screen. */
        val quote: CreditQuote? = null,
        val operation: CreditOperation? = null,
        val history: List<CreditOperation> = emptyList(),
        val pending: PendingCreditAcceptance? = null,
        val busy: Boolean = false,
        /** An error code; [creditErrorResource] turns it into a sentence. */
        val error: String? = null,
        val selectedCandidateId: String? = null,
        val lemma: String = "",
        val displayZhHant: String = "",
        /** The ja/en meaning a cross-language capture edits; empty otherwise. */
        val displayGloss: String = "",
        /** What the chosen candidate put in each field, so the form can mark the AI's guess. */
        val suggestion: Suggestion? = null,
    ) {
        val recognitionPrice: Int? get() = catalog?.policy?.recognition
        val precisionPrice: Int? get() = catalog?.policy?.precision
        val upgradePrice: Int? get() = catalog?.policy?.precisionUpgrade
        val price: Int? get() = if (mode == Mode.Primary) recognitionPrice else precisionPrice
        val operationsEnabled: Boolean get() = catalog?.operationsEnabled == true && wallet?.reconciliationRequired != true

        fun affordable(points: Int?): Boolean = points != null && (wallet?.available ?: 0) >= points

        /** Both runs on this photo, so 普通／高精度 can be switched without paying again. */
        fun run(mode: Mode): CreditOperation? {
            val imageId = operation?.imageId ?: return null
            return (listOfNotNull(operation) + history).firstOrNull {
                it.imageId == imageId && it.feature == mode.feature && it.state != "released"
            }
        }

        val shownMode: Mode? get() = operation?.let { if (it.feature == Mode.Precision.feature) Mode.Precision else Mode.Primary }

        val canUpgrade: Boolean
            get() = shownMode == Mode.Primary && operation?.state == "committed" && run(Mode.Precision) == null &&
                operation.confirmedItemId == null

        val canConfirm: Boolean
            get() = !busy && selectedCandidateId != null && lemma.isNotBlank() && displayZhHant.isNotBlank()

        /**
         * Upload → quote → accept under way, for a new photo or a 高精度 upgrade
         * of the result on screen. Not a relaunch's leftover `pending` with
         * nothing to show, which still needs its 重試同步.
         */
        val recognitionInFlight: Boolean
            get() = busy && if (operation == null) photo != null else pending != null || quote != null

        /** 1–5 on the step indicator, zero-based. */
        val step: Int
            get() = when {
                operation?.state == "committed" -> 3
                operation?.isRunning == true -> 2
                photo != null -> 1
                else -> 0
            }

        fun isStillSuggested(field: SuggestedField): Boolean {
            val s = suggestion ?: return false
            return when (field) {
                SuggestedField.Lemma -> s.lemma.isNotEmpty() && s.lemma == lemma
                SuggestedField.ZhHant -> s.zhHant.isNotEmpty() && s.zhHant == displayZhHant
                SuggestedField.Gloss -> s.gloss.isNotEmpty() && s.gloss == displayGloss
            }
        }

        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    enum class SuggestedField { Lemma, ZhHant, Gloss }

    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()

    /** Consecutive failed polls; one dropped request mid-run is not worth a banner. */
    private var pollFailures = 0

    private fun current() = currentOwner() == owner
    private fun journal(): String? = mutable.value.catalog?.environment?.let { "credit-accept:${owner.lowercase()}:$it" }
    private fun update(mutate: (State) -> State) { if (current()) mutable.value = mutate(mutable.value) }
    private fun apply(wallet: CreditWallet) {
        if (!current()) return
        if (mutable.value.wallet?.let { wallet.isNewerThan(it) } != false) mutable.value = mutable.value.copy(wallet = wallet)
    }
    private fun errorCode(error: Throwable): String =
        (error as? ApiError.Http)?.body?.let {
            runCatching { TujiJson.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        } ?: "credits_unavailable"

    private suspend fun act(block: suspend () -> Unit) {
        if (mutable.value.busy || !current()) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { update { it.copy(error = errorCode(e)) } }
        finally { update { it.copy(busy = false) } }
    }

    private fun launch(block: suspend () -> Unit) { scope.launch { block() } }

    // MARK: Lifecycle

    fun load() = launch {
        act {
            val catalog = repository.catalog()
            if (!current()) return@act
            mutable.value = mutable.value.copy(catalog = catalog)
            journal()?.let { key -> preferences.getString(key, null)?.let { saved ->
                val pending = runCatching { TujiJson.decodeFromString<PendingCreditAcceptance>(saved) }.getOrNull()
                update { it.copy(pending = pending) }
            } }
            apply(repository.wallet())
            val history = repository.history()
            if (!current()) return@act
            update { it.copy(history = history) }
            if (mutable.value.operation == null && mutable.value.photo == null) {
                val resumed = resumable(history, queuedOperations())
                update { it.copy(operation = resumed) }
                val imageId = resumed?.imageId ?: return@act
                // The crop never left the phone's last session; show the upload instead.
                val image = runCatching { repository.image(imageId) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
                if (mutable.value.operation?.imageId == imageId) update { it.copy(image = image) }
            }
        }
    }

    fun claim(monthly: Boolean) = launch { act { apply(repository.claim(monthly)) } }

    /** 換一張: back to the source chooser. Results already paid for stay in history. */
    fun reset() {
        pollFailures = 0
        update { it.copy(photo = null, image = null, operation = null, quote = null, error = null).cleared() }
    }

    fun setPhoto(bytes: ByteArray) {
        reset()
        update { it.copy(photo = bytes) }
    }

    fun setMode(mode: Mode) = update { it.copy(mode = mode) }

    fun fail(code: String) = update { it.copy(error = code) }

    // MARK: Recognition

    /** 開始識別: upload (once per photo), quote, and accept when the quote matches the price shown. */
    fun start(language: String, gloss: String?) = launch {
        val s = mutable.value
        val photo = s.photo ?: return@launch
        val expected = s.price ?: return@launch
        if (s.pending != null || !s.operationsEnabled) return@launch
        val mode = s.mode
        var quote: CreditQuote? = null
        act {
            if (mutable.value.image == null) {
                val image = repository.upload(photo)
                if (!current()) return@act
                update { it.copy(image = image) }
            }
            val imageId = mutable.value.image?.id ?: return@act
            quote = repository.quote(imageId, mode == Mode.Precision, language, gloss)
        }
        proceed(quote, expected)
    }

    /** 普通識別 → 高精度: same photo, pay the difference. */
    fun upgrade(language: String, gloss: String?) = launch {
        val s = mutable.value
        val operation = s.operation ?: return@launch
        val expected = s.upgradePrice ?: return@launch
        if (s.pending != null || !s.operationsEnabled || !s.canUpgrade) return@launch
        var quote: CreditQuote? = null
        act {
            // The run keeps the language it was paid for; a later settings change does not apply here.
            val target = operation.targetLanguage.ifBlank { language }
            quote = repository.quote(operation.imageId, true, target, gloss)
        }
        proceed(quote, expected)
    }

    private suspend fun proceed(quote: CreditQuote?, expected: Int) {
        if (quote == null || !current()) return
        update { it.copy(quote = quote) }
        if (quote.points == expected) acceptNow() else update { it.copy(error = PRICE_CHANGED) }
    }

    fun cancelQuote() = update { it.copy(quote = null) }

    fun accept() = launch { acceptNow() }

    private suspend fun acceptNow() {
        val key = journal() ?: return
        act {
            val pending = mutable.value.pending
                ?: mutable.value.quote?.let { PendingCreditAcceptance(it.id, UUID.randomUUID().toString().lowercase()) }
                ?: return@act
            // Synchronous persistence precedes a paid request. If it fails, send nothing.
            check(preferences.edit().putString(key, TujiJson.encodeToString(pending)).commit())
            update { it.copy(pending = pending) }
            try {
                val operation = repository.accept(pending)
                if (!current()) return@act
                preferences.edit().remove(key).apply()
                update {
                    it.copy(operation = operation, quote = null, pending = null,
                        history = listOf(operation) + it.history.filter { h -> h.id != operation.id }).cleared()
                }
                apply(repository.wallet())
            } catch (e: Throwable) {
                // Keep unknown outcomes; only an explicit rejection permits a new quote.
                if (e !is CancellationException && errorCode(e) in REJECTIONS && current()) {
                    preferences.edit().remove(key).apply()
                    update { it.copy(pending = null, quote = null) }
                }
                throw e
            }
        }
    }

    suspend fun poll() {
        val operation = mutable.value.operation ?: return
        if (!current() || !operation.needsPolling) return
        try {
            val next = repository.read(operation.id)
            if (!current() || mutable.value.operation?.id != operation.id) return
            update { it.copy(operation = next, history = listOf(next) + it.history.filter { h -> h.id != next.id }) }
            apply(repository.wallet())
            pollFailures = 0
            // Only lift the banner poll() raised; other messages belong to their own actions.
            if (mutable.value.error == POLL_FAILED) update { it.copy(error = null) }
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) {
            pollFailures += 1
            Log.w(TAG, "poll failed ($pollFailures)", e)
            if (pollFailures >= POLL_FAILURE_LIMIT) update { it.copy(error = POLL_FAILED) }
        }
    }

    fun cancelOperation() = launch {
        val id = mutable.value.operation?.id ?: return@launch
        act {
            val value = repository.cancel(id)
            update { it.copy(operation = value, history = listOf(value) + it.history.filter { h -> h.id != id }) }
            apply(repository.wallet())
        }
    }

    fun show(mode: Mode) {
        val run = mutable.value.run(mode) ?: return
        if (run.id == mutable.value.operation?.id) return
        update { it.copy(operation = run).cleared() }
    }

    // MARK: Card

    /** Tapping a chip is an explicit choice, so it replaces whatever the fields hold. */
    fun select(candidate: CreditCandidate) = update {
        it.copy(
            selectedCandidateId = candidate.id,
            lemma = candidate.label,
            displayZhHant = candidate.zhHant,
            displayGloss = candidate.gloss.orEmpty(),
            suggestion = Suggestion(candidate.label, candidate.zhHant, candidate.gloss.orEmpty()),
        )
    }

    fun edit(lemma: String? = null, zhHant: String? = null, gloss: String? = null) = update {
        it.copy(lemma = lemma ?: it.lemma, displayZhHant = zhHant ?: it.displayZhHant, displayGloss = gloss ?: it.displayGloss)
    }

    /**
     * 確認並生成卡片: hand the confirm → cards → fill-in tail to 生成佇列 and
     * return, so the screen closes at once and the 圖鑑 grid shows the card
     * being made. Returns whether it was handed over.
     */
    fun confirm(secondField: SecondField): Boolean {
        val s = mutable.value
        if (!s.canConfirm) return false
        val operation = s.operation ?: return false
        val candidate = operation.result?.candidates?.firstOrNull { it.id == s.selectedCandidateId } ?: return false
        // The gloss field only exists for a cross-language capture; elsewhere the server keeps the candidate's.
        val gloss = s.displayGloss.trim().takeIf { secondField == SecondField.Gloss && it.isNotEmpty() }
        enqueue(
            CreditConfirmRequest(operation.id, candidate.id, s.lemma.trim(), s.displayZhHant.trim(), gloss),
            operation.imageId,
            s.image?.thumbUrl ?: s.image?.imageUrl,
        )
        reset()
        return true
    }

    /** The card was saved but syncing it failed: retry without confirming again. */
    fun syncCards(onDone: () -> Unit) = launch {
        val itemId = mutable.value.operation?.confirmedItemId ?: return@launch
        var synced = false
        act {
            atlas.createCards(itemId, listOf("image_recall"))
            synced = true
        }
        if (synced && current()) onDone()
    }

    private fun State.cleared() = copy(selectedCandidateId = null, lemma = "", displayZhHant = "", displayGloss = "", suggestion = null)

    companion object {
        private const val TAG = "CreditCapture"
        private const val POLL_FAILURE_LIMIT = 3
        const val POLL_FAILED = "poll_failed"
        const val PRICE_CHANGED = "price_changed"
        private val REJECTIONS = setOf(
            "quote_expired", "quote_not_found", "invalid_ai_request", "invalid_credit_request",
            "insufficient_credits", "capacity_full", "image_changed", "image_not_found", "operation_busy",
            "idempotency_conflict", "credits_reconciliation_required",
        )

        fun secondField(ui: UiLanguage, target: TargetLanguage): SecondField = when (ui) {
            UiLanguage.ZhHant, UiLanguage.ZhHans -> SecondField.ChineseName
            UiLanguage.Ja -> if (target == TargetLanguage.JA) SecondField.Hidden else SecondField.Gloss
            UiLanguage.En -> if (target == TargetLanguage.EN) SecondField.Hidden else SecondField.Gloss
        }

        /**
         * Work already paid for that the screen should reopen on: a run in
         * flight, else a result nobody has picked from yet. A photo's 普通 and
         * 高精度 runs share one card, so a card made from either finishes both.
         * One 生成佇列 is already confirming is not waiting either.
         */
        fun resumable(operations: List<CreditOperation>, queued: Set<String> = emptySet()): CreditOperation? {
            val finished = operations.filter { it.confirmedItemId != null }.map { it.imageId }.toSet()
            return operations.firstOrNull { it.isRunning } ?: operations.firstOrNull {
                it.state == "committed" && it.confirmedItemId == null && it.imageId !in finished && it.id !in queued
            }
        }
    }
}
