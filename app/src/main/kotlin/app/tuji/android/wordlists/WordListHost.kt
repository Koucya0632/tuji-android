package app.tuji.android.wordlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.ShellContext
import app.tuji.android.StudyRoute
import app.tuji.android.WordListRoute
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.model.MemberAccessLevel
import app.tuji.android.core.model.MemberFeature
import app.tuji.android.study.StudySource

/** 個人詞表's two pages. */
@Composable
fun WordListHost(route: WordListRoute, shell: ShellContext) {
    val app = shell.app
    val page = shell.from(route)
    when (route) {
        WordListRoute.All -> WordListsScreen(
            store = app.wordListsStore,
            tier = shell.tier,
            bottomPadding = 0.dp,
            onBack = page::pop,
            onOpenList = { page.push(WordListRoute.Detail(it)) },
            onNeedsMembership = shell::openMembership,
        )

        is WordListRoute.Detail -> {
            val vm = remember(route.listId) {
                WordListDetailViewModel(route.listId, app.study).also { it.load() }
            }
            val words = shell.learning.catalog.words
            WordListDetailScreen(
                vm = vm,
                store = app.wordListsStore,
                resolve = { id -> words.firstOrNull { it.id == id } },
                showChinese = shell.settings.showZh,
                bottomPadding = 0.dp,
                onBack = page::pop,
                onOpenWord = { page.openWord(it) },
                onStudy = { mode -> page.push(StudyRoute(mode, StudySource.WordList(route.listId))) },
                onNeedsMembership = shell::openMembership,
            )
        }
    }
}

/**
 * Whether a non-member still has lists decides between the lock and the way
 * in, so they are read whenever the feature exists at all — and again when the
 * learning language changes, since lists are per language. For the whole
 * shell rather than for 我, so the row is right the first time 我 is opened.
 */
@Composable
fun WordListsPreload(shell: ShellContext) {
    val exists = shell.access(MemberFeature.WordListBrowse, hasOwnData = true) != MemberAccessLevel.Hidden
    LaunchedEffect(exists, shell.direction) { if (exists) shell.app.wordListsStore.loadIfNeeded() }
}

/** 我's 詞表 row: nothing, a lock, or the way in. */
@Composable
fun MeWordLists(shell: ShellContext) {
    val lists by shell.app.wordListsStore.state.collectAsStateWithLifecycle()
    when (shell.access(MemberFeature.WordListBrowse, lists.lists.isNotEmpty())) {
        MemberAccessLevel.Hidden -> Unit
        MemberAccessLevel.Locked -> MeWordListsRow(locked = true, listCount = 0, onClick = shell::openMembership)
        // A refund keeps the lists: open them read-only.
        MemberAccessLevel.ReadOnly, MemberAccessLevel.Open -> MeWordListsRow(
            locked = false,
            listCount = lists.lists.size,
            onClick = { shell.push(WordListRoute.All) },
        )
    }
}

/** A word page's 加入詞表: official words only — a list holds catalogue ids. */
@Composable
fun WordListSlot(shell: ShellContext, wordId: String) {
    if (CardsSourceRules.isCustom(wordId) || CardsSourceRules.isSaved(wordId)) return
    var adding by rememberSaveable(wordId) { mutableStateOf(false) }
    WordListButton(
        level = shell.access(MemberFeature.WordListAdd),
        onOpen = { adding = true },
        onLocked = shell::openMembership,
    )
    if (adding) {
        AddToWordListSheet(
            wordId = wordId,
            store = shell.app.wordListsStore,
            onDismiss = { adding = false },
            onNeedsUpgrade = {
                adding = false
                shell.openMembership()
            },
        )
    }
}
