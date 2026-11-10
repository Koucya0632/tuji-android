package app.tuji.android.core.scenes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One pass through a 場面: the planned steps, and where the current 問句 is.
 *
 * A 問句 is presenting → answered wrong (try again, as often as it takes) →
 * revealed → the next one. Progress is written at the reveal, not at "next",
 * so quitting on the reveal does not make the 問句 owed again. The cursor is
 * written as each 問句 is shown, so quitting before the reveal reopens it.
 */
class ScenePracticeSession(
    val scene: PracticeScene,
    private val store: SceneProgressStore,
    route: StageRoute? = null,
    private val today: () -> LocalDay = LocalDay::today,
) {
    sealed interface QuestionPhase {
        data object Presenting : QuestionPhase
        data class AnsweredWrong(val tried: Set<String>) : QuestionPhase
        data class Revealed(val firstTry: Boolean) : QuestionPhase
    }

    sealed interface Phase {
        data class Practising(val step: Int, val question: QuestionPhase) : Phase
        data class Finished(val summary: Summary) : Phase
    }

    data class QuestionResult(val question: SceneQuestion, val wrongTries: Int, val usedHint: Boolean = false) {
        /** Worth another look on the summary: missed, or got with help. */
        val needsAnotherLook: Boolean get() = wrongTries > 0 || usedHint
    }

    data class Summary(
        /** Missed or helped first, then the rest, each group in the order asked. */
        val results: List<QuestionResult>,
        val status: SceneStatus,
    )

    /** The trip this pass takes, on a staged scene. */
    val route: StageRoute? = route ?: ScenePractice.suggestedRoute(scene, store.book.value, today())
    val steps: List<PracticeStep> = ScenePractice.plan(scene, store.book.value, today(), this.route)

    private val _phase = MutableStateFlow<Phase>(
        Phase.Finished(Summary(emptyList(), ScenePractice.status(scene, store.book.value, today()))),
    )
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val wrongTries = mutableMapOf<Int, Int>()
    private val hinted = mutableSetOf<Int>()

    init {
        if (steps.isNotEmpty()) show(0)
    }

    val currentStep: PracticeStep? get() = (_phase.value as? Phase.Practising)?.let { steps[it.step] }

    val currentQuestion: SceneQuestion? get() = currentStep?.let { scene.questions[it.questionIndex] }

    /** 1-based position for the progress line. */
    val position: Pair<Int, Int>? get() = (_phase.value as? Phase.Practising)?.let { it.step + 1 to steps.size }

    fun answer(choiceId: String) {
        val choice = currentQuestion?.choices?.firstOrNull { it.id == choiceId } ?: return
        settle(choiceId, choice.correct)
    }

    /** A reply said on a staged listening 問句. One that does not fit this trip counts as a try. */
    fun reply(reply: StageReply) = settle("reply:" + reply.id, reply.fits)

    /** A machine run to its end. Only a finish reaches the session, once. */
    fun finish(outcome: MachineOutcome, fits: Boolean) = settle("machine:$outcome", fits)

    /** Slow playback, the subtitle, or 「請再說一次」 on the 問句 on screen. */
    fun noteHint() {
        val phase = _phase.value as? Phase.Practising ?: return
        if (phase.question is QuestionPhase.Revealed) return
        hinted += phase.step
    }

    /**
     * 「請再說一次」: another phrasing of the 問句 on screen. It is marked heard,
     * so tomorrow's review picks one not yet played. Null when every other
     * phrasing has been heard.
     */
    fun askAgain(): SpokenLine? {
        val step = currentStep ?: return null
        val question = currentQuestion ?: return null
        val heard = store.book.value.progress(question.id).usedPhrasingIds + step.phrasing.id
        val phrasing = question.phrasings.firstOrNull { it.spoken != null && it.id !in heard } ?: return null
        noteHint()
        store.update { book ->
            val progress = book.progress(question.id)
            book.copy(questions = book.questions + (question.id to progress.copy(usedPhrasingIds = progress.usedPhrasingIds + phrasing.id)))
        }
        return phrasing.spoken
    }

    private fun settle(key: String, correct: Boolean) {
        val phase = _phase.value as? Phase.Practising ?: return
        val question = currentQuestion ?: return
        val tried = when (val q = phase.question) {
            QuestionPhase.Presenting -> emptySet()
            is QuestionPhase.AnsweredWrong -> q.tried
            is QuestionPhase.Revealed -> return
        }
        if (!correct) {
            wrongTries[phase.step] = (wrongTries[phase.step] ?: 0) + 1
            _phase.value = Phase.Practising(phase.step, QuestionPhase.AnsweredWrong(tried + key))
            return
        }
        _phase.value = Phase.Practising(phase.step, QuestionPhase.Revealed(firstTry = tried.isEmpty()))
        record(steps[phase.step], question, phase.step + 1)
    }

    fun next() {
        val phase = _phase.value as? Phase.Practising ?: return
        if (phase.question !is QuestionPhase.Revealed) return
        if (phase.step + 1 < steps.size) {
            show(phase.step + 1)
            return
        }
        store.update { it.copy(cursors = it.cursors - scene.id) }
        val all = steps.indices.map { index ->
            QuestionResult(scene.questions[steps[index].questionIndex], wrongTries[index] ?: 0, index in hinted)
        }
        _phase.value = Phase.Finished(
            Summary(
                results = all.filter { it.needsAnotherLook } + all.filter { !it.needsAnotherLook },
                status = ScenePractice.status(scene, store.book.value, today()),
            ),
        )
    }

    private fun show(index: Int) {
        _phase.value = Phase.Practising(index, QuestionPhase.Presenting)
        val questionIndex = steps[index].questionIndex
        store.update { it.copy(cursors = it.cursors + (scene.id to questionIndex)) }
    }

    private fun record(step: PracticeStep, question: SceneQuestion, nextStep: Int) {
        val following = steps.getOrNull(nextStep)?.questionIndex
        val day = today()
        store.update { book ->
            book.copy(
                questions = book.questions + (question.id to ScenePractice.completing(book.progress(question.id), step, day)),
                cursors = if (following != null) book.cursors + (scene.id to following) else book.cursors - scene.id,
                lastSceneId = scene.id,
            )
        }
    }
}
