package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/**
 * The capabilities on 助手's home, in the order offered — iOS's
 * `AssistantHomeFeature`. Each one arrives with its page: a row that leads
 * nowhere is worse than no row.
 */
enum class AssistantHomeFeature { Lookup }

/**
 * 助手 — Moira, and what she can do. iOS's `AssistantHomeView`: the cat, a
 * greeting, a list of features, and 點數明細 in the top corner.
 */
@Composable
fun AssistantHomeScreen(
    onBack: () -> Unit,
    onOpen: (AssistantHomeFeature) -> Unit,
    onOpenStatement: () -> Unit,
    /** So AI 查詞 opens on its prices rather than waiting for them. */
    prefetch: suspend () -> Unit,
) {
    LaunchedEffect(Unit) { prefetch() }
    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            showsRule = true,
            trailing = { AssistantCreditLink(onOpenStatement) },
        )
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TujiSpace.S4)
                .padding(bottom = TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                AnimatedMascot(Modifier.fillMaxWidth().height(236.dp))
                Text(
                    stringResource(R.string.assistant_greeting),
                    style = TujiType.h2,
                    color = TujiColor.Ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.assistant_intro),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink2,
                    textAlign = TextAlign.Center,
                )
            }
            Column {
                Rule()
                AssistantHomeFeature.entries.forEach { feature ->
                    FeatureRow(feature) { onOpen(feature) }
                    Rule()
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: AssistantHomeFeature, onClick: () -> Unit) {
    val (title, detail) = when (feature) {
        AssistantHomeFeature.Lookup -> R.string.assistant_feature_lookup to R.string.assistant_feature_lookup_detail
    }
    Row(
        Modifier
            .fillMaxWidth()
            .tujiClickable(onClick = onClick)
            .padding(vertical = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(TujiColor.Paper2), contentAlignment = Alignment.Center) {
            when (feature) {
                AssistantHomeFeature.Lookup -> TujiGlyph.Lookup(size = 22.dp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            Text(stringResource(title), style = TujiType.bodyStrong, color = TujiColor.Ink)
            Text(stringResource(detail), style = TujiType.bodySm, color = TujiColor.Ink2)
        }
        Text("→", style = TujiType.h3, color = TujiColor.Ink3)
    }
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
}

/** The home leads to the wallet statement: the can, and its name unless type is very large. */
@Composable
private fun AssistantCreditLink(onClick: () -> Unit) {
    val label = stringResource(R.string.credit_statement_title)
    val large = LocalDensity.current.fontScale >= 1.5f
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .background(TujiColor.Paper2)
            .tujiClickable(onClick = onClick)
            .clearAndSetSemantics { contentDescription = label }
            .padding(horizontal = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.credit_can), null, Modifier.size(20.dp), tint = TujiColor.Ink)
        if (!large) Text(label, style = TujiType.bodySmStrong, color = TujiColor.Ink)
    }
}
