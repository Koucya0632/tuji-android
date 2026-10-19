package app.tuji.android.assistant

import android.content.SharedPreferences
import app.tuji.android.core.model.GrammarInput
import app.tuji.android.core.model.GrammarOffer
import app.tuji.android.core.model.GrammarPracticeProgress
import app.tuji.android.core.model.GrammarRecord
import app.tuji.android.core.model.StoryOffer
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
interface AssistantJournaling<Input> {
    fun pending(owner: String): List<Input>
    fun save(input: Input, owner: String)
    fun remove(id: String, owner: String)
}

typealias VocabularyJournaling = AssistantJournaling<VocabularyInput>
typealias GrammarJournaling = AssistantJournaling<GrammarInput>

class PreferencesJournal<Input>(
    private val preferences: SharedPreferences,
    /** iOS's key, `vocabulary.pending.v1` or `grammar.pending.v1`. */
    private val prefix: String,
    item: KSerializer<Input>,
    private val requestId: (Input) -> String,
) : AssistantJournaling<Input> {
    private val serializer = ListSerializer(item)
    private fun key(owner: String) = "$prefix.$owner"

    override fun pending(owner: String): List<Input> =
        preferences.getString(key(owner), null)
            ?.let { runCatching { TujiJson.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    override fun save(input: Input, owner: String) =
        write(pending(owner).filter { requestId(it) != requestId(input) } + input, owner)

    override fun remove(id: String, owner: String) = write(pending(owner).filter { requestId(it) != id }, owner)

    private fun write(values: List<Input>, owner: String) {
        val edit = preferences.edit()
        if (values.isEmpty()) edit.remove(key(owner)) else edit.putString(key(owner), TujiJson.encodeToString(serializer, values))
        edit.apply()
    }
}

/** How far each practice set got on this device — iOS's `GrammarProgressStoring`. */
interface GrammarProgressStoring {
    fun progress(owner: String, learning: String, setId: String): GrammarPracticeProgress
    fun save(progress: GrammarPracticeProgress, owner: String, learning: String, setId: String)
    fun remove(owner: String, learning: String, setId: String)
}

class PreferencesGrammarProgress(private val preferences: SharedPreferences) : GrammarProgressStoring {
    private fun key(owner: String, learning: String, setId: String) = "grammar.practice.v1.$owner.$learning.$setId"

    override fun progress(owner: String, learning: String, setId: String): GrammarPracticeProgress =
        preferences.getString(key(owner, learning, setId), null)
            ?.let { runCatching { TujiJson.decodeFromString(GrammarPracticeProgress.serializer(), it) }.getOrNull() }
            ?: GrammarPracticeProgress()

    override fun save(progress: GrammarPracticeProgress, owner: String, learning: String, setId: String) =
        preferences.edit()
            .putString(key(owner, learning, setId), TujiJson.encodeToString(GrammarPracticeProgress.serializer(), progress))
            .apply()

    override fun remove(owner: String, learning: String, setId: String) =
        preferences.edit().remove(key(owner, learning, setId)).apply()
}

/**
 * 情境故事's offer — iOS's `StoryOfferCache`: kept so the page opens on its
 * choices instead of a loading bar, and so entering does not read the balance
 * again. By interface language, because the labels are in it. A written story
 * brings its new balance with it ([store]); a failure or a purchase makes the
 * kept one untrusted ([balanceChanged]).
 */
class StoryOfferCache(private val preferences: SharedPreferences?) {
    private var read: Map<String, StoryOffer> = emptyMap()
    private var stale = false
    private val inFlight = mutableMapOf<String, CompletableDeferred<StoryOffer>>()
    private val revisions = MutableStateFlow(0)
    val revision: StateFlow<Int> = revisions.asStateFlow()

    private fun key(uiLanguage: String) = "tuji.story.offer.v1.$uiLanguage"

    @Synchronized
    fun entry(uiLanguage: String): AssistantOfferCache.Entry<StoryOffer>? {
        read[uiLanguage]?.let { return AssistantOfferCache.Entry(it, current = !stale) }
        val kept = preferences?.getString(key(uiLanguage), null) ?: return null
        val offer = runCatching { TujiJson.decodeFromString(StoryOffer.serializer(), kept) }.getOrNull() ?: return null
        return AssistantOfferCache.Entry(offer, current = false)
    }

    /** Asks the server, or joins the ask already on its way. */
    suspend fun load(uiLanguage: String, fetch: suspend () -> StoryOffer): StoryOffer {
        val (ask, mine) = synchronized(this) {
            inFlight[uiLanguage]?.let { it to false }
                ?: CompletableDeferred<StoryOffer>().also { inFlight[uiLanguage] = it }.let { it to true }
        }
        if (!mine) return ask.await()
        try {
            val offer = fetch()
            store(offer, uiLanguage)
            ask.complete(offer)
            return offer
        } catch (e: Throwable) {
            ask.completeExceptionally(e)
            throw e
        } finally {
            synchronized(this) { if (inFlight[uiLanguage] === ask) inFlight.remove(uiLanguage) }
        }
    }

    suspend fun prefetch(uiLanguage: String, fetch: suspend () -> StoryOffer) {
        if (entry(uiLanguage)?.current == true) return
        runCatching { load(uiLanguage, fetch) }
    }

    @Synchronized
    fun balanceChanged() {
        if (read.isEmpty()) return
        stale = true
        revisions.value += 1
    }

    /** Another language's copy would hold an older balance, so only this one is kept. */
    @Synchronized
    fun store(offer: StoryOffer, uiLanguage: String) {
        stale = false
        read = mapOf(uiLanguage to offer)
        preferences?.edit()?.putString(key(uiLanguage), TujiJson.encodeToString(StoryOffer.serializer(), offer))?.apply()
    }
}

/** The process's 助手 caches — iOS's `AssistantOfferCaches` and `AssistantHistoryCaches`. */
class AssistantCaches(preferences: SharedPreferences?) {
    val vocabularyOffer = AssistantOfferCache("tuji.vocabulary.offer.v1", preferences, VocabularyOffer.serializer())
    val vocabularyHistory = AssistantHistoryCache<VocabularyRecord>()
    val grammarOffer = AssistantOfferCache("tuji.grammar.offer.v1", preferences, GrammarOffer.serializer())
    val grammarHistory = AssistantHistoryCache<GrammarRecord>()
    val storyOffer = StoryOfferCache(preferences)

    /** Every kept 罐頭點數 balance in 助手. */
    fun balanceChanged() {
        vocabularyOffer.balanceChanged()
        grammarOffer.balanceChanged()
        storyOffer.balanceChanged()
    }
}
