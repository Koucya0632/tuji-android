package app.tuji.android.core.study

import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyReportPayload
import app.tuji.android.core.model.StudyReportSnapshot

/** The six kinds of 報錯 — iOS's `StudyReportIssueType`, in its order. */
enum class StudyReportIssue(val wire: String) {
    Image("image"),
    Content("content"),
    Audio("audio"),
    Answer("answer"),
    Ui("ui"),
    Other("other"),
}

/**
 * The part of a 報錯 only the session knows: which card, at what point, and
 * what the user had chosen.
 */
data class StudyReportSubject(
    val item: StudyQueueItem,
    val phase: String,
    val selectedAnswer: String?,
)

/**
 * The rules of a study 報錯, kept off the screen so both sessions share one
 * copy — iOS's `StudySessionShell.report` and `StudyReportSheet.submit`.
 */
object StudyReports {
    /** What the description box will hold. */
    const val DETAIL_LIMIT = 1000

    private const val CUSTOM_PREFIX = "atlas:"

    /**
     * Whether `/api/study/reports` can take this card. A 自製卡片 has no
     * cards-table row, so the server cannot accept it — the screen explains
     * instead of silently dropping the tap.
     */
    fun accepts(item: StudyQueueItem): Boolean {
        val id = item.card.id
        return !(id.startsWith(CUSTOM_PREFIX) && id.length > CUSTOM_PREFIX.length)
    }

    /**
     * 複習: the card on screen, whether it has been answered, and what was
     * chosen — the pick that ended it, or the options ruled out so far.
     */
    fun review(question: ReviewQuestion?): StudyReportSubject? = question?.let {
        StudyReportSubject(
            item = it.item,
            phase = if (it.phase == ReviewPhase.Answer) "answer" else "reveal",
            selectedAnswer = it.reportedSelection,
        )
    }

    /** The description as it will be sent. */
    fun trimmed(detail: String): String = detail.trim()

    /** Both a kind and a description are required, as on iOS. */
    fun canSubmit(issue: StudyReportIssue?, detail: String, submitting: Boolean): Boolean =
        issue != null && trimmed(detail).isNotEmpty() && !submitting

    fun payload(
        requestId: String,
        subject: StudyReportSubject,
        mode: StudyMode,
        issue: StudyReportIssue,
        detail: String,
        appVersion: String,
        uiLang: String,
    ): StudyReportPayload {
        val item = subject.item
        return StudyReportPayload(
            requestId = requestId,
            wordId = item.word.id,
            cardId = item.card.id,
            issueType = issue.wire,
            description = trimmed(detail),
            mode = mode.wire,
            phase = subject.phase,
            selectedAnswer = subject.selectedAnswer,
            platform = "android",
            appVersion = appVersion,
            uiLang = uiLang,
            snapshot = StudyReportSnapshot(
                word = item.word.word,
                chinese = item.word.chinese,
                imageUrl = item.word.imageUrl,
                pronunciation = item.word.pronunciation,
                category = item.word.category,
                cardType = item.card.cardType,
                deckKey = item.card.deckKey,
                choices = item.choices.orEmpty(),
                spellingChoices = item.spellingChoices.orEmpty(),
            ),
        )
    }

    /**
     * What a 拼字 board says so far. Tiles read as the string they spell; a
     * gap-fill reads as the word with the learner's chunks in its holes and
     * `_` in the ones still empty. Null before the first pick.
     */
    fun spellAttempt(form: SpellForm, chosen: List<String>): String? {
        if (chosen.isEmpty()) return null
        return when (form) {
            is SpellForm.Tiles -> chosen.joinToString("")
            is SpellForm.Gaps -> buildString {
                form.plan.segments.forEachIndexed { index, segment ->
                    append(segment)
                    if (index < form.plan.gaps.size) append(chosen.getOrNull(index) ?: "_")
                }
            }
        }
    }
}
