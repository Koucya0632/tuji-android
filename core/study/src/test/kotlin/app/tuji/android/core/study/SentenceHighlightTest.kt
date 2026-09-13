package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** iOS's `ReviewListeningTests` highlighting cases, one for one. */
class SentenceHighlightTest {

    private fun marked(word: String, sentence: String): String? =
        SentenceHighlight.range(word, sentence)?.let { sentence.substring(it.first, it.last + 1) }

    @Test fun `the headword is found in its own sentence`() {
        assertEquals("fork", marked("fork", "The fork is next to the plate."))
    }

    @Test fun `the match ignores case`() {
        assertEquals("Highlighter", marked("highlighter", "Highlighter ink bleeds through."))
    }

    @Test fun `a plural is highlighted whole`() {
        assertEquals("curtains", marked("curtain", "Please open the curtains."))
        assertEquals("traffic cones", marked("traffic cone", "The traffic cones mark the work area."))
        assertEquals("monitors", marked("monitor", "I have two monitors at my desk."))
    }

    @Test fun `a word is never highlighted inside a longer one`() {
        assertNull(marked("cup", "The cupboard is above the sink."))
        assertNull(marked("grate", "I bought a new grater."))
    }

    @Test fun `a second occurrence is found when the first is inside another word`() {
        assertEquals("cup", marked("cup", "The cupboard holds one cup."))
    }

    @Test fun `Japanese matches as a plain substring`() {
        assertEquals("エアコン", marked("エアコン", "エアコンは寝室にあります。"))
        assertEquals("寝室", marked("寝室", "エアコンは寝室にあります。"))
    }

    @Test fun `a sentence that never names the word gets no highlight`() {
        assertNull(marked("scanner", "Scan both sides of the document."))
        assertNull(marked("ベッド", "私は夜11時に寝ます。"))
    }

    @Test fun `an empty word or sentence is refused rather than matching everything`() {
        assertNull(marked("", "The fork is here."))
        assertNull(marked("fork", ""))
    }
}
