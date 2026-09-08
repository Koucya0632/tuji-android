package app.tuji.android.core.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceMaskTest {

    @Test fun `not one character of the sentence survives`() {
        val sentence = "花粉の季節は空気清浄機を使います。"
        val masked = maskedSentence(sentence)
        sentence.filterNot { it.isWhitespace() }.toSet().forEach {
            assertFalse("$it leaked through the mask", it in masked)
        }
    }

    @Test fun `punctuation is masked too`() {
        // 「。」 surviving at the end of one of two candidate sentences is a tell.
        assertFalse('。' in maskedSentence("使います。"))
        assertFalse('.' in maskedSentence("I use it."))
        assertFalse('?' in maskedSentence("Do you?"))
    }

    @Test fun `the line breaks land where the real ones do`() {
        val masked = maskedSentence("I open the window\nevery morning.")
        assertEquals(2, masked.lines().size)
        assertEquals("the shape has to survive or the eye means nothing", 17, masked.lines()[0].length)
    }

    @Test fun `spaces are kept so word shape survives`() {
        val masked = maskedSentence("a bb ccc")
        assertEquals("█ ██ ███", masked)
    }

    @Test fun `an empty sentence masks to nothing rather than failing`() {
        assertEquals("", maskedSentence(""))
    }

    @Test fun `the mask is the same length as the sentence`() {
        val sentence = "窓を開けて空気を入れ替えます。"
        assertEquals(sentence.length, maskedSentence(sentence).length)
        assertTrue(maskedSentence(sentence).all { it == '█' })
    }
}
