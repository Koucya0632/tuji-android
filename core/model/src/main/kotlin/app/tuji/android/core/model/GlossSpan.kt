package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * One unit of an annotated example sentence — 詞塊.
 *
 * Deliberately not "one word": `look forward to` is a single span, because to a
 * learner it is one unit and translating the bare `to` inside it is not merely
 * useless but wrong. Which is also why the split is made on the server against
 * the whole sentence, rather than by a tokeniser sitting free on the device
 * (iOS ADR-0009).
 *
 * **A span with a [gloss] is tappable and one without it is not.** There is no
 * separate flag, and the answer must not vary by interface language: a span
 * missing its ja gloss falls back to zh-Hant server-side rather than going
 * dead, or the same sentence would lose half its live words in 日本語.
 */
@Serializable
data class GlossSpan(
    /**
     * This span's slice of the sentence, verbatim — including whatever spaces
     * and punctuation belong to it. [SentenceAnnotation] re-spells the sentence
     * from these, so it is never normalised.
     */
    val text: String,
    /**
     * What the span means *in this sentence*, in the requested UI language.
     * Absent on function words and punctuation, which is exactly what makes
     * them untappable.
     */
    val gloss: String? = null,
    /**
     * `running` → `run`. Absent when the span is already its own base form.
     *
     * **Not `lemma`**: that name is taken by the 自製圖鑑 item headword
     * (`atlas_items.lemma`), and one word meaning two things in one codebase is
     * the next person's trap.
     */
    val baseForm: String? = null,
    /** Canonical English part of speech, as `WordDetailContent.partOfSpeech` expects. */
    val partOfSpeech: String? = null,
    /** Kana reading. Japanese sentences only. */
    val reading: String? = null,
    /**
     * The catalogue word this span teaches, when it is one. Lets the card offer
     * a way into 詞條; absent for the many spans that will never be dictionary
     * entries, and stripped by the server when it would point at the page the
     * reader is already on.
     */
    val wordId: String? = null,
    /**
     * The catalogue's transcription — IPA for English, a copy of the kana
     * reading for Japanese. Never authored per span and never produced by a
     * model.
     *
     * **A stricter condition than [wordId], and the client does not re-derive
     * it.** `wordId` is resolved from the span's *base form*, so `documents`
     * links to `document`; the server attaches a transcription only when the
     * span is spelled exactly like the headword, because the headword's
     * pronunciation printed under an inflection is not useless but wrong. About
     * one tappable span in five has one — the same shape of "sometimes" as
     * 書籤 and 看完整詳情, and for the same reason.
     */
    val pronunciation: String? = null,
) {
    /**
     * The one question every consumer asks. Spelled out here so no screen
     * re-derives it as `gloss != null` and lets the two drift.
     */
    val isTappable: Boolean get() = !gloss.isNullOrEmpty()
}

/**
 * Whether a sentence's spans may be trusted to render it.
 *
 * The annotation is **total**: every character of the sentence sits in exactly
 * one span, so concatenating them re-spells it. That is the same invariant
 * [FuriganaSegment] carries, kept for the same reason — the checkable half of a
 * model's answer gets checked. It also means a renderer never indexes into the
 * string by a length the server computed: JS counts UTF-16, Kotlin counts
 * UTF-16 too but Swift's `Character` is a grapheme cluster and Postgres counts
 * code points, and a rule that only holds for two of the four is not a rule.
 *
 * Failing the check is **not an error state**. It renders the plain sentence
 * the app showed before any of this existed — which is also why a partial
 * annotation is discarded whole rather than patched: half a sentence with
 * dotted underlines under three words and nothing under the rest reads as a
 * feature that is broken, not one that is absent.
 */
object SentenceAnnotation {
    fun spans(spans: List<GlossSpan>?, sentence: String): List<GlossSpan>? {
        if (spans.isNullOrEmpty()) return null
        if (spans.joinToString("") { it.text } != sentence) return null
        return spans
    }
}

/**
 * A 詞塊 asked the headword question.
 *
 * The same rule a word uses, so the phonetic line under a 詞塊 is decided in one
 * place: kana for Japanese, IPA for English, and nothing when the span already
 * says it — a kana span is its own reading. A span carries no furigana split,
 * so [HeadwordDisplay.Ruby] can never come back from one.
 */
private class SpanHeadword(
    private val span: GlossSpan,
    private val sentenceLanguage: TargetLanguage,
) : Headworded {
    override val word: String get() = span.text
    override val headwordPronunciation: String? get() = span.pronunciation
    override val readingSegments: List<FuriganaSegment>? get() = null
    override val reading: String? get() = span.reading
    override val targetLanguage: TargetLanguage get() = sentenceLanguage
}

/** [language] is the sentence's, because a fragment has none of its own. */
fun GlossSpan.asHeadworded(language: TargetLanguage): Headworded = SpanHeadword(this, language)
