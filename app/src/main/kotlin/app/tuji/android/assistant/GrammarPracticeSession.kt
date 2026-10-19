package app.tuji.android.assistant

import app.tuji.android.core.model.GrammarAttempt
import app.tuji.android.core.model.GrammarPractice
import app.tuji.android.core.model.GrammarPracticeProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Working through one saved practice set — iOS's `GrammarPracticeSession`.
 * Everything here is local: the set came with its answers and reasons, so
 * choosing, checking and redoing never ask the model again and never spend
 * points.
 */
class GrammarPracticeSession(
    val practice: GrammarPractice,
    private val setId: String,
    private val owner: String,
    private val learning: String,
    private val store: GrammarProgressStoring,
) {
    enum class Screen {
        /** Before a run, or between runs: start, continue, or read the answers. */
        Overview,
        Question,
        Summary,
        /** Every question with its answer and reasons, without answering. */
        Review,
    }

    data class State(
        val progress: GrammarPracticeProgress,
        val screen: Screen,
        /** The question on screen. */
        val questionId: String? = null,
        /** The option picked but not yet confirmed. Confirming fixes it. */
        val selection: String? = null,
    ) {
        val attempt: GrammarAttempt? get() = progress.attempts.lastOrNull()
        val unfinished: GrammarAttempt? get() = attempt?.takeIf { !it.finished }
        val confirmed: String? get() = questionId?.let { attempt?.answers?.get(it) }
    }

    data class Score(val correct: Int, val answered: Int, val total: Int)

    private val mutable = MutableStateFlow(
        store.progress(owner, learning, setId).let { progress ->
            // A finished run reopens on its results; anything else on the choice to go on.
            State(progress, if (progress.attempts.lastOrNull()?.finished == true) Screen.Summary else Screen.Overview)
        },
    )
    val state: StateFlow<State> = mutable.asStateFlow()
    private val now get() = mutable.value

    val question: GrammarPractice.Question?
        get() = now.questionId?.let { id -> practice.questions.firstOrNull { it.id == id } }

    /** 第 n／m 題 within the current run. */
    val position: Pair<Int, Int>?
        get() {
            val attempt = now.attempt ?: return null
            val index = attempt.questionIds.indexOf(now.questionId ?: return null)
            return if (index < 0) null else (index + 1) to attempt.questionIds.size
        }

    /** 首次作答: the first run, and only what was answered in it. */
    val firstScore: Score?
        get() {
            val first = now.progress.attempts.firstOrNull { it.isFirst } ?: return null
            return Score(correct(first), first.answers.size, first.questionIds.size)
        }

    fun correct(attempt: GrammarAttempt): Int = attempt.questionIds.count { isCorrect(attempt.answers[it], it) }

    fun wrong(attempt: GrammarAttempt): List<String> =
        attempt.questionIds.filter { id -> attempt.answers[id]?.let { !isCorrect(it, id) } ?: false }

    fun isCorrect(option: String?, question: String): Boolean =
        option != null && practice.questions.firstOrNull { it.id == question }?.answer == option

    /** 開始練習 / 繼續練習. */
    fun start() {
        if (now.unfinished == null) {
            val first = now.progress.attempts.none { it.isFirst } && !now.progress.revealed
            begin(practice.questions.map { it.id }, first)
        }
        moveToNextQuestion()
    }

    /** 重做錯題: the questions the last run got wrong. Free, and never the score. */
    fun redoWrong() {
        val wrong = wrong(now.attempt ?: return)
        if (wrong.isEmpty()) return
        begin(wrong, first = false)
        moveToNextQuestion()
    }

    /** 重做這組: every question again. */
    fun redoAll() {
        begin(practice.questions.map { it.id }, first = false)
        moveToNextQuestion()
    }

    /** 查看題目與解析. Read before the first run is done, that run can no longer be the score. */
    fun review() {
        var progress = now.progress
        if (progress.attempts.none { it.isFirst && it.finished }) {
            // A first run cut short becomes practice: its answers were made before the key was read.
            progress = progress.copy(
                revealed = true,
                attempts = progress.attempts.map { if (it.isFirst && !it.finished) it.copy(isFirst = false) else it },
            )
            save(progress)
        }
        mutable.value = now.copy(progress = progress, screen = Screen.Review)
    }

    /** Back from the answers, or from results, to the choice of what to do next. */
    fun showOverview() {
        mutable.value = now.copy(screen = if (now.attempt?.finished == true) Screen.Summary else Screen.Overview)
    }

    fun select(option: String) {
        if (now.confirmed == null) mutable.value = now.copy(selection = option)
    }

    /** 確認答案: fixes the choice and shows whether it was right, with every reason. */
    fun confirm() {
        val id = now.questionId ?: return
        val option = now.selection ?: return
        val attempt = now.attempt ?: return
        if (now.confirmed != null || attempt.finished || attempt.answers[id] != null) return
        val progress = now.progress.copy(
            attempts = now.progress.attempts.dropLast(1) + attempt.copy(answers = attempt.answers + (id to option)),
        )
        save(progress)
        mutable.value = now.copy(progress = progress)
    }

    /** 下一題, or the results after the last one. Never automatic: the reasons stay until the person moves on. */
    fun next() {
        if (now.confirmed != null) moveToNextQuestion()
    }

    private fun moveToNextQuestion() {
        val attempt = now.attempt ?: return
        val id = attempt.questionIds.firstOrNull { attempt.answers[it] == null }
        mutable.value = now.copy(
            selection = null,
            questionId = id,
            screen = if (id != null) Screen.Question else Screen.Summary,
        )
    }

    private fun begin(ids: List<String>, first: Boolean) {
        val progress = now.progress.copy(
            attempts = now.progress.attempts + GrammarAttempt(UUID.randomUUID().toString().lowercase(), first, ids),
        )
        save(progress)
        mutable.value = now.copy(progress = progress)
    }

    private fun save(progress: GrammarPracticeProgress) = store.save(progress, owner, learning, setId)
}
