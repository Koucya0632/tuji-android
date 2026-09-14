package app.tuji.android.core.model

/**
 * On-device speech for a pronunciation button whose word has no recording —
 * iOS's `SpeechService` fallback.
 *
 * Only for the button on a word's own page, where a synthetic voice is better
 * than no button: most 自製圖鑑 cards and 物見 words have no pre-generated clip.
 * **Never used to ask a 聽句 question** — see [ClipPlaying] for why a card
 * without a real clip is not asked that way.
 */
interface WordSpeaking {
    /** Whether a voice for this language is installed and ready. */
    fun canSpeak(language: TargetLanguage): Boolean

    /** Speak, and return when the voice finishes. */
    suspend fun speak(text: String, language: TargetLanguage, accent: String)

    fun stop()
}
