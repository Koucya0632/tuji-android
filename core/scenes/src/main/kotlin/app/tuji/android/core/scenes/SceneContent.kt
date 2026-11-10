package app.tuji.android.core.scenes

import app.tuji.android.core.model.UiLanguage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// 場面練習's content, decoded from the bundled `assets/scenes/<id>.json` — the
// same files tuji-ios ships. The JSON is shared with the audio pipeline, so
// its shape is flat: a phrasing carries `text`, `voice` and `audio` side by
// side, each nullable. Decoding folds those three into one optional
// `SpokenLine`, so "a voice with no text" fails to load instead of reaching a
// screen that would have to guess.

@JvmInline @Serializable
value class SceneId(val raw: String)

@JvmInline @Serializable
value class SceneQuestionId(val raw: String)

@JvmInline @Serializable
value class PhrasingId(val raw: String)

/**
 * Copy written for the three content languages. A Japanese interface reads the
 * English: nobody using Tuji in Japanese is learning to understand a Japanese clerk.
 */
@Serializable
data class LocalizedText(
    @SerialName("zh-Hant") val zhHant: String,
    @SerialName("zh-Hans") val zhHans: String,
    val en: String,
) {
    fun resolved(language: UiLanguage): String = when (language) {
        UiLanguage.ZhHant -> zhHant
        UiLanguage.ZhHans -> zhHans
        UiLanguage.En, UiLanguage.Ja -> en
    }

    val all: List<String> get() = listOf(zhHant, zhHans, en)
}

@Serializable
enum class SceneAccess {
    @SerialName("free") Free,
    @SerialName("lifetime") Lifetime,
}

@Serializable
data class PracticeScene(
    val schemaVersion: Int,
    val id: SceneId,
    val revision: Int,
    val access: SceneAccess,
    val title: LocalizedText,
    val summary: LocalizedText,
    val questions: List<SceneQuestion>,
    /** The immersive staging. A scene without one keeps the plain practice screen. */
    val stage: SceneStage? = null,
    /**
     * Every Japanese line this scene says, split into words, keyed by the line's
     * exact text so a phrasing, a 回應 and a reply share one entry.
     */
    @SerialName("gloss") val glossTable: Map<String, List<GlossToken>>? = null,
) {
    fun stageStep(question: SceneQuestionId): StageStep? = stage?.steps?.get(question.raw)

    /** The words of `line`, when they are written and add up to it exactly. */
    fun gloss(line: String): List<GlossToken>? =
        glossTable?.get(line)?.takeIf { tokens -> tokens.joinToString("") { it.t } == line }
}

/**
 * One piece of a glossed line. A piece with no meaning (a particle,
 * punctuation) is glue between the words and cannot be tapped.
 */
@Serializable
data class GlossToken(
    val t: String,
    /** The reading in kana, when the piece has kanji. */
    val r: String? = null,
    val m: LocalizedText? = null,
)

@Serializable
data class SceneQuestion(
    val id: SceneQuestionId,
    val kind: Kind,
    val goal: LocalizedText,
    val phrasings: List<Phrasing>,
    val choices: List<SceneChoice>,
    val response: SceneResponse,
    val explanation: LocalizedText,
    val wordIds: List<String>,
) {
    @Serializable
    enum class Kind {
        /** Play a phrasing, pick what it means. */
        @SerialName("listening") Listening,

        /** Look at a register or ordering screen, tap the button to press. */
        @SerialName("screen") Screen,
    }

    /** The one phrasing first practice uses. */
    val firstPhrasing: Phrasing? get() = phrasings.firstOrNull { it.role == Phrasing.Role.First }

    fun phrasing(id: PhrasingId): Phrasing? = phrasings.firstOrNull { it.id == id }

    val correctChoice: SceneChoice? get() = choices.firstOrNull { it.correct }

    /** What the question means, in the user's language. */
    val meaning: LocalizedText? get() = if (kind == Kind.Listening) correctChoice?.text else goal

    private val firstThenReview: List<Phrasing>
        get() = phrasings.filter { it.role == Phrasing.Role.First } + phrasings.filter { it.role == Phrasing.Role.Review }

    /** Who asks it as 進店前看一眼 shows it: whoever speaks the look line. */
    val lookSpeaker: Phrasing.Speaker
        get() = firstThenReview.firstOrNull { it.spoken != null }?.speaker ?: Phrasing.Speaker.Screen

    /**
     * The line 進店前看一眼 shows and plays: the first practice's, or for a
     * screen with nothing said over it, the first phrasing that does speak.
     */
    val lookLine: SpokenLine? get() = firstThenReview.firstNotNullOfOrNull { it.spoken }

    /** The choices in the order one phrasing shows them. */
    fun choices(phrasing: PhrasingId): List<SceneChoice> = mixed(choices, phrasing) { it.id }
}

/** One recorded line: what is said, by which TTS voice, in which bundled file. */
data class SpokenLine(val text: String, val voice: String, val audio: String)

@Serializable(with = PhrasingSerializer::class)
data class Phrasing(
    val id: PhrasingId,
    val role: Role,
    val speaker: Speaker,
    val speed: Speed,
    val ambience: Boolean,
    /** Null only for a screen shown with nothing said over it. */
    val spoken: SpokenLine?,
    val screen: ScreenMock?,
) {
    @Serializable
    enum class Role { @SerialName("first") First, @SerialName("review") Review }

    @Serializable
    enum class Speaker {
        @SerialName("clerk") Clerk,

        /** Counter staff who are not shop clerks, such as at a post office (職員). */
        @SerialName("staff") Staff,
        @SerialName("register") Register,
        @SerialName("screen") Screen,
    }

    @Serializable
    enum class Speed { @SerialName("normal") Normal, @SerialName("fast") Fast }

    /** Review prefers these: the version furthest from the clear first practice. */
    val isHardest: Boolean get() = speed == Speed.Fast && ambience
}

@Serializable
data class SceneChoice(
    /** Unique within its question; on a screen question it also names its button. */
    val id: String,
    val text: LocalizedText,
    val correct: Boolean,
)

@Serializable(with = SceneResponseSerializer::class)
data class SceneResponse(
    /** What to say, or the label of the button to press. */
    val ja: String,
    /** Null when the answer is a button press with nothing to say. */
    val clip: Clip?,
) {
    data class Clip(val audio: String, val voice: String)
}

/**
 * The register or ordering screen a screen question shows, as the scene JSON
 * writes it. Each button names the question's choice it answers.
 */
@Serializable
data class ScreenMock(
    /** What the screen says across its top, in Japanese as the machine would. */
    val heading: String,
    /** A line under the heading, such as the total. */
    val detail: String? = null,
    /** In the order the screen lays them out. */
    val buttons: List<Button>,
) {
    @Serializable
    data class Button(val choiceId: String, val ja: String)

    fun label(choiceId: String): String? = buttons.firstOrNull { it.choiceId == choiceId }?.ja
}
