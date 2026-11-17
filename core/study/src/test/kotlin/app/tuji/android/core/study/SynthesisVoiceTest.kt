package app.tuji.android.core.study

import app.tuji.android.core.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SynthesisVoiceTest {
    @Test fun `Australian setting selects the Australian locale`() {
        assertEquals("en-AU", SynthesisVoice.locale(TargetLanguage.EN, "au").toLanguageTag())
        assertEquals("en-GB", SynthesisVoice.locale(TargetLanguage.EN, "uk").toLanguageTag())
        assertEquals("ja-JP", SynthesisVoice.locale(TargetLanguage.JA, "au").toLanguageTag())
    }

    @Test fun `Australian speech requires country-specific availability`() {
        assertFalse(SynthesisVoice.supports(TargetLanguage.EN, "au", -1))
        assertFalse(SynthesisVoice.supports(TargetLanguage.EN, "au", 0))
        assertTrue(SynthesisVoice.supports(TargetLanguage.EN, "au", 1))
        assertTrue(SynthesisVoice.supports(TargetLanguage.EN, "au", 2))
        assertTrue(SynthesisVoice.supports(TargetLanguage.EN, "us", 0))
    }
}
