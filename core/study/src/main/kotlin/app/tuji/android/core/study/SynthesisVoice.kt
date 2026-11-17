package app.tuji.android.core.study

import app.tuji.android.core.model.TargetLanguage
import java.util.Locale

/** Locale and availability rule for the system speech fallback. */
object SynthesisVoice {
    fun locale(language: TargetLanguage, accent: String): Locale = when (language) {
        TargetLanguage.JA -> Locale.JAPAN
        TargetLanguage.EN -> when (accent) {
            "uk" -> Locale.UK
            "au" -> Locale.forLanguageTag("en-AU")
            else -> Locale.US
        }
    }

    // Android TextToSpeech: LANG_AVAILABLE = 0, LANG_COUNTRY_AVAILABLE = 1.
    // For AU, language-only availability does not prove an Australian voice.
    fun supports(language: TargetLanguage, accent: String, availability: Int): Boolean =
        availability >= if (language == TargetLanguage.EN && accent == "au") 1 else 0
}
