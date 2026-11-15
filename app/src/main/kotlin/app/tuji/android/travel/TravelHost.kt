package app.tuji.android.travel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.CommunityRoute
import app.tuji.android.ShellContext
import app.tuji.android.StudyRoute
import app.tuji.android.TravelRoute
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.SpotTagging
import app.tuji.android.core.model.TravelDecisions
import app.tuji.android.study.StudySource
import kotlinx.coroutines.launch

/** 跟著 Moira 去旅遊: a destination's map, a city's, and one 景點. */
@Composable
fun TravelHost(route: TravelRoute, shell: ShellContext) {
    val app = shell.app
    val page = shell.from(route)
    val scope = rememberCoroutineScope()
    val travel by app.travelStore.state.collectAsStateWithLifecycle()
    TravelLoad(shell)
    val retry: () -> Unit = { scope.launch { app.travelStore.reload(shell.uiLang, shell.direction) } }
    val scores = shell.learning.scores

    when (route) {
        is TravelRoute.Destination -> TravelDestinationScreen(
            state = travel,
            destinationId = route.destinationId,
            bottomPadding = 0.dp,
            onBack = page::pop,
            onRetry = retry,
            onOpenCity = { page.push(TravelRoute.City(it, route.destinationId)) },
        )

        is TravelRoute.City -> {
            val manage by shell.models.manage.state.collectAsStateWithLifecycle()
            OwnCardsSync(shell)
            TravelCityScreen(
                state = travel,
                cityId = route.cityId,
                destinationId = route.destinationId,
                scores = scores.byId,
                studyable = shell.studyable,
                bottomPadding = 0.dp,
                onBack = page::pop,
                onRetry = retry,
                onOpenSpot = { page.push(TravelRoute.Spot(it)) },
                photographed = SpotTagging.photographedSpotIds(manage.items),
            )
        }

        is TravelRoute.Spot -> {
            val manage by shell.models.manage.state.collectAsStateWithLifecycle()
            val blocked by shell.models.community.blocked.collectAsStateWithLifecycle()
            OwnCardsSync(shell)
            // 「大家拍的」: the same answer for everyone, so it is filtered here.
            val shared by produceState(emptyList<AtlasPublicItem>(), route.spotId) {
                value = runCatching { app.travel.spotPhotos(route.spotId) }.getOrDefault(emptyList())
            }
            val ownIds = manage.items.filter { it.spotId == route.spotId }.map { it.id }.toSet()
            val words = shell.learning.catalog.words
            TravelSpotScreen(
                state = travel,
                spotId = route.spotId,
                spotState = { spot ->
                    TravelDecisions.state(
                        spot, words, scores.byId, scores.nextReviewById, shell.studyable,
                        now = System.currentTimeMillis(),
                    )
                },
                score = scores::score,
                bottomPadding = 0.dp,
                onBack = page::pop,
                onRetry = retry,
                onOpenWord = { page.openWord(it) },
                onStudy = { mode -> page.push(StudyRoute(mode, StudySource.Spot(route.spotId))) },
                onNeedsMembership = shell::openMembership,
                ownWords = shell.learning.personal.mine.filter {
                    it.id.removePrefix(CardsSourceRules.CUSTOM_PREFIX) in ownIds
                },
                shared = SpotTagging.othersShared(shared, manage.items) { blocked.hides(it) },
                onOpenShared = { page.push(CommunityRoute.Item(it)) },
                onOpenAuthor = { page.push(CommunityRoute.Author(it)) },
            )
        }
    }
}

/**
 * The band on 物見's 探索 — the one way in. Asks for the map when it is drawn,
 * and again when either language changes: the map is named in one and exists
 * only for the other being 日文.
 */
@Composable
fun TravelEntry(shell: ShellContext) {
    val travel by shell.app.travelStore.state.collectAsStateWithLifecycle()
    TravelLoad(shell)
    val destination = travel.destinations.firstOrNull() ?: return
    TravelEntryBand(destination) { shell.push(TravelRoute.Destination(destination.id)) }
}

/** The map, for any page that draws it or names its 景點. Public, so it survives sign-out. */
@Composable
fun TravelLoad(shell: ShellContext) {
    LaunchedEffect(shell.uiLang, shell.direction) { shell.app.travelStore.loadIfNeeded(shell.uiLang, shell.direction) }
}

/** The map and a spot's page show this account's own tagged cards, which arrive with 圖鑑管理's sync — not run at launch. */
@Composable
private fun OwnCardsSync(shell: ShellContext) {
    LaunchedEffect(shell.direction) { shell.models.manage.load() }
}
