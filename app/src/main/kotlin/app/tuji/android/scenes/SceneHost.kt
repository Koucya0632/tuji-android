package app.tuji.android.scenes

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.tuji.android.AppRoute
import app.tuji.android.SceneRoute
import app.tuji.android.ShellContext
import app.tuji.android.core.model.MembershipTier
import app.tuji.android.core.scenes.SceneId

/** 場面練習's three pages. */
@Composable
fun SceneHost(route: SceneRoute, shell: ShellContext) {
    val page = shell.from(route)
    val scenes = shell.app.scenes
    when (route) {
        SceneRoute.All -> SceneListScreen(
            scenes = scenes,
            language = shell.uiLanguage,
            isMember = shell.sceneMember,
            onBack = page::pop,
            onOpen = { page.push(SceneRoute.Detail(it.raw)) },
        )

        is SceneRoute.Detail -> SceneDetailScreen(
            scenes = scenes,
            sceneId = SceneId(route.sceneId),
            language = shell.uiLanguage,
            isMember = shell.sceneMember,
            onBack = page::pop,
            onPractise = { routeId -> page.push(SceneRoute.Practice(route.sceneId, routeId)) },
            onUnlock = shell::openMembership,
            relatedWords = shell::sceneWords,
            onOpenWord = { page.openWord(it, fromScene = route.sceneId) },
        )

        is SceneRoute.Practice -> ScenePracticeScreen(
            scenes = scenes,
            sceneId = SceneId(route.sceneId),
            routeId = route.routeId,
            language = shell.uiLanguage,
            isMember = shell.sceneMember,
            onClose = page::pop,
            onDone = { page.move { select(AppRoute.Today) } },
            onUnlock = shell::openMembership,
            relatedWords = shell::sceneWords,
            onOpenWord = { page.openWord(it, fromScene = route.sceneId) },
        )
    }
}

/** This device's 場面 progress is per account: switched as the account is. */
@Composable
fun SceneProgressAccount(shell: ShellContext) {
    val owner = shell.ownerId
    LaunchedEffect(owner) {
        if (owner != null) shell.app.scenes.progress.use(owner) else shell.app.scenes.progress.reset()
    }
}

/** 今天's scene card; null on a deck with no 場面. */
fun sceneTodayCard(shell: ShellContext): (@Composable (Modifier) -> Unit)? =
    if (!shell.direction.hasScenes) {
        null
    } else {
        { modifier ->
            SceneTodayCard(
                scenes = shell.app.scenes,
                language = shell.uiLanguage,
                isMember = shell.sceneMember,
                onOpenList = { shell.push(SceneRoute.All) },
                onOpen = { shell.push(SceneRoute.Detail(it.raw)) },
                modifier = modifier,
            )
        }
    }

/** A theme page's 場面 row; null on a deck with no 場面. */
fun themeScenes(shell: ShellContext, categoryId: String, from: AppRoute): (@Composable () -> Unit)? =
    if (!shell.direction.hasScenes) {
        null
    } else {
        {
            val words = shell.learning.catalog.words
            val topicOf = remember(words) { words.mapNotNull { w -> w.category?.let { w.id to it } }.toMap() }
            TopicSceneEntries(shell.app.scenes, categoryId, topicOf, shell.uiLanguage) {
                shell.from(from).push(SceneRoute.Detail(it.raw))
            }
        }
    }

/** A word page's 場面 section, leaving out the one it was opened from. */
@Composable
fun WordScenes(shell: ShellContext, wordId: String, fromScene: String?, from: AppRoute, modifier: Modifier) {
    if (!shell.direction.hasScenes) return
    Box(modifier) {
        WordSceneSection(shell.app.scenes, wordId, fromScene?.let(::SceneId), shell.uiLanguage) {
            shell.from(from).push(SceneRoute.Detail(it.raw))
        }
    }
}

/** From the third scene on, practice is for 永久會員. */
private val ShellContext.sceneMember: Boolean get() = tier != MembershipTier.Free

/** A 場面's word ids, as the catalogue's words this account may open. */
private fun ShellContext.sceneWords(ids: List<String>): List<SceneWord> =
    SceneWords.resolve(ids, learning.catalog.words, studyable, direction)
