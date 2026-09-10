package app.tuji.android.core.study

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage

/**
 * Which recording to ask a word for.
 *
 * The server sends a Japanese word one clip and an English word **two** —
 * `en-US` and `en-GB` — and leaves the choice to the device. 設定 → 發音口音 is
 * that choice.
 *
 * A value in `core:study` rather than a line inside each view model, because
 * this app already learned what happens when it is a line: the rule was written
 * out twice, twenty-eight lines apart on iOS's own card, and the two copies
 * disagreed about which recording the same word had. On Android it was written
 * twice too — 複習 and 單字詳情 — and **both copies ignored the setting**, so
 * 英式 saved fine, synced fine, and changed nothing anybody could hear.
 */
object SpokenVoice {

    const val US = "en-US"
    const val UK = "en-GB"
    const val JAPANESE = "ja-JP"

    /**
     * @param language the word's own target language when the payload carries
     *   one — a Japanese word speaks Japanese even inside an English session —
     *   and null to follow [direction].
     * @param accent the saved 發音口音, `"us"` or `"uk"`. Anything else is `us`,
     *   which is the server's default and the one every existing row holds.
     */
    fun key(
        direction: LearningDirection,
        accent: String,
        language: TargetLanguage? = null,
    ): String = when (language ?: direction.targetLanguage) {
        TargetLanguage.JA -> JAPANESE
        else -> if (accent == "uk") UK else US
    }

    /**
     * The clip to play, honouring the accent but not insisting on it.
     *
     * A word with only one English recording is normal — the pair is generated,
     * and generation can be partial — and 英式 is a *preference*, not a demand
     * to stay silent. So the other accent answers when the asked-for one is
     * missing. Japanese has no second option and no fallback: a word with no
     * `ja-JP` clip has no recording at all.
     */
    fun clip(
        clips: Map<String, String>?,
        direction: LearningDirection,
        accent: String,
        language: TargetLanguage? = null,
    ): String? {
        if (clips.isNullOrEmpty()) return null
        val asked = key(direction, accent, language)
        clips[asked]?.let { return it }
        return when (asked) {
            US -> clips[UK]
            UK -> clips[US]
            else -> null
        }
    }
}
