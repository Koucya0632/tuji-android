package app.tuji.android.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.scenes.GlossToken
import app.tuji.android.core.scenes.MachineAction
import app.tuji.android.core.scenes.MachineControl
import app.tuji.android.core.scenes.Phrasing
import app.tuji.android.core.scenes.SceneMachine
import app.tuji.android.core.scenes.ScreenMock
import app.tuji.android.core.scenes.StageCard
import app.tuji.android.core.scenes.yen
import app.tuji.android.form.TujiSheet
import coil3.compose.AsyncImage

// The pieces every 場面 screen shares, as tuji-ios's SceneCopy, MachineView,
// ScreenMockView and StageBackdropView draw them.

internal object MachineColor {
    val Bezel = Color(0xFF2E2B28)
    val Bar = Color(0xFF46505A)
    val Edge = Color(0xFF9AA1A8)
    val Face = Color(0xFFFFFFFF)
}

/** 店員／職員／收銀機／螢幕. */
@Composable
internal fun speakerLabel(speaker: Phrasing.Speaker): String = stringResource(
    when (speaker) {
        Phrasing.Speaker.Clerk -> R.string.scene_speaker_clerk
        Phrasing.Speaker.Staff -> R.string.scene_speaker_staff
        Phrasing.Speaker.Register -> R.string.scene_speaker_register
        Phrasing.Speaker.Screen -> R.string.scene_speaker_screen
    },
)

@Composable
internal fun SceneSpeakerTag(speaker: Phrasing.Speaker) {
    Text(
        speakerLabel(speaker),
        style = TujiType.label,
        color = TujiColor.Ink2,
        modifier = Modifier.background(TujiColor.Paper2).padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

enum class Segment { Done, Current, Todo }

/** One segment per question: practised, the one on screen, still to come. */
@Composable
internal fun SceneSegments(
    segments: List<Segment>,
    modifier: Modifier = Modifier,
    onInk: Boolean = false,
    height: Dp = 4.dp,
    spacing: Dp = 3.dp,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
        segments.forEach { segment ->
            val color = when (segment) {
                Segment.Done -> if (onInk) TujiColor.AccumulationSoft else TujiColor.Accumulation
                Segment.Current -> TujiColor.Current
                Segment.Todo -> if (onInk) TujiColor.Paper.copy(alpha = 0.2f) else TujiColor.Paper3
            }
            Box(Modifier.weight(1f).height(height).background(color))
        }
    }
}

internal fun practisedSegments(practised: List<Boolean>) = practised.map { if (it) Segment.Done else Segment.Todo }

/** The square speaker button every line uses. */
@Composable
internal fun ScenePlayButton(label: String, onClick: () -> Unit, filled: Boolean = true, glyph: String = "🔊") {
    Box(
        Modifier
            .size(44.dp)
            .background(if (filled) TujiColor.Paper2 else Color.Transparent)
            .tujiClickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, style = TujiType.body)
    }
}

/** A capsule action on the practice panel: 重聽、請再說一次、慢速、字幕. */
@Composable
internal fun ScenePill(title: String, onClick: () -> Unit, pressed: Boolean = false) {
    Box(
        Modifier
            .height(36.dp)
            .background(if (pressed) TujiColor.Ink else TujiColor.Paper2, CircleShape)
            .tujiClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = TujiType.label, color = if (pressed) TujiColor.Paper else TujiColor.Ink2)
    }
}

@Composable
internal fun SceneRule() = Box(Modifier.fillMaxWidth().height(1.dp).background(TujiColor.Rule))

/** The ink call-to-action every 場面 screen ends a step with. */
@Composable
internal fun SceneCall(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, trailing: String? = "→") {
    Row(
        modifier
            .fillMaxWidth()
            .background(TujiColor.Current)
            .tujiClickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = TujiType.h3, color = TujiColor.Ink, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, style = TujiType.h3, color = TujiColor.Ink) }
    }
}

// MARK: - 點詞翻譯

/**
 * A Japanese line whose words can be tapped for their reading and meaning.
 * `tokens` is null when the line is not to be opened yet (before answering).
 */
@Composable
internal fun GlossedLine(text: String, tokens: List<GlossToken>?, style: TextStyle, onPick: (GlossToken) -> Unit) {
    if (tokens == null) {
        Text(text, style = style, color = TujiColor.Ink)
        return
    }
    val annotated = buildAnnotatedString {
        tokens.forEachIndexed { index, token ->
            if (token.m != null) {
                pushStringAnnotation("gloss", index.toString())
                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(token.t) }
                pop()
            } else {
                append(token.t)
            }
        }
    }
    @Suppress("DEPRECATION")
    ClickableText(annotated, style = style.copy(color = TujiColor.Ink)) { offset ->
        annotated.getStringAnnotations("gloss", offset, offset).firstOrNull()?.let { onPick(tokens[it.item.toInt()]) }
    }
}

/** What a tapped word means. */
@Composable
internal fun GlossSheet(token: GlossToken?, language: UiLanguage, onDismiss: () -> Unit) {
    token ?: return
    TujiSheet(title = stringResource(R.string.scene_gloss_title), onDismiss = onDismiss, maxHeight = 280.dp) {
        Column(Modifier.fillMaxWidth().padding(TujiSpace.S4), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            Text(token.t, style = TujiType.h2.copy(fontSize = 24.sp), color = TujiColor.Ink)
            token.r?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
            token.m?.let { Text(it.resolved(language), style = TujiType.bodyStrong, color = TujiColor.Ink) }
        }
    }
}

// MARK: - Machines

@Composable
internal fun MachineView(machine: SceneMachine, brandOverride: String?, send: (MachineAction) -> Unit) {
    val screen = machine.screen
    Column(Modifier.fillMaxWidth().background(MachineColor.Bezel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxWidth().background(MachineColor.Face)) {
            Text(
                brandOverride ?: machineBrand(machine),
                style = TujiType.label,
                color = MachineColor.Face,
                modifier = Modifier.fillMaxWidth().background(MachineColor.Bar).padding(horizontal = 12.dp, vertical = TujiSpace.S2),
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(screen.headline, style = TujiType.bodyStrong, color = MachineColor.Bezel, modifier = Modifier.semantics { heading() })
                screen.info?.let { Text(it, style = TujiType.bodySm, color = MachineColor.Bezel) }
                screen.paper?.let {
                    Text(
                        it,
                        style = TujiType.bodySm,
                        color = TujiColor.Ink,
                        modifier = Modifier.fillMaxWidth().background(TujiColor.Paper).border(1.dp, MachineColor.Edge).padding(10.dp),
                    )
                }
                screen.buttons.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { control -> MachineScreenButton(control, Modifier.weight(1f), send) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (screen.hardware.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                screen.hardware.forEach { control ->
                    Box(
                        Modifier
                            .defaultMinSize(minHeight = 44.dp)
                            .background(TujiColor.Ink2)
                            .tujiClickable { send(control.action) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(hardwareTitle(control), style = TujiType.label, color = TujiColor.Paper)
                    }
                }
            }
        }
    }
}

@Composable
private fun MachineScreenButton(control: MachineControl, modifier: Modifier, send: (MachineAction) -> Unit) {
    val label = (control.role as? MachineControl.Role.OnScreen)?.label.orEmpty()
    Box(
        modifier
            .defaultMinSize(minHeight = 52.dp)
            .background(if (control.isPrimary) MachineColor.Bar else MachineColor.Face)
            .border(1.dp, MachineColor.Edge)
            .tujiClickable(enabled = control.isEnabled) { send(control.action) }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TujiType.bodySmStrong,
            color = (if (control.isPrimary) MachineColor.Face else MachineColor.Bezel).copy(alpha = if (control.isEnabled) 1f else 0.45f),
            textAlign = TextAlign.Center,
        )
    }
}

private fun machineBrand(machine: SceneMachine): String = when (machine) {
    is app.tuji.android.core.scenes.Payment -> "お会計"
    is app.tuji.android.core.scenes.AgeConfirm -> "年齢確認"
    is app.tuji.android.core.scenes.FoodTicket -> "食券販売機"
    is app.tuji.android.core.scenes.OrderTablet -> "テーブル 08"
    is app.tuji.android.core.scenes.Kiosk -> "セルフオーダー"
}

@Composable
private fun hardwareTitle(control: MachineControl): String =
    when (val part = (control.role as? MachineControl.Role.Hardware)?.kind) {
        MachineControl.HardwareKind.CardReader -> stringResource(R.string.scene_hw_card)
        is MachineControl.HardwareKind.CoinSlot -> stringResource(R.string.scene_hw_insert, yen(part.yen))
        is MachineControl.HardwareKind.Tray ->
            stringResource(if (part.withChange) R.string.scene_hw_change_receipt else R.string.scene_hw_receipt)
        MachineControl.HardwareKind.TicketTray -> stringResource(R.string.scene_hw_ticket)
        null -> ""
    }

/** A register or ordering screen a 問句 shows, drawn rather than photographed. */
@Composable
internal fun ScreenMockView(screen: ScreenMock, tried: Set<String>, correctId: String?, onTap: ((String) -> Unit)?) {
    Column(Modifier.fillMaxWidth().background(MachineColor.Bezel).padding(12.dp)) {
        Column(Modifier.fillMaxWidth().background(MachineColor.Face)) {
            Text(
                screen.heading,
                style = TujiType.bodySmStrong,
                color = MachineColor.Face,
                modifier = Modifier.fillMaxWidth().background(MachineColor.Bar).padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                screen.detail?.let { Text(it, style = TujiType.bodyStrong, color = MachineColor.Bezel) }
                screen.buttons.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { button ->
                            val isCorrect = correctId == button.choiceId
                            val isTried = button.choiceId in tried
                            Box(
                                Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 64.dp)
                                    .background(if (isCorrect) TujiColor.Ink else MachineColor.Face)
                                    .border(
                                        if (isTried || isCorrect) 3.dp else 1.dp,
                                        when {
                                            isTried -> TujiColor.Alert
                                            isCorrect -> TujiColor.Accumulation
                                            else -> MachineColor.Edge
                                        },
                                    )
                                    .tujiClickable(enabled = onTap != null && !isTried && correctId == null) { onTap?.invoke(button.choiceId) }
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    button.ja,
                                    style = TujiType.bodyStrong,
                                    color = if (isCorrect) TujiColor.Paper else MachineColor.Bezel,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// MARK: - The stage

/** Where things sit in one background photo, as fractions of its size (tuji-ios `StageBackdrop`). */
internal data class StageBackdrop(val focusX: Float, val focusY: Float, val spots: List<Pair<Float, Float>>, val propWidth: Float, val card: Pair<Float, Float>) {
    companion object {
        private fun counter(x: Float, y: Float) = StageBackdrop(x, y - 0.06f, listOf(x to y), 0.24f, x to y)

        private val table = mapOf(
            "bg-konbini-register" to StageBackdrop(0.5f, 0.66f, listOf(0.43f to 0.71f, 0.76f to 0.8f, 0.17f to 0.76f), 0.28f, 0.5f to 0.72f),
            "story-restaurant-dining" to StageBackdrop(0.74f, 0.45f, listOf(0.8f to 0.425f), 0.12f, 0.5f to 0.72f),
            "story-restaurant-checkout" to StageBackdrop(0.8f, 0.45f, listOf(0.86f to 0.392f), 0.075f, 0.5f to 0.72f),
            "story-restaurant-entrance" to StageBackdrop(0.5f, 0.45f, listOf(0.83f to 0.41f), 0.1f, 0.5f to 0.72f),
            "scene-bg-ramen-shop" to counter(0.68f, 0.74f),
            "scene-bg-hotel-check-in" to counter(0.53f, 0.58f),
            "scene-bg-taxi" to counter(0.5f, 0.78f),
            "scene-bg-fast-food" to counter(0.72f, 0.75f),
            "scene-bg-ryokan" to counter(0.52f, 0.67f),
            "scene-bg-cafe" to counter(0.5f, 0.68f),
            "scene-bg-izakaya" to counter(0.49f, 0.68f),
            "scene-bg-conveyor-sushi" to counter(0.48f, 0.72f),
            "scene-bg-station-ticket-office" to counter(0.49f, 0.63f),
            "scene-bg-airport-check-in" to counter(0.49f, 0.7f),
            "scene-bg-car-rental" to counter(0.49f, 0.62f),
            "scene-bg-drugstore-checkout" to counter(0.49f, 0.68f),
            "scene-bg-clothing-store" to counter(0.54f, 0.73f),
            "scene-bg-electronics-store" to counter(0.47f, 0.69f),
            "scene-bg-department-tax-refund" to counter(0.5f, 0.72f),
            "scene-bg-post-office" to counter(0.58f, 0.7f),
            "scene-bg-hair-salon" to counter(0.49f, 0.69f),
        )

        fun named(name: String) = table[name] ?: StageBackdrop(0.5f, 0.5f, listOf(0.5f to 0.8f), 0.25f, 0.5f to 0.72f)
    }
}

/**
 * The photo the 問句 happens in, scaled to fill and shifted so its focus point
 * sits as near the middle as the photo's edges allow; props and the paper slip
 * sit at photo coordinates, so they stay on the counter however it is cropped.
 * Every image is 2:3 (1024×1536).
 */
@Composable
internal fun StageBackdropView(scenes: Scenes, background: String, props: List<String>, card: StageCard?, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.clipToBounds()) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = maxOf(boxW / 2f, boxH / 3f)
        val drawnW = 2f * scale
        val drawnH = 3f * scale
        val backdrop = StageBackdrop.named(background)
        fun place(length: Float, drawn: Float, focus: Float) = minOf(0f, maxOf(length - drawn, length / 2 - focus * drawn))
        val originX = place(boxW, drawnW, backdrop.focusX)
        val originY = place(boxH, drawnH, backdrop.focusY)
        with(density) {
            AsyncImage(
                model = scenes.imageUri(background),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .offset(originX.toDp(), originY.toDp())
                    .size(drawnW.toDp(), drawnH.toDp()),
            )
            val propW = minOf(160.dp.toPx(), backdrop.propWidth * drawnW)
            props.forEachIndexed { index, name ->
                val (sx, sy) = backdrop.spots[minOf(index, backdrop.spots.lastIndex)]
                AsyncImage(
                    model = scenes.imageUri(name),
                    contentDescription = null,
                    modifier = Modifier
                        .offset((originX + sx * drawnW - propW / 2).toDp(), (originY + sy * drawnH - propW / 2).toDp())
                        .width(propW.toDp()),
                )
            }
            card?.let {
                val cardW = minOf(210.dp.toPx(), boxW * 0.55f)
                Column(
                    Modifier
                        .offset((originX + backdrop.card.first * drawnW - cardW / 2).toDp(), (originY + backdrop.card.second * drawnH - 40.dp.toPx()).toDp())
                        .width(cardW.toDp())
                        .background(TujiColor.Paper)
                        .border(1.dp, TujiColor.Rule)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(it.title, style = TujiType.label, color = TujiColor.Ink2)
                    it.lines.forEach { line -> Text(line, style = TujiType.bodySmStrong, color = TujiColor.Ink) }
                }
            }
        }
    }
}

@Composable
internal fun FullBleed(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize()) { content() }
