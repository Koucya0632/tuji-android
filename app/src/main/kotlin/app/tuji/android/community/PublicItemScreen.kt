package app.tuji.android.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.tuji.android.R
import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import coil3.compose.AsyncImage

/** One published word: what it is, who published it, and the two things you can do to it. */
@Composable
fun PublicItemScreen(
    state: CommunityViewModel.ItemState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onSave: () -> Unit,
    onReport: (ReportTarget, ReportReason) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    when (state) {
        is CommunityViewModel.ItemState.Loading ->
            Centered(stringResource(R.string.community_loading))

        is CommunityViewModel.ItemState.Failed ->
            Centered(stringResource(R.string.community_failed))

        is CommunityViewModel.ItemState.Loaded -> {
            var reporting by remember { mutableStateOf(false) }
            val item = state.item

            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = TujiSpace.S4),
                    verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                ) {
                    Spacer(Modifier.height(TujiSpace.S2))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .background(TujiColor.Paper2),
                    ) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(TujiSpace.S3),
                        )
                    }

                    Text(item.lemma, style = TujiType.h1, color = TujiColor.Ink)
                    item.displayZhHant?.let {
                        Text(it, style = TujiType.h3, color = TujiColor.Ink2)
                    }

                    item.author?.let { author ->
                        Text(
                            author.name,
                            style = TujiType.bodySmStrong,
                            color = TujiColor.Accumulation,
                            modifier = Modifier
                                .tujiClickable { onOpenAuthor(author.handle) }
                                .padding(vertical = TujiSpace.S1),
                        )
                    }

                    val word = item.learningWord
                    (word?.chineseDefinition ?: word?.targetDefinition)?.let {
                        Text(it, style = TujiType.body, color = TujiColor.Ink)
                    }
                    word?.targetDefinition?.takeIf { word.chineseDefinition != null }?.let {
                        Text(it, style = TujiType.body, color = TujiColor.Ink2)
                    }

                    // The publisher's own words, labelled as theirs. Without the
                    // label it reads as part of the dictionary entry.
                    word?.note?.takeIf { it.isNotBlank() }?.let {
                        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                            Text(
                                stringResource(R.string.community_note),
                                style = TujiType.label,
                                color = TujiColor.Ink3,
                            )
                            Text(it, style = TujiType.body, color = TujiColor.Ink)
                        }
                    }

                    TujiButton(
                        text = stringResource(
                            when {
                                state.saved -> R.string.community_saved
                                state.saving -> R.string.community_saving
                                else -> R.string.community_save
                            },
                        ),
                        onClick = onSave,
                        enabled = !state.saving && !state.saved,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        stringResource(R.string.community_report),
                        style = TujiType.bodySm,
                        color = TujiColor.Ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .tujiClickable { reporting = true }
                            .padding(TujiSpace.S2),
                    )

                    Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
                }

                if (reporting) {
                    ReportSheet(
                        onPick = { reason ->
                            reporting = false
                            onReport(ReportTarget.Item(item.slug), reason)
                        },
                        onDismiss = { reporting = false },
                    )
                }
            }
        }
    }
}

/**
 * 檢舉 — pick a reason.
 *
 * A list of reasons rather than a free-text box: the server's one moderation
 * queue sorts on the reason, and a sentence nobody reads is not moderation.
 */
@Composable
fun ReportSheet(onPick: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Scrim)
            .tujiClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                .padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            Text(
                stringResource(R.string.community_report_title),
                style = TujiType.h3,
                color = TujiColor.Ink,
            )
            listOf(
                ReportReason.Spam to R.string.community_reason_spam,
                ReportReason.Inappropriate to R.string.community_reason_inappropriate,
                ReportReason.Copyright to R.string.community_reason_copyright,
                ReportReason.Wrong to R.string.community_reason_wrong,
                ReportReason.Other to R.string.community_reason_other,
            ).forEach { (reason, label) ->
                Text(
                    stringResource(label),
                    style = TujiType.body,
                    color = TujiColor.Ink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tujiClickable { onPick(reason) }
                        .padding(vertical = TujiSpace.S2),
                )
            }
            TujiButton(
                text = stringResource(R.string.community_cancel),
                style = TujiButtonStyle.Secondary,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
