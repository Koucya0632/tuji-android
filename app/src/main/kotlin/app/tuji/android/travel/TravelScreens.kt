package app.tuji.android.travel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.atlas.MasteryScale
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiProgressBar
import app.tuji.android.core.design.TujiRow
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordTile
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.TravelCity
import app.tuji.android.core.model.TravelDecisions
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelMapShape
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.model.TravelSpotAccess
import app.tuji.android.core.model.TravelSpotState
import app.tuji.android.wordlists.BlankState

/**
 * The way into 跟著 Moira 去旅遊, at the top of 物見's 探索 shelf.
 *
 * Moira sits on 瞳黃 rather than on the ink: she is a black cat, and on the ink
 * block only her eyes would show.
 */
@Composable
fun TravelEntryBand(destination: TravelDestination, onOpen: () -> Unit) {
    val title = stringResource(R.string.travel_title)
    val subtitle = stringResource(R.string.travel_place_spots, destination.name, TravelDecisions.openSpotCount(destination))
    Row(
        Modifier
            .padding(horizontal = TujiSpace.S4)
            .padding(bottom = TujiSpace.S3)
            .fillMaxWidth()
            .height(112.dp)
            .tujiClickable(onClick = onOpen)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().background(TujiColor.Ink).padding(TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S1),
        ) {
            Text(title, style = TujiType.h3, color = TujiColor.Paper)
            Text(subtitle, style = TujiType.bodySm, color = TujiColor.Paper3)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                Text(stringResource(R.string.travel_see_map), style = TujiType.bodySmStrong, color = TujiColor.BrandPrimary)
                TujiGlyph.ArrowRight(size = 12.dp, tint = TujiColor.BrandPrimary)
            }
        }
        Box(Modifier.width(112.dp).fillMaxHeight().background(TujiColor.BrandPrimary), contentAlignment = Alignment.Center) {
            Image(
                painterResource(MascotPose.Think.res),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(TujiSpace.S1),
            )
        }
    }
}

@Composable
private fun TravelScaffold(onBack: () -> Unit, bottomPadding: Dp, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(onLeading = onBack, leadingLabel = stringResource(R.string.atlas_back))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            content()
            Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
        }
    }
}

/** What a travel screen shows when its place is not in the answer. */
@Composable
private fun TravelMissing(phase: TravelStore.Phase, onRetry: () -> Unit) {
    when (phase) {
        TravelStore.Phase.Failed -> BlankState(text = stringResource(R.string.load_failed), onRetry = onRetry)
        // The place closed while this screen was on the stack.
        TravelStore.Phase.Loaded -> BlankState(text = stringResource(R.string.travel_not_open_here))
        TravelStore.Phase.Idle, TravelStore.Phase.Loading -> TujiPageLoading(label = stringResource(R.string.atlas_loading))
    }
}

/**
 * One destination: the country as a grid of cells, lit where there is a city
 * to open, and those cities listed underneath.
 */
@Composable
fun TravelDestinationScreen(
    state: TravelStore.State,
    destinationId: String,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenCity: (String) -> Unit,
) {
    TravelScaffold(onBack, bottomPadding) {
        val destination = state.destination(destinationId)
        if (destination == null) {
            TravelMissing(state.phase, onRetry)
            return@TravelScaffold
        }
        TujiScreenTitle(destination.name)
        TravelGridMap(destination, Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S4))
        val open = destination.regions.flatMap { region -> destination.openCities(region).map { region to it } }
        open.forEachIndexed { index, (region, city) ->
            if (index > 0) TujiRowDivider()
            TujiRow(
                onClick = { onOpenCity(city.id) },
                trailing = { TujiGlyph.ArrowRight(size = 14.dp, tint = TujiColor.Ink3) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(TujiColor.BrandPrimary).border(TujiBorder.Bw2, TujiColor.Ink))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(city.name, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        Text(
                            stringResource(R.string.travel_place_spots, region.name, city.openSpots.size),
                            style = TujiType.bodySm,
                            color = TujiColor.Ink3,
                        )
                    }
                }
            }
        }
        if (destination.regions.any { destination.openCities(it).isEmpty() }) {
            if (open.isNotEmpty()) TujiRowDivider()
            TujiRow {
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).background(TujiColor.Paper3))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.travel_other_regions), style = TujiType.bodyStrong, color = TujiColor.Ink2)
                        Text(stringResource(R.string.travel_not_open), style = TujiType.bodySm, color = TujiColor.Ink3)
                    }
                }
            }
        }
    }
}

/** The country, one square per cell. */
@Composable
private fun TravelGridMap(destination: TravelDestination, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.travel_map_of, destination.name)
    val gap = 2.dp
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        // Wider than this and the cells stop reading as a map of anything.
        BoxWithConstraints(Modifier.widthIn(max = 360.dp).fillMaxWidth().clearAndSetSemantics { contentDescription = label }) {
            val side = TravelDecisions.cellSide(maxWidth.value, destination.grid.columns, gap.value).dp
            val height = side * destination.grid.rows + gap * (destination.grid.rows - 1).coerceAtLeast(0)
            fun at(index: Float): Dp = TravelDecisions.cellOffset(index, side.value, gap.value).dp
            Box(Modifier.fillMaxWidth().height(height)) {
                destination.regions.forEach { region ->
                    val lit = destination.openCities(region).isNotEmpty()
                    region.tiles.forEach { tile ->
                        Box(
                            Modifier
                                .offset(x = at(tile.x), y = at(tile.y))
                                .size(side)
                                .background(if (lit) TujiColor.BrandPrimary else TujiColor.Paper3)
                                .then(if (lit) Modifier.border(TujiBorder.Bw2, TujiColor.Ink) else Modifier),
                        )
                    }
                    CenteredAt(at(region.nameAt.x), at(region.nameAt.y)) {
                        Text(
                            region.name,
                            style = if (lit) TujiType.bodySmStrong else TujiType.label,
                            color = if (lit) TujiColor.Ink else TujiColor.Ink2,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                // The lower right of the grid is open sea on every map drawn
                // so far, which is where Moira stands.
                Image(
                    painterResource(MascotPose.Wave.res),
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.BottomEnd).size(side * 4.5f),
                )
            }
        }
    }
}

/** Places `content` so its centre is at (`x`, `y`) of the parent, whatever its own size. */
@Composable
private fun CenteredAt(x: Dp, y: Dp, content: @Composable () -> Unit) {
    val (px, py) = with(LocalDensity.current) { x.roundToPx() to y.roundToPx() }
    Layout(content) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(0, 0) { placeable.place(px - placeable.width / 2, py - placeable.height / 2) }
    }
}

/**
 * One city: a schematic map with its 景點 on it, and the same spots as a list.
 *
 * The map is not for finding the way: it says roughly where each spot is
 * relative to things a visitor already recognises.
 */
@Composable
fun TravelCityScreen(
    state: TravelStore.State,
    cityId: String,
    destinationId: String,
    scores: Map<String, Int>,
    studyable: List<String>?,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenSpot: (String) -> Unit,
) {
    TravelScaffold(onBack, bottomPadding) {
        val city = state.city(cityId, destinationId)
        if (city == null) {
            TravelMissing(state.phase, onRetry)
            return@TravelScaffold
        }
        TujiScreenTitle(city.name)
        TravelCityMap(city, onOpenSpot)
        Spacer(Modifier.height(TujiSpace.S3))
        city.spots.forEachIndexed { index, spot ->
            if (index > 0) TujiRowDivider()
            TujiRow(
                onClick = if (spot.open) ({ onOpenSpot(spot.id) }) else null,
                trailing = { SpotRowTrailing(spot, scores, studyable) },
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                    SpotMarker(index + 1, spot.open)
                    Text(spot.name, style = TujiType.bodyStrong, color = if (spot.open) TujiColor.Ink else TujiColor.Ink3)
                }
            }
        }
    }
}

@Composable
private fun SpotRowTrailing(spot: TravelSpot, scores: Map<String, Int>, studyable: List<String>?) {
    if (!spot.open) {
        Text(stringResource(R.string.travel_not_open), style = TujiType.bodySm, color = TujiColor.Ink3)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
        if (TravelDecisions.access(spot, studyable) == TravelSpotAccess.NeedsMembership) {
            val locked = stringResource(R.string.travel_spot_locked)
            TujiGlyph.Lock(size = 11.dp, tint = TujiColor.Ink3, modifier = Modifier.semantics { contentDescription = locked })
        } else if (studyable != null) {
            // Only a gated account is told a spot is free — to a member every
            // spot is, and the word would be noise.
            Text(
                stringResource(R.string.travel_free),
                style = TujiType.label,
                color = TujiColor.Ink2,
                modifier = Modifier.background(TujiColor.Paper2).padding(horizontal = TujiSpace.S2, vertical = TujiSpace.S1),
            )
        }
        val studied = TravelDecisions.studiedCount(spot, scores)
        if (studied > 0) {
            Text(
                stringResource(R.string.travel_studied_of, studied, spot.wordIds.size),
                style = TujiType.bodySmStrong,
                color = TujiColor.AccumulationDeep,
            )
        } else {
            Text(stringResource(R.string.travel_things, spot.wordIds.size), style = TujiType.bodySm, color = TujiColor.Ink2)
        }
        TujiGlyph.ArrowRight(size = 14.dp, tint = TujiColor.Ink3)
    }
}

/** The numbered square that stands for a spot, on the map and in the list. */
@Composable
private fun SpotMarker(number: Int, open: Boolean) {
    Box(
        Modifier.size(28.dp).background(if (open) TujiColor.Ink else TujiColor.Paper3).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = TujiType.bodySmStrong, color = if (open) TujiColor.Paper else TujiColor.Ink3)
    }
}

@Composable
private fun TravelCityMap(city: TravelCity, onOpenSpot: (String) -> Unit) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(city.aspect).background(TujiColor.Paper2).clipToBounds(),
    ) {
        val width = maxWidth
        val height = maxHeight
        Canvas(Modifier.fillMaxSize()) { city.shapes.forEach { drawShape(it) } }
        city.shapes.forEach { shape ->
            if (shape.kind in DRAWN_KINDS) {
                CenteredAt(width * shape.nameAt.x, height * shape.nameAt.y) {
                    Text(
                        shape.name,
                        style = TujiType.label,
                        color = if (shape.isWater) TujiColor.AccumulationDeep else TujiColor.Ink2,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
        }
        city.spots.forEachIndexed { index, spot ->
            SpotPin(spot, index + 1, width * spot.at.x, height * spot.at.y, onOpenSpot)
        }
    }
}

/**
 * A marker with its name hanging off one side. The square stays centred on the
 * spot's position whichever side that is, so the layout measures the name
 * separately instead of centring the pair.
 */
@Composable
private fun SpotPin(spot: TravelSpot, number: Int, x: Dp, y: Dp, onOpenSpot: (String) -> Unit) {
    val closed = stringResource(R.string.travel_spot_closed, spot.name)
    val (px, py) = with(LocalDensity.current) { x.roundToPx() to y.roundToPx() }
    val gap = with(LocalDensity.current) { 6.dp.roundToPx() }
    Layout(
        content = {
            Box(
                Modifier
                    .size(44.dp)
                    .then(if (spot.open) Modifier.tujiClickable { onOpenSpot(spot.id) } else Modifier)
                    .clearAndSetSemantics { contentDescription = if (spot.open) spot.name else closed },
                contentAlignment = Alignment.Center,
            ) { SpotMarker(number, spot.open) }
            Text(
                spot.name,
                style = TujiType.bodySmStrong,
                color = if (spot.open) TujiColor.Ink else TujiColor.Ink3,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics {},
            )
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val target = measurables[0].measure(loose)
        val name = measurables[1].measure(loose)
        // The visible square is 28dp inside the 44dp target.
        val square = target.width * 28 / 44
        layout(0, 0) {
            target.place(px - target.width / 2, py - target.height / 2)
            val nameX = if (spot.labelOnLeft) px - square / 2 - gap - name.width else px + square / 2 + gap
            name.place(nameX, py - name.height / 2)
        }
    }
}

private val DRAWN_KINDS = setOf(TravelMapShape.KIND_RING, TravelMapShape.KIND_RIVER, TravelMapShape.KIND_WATER)

private fun DrawScope.drawShape(shape: TravelMapShape) {
    fun place(x: Float, y: Float) = Offset(x * size.width, y * size.height)
    when (shape.kind) {
        TravelMapShape.KIND_WATER -> {
            val first = shape.points.firstOrNull() ?: return
            val path = Path().apply {
                moveTo(first.x * size.width, first.y * size.height)
                shape.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                close()
            }
            drawPath(path, TujiColor.AccumulationSoft)
        }
        TravelMapShape.KIND_RIVER -> {
            val first = shape.points.firstOrNull() ?: return
            // A smooth line through the points: each one is the end of a curve
            // whose control point is the point before it.
            var previous = place(first.x, first.y)
            val path = Path().apply {
                moveTo(previous.x, previous.y)
                shape.points.drop(1).forEach { point ->
                    val next = place(point.x, point.y)
                    quadraticTo(previous.x, previous.y, (previous.x + next.x) / 2, (previous.y + next.y) / 2)
                    previous = next
                }
                lineTo(previous.x, previous.y)
            }
            drawPath(
                path,
                TujiColor.AccumulationSoft,
                style = Stroke(width = size.width * 0.036f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        TravelMapShape.KIND_RING -> {
            val center = shape.center ?: return
            val radius = shape.radius ?: return
            drawOval(
                TujiColor.Ink,
                topLeft = place(center.x - radius.x, center.y - radius.y),
                size = Size(radius.x * size.width * 2, radius.y * size.height * 2),
                style = Stroke(
                    width = TujiBorder.Bw2.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 4.dp.toPx())),
                ),
            )
        }
        // A kind this build does not know: not drawn, not named.
        else -> Unit
    }
}

/**
 * One 景點: how far through its words this account is, a way to study them,
 * and the words themselves. Browsing is never gated; studying follows the spot.
 */
@Composable
fun TravelSpotScreen(
    state: TravelStore.State,
    spotId: String,
    spotState: (TravelSpot) -> TravelSpotState,
    score: (String) -> Int?,
    bottomPadding: Dp,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenWord: (String) -> Unit,
    onStudy: (StudyMode) -> Unit,
    onNeedsMembership: () -> Unit,
) {
    TravelScaffold(onBack, bottomPadding) {
        val spot = state.spot(spotId)
        if (spot == null) {
            TravelMissing(state.phase, onRetry)
            return@TravelScaffold
        }
        val s = spotState(spot)
        Column(
            Modifier.padding(horizontal = TujiSpace.S4).fillMaxWidth().background(TujiColor.Ink).padding(TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                Text(spot.name, style = TujiType.h1, color = TujiColor.Paper, modifier = Modifier.semantics { heading() })
                Text("${spot.native} · ${spot.reading}", style = TujiType.bodySm, color = TujiColor.Paper3)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                TujiProgressBar(
                    progress = s.progress.toDouble(),
                    modifier = Modifier.weight(1f).height(6.dp),
                    track = TujiColor.Ink2,
                    fill = TujiColor.AccumulationSoft,
                )
                Text(stringResource(R.string.travel_words_of, s.studied, s.total), style = TujiType.bodySmStrong, color = TujiColor.Paper)
            }
            SpotActions(s, onStudy, onNeedsMembership)
        }
        Text(
            stringResource(R.string.travel_seen_here),
            style = TujiType.label,
            color = TujiColor.Ink3,
            modifier = Modifier.padding(horizontal = TujiSpace.S4).padding(top = TujiSpace.S4, bottom = TujiSpace.S2),
        )
        // Three to a row, laid out by hand: the page already scrolls, and a
        // lazy grid inside a scrolling column has no height to measure against.
        Column(Modifier.padding(horizontal = TujiSpace.S4), verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            s.words.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    row.forEach { word ->
                        WordTile(
                            word = word,
                            modifier = Modifier.weight(1f).tujiClickable { onOpenWord(word.id) },
                            badge = { MasteryScale(score(word.id)) },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SpotActions(state: TravelSpotState, onStudy: (StudyMode) -> Unit, onNeedsMembership: () -> Unit) {
    when {
        state.access == TravelSpotAccess.NeedsMembership -> Row(
            Modifier.fillMaxWidth().height(48.dp).background(TujiColor.BrandPrimary).tujiClickable(onClick = onNeedsMembership),
            horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TujiGlyph.Lock(size = 13.dp, tint = TujiColor.Ink)
            Text(stringResource(R.string.travel_spot_needs_membership), style = TujiType.bodySmStrong, color = TujiColor.Ink)
        }

        state.studyModes.isEmpty() -> Text(
            stringResource(if (state.total == 0) R.string.travel_words_on_the_way else R.string.travel_all_studied),
            style = TujiType.bodySm,
            color = TujiColor.Paper3,
        )

        else -> Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            state.studyModes.forEach { mode ->
                Text(
                    stringResource(if (mode == StudyMode.New) R.string.travel_study_here else R.string.study_review_label),
                    style = TujiType.bodySmStrong,
                    color = TujiColor.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .background(if (mode == StudyMode.New) TujiColor.Current else TujiColor.Paper2)
                        .tujiClickable { onStudy(mode) }
                        .padding(vertical = TujiSpace.S3),
                )
            }
        }
    }
}
