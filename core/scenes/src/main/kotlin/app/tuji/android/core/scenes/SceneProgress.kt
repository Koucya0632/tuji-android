package app.tuji.android.core.scenes

import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Where one account stands on each 問句, and what to practise next.
//
// A 問句 goes new → awaiting review (due the next local day) → reviewed, once.
// It is kept apart from the word SRS on purpose: a 問句 is learnt when you
// understand it in the shop, not when a forgetting curve says so. Everything
// here is a pure function of the progress book, the content and a day.

/** A calendar day on this device, stored as `yyyy-MM-dd`. */
@Serializable(with = LocalDaySerializer::class)
data class LocalDay(val date: LocalDate) : Comparable<LocalDay> {
    val year: Int get() = date.year
    val month: Int get() = date.monthValue
    val day: Int get() = date.dayOfMonth

    fun addingDays(days: Int): LocalDay = LocalDay(date.plusDays(days.toLong()))

    override fun compareTo(other: LocalDay): Int = date.compareTo(other.date)

    override fun toString(): String = date.toString()

    companion object {
        fun of(year: Int, month: Int, day: Int) = LocalDay(LocalDate.of(year, month, day))
        fun today(): LocalDay = LocalDay(LocalDate.now())
    }
}

object LocalDaySerializer : KSerializer<LocalDay> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDay", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDay) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder) = LocalDay(LocalDate.parse(decoder.decodeString()))
}

@Serializable
sealed interface QuestionStage {
    @Serializable @SerialName("new")
    data object New : QuestionStage

    @Serializable @SerialName("awaitingReview")
    data class AwaitingReview(val due: LocalDay) : QuestionStage

    @Serializable @SerialName("reviewed")
    data object Reviewed : QuestionStage
}

@Serializable
data class QuestionProgress(
    val stage: QuestionStage = QuestionStage.New,
    /** Every phrasing this account has practised, so a review never repeats one. */
    val usedPhrasingIds: Set<PhrasingId> = emptySet(),
)

/** One account's whole 場面練習 record: the file `SceneProgressStore` keeps. */
@Serializable
data class SceneProgressBook(
    val questions: Map<SceneQuestionId, QuestionProgress> = emptyMap(),
    /** Index into the scene's questions of the one to open next. */
    val cursors: Map<SceneId, Int> = emptyMap(),
    /** The scene last answered in, for 今天's card once every scene is done. */
    val lastSceneId: SceneId? = null,
) {
    fun progress(id: SceneQuestionId): QuestionProgress = questions[id] ?: QuestionProgress()
}

enum class PracticeMode {
    /** Never practised. The clear phrasing, with slow playback and a transcript. */
    First,

    /** Due today. A phrasing this account has not heard. */
    Review,

    /** Nothing owed today; practised again without moving its stage. */
    Rehearsal,
}

data class PracticeStep(val questionIndex: Int, val mode: PracticeMode, val phrasing: Phrasing)

/** What the scene list and the scene page say about a scene. */
sealed interface SceneStatus {
    data object NotStarted : SceneStatus
    data class InProgress(val practised: Int, val total: Int) : SceneStatus
    data class ReviewDue(val count: Int) : SceneStatus

    /** Every question practised, the earliest review still ahead. */
    data class ReviewLater(val due: LocalDay) : SceneStatus
    data object Complete : SceneStatus
}

object ScenePractice {
    fun mode(progress: QuestionProgress, today: LocalDay): PracticeMode = when (val stage = progress.stage) {
        QuestionStage.New -> PracticeMode.First
        is QuestionStage.AwaitingReview -> if (stage.due <= today) PracticeMode.Review else PracticeMode.Rehearsal
        QuestionStage.Reviewed -> PracticeMode.Rehearsal
    }

    /**
     * First practice takes the `first` phrasing. A review takes a review phrasing
     * this account has not used, the fast one with ambience first. A rehearsal
     * before the review is due repeats the first phrasing, so it does not spend
     * tomorrow's.
     */
    fun phrasing(question: SceneQuestion, progress: QuestionProgress, mode: PracticeMode): Phrasing? {
        val first = question.firstPhrasing
        val usesFirst = mode == PracticeMode.First ||
            (mode == PracticeMode.Rehearsal && progress.stage !is QuestionStage.Reviewed)
        if (usesFirst) return first
        val reviews = question.phrasings
            .withIndex()
            .filter { it.value.role == Phrasing.Role.Review }
            .sortedWith(compareBy({ if (it.value.isHardest) 0 else 1 }, { it.index }))
            .map { it.value }
        return reviews.firstOrNull { it.id !in progress.usedPhrasingIds } ?: reviews.firstOrNull() ?: first
    }

    /**
     * The questions to practise now, starting from the scene's cursor and
     * wrapping around. Only what is owed today (new or due) when anything is;
     * otherwise the whole scene as a rehearsal.
     */
    fun plan(scene: PracticeScene, book: SceneProgressBook, today: LocalDay, route: StageRoute? = null): List<PracticeStep> {
        val indices = route?.questions?.mapNotNull { id -> scene.questions.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
            ?: scene.questions.indices.toList()
        val all = indices.mapNotNull { index ->
            val question = scene.questions[index]
            val progress = book.progress(question.id)
            val mode = mode(progress, today)
            phrasing(question, progress, mode)?.let { PracticeStep(index, mode, it) }
        }
        val owed = all.filter { it.mode != PracticeMode.Rehearsal }
        val steps = owed.ifEmpty { all }
        val cursor = book.cursors[scene.id]
        val start = when {
            cursor == null -> 0
            route == null -> steps.indexOfFirst { it.questionIndex >= cursor }.takeIf { it >= 0 } ?: 0
            else -> steps.indexOfFirst { it.questionIndex == cursor }.takeIf { it >= 0 } ?: 0
        }
        return steps.drop(start) + steps.take(start)
    }

    /** The progress after `step` is answered. */
    fun completing(progress: QuestionProgress, step: PracticeStep, today: LocalDay): QuestionProgress {
        val used = progress.usedPhrasingIds + step.phrasing.id
        val stage = when (step.mode) {
            PracticeMode.First -> QuestionStage.AwaitingReview(today.addingDays(1))
            PracticeMode.Review -> QuestionStage.Reviewed
            PracticeMode.Rehearsal -> progress.stage
        }
        return QuestionProgress(stage, used)
    }

    fun status(scene: PracticeScene, book: SceneProgressBook, today: LocalDay): SceneStatus {
        val stages = scene.counted(book).map { book.progress(it.id).stage }
        val fresh = stages.count { it == QuestionStage.New }
        val dues = stages.filterIsInstance<QuestionStage.AwaitingReview>().map { it.due }
        val dueNow = dues.count { it <= today }
        return when {
            fresh == stages.size -> SceneStatus.NotStarted
            dueNow > 0 -> SceneStatus.ReviewDue(dueNow)
            fresh > 0 -> SceneStatus.InProgress(stages.size - fresh, stages.size)
            dues.isNotEmpty() -> SceneStatus.ReviewLater(dues.min())
            else -> SceneStatus.Complete
        }
    }

    /**
     * The trip the scene page starts: one with a 問句 due today, so a review
     * meets its own premise again, else the default trip.
     */
    fun suggestedRoute(scene: PracticeScene, book: SceneProgressBook, today: LocalDay): StageRoute? {
        val stage = scene.stage ?: return null
        val due = stage.routes.firstOrNull { route ->
            route.questions.any { mode(book.progress(it), today) == PracticeMode.Review }
        }
        return due ?: stage.defaultRoute
    }
}

/**
 * The 問句 a scene's standing is measured on: its default trip, plus any other
 * 問句 already practised. A route never taken does not hold the scene open.
 */
fun PracticeScene.counted(book: SceneProgressBook): List<SceneQuestion> {
    val route = stage?.defaultRoute ?: return questions
    val core = route.questions.toSet()
    return questions.filter { it.id in core || book.progress(it.id).stage != QuestionStage.New }
}

/** One flag per counted question: practised at least once. */
fun PracticeScene.practised(book: SceneProgressBook): List<Boolean> =
    counted(book).map { book.progress(it.id).stage != QuestionStage.New }
