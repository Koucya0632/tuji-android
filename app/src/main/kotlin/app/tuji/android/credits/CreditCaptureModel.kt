package app.tuji.android.credits

import android.content.SharedPreferences
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

class CreditCaptureModel(
    private val repository: CreditRepository,
    private val atlas: AtlasAuthoring,
    private val owner: String,
    private val currentOwner: () -> String?,
    private val preferences: SharedPreferences,
    private val scope: CoroutineScope,
) {
    data class State(
        val wallet: CreditWallet? = null, val catalog: CreditCatalog? = null,
        val image: AtlasImageSummary? = null, val operation: CreditOperation? = null,
        val quote: CreditQuote? = null, val pending: PendingCreditAcceptance? = null,
        val history: List<CreditOperation> = emptyList(), val busy: Boolean = false, val error: String? = null,
    )
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private fun current() = currentOwner() == owner
    private fun journal(): String? = mutable.value.catalog?.environment?.let { "credit-accept:${owner.lowercase()}:$it" }
    private fun apply(wallet: CreditWallet) {
        if (!current()) return
        if (mutable.value.wallet?.let { wallet.isNewerThan(it) } != false) mutable.value = mutable.value.copy(wallet = wallet)
    }
    private fun errorCode(error: Throwable): String =
        (error as? ApiError.Http)?.body?.let {
            runCatching { TujiJson.parseToJsonElement(it).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        } ?: "credits_unavailable"
    private fun run(block: suspend () -> Unit) {
        if (mutable.value.busy || !current()) return
        mutable.value = mutable.value.copy(busy = true, error = null)
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (current()) mutable.value = mutable.value.copy(error = errorCode(e)) }
            finally { if (current()) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun load() = run {
        val catalog = repository.catalog()
        if (!current()) return@run
        mutable.value = mutable.value.copy(catalog = catalog)
        journal()?.let { key -> preferences.getString(key, null)?.let { saved ->
            val pending = runCatching { TujiJson.decodeFromString<PendingCreditAcceptance>(saved) }.getOrNull()
            mutable.value = mutable.value.copy(pending = pending)
        } }
        apply(repository.wallet())
        val history = repository.history()
        if (current()) mutable.value = mutable.value.copy(history = history, operation = mutable.value.operation ?: history.firstOrNull { it.needsPolling } ?: history.firstOrNull())
    }
    fun claim(monthly: Boolean) = run { apply(repository.claim(monthly)) }
    fun upload(readBytes: suspend () -> ByteArray) = run {
        if (mutable.value.pending != null) return@run
        val image = repository.upload(readBytes())
        if (current()) mutable.value = mutable.value.copy(image = image, operation = null, quote = null)
    }
    fun quote(precision: Boolean, language: String, gloss: String?) = run {
        val id = mutable.value.image?.id ?: mutable.value.operation?.imageId ?: return@run
        val quote = repository.quote(id, precision, language, gloss)
        if (current()) mutable.value = mutable.value.copy(quote = quote)
    }
    fun dismissQuote() { mutable.value = mutable.value.copy(quote = null) }
    fun accept() = run {
        val key = journal() ?: return@run
        val pending = mutable.value.pending ?: mutable.value.quote?.let { PendingCreditAcceptance(it.id, UUID.randomUUID().toString()) } ?: return@run
        // Synchronous persistence precedes a paid request. If it fails, send nothing.
        check(preferences.edit().putString(key, TujiJson.encodeToString(pending)).commit())
        mutable.value = mutable.value.copy(pending = pending)
        try {
            val operation = repository.accept(pending)
            if (!current()) return@run
            preferences.edit().remove(key).apply()
            mutable.value = mutable.value.copy(operation = operation, quote = null, pending = null)
            apply(repository.wallet())
        } catch (e: Throwable) {
            if (errorCode(e) in setOf("quote_expired", "quote_not_found", "invalid_ai_request", "invalid_credit_request",
                    "insufficient_credits", "capacity_full", "image_changed", "image_not_found", "operation_busy",
                    "idempotency_conflict", "credits_reconciliation_required") && current()) {
                preferences.edit().remove(key).apply()
                mutable.value = mutable.value.copy(pending = null, quote = null)
            }
            throw e
        }
    }
    suspend fun poll() {
        val operation = mutable.value.operation ?: return
        if (!current() || !operation.needsPolling) return
        try {
            val next = repository.read(operation.id)
            if (current() && mutable.value.operation?.id == operation.id) {
                mutable.value = mutable.value.copy(operation = next, history = listOf(next) + mutable.value.history.filter { it.id != next.id })
                apply(repository.wallet())
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Throwable) { if (current()) mutable.value = mutable.value.copy(error = errorCode(e)) }
    }
    fun select(operation: CreditOperation) { mutable.value = mutable.value.copy(operation = operation, image = null, quote = null) }
    fun cancel() = run {
        val id = mutable.value.operation?.id ?: return@run
        val value = repository.cancel(id)
        if (current()) mutable.value = mutable.value.copy(operation = value)
        apply(repository.wallet())
    }
    fun confirm(candidateId: String) = run {
        val id = mutable.value.operation?.id ?: return@run
        val result = repository.confirm(id, candidateId)
        if (!current()) return@run
        mutable.value = mutable.value.copy(operation = result.operation)
        atlas.createCards(result.item.id, listOf("image_recall", "flashcard"))
    }
    fun cards() = run {
        val id = mutable.value.operation?.confirmedItemId ?: return@run
        atlas.createCards(id, listOf("image_recall", "flashcard"))
    }
}
