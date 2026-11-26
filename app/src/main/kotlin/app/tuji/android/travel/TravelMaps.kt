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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.WordPicture
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.TravelCity
import app.tuji.android.core.model.TravelCountryOutline
import app.tuji.android.core.model.TravelDecisions
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelMapShape
import app.tuji.android.core.model.TravelPoint
import app.tuji.android.core.model.TravelRegion
import app.tuji.android.core.model.TravelRegionCard
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.model.TravelSpotAccess
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordImageKind

/**
 * The maps were laid out by the server for these type sizes (tuji-web
 * lib/travel/japan/map.ts, layout.ts); a larger font scale would run their
 * cards and tags into each other, so it stops at 1 inside a map.
 */
@Composable
private fun FixedType(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, 1f)), content = content)
}

/**
 * The country from its real outline: one colour per region with somewhere to
 * go, grey where nothing is open, and a card on the sea naming each region's
 * open cities, joined to it by a dashed line. A card scrolls the list to its
 * region.
 */
@Composable
internal fun TravelCountryMap(destination: TravelDestination, outline: TravelCountryOutline, onSelectRegion: (String) -> Unit) {
    val label = stringResource(R.string.travel_map_of, destination.name)
    FixedType {
        BoxWithConstraints(
            Modifier.fillMaxWidth().aspectRatio(outline.aspect).background(TujiColor.MapSea).clipToBounds(),
        ) {
            val w = maxWidth
            val h = maxHeight
            val cardHeight = { card: TravelRegionCard -> TravelDecisions.cardHeight(card.lines.size).dp }
            val cardPx = with(LocalDensity.current) { destination.regions.associate { r -> r.id to (r.card?.let { cardHeight(it).toPx() } ?: 0f) } }
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = label }) {
                drawCountry(destination, outline, cardPx)
            }
            outline.seas.forEach { sea ->
                CenteredAt(w * sea.at.x, h * sea.at.y) {
                    Text(
                        sea.name,
                        style = TujiType.label.copy(letterSpacing = 2.sp),
                        color = TujiColor.MapSeaInk,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
            destination.regions.forEach { region ->
                val at = region.labelAt
                if (region.card == null && at != null) {
                    val open = destination.openCities(region).isNotEmpty()
                    CenteredAt(w * at.x, h * at.y) {
                        Text(
                            region.name,
                            style = if (open) TujiType.bodySmStrong else TujiType.label,
                            color = if (open) TujiColor.Ink else TujiColor.Ink2,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
            // Moira stands in the open sea of the lower right, under the cards.
            Image(
                painterResource(R.drawable.story_moira_check_map),
                contentDescription = null,
                modifier = Modifier.offset(x = w * 0.75f, y = h - w * 0.25f - 8.dp).size(w * 0.25f),
            )
            destination.regions.forEach { region ->
                region.card?.let { card -> RegionCard(region, card, w, h, cardHeight(card), onSelectRegion) }
            }
        }
    }
}

@Composable
private fun RegionCard(region: TravelRegion, card: TravelRegionCard, w: Dp, h: Dp, height: Dp, onSelectRegion: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val described = "${region.name}：${card.lines.joinToString(" ")}"
    Column(
        Modifier
            .offset(x = w * card.at.x, y = h * card.at.y)
            .width(w * card.width)
            .height(height)
            .clip(shape)
            .background(TujiColor.Paper, shape)
            .border(TujiBorder.Bw2, TujiColor.Ink, shape)
            .tujiClickable { onSelectRegion(region.id) }
            .clearAndSetSemantics { contentDescription = described }
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(Modifier.height(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(TujiColor.mapTone(region.tone), CircleShape)
                    .border(1.5.dp, TujiColor.Ink, CircleShape),
            )
            Text(region.name, style = TujiType.bodySmStrong, color = TujiColor.Ink, maxLines = 1, softWrap = false)
        }
        card.lines.forEach { line ->
            Text(
                line,
                style = TujiType.bodySm,
                color = TujiColor.Ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(18.dp),
            )
        }
    }
}

private fun DrawScope.drawCountry(destination: TravelDestination, outline: TravelCountryOutline, cardPx: Map<String, Float>) {
    fun place(p: TravelPoint) = Offset(p.x * size.width, p.y * size.height)
    fun path(points: List<TravelPoint>) = Path().apply {
        val first = points.firstOrNull() ?: return@apply
        moveTo(first.x * size.width, first.y * size.height)
        points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
        close()
    }

    outline.frames.forEach { frame ->
        drawRoundRect(
            color = TujiColor.Ink2,
            topLeft = place(frame.at),
            size = androidx.compose.ui.geometry.Size(frame.size.x * size.width, frame.size.y * size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
    }
    // Each region filled, its edges drawn in paper so neighbours part; then the coast in ink over everything.
    destination.regions.forEach { region ->
        val fill = if (destination.openCities(region).isNotEmpty()) TujiColor.mapTone(region.tone) else TujiColor.Paper3
        region.shapes.orEmpty().forEach { shape ->
            val p = path(shape)
            drawPath(p, fill)
            drawPath(p, TujiColor.Paper, style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
        }
    }
    outline.coast.forEach { island ->
        drawPath(path(island), TujiColor.Ink, style = Stroke(width = TujiBorder.Bw2.toPx(), join = StrokeJoin.Round))
    }
    destination.regions.forEach { region ->
        val card = region.card ?: return@forEach
        val rect = Rect(place(card.at), androidx.compose.ui.geometry.Size(card.width * size.width, cardPx[region.id] ?: 0f))
        val end = place(card.pointAt)
        drawLine(
            TujiColor.Ink2,
            end,
            rect.center,
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
        )
        drawCircle(TujiColor.Ink, radius = 3.dp.toPx(), center = end)
    }
    destination.cities.forEach { city ->
        val at = city.mapAt ?: return@forEach
        val c = place(at)
        drawCircle(TujiColor.Ink, radius = 3.2.dp.toPx(), center = c)
        drawCircle(TujiColor.Paper, radius = 3.2.dp.toPx(), center = c, style = Stroke(width = 1.4.dp.toPx()))
    }
}

/** A city's stamp: its first character inside a dashed ring, on its region's colour. */
@Composable
internal fun TravelStamp(mark: String, fill: Color) {
    Box(
        Modifier.size(44.dp).background(fill, CircleShape).border(TujiBorder.Bw2, TujiColor.Ink, CircleShape).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(32.dp)) {
            drawCircle(
                TujiColor.Ink,
                radius = size.minDimension / 2 - 0.75.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))),
            )
        }
        Text(mark, style = TujiType.bodySmStrong, color = TujiColor.Ink)
    }
}

/**
 * The 2026-11 city map: every marker on its true place, a tag with the spot's
 * number and name joined to it by a line. The server placed the tags so none
 * covers another (tuji-web lib/travel/japan/layout.ts).
 */
@Composable
internal fun TravelCityTagMap(city: TravelCity, tone: Color, photographed: Set<String>, onOpenSpot: (String) -> Unit) {
    FixedType {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(city.aspect)
                .background(TujiColor.Paper)
                .background(tone.copy(alpha = 0.3f))
                .clipToBounds(),
        ) {
            val w = maxWidth
            val h = maxHeight
            Canvas(Modifier.fillMaxSize()) { drawTaggedCity(city) }
            city.shapes.forEach { shape ->
                if (shape.kind in TAGGED_KINDS) {
                    CenteredAt(w * shape.nameAt.x, h * shape.nameAt.y) {
                        Text(
                            shape.name,
                            style = TujiType.label,
                            color = if (shape.isWater) TujiColor.MapSeaInk else TujiColor.Ink2,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.clearAndSetSemantics {},
                        )
                    }
                }
            }
            city.spots.forEachIndexed { index, spot ->
                val tag = spot.tag ?: return@forEachIndexed
                CenteredAt(w * tag.x, h * tag.y) { SpotTag(spot, index + 1, spot.id in photographed, onOpenSpot) }
            }
        }
    }
}

private val TAGGED_KINDS = setOf(TravelMapShape.KIND_RING, TravelMapShape.KIND_RIVER, TravelMapShape.KIND_WATER)

@Composable
private fun SpotTag(spot: TravelSpot, number: Int, photographed: Boolean, onOpenSpot: (String) -> Unit) {
    val closed = stringResource(R.string.travel_spot_closed, spot.name)
    val named = if (photographed) stringResource(R.string.travel_spot_photographed, spot.name) else spot.name
    val shape = RoundedCornerShape(8.dp)
    // A 44dp target round the 28dp tag.
    Box(
        Modifier
            .then(if (spot.open) Modifier.tujiClickable { onOpenSpot(spot.id) } else Modifier)
            .clearAndSetSemantics { contentDescription = if (spot.open) named else closed }
            .padding(vertical = 8.dp),
    ) {
        Row(
            Modifier
                .height(28.dp)
                .background(TujiColor.Paper, shape)
                .border(TujiBorder.Bw2, if (spot.open) TujiColor.Ink else TujiColor.Ink3, shape)
                .padding(start = 4.dp, end = if (spot.tagShort) 4.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NumberSquare(number, spot.open, photographed)
            if (!spot.tagShort) {
                Text(
                    spot.name,
                    style = TujiType.bodySmStrong,
                    color = if (spot.open) TujiColor.Ink else TujiColor.Ink3,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun NumberSquare(number: Int, open: Boolean, photographed: Boolean = false, closedGround: Color = TujiColor.Paper3) {
    Box(Modifier.size(20.dp)) {
        Box(
            Modifier.fillMaxSize().background(if (open) TujiColor.Ink else closedGround, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = TujiType.label, color = if (open) TujiColor.Paper else TujiColor.Ink3)
        }
        if (photographed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-7).dp)
                    .size(14.dp)
                    .background(TujiColor.BrandPrimary, CircleShape)
                    .border(1.dp, TujiColor.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) { TujiGlyph.Camera(size = 8.dp, tint = TujiColor.Ink) }
        }
    }
}

private fun DrawScope.drawTaggedCity(city: TravelCity) {
    fun place(p: TravelPoint) = Offset(p.x * size.width, p.y * size.height)
    city.shapes.forEach { shape ->
        when (shape.kind) {
            TravelMapShape.KIND_WATER -> {
                val first = shape.points.firstOrNull() ?: return@forEach
                val path = Path().apply {
                    moveTo(first.x * size.width, first.y * size.height)
                    shape.points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
                    close()
                }
                drawPath(path, TujiColor.MapSea)
            }
            TravelMapShape.KIND_RIVER -> {
                val first = shape.points.firstOrNull() ?: return@forEach
                var previous = place(first)
                val path = Path().apply {
                    moveTo(previous.x, previous.y)
                    shape.points.drop(1).forEach { point ->
                        val next = place(point)
                        quadraticTo(previous.x, previous.y, (previous.x + next.x) / 2, (previous.y + next.y) / 2)
                        previous = next
                    }
                    lineTo(previous.x, previous.y)
                }
                drawPath(path, TujiColor.MapRiver, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            TravelMapShape.KIND_RING -> {
                val center = shape.center ?: return@forEach
                val radius = shape.radius ?: return@forEach
                drawOval(
                    TujiColor.Ink3,
                    topLeft = Offset((center.x - radius.x) * size.width, (center.y - radius.y) * size.height),
                    size = androidx.compose.ui.geometry.Size(radius.x * 2 * size.width, radius.y * 2 * size.height),
                    style = Stroke(width = TujiBorder.Bw2.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))),
                )
            }
        }
    }
    city.spots.forEach { spot ->
        val pin = spot.pin ?: return@forEach
        val tag = spot.tag ?: return@forEach
        drawLine(if (spot.open) TujiColor.Ink else TujiColor.Ink3, place(pin), place(tag), strokeWidth = 1.5.dp.toPx())
    }
    val r = 7.5.dp.toPx()
    city.spots.forEach { spot ->
        val c = place(spot.pin ?: return@forEach)
        val diamond = Path().apply {
            moveTo(c.x, c.y - r)
            lineTo(c.x + r, c.y)
            lineTo(c.x, c.y + r)
            lineTo(c.x - r, c.y)
            close()
        }
        drawPath(diamond, if (spot.open) TujiColor.Ink else TujiColor.Ink3)
        drawPath(diamond, TujiColor.Paper, style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * One spot under the city map: its number and name on the region's colour, the
 * picture of its first word, how it is written and read in Japanese, and how
 * far through its words this account is.
 */
@Composable
internal fun TravelSpotCard(
    spot: TravelSpot,
    number: Int,
    tone: Color,
    cover: Word?,
    access: TravelSpotAccess,
    gated: Boolean,
    studied: Int,
    photographed: Boolean,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val locked = stringResource(R.string.travel_spot_locked)
    val shot = stringResource(R.string.travel_photographed_here)
    Column(
        modifier
            .clip(shape)
            .background(TujiColor.Paper, shape)
            .border(TujiBorder.Bw2, if (spot.open) TujiColor.Ink else TujiColor.Ink3, shape)
            .then(if (onOpen != null) Modifier.tujiClickable(onClick = onOpen) else Modifier)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .background(if (spot.open) tone else TujiColor.Paper3)
                .padding(horizontal = TujiSpace.S2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NumberSquare(number, spot.open, closedGround = TujiColor.Paper2)
            Text(
                spot.name,
                style = TujiType.bodySmStrong,
                color = if (spot.open) TujiColor.Ink else TujiColor.Ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (spot.open && access == TravelSpotAccess.NeedsMembership) {
                TujiGlyph.Lock(size = 11.dp, tint = TujiColor.Ink, modifier = Modifier.semantics { contentDescription = locked })
            } else if (photographed) {
                TujiGlyph.Camera(size = 12.dp, tint = TujiColor.Ink, modifier = Modifier.semantics { contentDescription = shot })
            }
        }
        Box(Modifier.fillMaxWidth().height(TujiBorder.Bw2).background(TujiColor.Ink))
        Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(TujiColor.Paper2).clearAndSetSemantics {}) {
            if (spot.open && cover != null) {
                WordPicture(
                    url = cover.imageUrl,
                    kind = WordImageKind.of(cover.category),
                    inset = TujiSpace.S2,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    cover.word,
                    style = TujiType.label,
                    color = TujiColor.Ink,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(TujiSpace.S2)
                        .background(TujiColor.Paper, RoundedCornerShape(11.dp))
                        .padding(horizontal = TujiSpace.S2, vertical = 2.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(TujiSpace.S2), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                spot.native,
                style = TujiType.bodySmStrong,
                color = if (spot.open) TujiColor.Ink else TujiColor.Ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(spot.reading, style = TujiType.bodySm, color = TujiColor.Ink3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            if (!spot.open) {
                Text(stringResource(R.string.travel_not_open), style = TujiType.bodySm, color = TujiColor.Ink3)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                    if (studied > 0) {
                        Text(
                            stringResource(R.string.travel_studied_of, studied, spot.wordIds.size),
                            style = TujiType.bodySmStrong,
                            color = TujiColor.AccumulationDeep,
                        )
                    } else {
                        Text(stringResource(R.string.travel_words, spot.wordIds.size), style = TujiType.bodySm, color = TujiColor.Ink2)
                    }
                    if (gated && access == TravelSpotAccess.Open) {
                        Text(
                            stringResource(R.string.travel_free),
                            style = TujiType.label,
                            color = TujiColor.Ink2,
                            modifier = Modifier.background(TujiColor.Paper2).padding(horizontal = TujiSpace.S2, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
