package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowingScoreTest {

    @Test fun `English is scored word by word, punctuation aside`() {
        val score = ShadowingScore.of("I'd like the soup, please.", "i'd like soup please", TargetLanguage.EN)
        assertEquals(listOf("I'd", "like", "the", "soup", "please"), score.words.map { it.surface })
        assertEquals(listOf(true, true, false, true, true), score.words.map { it.heard })
        assertEquals(0.8, score.ratio, 0.0001)
    }

    @Test fun `Japanese heard in kana still counts for a kanji word`() {
        // The recogniser wrote かさ where the line has 傘; the furigana says they are the same.
        val furigana = listOf(FuriganaSegment("傘", "かさ"), FuriganaSegment("を"), FuriganaSegment("持", "も"), FuriganaSegment("って"))
        val score = ShadowingScore.of("傘を持って", "かさを持って", TargetLanguage.JA, furigana)
        assertTrue(score.words.all { it.heard })
        assertEquals(1.0, score.ratio, 0.0001)
    }

    @Test fun `katakana and hiragana are the same sound`() {
        val score = ShadowingScore.of("カサ", "かさ", TargetLanguage.JA)
        assertEquals(1.0, score.ratio, 0.0001)
    }

    @Test fun `nothing heard scores nothing`() {
        val score = ShadowingScore.of("hello there", "", TargetLanguage.EN)
        assertEquals(0.0, score.ratio, 0.0001)
        assertTrue(score.words.none { it.heard })
    }

    @Test fun `a word mostly missed is not counted`() {
        val score = ShadowingScore.of("ありがとう", "あり", TargetLanguage.JA)
        assertEquals(0.4, score.ratio, 0.0001)
    }
}
