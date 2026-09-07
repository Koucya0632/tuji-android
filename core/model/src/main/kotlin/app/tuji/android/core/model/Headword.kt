package app.tuji.android.core.model

/**
 * How a headword is presented. Ported from `Tuji/Core/Models/Word.swift`.
 *
 * One place decides, because the question has been answered wrong before: a
 * screen that shows ruby *and* the reading line prints バスマット over the word
 * and again underneath it.
 */
sealed interface HeadwordDisplay {
    /** Japanese with a usable split: kana sit over the characters they read. */
    data class Ruby(val segments: List<FuriganaSegment>) : HeadwordDisplay

    /**
     * A line of its own — an English IPA transcription, or Japanese kana that
     * could not be aligned.
     */
    data class Line(val text: String) : HeadwordDisplay

    /** Nothing to add. The headword already says it. */
    data object Plain : HeadwordDisplay
}

/** Whether the phonetic line has anything to say that the headword does not. */
object ReadingLine {
    fun shown(reading: String?, headword: String): String? {
        val trimmed = reading?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed == headword) return null
        return trimmed
    }
}

/** A payload that can decide how its headword is presented. */
interface Headworded {
    val word: String
    val headwordPronunciation: String?
    val readingSegments: List<FuriganaSegment>?
    val reading: String?
    val targetLanguage: TargetLanguage?
}

/**
 * What the *payload* says: the explicit server tag wins, else a kana [reading]
 * (a JA-only backend field) marks it Japanese. null when it carries neither —
 * older caches, and just-captured 自製圖鑑 words.
 */
val Headworded.taggedLanguage: TargetLanguage?
    get() = targetLanguage ?: if (!reading.isNullOrEmpty()) TargetLanguage.JA else null

/**
 * The word's language. Always an answer.
 *
 * The fallback used to be the caller's job on iOS, stated in a doc comment:
 * eleven of thirteen call sites never did it, so an untagged word read as
 * English everywhere — the wrong half of a coin flip for a payload whose
 * commonest source is a Japanese learner's own capture. [session] is
 * 當前圖鑑語言.
 */
fun Headworded.language(session: TargetLanguage): TargetLanguage =
    taggedLanguage ?: session

fun Headworded.headwordDisplay(session: TargetLanguage): HeadwordDisplay {
    // Japanese only. An English `pronunciation` is IPA — different information
    // from the headword, and nothing ruby can express.
    if (language(session) != TargetLanguage.JA) {
        return ReadingLine.shown(headwordPronunciation, word)
            ?.let(HeadwordDisplay::Line) ?: HeadwordDisplay.Plain
    }
    val segments = readingSegments
    if (!segments.isNullOrEmpty()) return HeadwordDisplay.Ruby(segments)
    // No split: fall back to the line this replaced. For Japanese the server
    // sends `pronunciation` as a copy of `reading`, so either spelling of the
    // question gives the same answer — and a headword already written in kana
    // is its own reading, which is the case that used to print twice.
    return ReadingLine.shown(reading ?: headwordPronunciation, word)
        ?.let(HeadwordDisplay::Line) ?: HeadwordDisplay.Plain
}

/** [Word] answers the headword question directly; nothing derives it a second time. */
val Word.headwordPronunciation: String? get() = pronunciation

private class WordHeadword(private val w: Word) : Headworded {
    override val word get() = w.word
    override val headwordPronunciation get() = w.pronunciation
    override val readingSegments get() = w.readingSegments
    override val reading get() = w.reading
    override val targetLanguage get() = w.targetLanguage
}

fun Word.asHeadworded(): Headworded = WordHeadword(this)
