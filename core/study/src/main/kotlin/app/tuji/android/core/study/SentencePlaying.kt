package app.tuji.android.core.study

/** How a sentence's audio ended. */
enum class SentencePlayback {
    /** The pre-generated clip played to its end. */
    Finished,

    /** Nothing came out, or it stopped early. Recorded as `audioFailed`. */
    Failed,
}

/**
 * Playing a recording, and being told when it has ended.
 *
 * A seam rather than a singleton call inside the caller, because 聽句's clock
 * hangs off the *end* of the audio — the question starts when the sentence
 * stops — and a rule that only fires after a real two-second clip is a rule no
 * test can reach. The `suspend` shape is the point: awaiting is what the caller
 * actually wants to express, and a fake satisfies it in one line.
 *
 * **There is deliberately no on-device synthesis fallback here**, unlike the
 * pronunciation button on iOS. A Japanese sentence read by a voice guessing at
 * kanji is not a worse question, it is an unanswerable one — so a card with no
 * real clip is simply not asked as 聽句. That is what [canPlay] is for.
 */
interface SentencePlaying {

    /**
     * Whether this clip plays *right now* — already on disk, or a live
     * connection to fetch it. False sends the card to 選字 instead.
     *
     * Cached beats connected: a clip already downloaded plays on a plane.
     */
    fun canPlay(url: String?, online: Boolean): Boolean

    /**
     * Play, and return when the audio ends.
     *
     * @param rate a multiplier on normal speed — 慢讀 passes 0.8.
     */
    suspend fun play(url: String?, rate: Float = 1f): SentencePlayback

    /**
     * Cut playback off. Leaving 複習 mid-sentence must not narrate the screen
     * the user went to instead — and 聽句 auto-plays, so this is audio nobody
     * asked to start.
     */
    fun stop()
}
