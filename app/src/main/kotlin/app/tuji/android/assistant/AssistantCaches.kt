package app.tuji.android.assistant

import android.content.SharedPreferences
import app.tuji.android.core.model.VocabularyInput
import app.tuji.android.core.model.VocabularyOffer
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.core.network.TujiJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * AI 查詞's offer, kept so entering the page does not ask the server for the
 * balance again — iOS's `AssistantOfferCache`.
 *
 * One read per launch: 助手 or the page itself, whichever comes first, and the
 * two share one request. After that the balance is read again only when it is
 * known to have moved — a card was generated or refused (the page asks), or a
 * purchase was delivered ([balanceChanged]). The last answer is also kept on
 * disk, so the first visit after a launch shows the prices at once; what comes
 * off the disk is not [Entry.current], so the page shows it but does not let it
 * decide whether a card can be paid for.
 */
class AssistantOfferCache<Offer>(
    private val key: String,
    private val preferences: SharedPreferences?,
    private val serializer: KSerializer<Offer>,
) {
    data class Entry<Offer>(val offer: Offer, val current: Boolean)

    private var read: Pair<String, Offer>? = null
    private var stale = false
    private var inFlight: Pair<String, CompletableDeferred<Offer>>? = null
    private val revisions = MutableStateFlow(0)

    /** Bumped when the kept balance stops being trusted, so an open page knows to read it again. */
    val revision: StateFlow<Int> = revisions.asStateFlow()

    @Synchronized
    fun entry(owner: String): Entry<Offer>? {
        read?.let { (who, offer) -> if (who == owner) return Entry(offer, current = !stale) }
        if (preferences?.getString("$key.owner", null) != owner) return null
        val kept = preferences.getString(key, null) ?: return null
        val offer = runCatching { TujiJson.decodeFromString(serializer, kept) }.getOrNull() ?: return null
        return Entry(offer, current = false)
    }

    /** Asks the server, or joins the ask already on its way for the same account. */
    suspend fun load(owner: String, fetch: suspend () -> Offer): Offer {
        val (ask, mine) = synchronized(this) {
            inFlight?.takeIf { it.first == owner }?.let { it.second to false }
                ?: CompletableDeferred<Offer>().also { inFlight = owner to it }.let { it to true }
        }
        if (!mine) return ask.await()
        try {
            val offer = fetch()
            synchronized(this) {
                read = owner to offer
                stale = false
                preferences?.edit()
                    ?.putString("$key.owner", owner)
                    ?.putString(key, TujiJson.encodeToString(serializer, offer))
                    ?.apply()
            }
            ask.complete(offer)
            return offer
        } catch (e: Throwable) {
            ask.completeExceptionally(e)
            throw e
        } finally {
            synchronized(this) { if (inFlight?.second === ask) inFlight = null }
        }
    }

    /** For a page that leads to the feature: reads only what is not already in hand. */
    suspend fun prefetch(owner: String?, fetch: suspend () -> Offer) {
        if (owner == null || entry(owner)?.current == true) return
        runCatching { load(owner, fetch) }
    }

    /** Points were bought or a membership began: the kept balance is behind. */
    @Synchronized
    fun balanceChanged() {
        if (read == null) return
        stale = true
        revisions.value += 1
    }
}

/**
 * 查詞紀錄, kept so opening it again does not ask the server again — iOS's
 * `AssistantHistoryCache`. Memory only: the lists are long and the first visit
 * after a launch reads them anyway.
 */
class AssistantHistoryCache<Record> {
    data class Page<Record>(val records: List<Record>, val nextCursor: String?)

    private val pages = mutableMapOf<String, Page<Record>>()

    @Synchronized
    fun page(owner: String, filter: String): Page<Record>? = pages[key(owner, filter)]

    @Synchronized
    fun store(page: Page<Record>, owner: String, filter: String) {
        pages[key(owner, filter)] = page
    }

    /** Every filter's list for this account is behind. */
    @Synchronized
    fun invalidate(owner: String) {
        val prefix = key(owner, "")
        pages.keys.removeAll { it.startsWith(prefix) }
    }

    private fun key(owner: String, filter: String) = "$owner\n$filter"
}

/** Only unacknowledged requests live on the device; results belong to server history. */
interface VocabularyJournaling {
    fun pending(owner: String): List<VocabularyInput>
    fun save(input: VocabularyInput, owner: String)
    fun remove(id: String, owner: String)
}

class PreferencesVocabularyJournal(private val preferences: SharedPreferences) : VocabularyJournaling {
    private val serializer = ListSerializer(VocabularyInput.serializer())
    private fun key(owner: String) = "vocabulary.pending.v1.$owner"

    override fun pending(owner: String): List<VocabularyInput> =
        preferences.getString(key(owner), null)
            ?.let { runCatching { TujiJson.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    override fun save(input: VocabularyInput, owner: String) =
        write(pending(owner).filter { it.requestId != input.requestId } + input, owner)

    override fun remove(id: String, owner: String) = write(pending(owner).filter { it.requestId != id }, owner)

    private fun write(values: List<VocabularyInput>, owner: String) {
        val edit = preferences.edit()
        if (values.isEmpty()) edit.remove(key(owner)) else edit.putString(key(owner), TujiJson.encodeToString(serializer, values))
        edit.apply()
    }
}

/** The process's 助手 caches — iOS's `AssistantOfferCaches` and `AssistantHistoryCaches`. */
class AssistantCaches(preferences: SharedPreferences?) {
    val vocabularyOffer = AssistantOfferCache("tuji.vocabulary.offer.v1", preferences, VocabularyOffer.serializer())
    val vocabularyHistory = AssistantHistoryCache<VocabularyRecord>()

    /** Every kept 罐頭點數 balance in 助手. */
    fun balanceChanged() {
        vocabularyOffer.balanceChanged()
    }
}
