package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one check that stands between a model's answer and the screen.
 *
 * Every case here is a real payload shape: the annotation is produced against a
 * sentence the server held, and the sentence the client draws has been through
 * a different pipeline — translation, trimming, a UI-language swap. When the
 * two disagree the spans are describing *some other string*, and rendering them
 * anyway puts dotted underlines under the wrong words.
 */
class SentenceAnnotationTest {

    private fun span(text: String, gloss: String? = null) = GlossSpan(text = text, gloss = gloss)

    @Test fun `spans that re-spell the sentence are usable`() {
        val spans = listOf(span("I "), span("like", "喜歡"), span(" it."))
        assertEquals(spans, SentenceAnnotation.spans(spans, "I like it."))
    }

    @Test fun `a missing space is a different sentence`() {
        // The commonest way a set goes stale: the sentence was re-trimmed on
        // one side of the wire and not the other.
        val spans = listOf(span("I"), span("like", "喜歡"), span(" it."))
        assertNull(SentenceAnnotation.spans(spans, "I like it."))
    }

    @Test fun `spans covering only part of the sentence are discarded whole`() {
        // Not truncated to what fits: three underlined words followed by
        // nothing reads as a broken feature, and the plain sentence is the
        // honest fallback.
        val spans = listOf(span("I "), span("like", "喜歡"))
        assertNull(SentenceAnnotation.spans(spans, "I like it."))
    }

    @Test fun `an empty or absent set is simply no annotation`() {
        assertNull(SentenceAnnotation.spans(null, "I like it."))
        assertNull(SentenceAnnotation.spans(emptyList(), "I like it."))
    }

    @Test fun `punctuation and spaces belong to spans, so Japanese re-spells too`() {
        val spans = listOf(span("窓", "窗戶"), span("を"), span("開けます", "打開"), span("。"))
        assertEquals(spans, SentenceAnnotation.spans(spans, "窓を開けます。"))
    }

    @Test fun `a span is tappable exactly when it has a gloss`() {
        // No separate flag, and no screen re-deriving it: a span whose gloss is
        // an empty string came back from a language that has none, and an
        // underline promising a meaning that is blank is worse than no
        // underline.
        assertEquals(true, span("like", "喜歡").isTappable)
        assertEquals(false, span("to").isTappable)
        assertEquals(false, span("to", "").isTappable)
    }

    @Test fun `a fragment takes the sentence's language, and never comes back as ruby`() {
        // A 詞塊 carries no furigana split, so the kana can only ever be a line
        // of its own — the case that used to print バスマット twice.
        val kanji = GlossSpan(text = "窓", gloss = "窗戶", reading = "まど")
        assertEquals(
            HeadwordDisplay.Line("まど"),
            kanji.asHeadworded(TargetLanguage.JA).headwordDisplay(TargetLanguage.JA),
        )
        // A kana span is its own reading: nothing to add under it.
        val kana = GlossSpan(text = "まど", gloss = "窗戶", reading = "まど")
        assertEquals(
            HeadwordDisplay.Plain,
            kana.asHeadworded(TargetLanguage.JA).headwordDisplay(TargetLanguage.JA),
        )
    }

    @Test fun `an English span shows IPA, and only when the server sent one`() {
        // pronunciation is a stricter condition than wordId — the server
        // attaches it only when the span is spelled like the headword — so most
        // spans have no line at all.
        val withIpa = GlossSpan(text = "weekend", gloss = "週末", pronunciation = "ˈwiːkˌɛnd")
        assertEquals(
            HeadwordDisplay.Line("ˈwiːkˌɛnd"),
            withIpa.asHeadworded(TargetLanguage.EN).headwordDisplay(TargetLanguage.EN),
        )
        val inflected = GlossSpan(text = "documents", gloss = "文件", baseForm = "document", wordId = "document")
        assertEquals(
            HeadwordDisplay.Plain,
            inflected.asHeadworded(TargetLanguage.EN).headwordDisplay(TargetLanguage.EN),
        )
    }
}
