package app.tuji.android.core.study

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpokenVoiceTest {

    private val en = LearningDirection.ZH_EN
    private val ja = LearningDirection.ZH_JA

    @Test fun `the saved accent picks the English recording`() {
        assertEquals("en-US", SpokenVoice.key(en, accent = "us"))
        assertEquals("en-GB", SpokenVoice.key(en, accent = "uk"))
    }

    /** The server's default, and what every row that never touched the setting holds. */
    @Test fun `an unknown accent is US`() {
        assertEquals("en-US", SpokenVoice.key(en, accent = ""))
        assertEquals("en-US", SpokenVoice.key(en, accent = "au"))
    }

    /** A Japanese recording has one accent; the setting means nothing here. */
    @Test fun `Japanese ignores the accent`() {
        assertEquals("ja-JP", SpokenVoice.key(ja, accent = "uk"))
        assertEquals("ja-JP", SpokenVoice.key(ja, accent = "us"))
    }

    /**
     * A word's own language wins over the session's: 物見 and 自製 cards carry
     * one, and a Japanese word read in English is not a pronunciation at all.
     */
    @Test fun `the word's own language wins over the session`() {
        assertEquals("ja-JP", SpokenVoice.key(en, accent = "us", language = TargetLanguage.JA))
        assertEquals("en-GB", SpokenVoice.key(ja, accent = "uk", language = TargetLanguage.EN))
    }

    @Test fun `the asked-for clip is returned when it exists`() {
        val clips = mapOf("en-US" to "us.mp3", "en-GB" to "uk.mp3")
        assertEquals("uk.mp3", SpokenVoice.clip(clips, en, accent = "uk"))
        assertEquals("us.mp3", SpokenVoice.clip(clips, en, accent = "us"))
    }

    /**
     * 英式 is a preference, not a demand to stay silent. The pair is generated
     * and generation can be partial.
     */
    @Test fun `the other accent answers when the asked-for one is missing`() {
        assertEquals("us.mp3", SpokenVoice.clip(mapOf("en-US" to "us.mp3"), en, accent = "uk"))
        assertEquals("uk.mp3", SpokenVoice.clip(mapOf("en-GB" to "uk.mp3"), en, accent = "us"))
    }

    /** Japanese has no second option: no ja-JP clip means no recording. */
    @Test fun `Japanese does not fall back to an English clip`() {
        assertNull(SpokenVoice.clip(mapOf("en-US" to "us.mp3"), ja, accent = "us"))
    }

    @Test fun `nothing to play is null, not a crash`() {
        assertNull(SpokenVoice.clip(null, en, accent = "us"))
        assertNull(SpokenVoice.clip(emptyMap(), en, accent = "us"))
    }
}
