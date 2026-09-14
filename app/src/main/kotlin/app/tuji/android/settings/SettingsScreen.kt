package app.tuji.android.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.BuildConfig
import app.tuji.android.R
import app.tuji.android.core.design.TujiCheckbox
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiPromptStyle
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiSection
import app.tuji.android.core.design.TujiSettingRow
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.model.UserSettings
import app.tuji.android.core.study.SettingsRules

/**
 * 設定.
 *
 * Rows in one column with hairlines between them and no cards. What each
 * section groups is what the rows are *about*, not what part of the app they
 * live in: 學習 changes what you are asked, 顯示 changes how it looks, 帳號 is
 * you, and the two irreversible ones sit alone under a footer that names what
 * survives.
 */
@Composable
fun SettingsScreen(
    settings: UserSettings,
    busy: SettingsBusy,
    bottomPadding: Dp,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onClearProgress: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
    onOpenStudyThemes: () -> Unit,
    /** Null for a guest, who has no public profile and nobody to have blocked. */
    onEditProfile: (() -> Unit)?,
    onOpenBlocked: (() -> Unit)?,
    readiness: SettingsReadiness,
    onRetryLoad: () -> Unit,
) {
    // 學習 and 顯示 are the account's settings; 帳號 and below are not, and stay
    // usable while those load — signing out must never wait on a read.
    val ready = readiness == SettingsReadiness.Ready
    val inert = Modifier.alpha(if (ready) 1f else 0.45f)
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TujiScreenTitle(stringResource(R.string.settings_title))

        TujiSection(title = stringResource(R.string.settings_group_study)) {
            SettingsReadinessLine(readiness, onRetryLoad)
            // Values are hidden, not just dimmed, until they are the account's:
            // 「未選主題」 on a seed is a claim that the themes are gone.
            TujiSettingRow(
                label = stringResource(R.string.settings_direction),
                modifier = inert,
                showsArrow = ready,
                subtitle = stringResource(R.string.settings_direction_why),
                value = if (ready) directionLabel(settings.direction) else null,
                onClick = if (ready) ({ picker = Picker.Direction }) else null,
            )
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(R.string.settings_daily_goal),
                modifier = inert,
                showsArrow = ready,
                subtitle = stringResource(R.string.settings_daily_goal_why),
                value = if (ready) stringResource(R.string.settings_goal_value, settings.dailyGoal) else null,
                onClick = if (ready) ({ picker = Picker.DailyGoal }) else null,
            )
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(R.string.settings_themes),
                modifier = inert,
                showsArrow = ready,
                subtitle = stringResource(R.string.settings_themes_why),
                value = if (ready) themesLabel(settings.studyCategories) else null,
                onClick = if (ready) onOpenStudyThemes else null,
            )
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(R.string.settings_show_zh),
                modifier = inert,
                showsArrow = false,
                trailing = if (ready) {
                    { TujiCheckbox(settings.showZh) { on -> onChange { it.copy(showZh = on) } } }
                } else {
                    null
                },
            )
        }

        TujiSection(title = stringResource(R.string.settings_group_display)) {
            TujiSettingRow(
                label = stringResource(R.string.settings_language),
                modifier = inert,
                showsArrow = ready,
                value = if (ready) languageLabel(settings.language) else null,
                onClick = if (ready) ({ picker = Picker.Language }) else null,
            )
            // Only while learning English: a Japanese recording has one accent,
            // and a control that changes nothing is worse than no control.
            if (SettingsRules.accentApplies(settings.learningDirection)) {
                TujiRowDivider()
                TujiSettingRow(
                    label = stringResource(R.string.settings_accent),
                    modifier = inert,
                    showsArrow = ready,
                    value = if (ready) accentLabel(settings.accent) else null,
                    onClick = if (ready) ({ picker = Picker.Accent }) else null,
                )
            }
        }

        TujiSection(title = stringResource(R.string.settings_group_account)) {
            if (onEditProfile != null) {
                TujiSettingRow(label = stringResource(R.string.profile_title), onClick = onEditProfile)
                TujiRowDivider()
            }
            // A block has to be undoable somewhere that does not require
            // finding the person again — which is what blocking them made hard.
            if (onOpenBlocked != null) {
                TujiSettingRow(label = stringResource(R.string.blocked_title), onClick = onOpenBlocked)
                TujiRowDivider()
            }
            TujiSettingRow(
                label = stringResource(R.string.me_sign_out),
                showsArrow = false,
                destructive = true,
                onClick = { confirm = Confirm.SignOut },
            )
        }

        TujiSection(footer = stringResource(R.string.settings_clear_footer)) {
            TujiSettingRow(
                label = stringResource(
                    if (busy.clearing) R.string.settings_clearing else R.string.settings_clear,
                ),
                showsArrow = false,
                destructive = true,
                onClick = if (busy.clearing) null else ({ confirm = Confirm.Clear }),
            )
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(
                    if (busy.deleting) R.string.settings_deleting else R.string.settings_delete,
                ),
                showsArrow = false,
                destructive = true,
                onClick = if (busy.deleting) null else ({ confirm = Confirm.DeleteFirst }),
            )
        }

        TujiSection(title = stringResource(R.string.settings_group_other)) {
            TujiSettingRow(
                label = stringResource(R.string.settings_about),
                onClick = { picker = Picker.About },
            )
        }

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = TujiType.label,
            color = TujiColor.Ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = TujiSpace.S6, bottom = TujiSpace.S5),
        )
        Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
    }

    when (picker) {
        Picker.Direction -> OptionSheet(
            title = stringResource(R.string.settings_direction),
            options = LearningDirection.entries.map { it.wire to directionLabel(it) },
            selected = settings.learningDirection,
            onPick = { wire ->
                onChange { it.copy(learningDirection = wire) }
                picker = null
            },
            onDismiss = { picker = null },
        )

        Picker.Language -> OptionSheet(
            title = stringResource(R.string.settings_language),
            options = UiLanguage.entries.map { it.wire to languageLabel(it) },
            selected = settings.uiLang,
            onPick = { wire ->
                onChange { it.copy(uiLang = wire) }
                picker = null
            },
            onDismiss = { picker = null },
        )

        Picker.Accent -> OptionSheet(
            title = stringResource(R.string.settings_accent),
            options = listOf("us" to accentLabel("us"), "uk" to accentLabel("uk")),
            selected = settings.accent,
            onPick = { value ->
                onChange { it.copy(accent = value) }
                picker = null
            },
            onDismiss = { picker = null },
        )

        // A list of choices rather than the −／＋ it replaces (iOS's picker): a
        // stepper by one takes forty-five taps to go from 5 to 50, and the
        // goal is a pace, not a number anyone tunes by one.
        Picker.DailyGoal -> OptionSheet(
            title = stringResource(R.string.settings_daily_goal),
            options = SettingsRules.dailyGoalOptions(settings.dailyGoal).map {
                it.toString() to stringResource(R.string.settings_goal_value, it)
            },
            selected = settings.dailyGoal.toString(),
            onPick = { value ->
                onChange { it.copy(dailyGoal = SettingsRules.clampDailyGoal(value.toInt())) }
                picker = null
            },
            onDismiss = { picker = null },
            footer = stringResource(R.string.settings_goal_footer),
        )

        Picker.About -> AboutSheet(onDismiss = { picker = null })
        null -> Unit
    }

    when (confirm) {
        Confirm.SignOut -> TujiPrompt(
            title = stringResource(R.string.settings_sign_out_title),
            message = stringResource(R.string.settings_sign_out_message),
            confirm = stringResource(R.string.me_sign_out),
            cancel = stringResource(R.string.cancel),
            onConfirm = { confirm = null; onSignOut() },
            onCancel = { confirm = null },
        )

        Confirm.Clear -> TujiPrompt(
            style = TujiPromptStyle.Destructive,
            title = stringResource(R.string.settings_clear_title),
            message = stringResource(R.string.prompt_irreversible),
            // The footer's wording rather than iOS's, which leaves out 自製圖鑑:
            // what survives is the part of this question people read.
            detail = stringResource(R.string.settings_clear_footer),
            confirm = stringResource(R.string.settings_clear_confirm),
            cancel = stringResource(R.string.cancel),
            onConfirm = { confirm = null; onClearProgress() },
            onCancel = { confirm = null },
        )

        // Two prompts, not one. Deleting an account is the only thing in this
        // app that cannot be undone by any means, and a single tap-through is
        // how it happens by accident.
        Confirm.DeleteFirst -> TujiPrompt(
            style = TujiPromptStyle.Destructive,
            title = stringResource(R.string.settings_delete_title),
            message = stringResource(R.string.prompt_irreversible),
            detail = stringResource(R.string.settings_delete_message),
            confirm = stringResource(R.string.settings_delete_continue),
            cancel = stringResource(R.string.cancel),
            onConfirm = { confirm = Confirm.DeleteSecond },
            onCancel = { confirm = null },
        )

        Confirm.DeleteSecond -> TujiPrompt(
            style = TujiPromptStyle.Destructive,
            title = stringResource(R.string.settings_delete_last_title),
            message = stringResource(R.string.settings_delete_last_message),
            detail = stringResource(R.string.settings_delete_last_detail),
            confirm = stringResource(R.string.settings_delete_forever),
            cancel = stringResource(R.string.cancel),
            onConfirm = { confirm = null; onDeleteAccount() },
            onCancel = { confirm = null },
        )

        null -> Unit
    }
}

/** Whether one of the two irreversible calls is in flight. */
data class SettingsBusy(val clearing: Boolean = false, val deleting: Boolean = false)

private enum class Picker { Direction, Language, Accent, DailyGoal, About }

private enum class Confirm { SignOut, Clear, DeleteFirst, DeleteSecond }

@Composable
private fun directionLabel(direction: LearningDirection): String = stringResource(
    when (direction) {
        LearningDirection.ZH_EN -> R.string.direction_zh_en
        LearningDirection.ZH_JA -> R.string.direction_zh_ja
    },
)

@Composable
private fun languageLabel(language: UiLanguage): String = stringResource(
    when (language) {
        UiLanguage.ZhHant -> R.string.language_zh_hant
        UiLanguage.ZhHans -> R.string.language_zh_hans
        UiLanguage.Ja -> R.string.language_ja
        UiLanguage.En -> R.string.language_en
    },
)

@Composable
private fun accentLabel(accent: String): String =
    stringResource(if (accent == "uk") R.string.accent_uk else R.string.accent_us)

/**
 * 全部 when nothing is picked — the same reading every other consumer gives an
 * empty selection. Naming the count instead ("0 個主題") would describe a state
 * the app does not actually have.
 */
@Composable
private fun themesLabel(selected: List<String>): String = if (selected.isEmpty()) {
    stringResource(R.string.settings_themes_all)
} else {
    stringResource(R.string.settings_themes_count, selected.size)
}
