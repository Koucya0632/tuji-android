package app.tuji.android.core.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle

/**
 * A number that rolls when it changes — iOS's `.contentTransition(.numericText())`.
 *
 * **Only the digits move.** The string handed in is usually a sentence with
 * numbers in it — 「完成 3 / 12 字」, 「9 / 20」, 「62%」 — and rolling the whole
 * thing would send 完成 and 字 up the screen along with the count. So the text
 * is cut into runs of digits and runs of everything else, and only the first
 * kind animates. That also keeps each run a normal [Text]: shaping, font
 * scaling and CJK line breaking are the text engine's job, and a row of
 * per-character views gives all three up.
 *
 * Direction is the odometer's: a number going up brings the new value in from
 * below and pushes the old one off the top.
 *
 * **It only ever animates a change.** A screen that opens with the number
 * already final — 複習完成, a milestone — shows it still, which is exactly what
 * the iOS modifier does and the reason those screens are not on this list.
 *
 * 移除動畫 gets a plain [Text]: a value climbing is the one kind of motion meant
 * to be followed, and the rule for that kind is suppress rather than shorten.
 */
@Composable
fun TujiRollingNumber(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (rememberReduceMotion()) {
        Text(text, style = style, color = color, modifier = modifier)
        return
    }
    val parts = remember(text) { numericRuns(text) }
    Row(
        // One announcement, not one per run: a screen reader reading
        // 「完成」「3」「 / 」「12」「 字」 as five nodes has been handed a worse
        // version of the sentence it started as.
        modifier.clearAndSetSemantics { contentDescription = text },
        verticalAlignment = Alignment.Bottom,
    ) {
        parts.forEach { part ->
            if (!part.isDigits) {
                Text(part.text, style = style, color = color)
                return@forEach
            }
            AnimatedContent(
                targetState = part.text,
                transitionSpec = {
                    val rising = (targetState.toLongOrNull() ?: 0) >= (initialState.toLongOrNull() ?: 0)
                    val enter = slideInVertically(tween(TujiMotion.D2, easing = TujiMotion.EaseOut)) {
                        if (rising) it else -it
                    } + fadeIn(tween(TujiMotion.D2, easing = TujiMotion.EaseOut))
                    val exit = slideOutVertically(tween(TujiMotion.D2, easing = TujiMotion.EaseOut)) {
                        if (rising) -it else it
                    } + fadeOut(tween(TujiMotion.D2, easing = TujiMotion.EaseOut))
                    // The width animates when a digit is gained (9 → 10) and
                    // nothing is clipped horizontally; the vertical clip is
                    // what makes it read as a wheel rather than a word flying
                    // past its neighbours.
                    enter togetherWith exit using SizeTransform(clip = false)
                },
                modifier = Modifier.clipToBounds(),
                label = "digits",
            ) { value ->
                Text(value, style = style, color = color)
            }
        }
    }
}

/** One run of the string: all digits, or none. */
internal data class NumericRun(val text: String, val isDigits: Boolean)

/**
 * Cuts a string into alternating runs of digits and everything else.
 *
 * Not one run per character: a five-digit number is one wheel, not five, and
 * the label around it must stay a single piece of text or it loses its shaping.
 */
internal fun numericRuns(text: String): List<NumericRun> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<NumericRun>()
    var start = 0
    var digits = text[0].isDigit()
    for (i in 1..text.lastIndex) {
        val isDigit = text[i].isDigit()
        if (isDigit == digits) continue
        out += NumericRun(text.substring(start, i), digits)
        start = i
        digits = isDigit
    }
    out += NumericRun(text.substring(start), digits)
    return out
}
