package app.tuji.android.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The list — what replaces Material's `ListItem` and iOS's inset-grouped `List`.
 *
 * iOS's grouped list is recognisable because three things are true at once: it
 * is rounded, it is a white card, and it floats on a tinted ground. Take all
 * three away and there is no residue left to recognise, which is the point.
 *
 * **Full-bleed rows are what makes that work.** With no card edge to align to,
 * row content aligns straight to the page margin, and that one vertical line
 * runs through every screen in the app. It is the skeleton the card used to be.
 *
 * Sections are separated by空白, and the header sits *outside* the rows, so the
 * group reads as a heading over a list rather than as its own first row.
 */
@Composable
fun TujiSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(top = TujiSpace.S5)) {
        if (title != null) {
            Text(
                title,
                style = TujiType.label,
                color = TujiColor.Ink3,
                modifier = Modifier.padding(
                    start = TujiSpace.S4,
                    end = TujiSpace.S4,
                    bottom = TujiSpace.S2,
                ),
            )
        }
        content()
        if (footer != null) {
            Text(
                footer,
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                modifier = Modifier.padding(
                    start = TujiSpace.S4,
                    end = TujiSpace.S4,
                    top = TujiSpace.S2,
                ),
            )
        }
    }
}

/**
 * The hairline *between* rows, drawn by the caller before each row but the
 * first.
 *
 * iOS counts its children to do this automatically. Compose cannot count a
 * `@Composable` lambda's emissions either, and every workaround — a list of
 * lambdas, a separator baked into every row — costs more than it saves. So the
 * rule is a call, and the invariant is stated here: **never after the last
 * row**, because a trailing rule reads as the start of another group.
 */
@Composable
fun TujiRowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TujiSpace.S4)
            .height(TujiBorder.Bw1)
            .background(TujiColor.Rule),
    )
}

/**
 * One row: something on the left, something on the right, 56dp minimum.
 *
 * The height follows what it carries rather than being fixed — 56 plain, taller
 * once a subtitle wraps — which is what keeps a two-line explanation from being
 * clipped in the one language whose translation runs long.
 */
@Composable
fun TujiRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    leading: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.tujiClickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp)
            .padding(horizontal = TujiSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The *label* takes the slack, not a spacer between the two. With the
        // weight on a spacer, the leading column is unconstrained: a long
        // subtitle takes as much width as it likes and pushes the trailing
        // content off the right edge — which is where the goal stepper went,
        // its ＋ clipped by the screen.
        Box(Modifier.weight(1f)) { Row(verticalAlignment = Alignment.CenterVertically) { leading() } }
        trailing()
    }
}

/** The common case: a label, an explanation, the current value, and an arrow. */
@Composable
fun TujiSettingRow(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    showsArrow: Boolean = true,
    destructive: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    TujiRow(
        modifier = modifier,
        onClick = onClick,
        trailing = {
            if (trailing != null) {
                trailing()
            } else {
                if (value != null) {
                    Text(value, style = TujiType.body, color = TujiColor.Ink2, maxLines = 1)
                }
                if (showsArrow) {
                    // `→`, not `›`. The chevron is iOS's own accent; a
                    // horizontal arrow means moving forward and shares the
                    // stroke every other mark in this app uses.
                    Text("→", style = TujiType.h3, color = TujiColor.Ink3)
                }
            }
        },
    ) {
        Column(
            Modifier.padding(vertical = TujiSpace.S2),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = TujiType.h3,
                color = if (destructive) TujiColor.Alert else TujiColor.Ink,
            )
            if (subtitle != null) {
                Text(subtitle, style = TujiType.bodySm, color = TujiColor.Ink3)
            }
        }
    }
}

/**
 * A square that fills with ink when it is on.
 *
 * Not Material's `Switch`: that control is Material's signature the way the
 * chevron is iOS's, and it is a lozenge in a system whose every other selected
 * state is a filled rectangle.
 */
@Composable
fun TujiCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .background(if (checked) TujiColor.Ink else TujiColor.Paper2)
            .tujiClickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", style = TujiType.bodySmStrong, color = TujiColor.Paper)
    }
}
