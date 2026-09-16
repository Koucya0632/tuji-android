package app.tuji.android.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.catalog.CategoryShelf
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.Category
import app.tuji.android.core.study.SetupChoices
import app.tuji.android.core.study.SettingsRules
import app.tuji.android.settings.ThemeTile
import kotlinx.coroutines.launch

/**
 * 先幫你排一份學習節奏 — the one screen a signed-in account sees before 今日.
 *
 * `LaunchRouting` has computed `Setup` since the launch module was written;
 * `TujiRoot` drew the main shell for it anyway, so nobody on Android had ever
 * seen this. A new account landed on 今日 studying whatever the defaults were,
 * with a daily goal nobody chose.
 *
 * Two questions, both of which the account can already answer — which is why
 * this is a confirmation and not an interview. [SetupChoices.seed] decides what
 * opens ticked, and the reason it takes the account's own settings is that the
 * flag bringing somebody here lives on the *device*: a reinstall runs this
 * again, months in.
 */
@Composable
fun SetupScreen(
    categories: List<Category>,
    uiLang: String,
    account: SetupChoices.AccountThemes?,
    settingsLoaded: Boolean,
    onDone: suspend (topicIds: Set<String>, dailyGoal: Int) -> Result<Unit>,
    onSignOut: () -> Unit,
) {
    var picked by remember { mutableStateOf<Set<String>?>(null) }
    var goal by remember { mutableStateOf(SetupChoices.DEFAULT_DAILY_GOAL) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Seeded once, and only once both halves have arrived. Re-seeding on every
    // settings emission would take a tap back off a tile under the user's
    // finger; seeding before they arrive would open the beginner trio for an
    // account that has its own themes and is about to have them overwritten.
    LaunchedEffect(categories.isNotEmpty(), settingsLoaded) {
        if (picked != null || categories.isEmpty() || !settingsLoaded) return@LaunchedEffect
        val seed = SetupChoices.seed(
            account = account,
            catalogIds = categories.map { it.id }.toSet(),
            firstThemesFallback = categories.map { it.id },
        )
        picked = seed.topicIds
        goal = seed.dailyGoal
    }

    val selection = picked
    // This screen is drawn before any shell, so nothing above it has reserved
    // the system bars — the title ran under the clock without this.
    val insets = WindowInsets.systemBars.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .padding(top = insets.calculateTopPadding()),
    ) {
        Box(Modifier.weight(1f)) {
            if (selection == null) {
                TujiPageLoading(label = stringResource(R.string.setup_loading))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(
                        start = TujiSpace.S4, end = TujiSpace.S4,
                        top = TujiSpace.S3, bottom = TujiSpace.S4,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TujiScreenTitle(stringResource(R.string.setup_title))
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel(stringResource(R.string.setup_topics))
                    }
                    items(categories, key = { it.id }) { category ->
                        ThemeTile(
                            label = CategoryShelf.title(category, uiLang),
                            selected = category.id in selection,
                            onClick = {
                                picked = SettingsRules
                                    .toggleCategory(selection.toList(), category.id)
                                    .toSet()
                            },
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel(
                            stringResource(R.string.setup_goal),
                            Modifier.padding(top = TujiSpace.S3),
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                            // Three, not the six 設定 offers: this is somebody's
                            // first minute, and a six-way choice about a number
                            // they have no feel for yet is a question they
                            // cannot answer.
                            SETUP_GOALS.forEach { option ->
                                ThemeTile(
                                    label = stringResource(R.string.settings_goal_value, option),
                                    selected = goal == option,
                                    onClick = { goal = option },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    if (failed) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                                Text(
                                    stringResource(R.string.setup_failed),
                                    style = TujiType.label,
                                    color = TujiColor.Alert,
                                )
                                // The failure this screen cannot recover from is
                                // a session the server will not accept, and the
                                // only way out of that is a new one.
                                TujiButton(
                                    text = stringResource(R.string.setup_sign_in_again),
                                    style = app.tuji.android.core.design.TujiButtonStyle.Secondary,
                                    onClick = onSignOut,
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
        TujiButton(
            text = stringResource(if (saving) R.string.setup_saving else R.string.setup_done),
            // Not 開始使用: that is the last intro page's button, and this is a
            // second tap in the same run-in.
            enabled = !saving && !selection.isNullOrEmpty(),
            onClick = {
                if (saving || selection.isNullOrEmpty()) return@TujiButton
                saving = true
                failed = false
                scope.launch {
                    val result = onDone(selection, goal)
                    saving = false
                    failed = result.isFailure
                }
            },
            modifier = Modifier
                .padding(
                    start = TujiSpace.S4,
                    end = TujiSpace.S4,
                    top = TujiSpace.S4,
                    bottom = TujiSpace.S4 + insets.calculateBottomPadding(),
                )
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TujiType.label,
        color = TujiColor.Ink3,
        modifier = modifier.padding(bottom = TujiSpace.S1),
    )
}

/** iOS's Setup offers exactly these three. */
private val SETUP_GOALS = listOf(5, 10, 20)
