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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.BuildConfig
import app.tuji.android.R
import app.tuji.android.core.design.TujiCheckbox
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiSection
import app.tuji.android.core.design.TujiSettingRow
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.Category
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
    categories: List<Category>,
    uiLang: String,
    busy: SettingsBusy,
    bottomPadding: Dp,
    onChange: ((UserSettings) -> UserSettings) -> Unit,
    onClearProgress: () -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = TujiType.h1,
            color = TujiColor.Ink,
            modifier = Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
        )

        TujiSection(title = stringResource(R.string.settings_group_study)) {
            TujiSettingRow(
                label = stringResource(R.string.settings_direction),
                subtitle = stringResource(R.string.settings_direction_why),
                value = directionLabel(settings.direction),
                onClick = { picker = Picker.Direction },
            )
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(R.string.settings_daily_goal),
                subtitle = stringResource(R.string.settings_daily_goal_why),
                showsArrow = false,
                trailing = {
                    // The number lives *inside* the trailing slot, not in
                    // `value`: passing both draws the stepper and drops the
                    // count, which left the row asking the user to adjust a
                    // quantity it never showed them.
                    Text(
                        stringResource(R.string.settings_goal_value, settings.dailyGoal),
                        style = TujiType.body,
                        color = TujiColor.Ink2,
                    )
                    Stepper(
                        value = settings.dailyGoal,
                        onChange = { next ->
                            onChange { it.copy(dailyGoal = SettingsRules.clampDailyGoal(next)) }
                        },
                    )
                },
            )
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(R.string.settings_themes),
                subtitle = stringResource(R.string.settings_themes_why),
                value = themesLabel(settings.studyCategories, categories, uiLang),
                onClick = { picker = Picker.Themes },
            )
            TujiRowDivider()
            TujiSettingRow(
                label = stringResource(R.string.settings_show_zh),
                showsArrow = false,
                trailing = {
                    TujiCheckbox(settings.showZh) { on -> onChange { it.copy(showZh = on) } }
                },
            )
        }

        TujiSection(title = stringResource(R.string.settings_group_display)) {
            TujiSettingRow(
                label = stringResource(R.string.settings_language),
                value = languageLabel(settings.language),
                onClick = { picker = Picker.Language },
            )
            // Only while learning English: a Japanese recording has one accent,
            // and a control that changes nothing is worse than no control.
            if (SettingsRules.accentApplies(settings.learningDirection)) {
                TujiRowDivider()
                TujiSettingRow(
                    label = stringResource(R.string.settings_accent),
                    value = accentLabel(settings.accent),
                    onClick = { picker = Picker.Accent },
                )
            }
        }

        TujiSection(title = stringResource(R.string.settings_group_account)) {
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

        // Multi-select, so it stays open: picking themes is several taps, and a
        // sheet that closed after each one would have to be reopened five times.
        Picker.Themes -> ThemeSheet(
            selected = settings.studyCategories,
            categories = categories,
            uiLang = uiLang,
            onToggle = { id ->
                onChange { it.copy(studyCategories = SettingsRules.toggleCategory(it.studyCategories, id)) }
            },
            onDismiss = { picker = null },
        )

        Picker.About -> AboutSheet(onDismiss = { picker = null })
        null -> Unit
    }

    when (confirm) {
        Confirm.SignOut -> TujiPrompt(
            title = stringResource(R.string.settings_sign_out_title),
            message = stringResource(R.string.settings_sign_out_message),
            confirm = stringResource(R.string.me_sign_out),
            cancel = stringResource(R.string.study_leave_cancel),
            onConfirm = { confirm = null; onSignOut() },
            onCancel = { confirm = null },
        )

        Confirm.Clear -> TujiPrompt(
            title = stringResource(R.string.settings_clear_title),
            message = stringResource(R.string.settings_clear_footer),
            confirm = stringResource(R.string.settings_clear),
            cancel = stringResource(R.string.study_leave_cancel),
            onConfirm = { confirm = null; onClearProgress() },
            onCancel = { confirm = null },
        )

        // Two prompts, not one. Deleting an account is the only thing in this
        // app that cannot be undone by any means, and a single tap-through is
        // how it happens by accident.
        Confirm.DeleteFirst -> TujiPrompt(
            title = stringResource(R.string.settings_delete_title),
            message = stringResource(R.string.settings_delete_message),
            confirm = stringResource(R.string.settings_delete_continue),
            cancel = stringResource(R.string.study_leave_cancel),
            onConfirm = { confirm = Confirm.DeleteSecond },
            onCancel = { confirm = null },
        )

        Confirm.DeleteSecond -> TujiPrompt(
            title = stringResource(R.string.settings_delete_last_title),
            message = stringResource(R.string.settings_delete_last_message),
            confirm = stringResource(R.string.settings_delete),
            cancel = stringResource(R.string.study_leave_cancel),
            onConfirm = { confirm = null; onDeleteAccount() },
            onCancel = { confirm = null },
        )

        null -> Unit
    }
}

/** Whether one of the two irreversible calls is in flight. */
data class SettingsBusy(val clearing: Boolean = false, val deleting: Boolean = false)

private enum class Picker { Direction, Language, Accent, Themes, About }

private enum class Confirm { SignOut, Clear, DeleteFirst, DeleteSecond }

/**
 * −／＋ either side of nothing.
 *
 * A stepper rather than a sheet of preset numbers: the goal is a small integer
 * the user tunes by one or two, and a list of 5 / 10 / 20 / 50 answers a
 * different question than "a bit more than yesterday".
 */
@Composable
private fun Stepper(value: Int, onChange: (Int) -> Unit) {
    Row {
        StepButton("−", enabled = value > SettingsRules.DAILY_GOAL_MIN) {
            onChange(value - SettingsRules.DAILY_GOAL_STEP)
        }
        Spacer(Modifier.size(TujiSpace.S2))
        StepButton("＋", enabled = value < SettingsRules.DAILY_GOAL_MAX) {
            onChange(value + SettingsRules.DAILY_GOAL_STEP)
        }
    }
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .background(if (enabled) TujiColor.Paper2 else TujiColor.Paper3)
            .tujiClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = TujiType.h3,
            color = if (enabled) TujiColor.Ink else TujiColor.Ink3,
        )
    }
}

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
private fun themesLabel(
    selected: List<String>,
    categories: List<Category>,
    uiLang: String,
): String = if (selected.isEmpty()) {
    stringResource(R.string.settings_themes_all)
} else {
    stringResource(R.string.settings_themes_count, selected.size)
}
