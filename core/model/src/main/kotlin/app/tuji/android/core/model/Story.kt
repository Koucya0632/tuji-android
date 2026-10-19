package app.tuji.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 情境故事 — iOS's `AssistantStoryWriting.swift`, the wire half.
 *
 * The person picks one to three 圖鑑 items, a place, what is happening there
 * and a difficulty; a model writes a short scene between Moira and them. The
 * server owns the places and levels (labelled in the interface language) and
 * charges a story what its tokens cost — `minPoints`–`maxPoints` is only the
 * typical range — and nothing for a story it cannot deliver.
 */
@Serializable
data class StoryOffer(
    /** The typical range, said before the tap. Not a cap. */
    val minPoints: Int,
    /** Also the balance a story needs to start. */
    val maxPoints: Int,
    val available: Int = 0,
    val scenes: List<Scene> = emptyList(),
    val levels: List<Level> = emptyList(),
) {
    @Serializable
    data class Situation(val id: String, val label: String)

    @Serializable
    data class Scene(val id: String, val label: String, val situations: List<Situation> = emptyList())

    @Serializable
    data class Level(val id: String, val label: String, val hint: String = "")
}

@Serializable
data class StoryPayload(
    /** Kept across a resend of the same choices: the server charges a request id once. */
    val requestId: String,
    val uiLanguage: String,
    val learningDirection: String,
    /** Catalogue ids or `atlas:<uuid>`. The server reads the entries itself. */
    val wordIds: List<String>,
    val sceneId: String,
    val situationId: String,
    val levelId: String,
)

@Serializable
data class Story(
    val title: String,
    val intro: String,
    /** Grows while the story is being written. */
    val segments: List<Segment> = emptyList(),
    /** Only when the interface language is the one being learned: the picked words are explained instead. */
    val wordNotes: List<WordNote>? = null,
) {
    @Serializable
    data class Segment(
        val speaker: Speaker,
        /** Narration in the interface language; a spoken line in the language being learned. */
        val text: String,
        /** Japanese lines only: kana over each kanji run. */
        val furigana: List<FuriganaSegment>? = null,
        /** Japanese lines only, when there is no `furigana`: the whole line in kana. */
        val reading: String? = null,
        /** Spoken lines only, and only when the interface language is not the one being learned. */
        val translation: String? = null,
        val wordIds: List<String> = emptyList(),
    )

    @Serializable
    enum class Speaker {
        @SerialName("narrator") Narrator,
        @SerialName("cat") Cat,
        @SerialName("you") You,
    }

    @Serializable
    data class WordNote(val id: String, val note: String)
}

@Serializable
data class StoryResult(
    val story: Story,
    val charged: Int = 0,
    val available: Int = 0,
    val minPoints: Int = 0,
    val maxPoints: Int = 0,
)

/** A story as it is written: the opening, each segment as it closes, then the whole story and its cost. */
sealed interface StoryEvent {
    data class Opening(val title: String, val intro: String) : StoryEvent
    data class Segment(val segment: Story.Segment) : StoryEvent
    data class Done(val result: StoryResult) : StoryEvent
}

/** The server stopped partway, after the story had started arriving. Nothing was charged. */
class StoryInterrupted(val code: String) : Exception("story interrupted: $code")

/**
 * A Japanese sentence cut where a line may break — iOS's `FuriganaSentence.units`:
 * a kanji run with its ruby, or a single bare character. Closing punctuation
 * and small kana stay glued to the unit before them, so a line never starts
 * with 。 or ょ.
 */
object FuriganaUnits {
    data class Unit(val text: String, val ruby: String?)

    private val gluedToPrevious: Set<Char> =
        "、。，．！？!?）」』】〉》…ーぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮ".toSet()

    fun of(segments: List<FuriganaSegment>): List<Unit> {
        val units = mutableListOf<Unit>()
        for (segment in segments) {
            val ruby = segment.ruby
            if (!ruby.isNullOrEmpty()) {
                units += Unit(segment.text, ruby)
                continue
            }
            for (character in segment.text) {
                val last = units.lastOrNull()
                if (character in gluedToPrevious && last != null) {
                    units[units.lastIndex] = last.copy(text = last.text + character)
                } else {
                    units += Unit(character.toString(), null)
                }
            }
        }
        return units
    }
}
