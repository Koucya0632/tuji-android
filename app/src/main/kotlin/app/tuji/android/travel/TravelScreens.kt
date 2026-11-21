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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.atlas.label
import app.tuji.android.atlas.masterySpoken
import app.tuji.android.core.design.MasteryBadge
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiProgressBar
import app.tuji.android.core.design.TujiRow
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSection
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordTile
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.TravelCity
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelMapShape
import app.tuji.android.core.model.TravelPoint
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.model.Word
import app.tuji.android.core.study.MasteryLevel
import app.tuji.android.core.study.TravelDecisions
import app.tuji.android.core.study.TravelSpotAccess
import app.tuji.android.core.study.TravelSpotState
import app.tuji.android.wordlists.BlankState
import kotlin.math.roundToInt

/*
 * 跟著 Moira 去旅遊 — iOS's `Features/Travel`. The entry on 物見's 探索 shelf,
 * the country as a grid, one city as a schematic map, and one 景點's words.
 */

// MARK: - Entry

/**
 * The way into 跟著 Moira 去旅遊, at the top of 物見's 探索 shelf.
 *
 * Moira sits on 瞳黃 rather than on the ink: she is a black cat, and on the
 * ink block only her eyes would show.
 */
@Composable
fun TravelEntryBand(destination: TravelDestination, onOpen: () -> Unit) {
    val title = stringResource(R.string.travel_title)
    val subtitle = stringResource(
        R.string.travel_entry_subtitle,
        destination.name,
        TravelDecisions.openSpotCount(destination),
    )
    Row(
        Modifier
            .padding(horizontal = TujiSpace.S4)
            .padding(bottom = TujiSpace.S3)
            .fillMaxWidth()
            .height(112.dp)
            .tujiClickable(onClick = onOpen)
            .clearAndSetSemantics { contentDescription = "$title, $subtitle" },
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(TujiColor.Ink)
                .padding(TujiSpace.S3),
        ) {
            Text(title, style = TujiType.h3, color = TujiColor.Paper)
            Spacer(Modifier.height(TujiSpace.S1))
            Text(subtitle, style = TujiType.bodySm, color = TujiColor.Paper3)
            Spacer(Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.travel_see_map), style = TujiType.bodySmStrong, color = TujiColor.Current)
                TujiGlyph.ArrowRight(size = 12.dp, tint = TujiColor.Current)
            }
        }
        Image(
            painter = painterResource(R.drawable.story_moira_check_map),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(112.dp)
                .fillMaxHeight()
                .background(TujiColor.Current)
                .padding(TujiSpace.S1),
        )
    }
}

// MARK: - Shared pieces

/** Loading, failed or closed — what a travel page shows when its place is not in the store. */
@Composable
private fun Missing(phase: TravelStore.Phase, onRetry: () -> Unit) {
    when (phase) {
        TravelStore.Phase.Failed -> BlankState(text = stringResource(R.string.load_failed), onRetry = onRetry)
        // The place closed while this screen was on the stack.
        TravelStore.Phase.Loaded -> BlankState(text = stringResource(R.string.travel_not_open_here))
        else -> TujiPageLoading(label = stringResource(R.string.atlas_loading))
    }
}

@Composable
private fun TravelPage(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        content()
        Spacer(Modifier.height(TujiSpace.S6))
    }
}

/**
 * Places a child so that its point at ([anchorX], 0.5) of its own size sits at
 * ([x], [y]) in the parent — 0 for its leading edge, 0.5 for its centre, 1 for
 * its trailing edge. The child is measured at its own size, as SwiftUI's
 * `.fixedSize().position(…)` does, so a long name is never wrapped by the map.
 */
private fun Modifier.anchoredAt(x: Float, y: Float, anchorX: Float = 0.5f) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) {
        placeable.place((x - placeable.width * anchorX).roundToInt(), (y - placeable.height / 2f).roundToInt())
    }
}

// MARK: - Destination

/**
 * One destination: the country as a grid of cells, lit where there is a city
 * to open, and those cities listed underneath.
 *
 * Nothing is tappable but what is lit; a region with no city yet is the
 * paper's third step, and a single 還沒開 row speaks for all of them.
 */
@Composable
fun TravelDestinationScreen(
    state: TravelStore.State,
    destinationId: String,
    onRetry: () -> Unit,
    onOpenCity: (String) -> Unit,
) {
    TravelPage {
        val destination = state.destination(destinationId)
        if (destination == null) {
            Missing(state.phase, onRetry)
            return@TravelPage
        }
        TujiScreenTitle(destination.name)
        TravelGridMap(
            destination,
            Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S4),
        )
        val open = destination.regions.flatMap { region -> destination.openCities(region).map { region to it } }
        val hasClosedRegion = destination.regions.any { destination.openCities(it).isEmpty() }
        TujiSection {
            open.forEachIndexed { index, (region, city) ->
                if (index > 0) TujiRowDivider()
                TujiRow(
                    onClick = { onOpenCity(city.id) },
                    trailing = { TujiGlyph.ArrowRight(size = 14.dp, tint = TujiColor.Ink3) },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(TujiColor.Current)
                                .border(TujiBorder.Bw2, TujiColor.Ink),
                        )
                        RowLabel(
                            city.name,
                            stringResource(R.string.travel_entry_subtitle, region.name, city.openSpots.size),
                        )
                    }
                }
            }
            if (hasClosedRegion) {
                if (open.isNotEmpty()) TujiRowDivider()
                TujiRow {
                    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).background(TujiColor.Paper3))
                        RowLabel(stringResource(R.string.travel_other_regions), stringResource(R.string.travel_not_open))
                    }
                }
            }
        }
    }
}

@Composable
private fun RowLabel(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = TujiType.bodyStrong, color = TujiColor.Ink)
        Text(subtitle, style = TujiType.bodySm, color = TujiColor.Ink3)
    }
}

/** The country, one square per cell. */
@Composable
private fun TravelGridMap(destination: TravelDestination, modifier: Modifier = Modifier) {
    val columns = destination.grid.columns
    val rows = destination.grid.rows.coerceAtLeast(1)
    val mapLabel = stringResource(R.string.travel_map_label, destination.name)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Wider than this and the cells stop reading as a map of anything.
        BoxWithConstraints(
            Modifier
                .widthIn(max = MAX_GRID_WIDTH)
                .fillMaxWidth()
                .aspectRatio(columns.toFloat() / rows)
                .clearAndSetSemantics { contentDescription = mapLabel },
        ) {
            val density = LocalDensity.current
            val gap = with(density) { GRID_GAP.toPx() }
            val width = with(density) { maxWidth.toPx() }
            val side = TravelDecisions.cellSide(width, columns, gap)
            val stroke = with(density) { TujiBorder.Bw2.toPx() }
            Canvas(Modifier.fillMaxSize()) {
                destination.regions.forEach { region ->
                    val lit = destination.openCities(region).isNotEmpty()
                    region.tiles.forEach { tile ->
                        val (x, y) = TravelDecisions.cellOrigin(tile, side, gap)
                        drawRect(if (lit) TujiColor.Current else TujiColor.Paper3, Offset(x, y), Size(side, side))
                        if (lit) {
                            drawRect(
                                TujiColor.Ink,
                                Offset(x + stroke / 2, y + stroke / 2),
                                Size(side - stroke, side - stroke),
                                style = Stroke(stroke),
                            )
                        }
                    }
                }
            }
            destination.regions.forEach { region ->
                val lit = destination.openCities(region).isNotEmpty()
                val (x, y) = TravelDecisions.cellOrigin(region.nameAt, side, gap)
                Text(
                    region.name,
                    style = if (lit) TujiType.bodySmStrong else TujiType.label,
                    color = if (lit) TujiColor.Ink else TujiColor.Ink2,
                    maxLines = 1,
                    modifier = Modifier.anchoredAt(x, y),
                )
            }
            // The lower right of the grid is open sea on every map drawn so
            // far, which is where Moira stands.
            Image(
                painter = painterResource(R.drawable.story_moira_point),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(with(density) { (side * 4.5f).toDp() }),
            )
        }
    }
}

private val GRID_GAP: Dp = 2.dp
private val MAX_GRID_WIDTH: Dp = 360.dp

// MARK: - City

/**
 * One city: a schematic map with its 景點 on it, and the same spots as a list.
 *
 * The map is three or four shapes the server describes — a loop line, a river,
 * a bay — drawn here, with every name text so it follows the interface
 * language. It is not for finding the way: it says roughly where each spot is
 * relative to things a visitor already recognises.
 */
@Composable
fun TravelCityScreen(
    state: TravelStore.State,
    cityId: String,
    destinationId: String,
    scores: Map<String, Int>,
    studyable: List<String>?,
    onRetry: () -> Unit,
    onOpenSpot: (String) -> Unit,
) {
    TravelPage {
        val city = state.city(cityId, destinationId)
        if (city == null) {
            Missing(state.phase, onRetry)
            return@TravelPage
        }
        TujiScreenTitle(city.name)
        TravelCityMap(city, onOpenSpot, Modifier.padding(bottom = TujiSpace.S3))
        TujiSection {
            city.spots.forEachIndexed { index, spot ->
                if (index > 0) TujiRowDivider()
                TujiRow(
                    onClick = if (spot.open) ({ onOpenSpot(spot.id) }) else null,
                    trailing = { SpotTrailing(spot, scores, studyable) },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
                        SpotMarker(index + 1, spot.open)
                        Text(
                            spot.name,
                            style = TujiType.bodyStrong,
                            color = if (spot.open) TujiColor.Ink else TujiColor.Ink3,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotTrailing(spot: TravelSpot, scores: Map<String, Int>, studyable: List<String>?) {
    if (!spot.open) {
        Text(stringResource(R.string.travel_not_open), style = TujiType.bodySm, color = TujiColor.Ink3)
        return
    }
    val studied = TravelDecisions.studiedCount(spot, scores)
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
        if (TravelDecisions.access(spot, studyable) == TravelSpotAccess.NeedsMembership) {
            val locked = stringResource(R.string.travel_spot_locked_label)
            TujiGlyph.Lock(size = 11.dp, tint = TujiColor.Ink3, modifier = Modifier.semantics { contentDescription = locked })
        } else if (studyable != null) {
            // Only a gated account is told a spot is free — to a member every
            // spot is, and the word would be noise.
            Text(
                stringResource(R.string.travel_free),
                style = TujiType.label,
                color = TujiColor.Ink2,
                modifier = Modifier
                    .background(TujiColor.Paper2)
                    .padding(horizontal = TujiSpace.S2, vertical = 4.dp),
            )
        }
        if (studied > 0) {
            Text(
                stringResource(R.string.travel_studied_count, studied, spot.wordIds.size),
                style = TujiType.bodySmStrong,
                color = TujiColor.AccumulationDeep,
            )
        } else {
            Text(
                pluralStringResource(R.plurals.travel_things, spot.wordIds.size, spot.wordIds.size),
                style = TujiType.bodySm,
                color = TujiColor.Ink2,
            )
        }
        TujiGlyph.ArrowRight(size = 13.dp, tint = TujiColor.Ink3)
    }
}

/** The numbered square that stands for a spot, on the map and in the list. */
@Composable
private fun SpotMarker(number: Int, open: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.size(28.dp).background(if (open) TujiColor.Ink else TujiColor.Paper3),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.toString(),
            style = TujiType.bodySmStrong,
            color = if (open) TujiColor.Paper else TujiColor.Ink3,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}

@Composable
private fun TravelCityMap(city: TravelCity, onOpenSpot: (String) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(city.aspect.toFloat().takeIf { it > 0f } ?: 1f)
            .background(TujiColor.Paper2),
    ) {
        val density = LocalDensity.current
        val w = with(density) { maxWidth.toPx() }
        val h = with(density) { maxHeight.toPx() }
        val stroke = with(density) { TujiBorder.Bw2.toPx() }
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            city.shapes.forEach { drawShape(it, stroke) }
        }
        city.shapes.forEach { shape ->
            Text(
                shape.name,
                style = TujiType.label,
                color = if (shape.isWater) TujiColor.AccumulationDeep else TujiColor.Ink2,
                maxLines = 1,
                modifier = Modifier
                    .anchoredAt((shape.nameAt.x * w).toFloat(), (shape.nameAt.y * h).toFloat())
                    .clearAndSetSemantics { },
            )
        }
        val labelGap = with(density) { 20.dp.toPx() }
        city.spots.forEachIndexed { index, spot ->
            val x = (spot.at.x * w).toFloat()
            val y = (spot.at.y * h).toFloat()
            val right = spot.label == TravelSpot.LabelSide.Right
            // The name hangs off one side of the square, so the square itself
            // stays on the spot's position whichever side that is.
            Text(
                spot.name,
                style = TujiType.bodySmStrong,
                color = if (spot.open) TujiColor.Ink else TujiColor.Ink3,
                maxLines = 1,
                modifier = Modifier
                    .anchoredAt(if (right) x + labelGap else x - labelGap, y, anchorX = if (right) 0f else 1f)
                    .clearAndSetSemantics { },
            )
            val closedLabel = stringResource(R.string.travel_spot_closed_label, spot.name)
            Box(
                Modifier
                    .anchoredAt(x, y)
                    .size(44.dp)
                    .then(if (spot.open) Modifier.tujiClickable { onOpenSpot(spot.id) } else Modifier)
                    .clearAndSetSemantics { contentDescription = if (spot.open) spot.name else closedLabel },
                contentAlignment = Alignment.Center,
            ) {
                SpotMarker(index + 1, spot.open)
            }
        }
    }
}

private fun DrawScope.drawShape(shape: TravelMapShape, ringStroke: Float) {
    fun place(p: TravelPoint) = Offset((p.x * size.width).toFloat(), (p.y * size.height).toFloat())
    when (shape) {
        is TravelMapShape.Water -> {
            val first = shape.points.firstOrNull() ?: return
            val path = Path().apply {
                moveTo(place(first).x, place(first).y)
                shape.points.drop(1).forEach { lineTo(place(it).x, place(it).y) }
                close()
            }
            drawPath(path, TujiColor.AccumulationSoft)
        }
        is TravelMapShape.River -> {
            val first = shape.points.firstOrNull() ?: return
            // A smooth line through the points: each one is the end of a curve
            // whose control point is the midpoint before it.
            val path = Path()
            var previous = place(first)
            path.moveTo(previous.x, previous.y)
            shape.points.drop(1).forEach { point ->
                val next = place(point)
                val mid = Offset((previous.x + next.x) / 2, (previous.y + next.y) / 2)
                path.quadraticTo(previous.x, previous.y, mid.x, mid.y)
                previous = next
            }
            path.lineTo(previous.x, previous.y)
            drawPath(
                path,
                TujiColor.AccumulationSoft,
                style = Stroke(width = size.width * 0.036f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        is TravelMapShape.Ring -> {
            val c = place(shape.center)
            val rx = (shape.radius.x * size.width).toFloat()
            val ry = (shape.radius.y * size.height).toFloat()
            drawOval(
                TujiColor.Ink,
                topLeft = Offset(c.x - rx, c.y - ry),
                size = Size(rx * 2, ry * 2),
                style = Stroke(width = ringStroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 4.dp.toPx()))),
            )
        }
    }
}

// MARK: - Spot

/**
 * One 景點: how far through its words this account is, a way to study them,
 * and the words themselves.
 *
 * The words are ordinary 官方圖鑑 words. Browsing them is never gated — a tile
 * opens the word page for anyone. Studying follows the spot: a non-member may
 * study a free spot and meets the paywall at any other.
 */
@Composable
fun TravelSpotScreen(
    state: TravelStore.State,
    spotId: String,
    words: List<Word>,
    scores: Map<String, Int>,
    nextReview: Map<String, Long>,
    studyable: List<String>?,
    onRetry: () -> Unit,
    onOpenWord: (String) -> Unit,
    onStudy: (StudyMode) -> Unit,
    onNeedsMembership: () -> Unit,
) {
    TravelPage {
        val spot = state.spot(spotId)
        if (spot == null) {
            Missing(state.phase, onRetry)
            return@TravelPage
        }
        val spotState = TravelDecisions.state(spot, words, scores, nextReview, studyable)
        SpotHero(spot, spotState, onStudy, onNeedsMembership)
        Text(
            stringResource(R.string.travel_you_will_see),
            style = TujiType.label,
            color = TujiColor.Ink3,
            modifier = Modifier
                .padding(horizontal = TujiSpace.S4)
                .padding(top = TujiSpace.S4, bottom = TujiSpace.S2),
        )
        Column(
            Modifier.padding(horizontal = TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            spotState.words.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    row.forEach { word ->
                        WordTile(
                            word = word,
                            modifier = Modifier.weight(1f).tujiClickable { onOpenWord(word.id) },
                            badge = {
                                val level = MasteryLevel.of(scores[word.id])
                                MasteryBadge(level = level, label = level.label(), spoken = masterySpoken(scores[word.id]))
                            },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SpotHero(
    spot: TravelSpot,
    state: TravelSpotState,
    onStudy: (StudyMode) -> Unit,
    onNeedsMembership: () -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = TujiSpace.S4)
            .fillMaxWidth()
            .background(TujiColor.Ink)
            .padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(spot.name, style = TujiType.h1, color = TujiColor.Paper, modifier = Modifier.semantics { heading() })
            Text("${spot.native} · ${spot.reading}", style = TujiType.bodySm, color = TujiColor.Paper3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
            TujiProgressBar(
                progress = state.progress,
                track = TujiColor.Ink2,
                fill = TujiColor.AccumulationSoft,
                modifier = Modifier.weight(1f).height(6.dp),
            )
            Text(
                stringResource(R.string.travel_word_progress, state.studied, state.total),
                style = TujiType.bodySmStrong,
                color = TujiColor.Paper,
            )
        }
        when {
            state.access == TravelSpotAccess.NeedsMembership -> Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(TujiColor.Current)
                    .tujiClickable(onClick = onNeedsMembership),
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TujiGlyph.Lock(size = 13.dp, tint = TujiColor.Ink)
                Text(stringResource(R.string.travel_study_needs_member), style = TujiType.bodySmStrong, color = TujiColor.Ink)
            }
            state.studyModes.isEmpty() -> Text(
                stringResource(if (state.total == 0) R.string.travel_words_on_way else R.string.travel_all_studied),
                style = TujiType.bodySm,
                color = TujiColor.Paper3,
            )
            else -> Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
                state.studyModes.forEach { mode ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(if (mode == StudyMode.New) TujiColor.Current else TujiColor.Paper2)
                            .tujiClickable { onStudy(mode) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(if (mode == StudyMode.New) R.string.travel_study_here else R.string.study_review_label),
                            style = TujiType.bodySmStrong,
                            color = TujiColor.Ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
