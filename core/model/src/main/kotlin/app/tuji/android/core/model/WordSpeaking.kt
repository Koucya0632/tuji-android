package app.tuji.android.core.model

/**
 * On-device speech for a pronunciation button whose word has no recording —
 * iOS's `SpeechService` fallback.
 *
 * Used by pronunciation buttons when a suitable recording is unavailable.
 * **Never used to ask a 聽句 question** — see [ClipPlaying] for why a card
 * without a real clip is not asked that way.
 */
interface WordSpeaking {
    /** Whether a voice for this language is installed and ready. */
    fun canSpeak(language: TargetLanguage): Boolean

    /** Accent-aware availability; older implementations may remain language-only. */
    fun canSpeak(language: TargetLanguage, accent: String): Boolean = canSpeak(language)

    /** Speak, and return when the voice finishes. */
    suspend fun speak(text: String, language: TargetLanguage, accent: String)

    fun stop()
}
