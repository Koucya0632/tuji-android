package app.tuji.android.core.study

import app.tuji.android.core.model.CategoryProgress
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyStats

/**
 * What 今日 says and offers, derived from the day's state — iOS's `TodayDecisions`.
 *
 * Enums, not strings: the screen owns the words, this owns the verdict. That
 * split is what keeps it testable without a resource bundle, and it is also
 * what stops a hard-coded Chinese line leaking into a ja/en UI from here —
 * there is no string to leak.
 *
 * The ink block answers three questions now — [heading], [call] and
 * [footer] — where it used to have a seven-case subtitle and a three-case
 * hint. Those went with the 複習／學新字 pair and the 今日目標 bar, as on iOS.
 *
 * This used to be a deliberate subset, missing iOS's 主題 cases because
 * Android had no theme selection or completion readout yet. It has both now,
 * so what is left to learn is counted over the themes being studied, and the
 * no-themes and empty-themes dead ends are named as iOS names them.
 */
data class TodayInputs(
    /** Null until the first fetch lands. */
    val stats: StudyStats? = null,
    val dailyGoal: Int = DEFAULT_DAILY_GOAL,
    /**
     * The selected themes' readout — the hero's 主題進度. With it, what is
     * left to learn is counted over the themes being studied; without it, the
     * server's `new` stands in.
     */
    val completion: CompletionReadout? = null,
    /** Whether the per-theme progress has arrived; until then its 0s are not facts. */
    val progressLoaded: Boolean = false,
    /** Every free theme's progress, for a non-member; null for everyone else. */
    val freeThemes: FreeThemesProgress? = null,
) {
    companion object {
        /** iOS's `UserSettings` default. */
        const val DEFAULT_DAILY_GOAL = 10
    }
}

/**
 * What the ink block's title says — iOS's `TodayHeading`. The screen looks the
 * id up for a name; this module answers whether there are themes, which comes
 * first, and how many.
 */
sealed interface TodayHeading {
    /** Settings have not arrived: an empty list means nothing yet. */
    data object Unknown : TodayHeading
    data object NoThemes : TodayHeading
    data class Themes(val firstId: String, val count: Int) : TodayHeading
}

/**
 * What the one 背單字 button will do — iOS's `TodayCall`.
 *
 * It replaced the 複習／學新字 pair. The mode is picked here, not by the user:
 * review when anything is due, new words otherwise. [TodayDecisions.footer]'s
 * [TodayFooter.SwitchToNew] is the way out of that choice.
 */
sealed interface TodayCall {
    /** No themes picked: the button goes and picks some. */
    data object PickThemes : TodayCall

    /** Stats have not arrived. The button still works; it just names no number. */
    data object Unknown : TodayCall
    data class Study(val mode: StudyMode, val count: Int) : TodayCall
    data class Blocked(val block: TodayBlock) : TodayCall
}

/** Why the button is dead. Both are "the picked themes give nothing", for different reasons. */
enum class TodayBlock {
    /** Everything in the picked themes has been learned. */
    ThemesExhausted,

    /**
     * As above, and a non-member has also learned every free theme: each theme
     * they could switch to is locked, so 「換幾個主題」 leads nowhere.
     */
    FreeThemesLearned,

    /** The picked themes have no words in the current deck. */
    NoCards,
}

/** The one line under the button: always another thing the user can do. */
enum class TodayFooter {
    /** Heading for review, but new words are there too. */
    SwitchToNew,

    /** The button is dead; the way on is other themes. */
    ChangeThemes,

    /** The button is dead and every theme to switch to needs membership. */
    UnlockAllThemes,
}

/**
 * How far a non-member is through every theme they may study, picked or not —
 * iOS's `FreeThemesProgress`.
 */
data class FreeThemesProgress(
    /** Published cards across the free themes. */
    val total: Int,
    /**
     * Cards not yet studied, counted theme by theme. Not `total − seen`: a
     * withdrawn word leaves one theme's seen above its total, and summed, that
     * surplus would hide another theme's unlearned words.
     */
    val unseen: Int,
) {
    val allLearned: Boolean get() = total > 0 && unseen == 0

    companion object {
        /**
         * Null when nothing is gated: members, and before the first entitlement
         * sync. An empty gate is not read as "every theme".
         */
        fun of(studyable: List<String>?, progress: List<CategoryProgress>): FreeThemesProgress? {
            if (studyable.isNullOrEmpty()) return null
            val byTheme = progress.associateBy { it.category }
            return FreeThemesProgress(
                total = studyable.sumOf { byTheme[it]?.total ?: 0 },
                unseen = studyable.sumOf { theme -> byTheme[theme]?.let { maxOf(0, it.total - it.seen) } ?: 0 },
            )
        }
    }
}

/** Why 學新字 is unavailable, so the screen can explain the dead end. */
enum class TodayNewBlock {
    None,

    /** No themes picked. The theme prompt below says so; the hero adds no caption. */
    NoThemes,

    /** The picked themes have no words behind them. */
    NoCards,

    /** Nothing left to learn in the themes being studied. */
    AllLearned,

    /** The review backlog has eaten the new-word quota. */
    ReviewBacklog,
}

class TodayDecisions(private val inputs: TodayInputs) {

    private val goal: Int get() = maxOf(1, inputs.dailyGoal)

    private val showThemePrompt: Boolean get() = inputs.completion?.showsThemePrompt == true

    /**
     * New words still to learn: in the selected themes once their progress has
     * loaded, so 學新字 is not lit for words outside what is being studied;
     * the server's count before that. Zero until either lands.
     */
    val newAvailable: Int
        get() {
            val selection = inputs.completion?.inputs
            return if (selection != null && inputs.progressLoaded) {
                maxOf(0, selection.totalInSelection - selection.seenInSelection)
            } else {
                inputs.stats?.new ?: 0
            }
        }

    val reviewDisabled: Boolean
        get() = (inputs.stats?.due ?: 0) == 0

    val newBlock: TodayNewBlock
        get() {
            if (showThemePrompt) return TodayNewBlock.NoThemes
            val selection = inputs.completion?.inputs
            if (selection != null && inputs.progressLoaded && selection.totalInSelection == 0) {
                return TodayNewBlock.NoCards
            }
            // Nothing known yet is not "all learned".
            if (inputs.stats == null && !inputs.progressLoaded) return TodayNewBlock.None
            if (newAvailable == 0) return TodayNewBlock.AllLearned
            // Grey the button rather than let the user in only to bounce back
            // out of an empty session.
            if (StudyQuotas.computeNewLimit(goal = goal, due = inputs.stats?.due ?: 0) == 0) {
                return TodayNewBlock.ReviewBacklog
            }
            return TodayNewBlock.None
        }

    val newDisabled: Boolean
        get() = newBlock != TodayNewBlock.None

    /** 主題進度 — the readout 我 prints too. Null until the screen has one. */
    val completion: CompletionReadout?
        get() = inputs.completion

    val heading: TodayHeading
        get() {
            if (showThemePrompt) return TodayHeading.NoThemes
            val picked = inputs.completion?.inputs?.studyCategories.orEmpty()
            val first = picked.firstOrNull() ?: return TodayHeading.Unknown
            return TodayHeading.Themes(firstId = first, count = picked.size)
        }

    /**
     * How many new words this round draws: the daily goal, capped by what is
     * left. Not `computeNewLimit`: reaching here means nothing is due, and the
     * backlog taper only bites past 20.
     */
    val newCount: Int
        get() = maxOf(0, minOf(newAvailable, goal))

    val call: TodayCall
        get() {
            if (showThemePrompt) return TodayCall.PickThemes
            val stats = inputs.stats ?: return TodayCall.Unknown
            if (stats.due > 0) return TodayCall.Study(StudyMode.Review, stats.due)
            return when (newBlock) {
                TodayNewBlock.NoThemes -> TodayCall.PickThemes
                TodayNewBlock.NoCards -> TodayCall.Blocked(TodayBlock.NoCards)
                TodayNewBlock.AllLearned -> TodayCall.Blocked(
                    if (freeThemesLearned) TodayBlock.FreeThemesLearned else TodayBlock.ThemesExhausted,
                )
                // Unreachable with nothing due (the taper zeroes only past 100);
                // if it ever is reached, it is the same dead end.
                TodayNewBlock.ReviewBacklog -> TodayCall.Blocked(TodayBlock.ThemesExhausted)
                TodayNewBlock.None -> TodayCall.Study(StudyMode.New, newCount)
            }
        }

    val footer: TodayFooter?
        get() = when (val call = call) {
            TodayCall.PickThemes, TodayCall.Unknown -> null
            is TodayCall.Study ->
                if (call.mode == StudyMode.Review && !newDisabled) TodayFooter.SwitchToNew else null
            is TodayCall.Blocked ->
                if (call.block == TodayBlock.FreeThemesLearned) TodayFooter.UnlockAllThemes else TodayFooter.ChangeThemes
        }

    /**
     * A non-member has learned every word in every free theme. Waits for
     * progress: before it lands every count is zero, which is not "all learned".
     */
    val freeThemesLearned: Boolean
        get() = inputs.progressLoaded && inputs.freeThemes?.allLearned == true
}
