package app.tuji.android.core.design

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import app.tuji.android.core.model.FuriganaSegment
import app.tuji.android.core.model.FuriganaUnits

/**
 * A Japanese sentence with kana set over its kanji, wrapping like running text
 * — iOS's `FuriganaSentence`.
 *
 * [FuriganaHeadword] draws one word on one line and shrinks it to fit; a
 * sentence has to wrap instead, so it is cut into units that may not be split
 * ([FuriganaUnits]) and flowed. Every unit reserves a ruby row, empty where
 * there is none, so the base characters of a line share one baseline. The base
 * sets the width; the ruby is centred over it and may hang over the kana
 * beside it, the way Japanese is typeset.
 */
@Composable
fun FuriganaSentence(
    segments: List<FuriganaSegment>,
    modifier: Modifier = Modifier,
    style: TextStyle = TujiType.bodyStrong,
    rubyStyle: TextStyle = TujiType.label,
) {
    val units = remember(segments) { FuriganaUnits.of(segments) }
    val sentence = remember(segments) { segments.joinToString("") { it.text } }
    // Unit by unit, TalkBack would read the kana and the kanji as separate
    // items. One label, the sentence as written.
    FlowRow(modifier.clearAndSetSemantics { contentDescription = sentence }) {
        units.forEach { unit -> RubyUnit(unit, style, rubyStyle) }
    }
}

/**
 * One unit: the ruby row, then the base. The base alone sets the width — sized
 * by the ruby instead, a three-kana reading over one kanji would open a gap on
 * either side of it — so the ruby is measured unbounded and centred, hanging
 * over its neighbours when it is wider.
 */
@Composable
private fun RubyUnit(unit: FuriganaUnits.Unit, style: TextStyle, rubyStyle: TextStyle) {
    Layout(
        content = {
            Text(" ", style = rubyStyle, maxLines = 1)
            Text(unit.text, style = style, color = TujiColor.Ink, maxLines = 1, softWrap = false)
            unit.ruby?.let { Text(it, style = rubyStyle, color = TujiColor.Ink3, maxLines = 1, softWrap = false) }
        },
    ) { measurables, constraints ->
        val loose = Constraints()
        val row = measurables[0].measure(loose)
        val base = measurables[1].measure(loose)
        val ruby = measurables.getOrNull(2)?.measure(loose)
        layout(base.width, row.height + base.height) {
            ruby?.place((base.width - ruby.width) / 2, 0)
            base.place(0, row.height)
        }
    }
}
