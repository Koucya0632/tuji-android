package app.tuji.android.gloss

import app.tuji.android.R
import app.tuji.android.core.design.GlossCalloutPlacement
import app.tuji.android.core.design.GlossCalloutShape
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIconButton
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.tuji.android.core.model.GlossSpan
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordSpeaking
import kotlinx.coroutines.launch

/**
 * Which 詞塊 one screen is currently showing, where it is, and the language to
 * speak it in.
 *
 * Screen-scoped rather than app-scoped: two screens are never showing a card at
 * once, and a card outliving the screen that raised it is a bug, not a feature.
 * [GlossCardHost] owns the instance; nothing else constructs one.
 */
@Stable
class GlossSelection {
    /**
     * Which 詞塊 of which sentence.
     *
     * The sentence's own text is the identity. A screen shows a 譯義 and up to
     * three examples and those are never the same string, so an id threaded
     * through every call site would buy nothing; two genuinely identical
     * sentences would simply both light up, which looks odd rather than wrong.
     */
    data class Target(val sentence: String, val index: Int)

    var span by mutableStateOf<GlossSpan?>(null)
        private set
    var target by mutableStateOf<Target?>(null)
        private set

    /**
     * The sentence's language, for the card's 發音 voice. Carried here rather
     * than re-derived in the card, because the card sees a 詞塊 and a 詞塊 is a
     * fragment — `look forward to` has no language of its own to resolve.
     */
    var language by mutableStateOf(TargetLanguage.EN)
        private set

    /**
     * Where the selected 詞塊 landed, in the host's own coordinates. null until
     * the sentence reports — and **stays** null when nothing can report, which
     * is what keeps the bottom-anchored fallback a live path rather than dead
     * code.
     */
    var anchor by mutableStateOf<Rect?>(null)
        private set

    fun select(span: GlossSpan, index: Int, sentence: String, language: TargetLanguage) {
        if (!span.isTappable) return
        this.span = span
        this.target = Target(sentence, index)
        this.language = language
        // The previous word's anchor would otherwise aim this card at it for
        // the one frame before the new measurement lands.
        this.anchor = null
    }

    /**
     * Which 詞塊 of [sentence] is selected, if any — the sentence asks this to
     * decide what to highlight and what to measure.
     */
    fun selectedIndex(sentence: String): Int? =
        target?.takeIf { it.sentence == sentence }?.index

    /**
     * Records where the selected 詞塊 landed.
     *
     * Refusing a report for anything but the live target is not defensive
     * tidiness: a sentence that lays out one frame late would otherwise point
     * the caret at the word the user tapped *before* this one.
     */
    fun report(anchor: Rect, forTarget: Target) {
        if (target != forTarget || this.anchor == anchor) return
        this.anchor = anchor
    }

    fun clear() {
        span = null
        target = null
        anchor = null
    }
}

/**
 * null until a screen hosts a card. [InteractiveSentenceText] reads it to decide
 * whether to make anything tappable at all: a live link with nowhere to deliver
 * its tap is worse than plain text, because it looks like the feature is broken
 * rather than absent.
 */
val LocalGlossSelection = staticCompositionLocalOf<GlossSelection?> { null }

/**
 * Hosts the 詞塊 card for one screen.
 *
 * Belongs at the **screen root**, not on the sentence and not inside the
 * scrolling column — the card is an overlay on whatever it wraps, so hosted
 * deeper it would scroll away with the content and be clipped by the scroll
 * container. It is also the origin every anchor is measured against, which is a
 * second reason the same rule holds: hosted deeper, that origin would move
 * under the card.
 *
 * @param partOfSpeech localises a canonical English part of speech. Passed in
 *   rather than imported: the mapping is a content rule and lives in
 *   `core:catalog`, and a design module that reached for it would be the wrong
 *   way round.
 * @param onOpenWord 看完整詳情. null hides the row — most 詞塊 have no catalogue
 *   entry to open, and a screen that cannot navigate should not offer to.
 * @param bookmarks null on a screen where 書籤 means nothing. 物見 is the case:
 *   書籤 filters the *catalogue*, and a 物見 item is not in it — which is also
 *   why that page's own header carries no star.
 */
@Composable
fun GlossCardHost(
    partOfSpeech: (String) -> String,
    modifier: Modifier = Modifier,
    speech: WordSpeaking? = null,
    accent: String = "us",
    onOpenWord: ((String) -> Unit)? = null,
    bookmarks: GlossBookmarks? = null,
    content: @Composable () -> Unit,
) {
    val selection = remember { GlossSelection() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val metrics = remember(density) { GlossCalloutPlacement.metrics(density) }

    var hostOrigin by remember { mutableStateOf(Offset.Zero) }
    var hostSize by remember { mutableStateOf(Size.Zero) }
    // Kept across selections on purpose: a stale size is a better first guess
    // than none, and the card is invisible until it has one.
    var cardSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                hostOrigin = it.positionInWindow()
                hostSize = Size(it.size.width.toFloat(), it.size.height.toFloat())
            },
    ) {
        CompositionLocalProvider(
            LocalGlossSelection provides selection,
            LocalGlossHostOrigin provides hostOrigin,
        ) {
            content()
        }

        val span = selection.span
        if (span != null) {
            // Swallows the tap that would otherwise reach the sentence
            // underneath and immediately raise another card.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(TujiColor.Scrim)
                    .tujiClickable { selection.clear() },
            )
            val placement = selection.anchor?.let {
                GlossCalloutPlacement.place(
                    anchor = it,
                    cardSize = Size(cardSize.width.toFloat(), cardSize.height.toFloat()),
                    container = hostSize,
                    m = metrics,
                )
            }
            val widthPx = GlossCalloutPlacement.cardWidth(hostSize.width, metrics)
            val fallbackTop = hostSize.height - cardSize.height - metrics.edgeMargin
            GlossCard(
                span = span,
                language = selection.language,
                callout = placement?.let { GlossCalloutShape.Caret(it.caretX, it.pointsDown) },
                partOfSpeech = partOfSpeech,
                onSpeak = {
                    speech?.let { voice ->
                        scope.launch { voice.speak(span.text, selection.language, accent) }
                    }
                },
                canSpeak = speech?.canSpeak(selection.language) == true,
                onOpenWord = onOpenWord?.let { open -> { id: String -> selection.clear(); open(id) } },
                bookmarked = span.wordId?.let { bookmarks?.isMarked(it) } == true,
                onBookmark = bookmarks?.let { marks -> { id: String -> marks.toggle(id) } },
                modifier = Modifier
                    .width(with(density) { widthPx.toDp() })
                    .onSizeChanged { cardSize = it }
                    // One unmeasured frame exists between raising the card and
                    // knowing where it goes. Showing it there would be a
                    // visible jump.
                    .alpha(if (cardSize == IntSize.Zero) 0f else 1f)
                    .offset {
                        IntOffset(
                            x = metrics.sideMargin.toInt(),
                            y = (placement?.top ?: fallbackTop).toInt(),
                        )
                    },
            )
        }
    }
}

/**
 * Where the host's top-left sits in the window, so a sentence can turn its own
 * window position into an anchor the host can place against.
 *
 * A composition local rather than a coordinate space name (which is how iOS
 * does it) because Compose has no named spaces: the two ends have to agree on
 * an origin, and this is that agreement.
 */
internal val LocalGlossHostOrigin = staticCompositionLocalOf { Offset.Zero }

/**
 * 書籤, as much of it as a 詞塊 card needs.
 *
 * One value rather than two parameters because they are never useful apart: a
 * screen that can read the marks can always write them, and one of the two
 * arriving alone is a card with a star that does nothing.
 */
data class GlossBookmarks(
    val isMarked: (String) -> Boolean,
    val toggle: (String) -> Unit,
)
