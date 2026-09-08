package app.tuji.android.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ReportTarget
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasAuthorPage
import coil3.compose.AsyncImage

/**
 * One author's public shelf.
 *
 * A 檢舉 here targets the **identity**, not one word — which is why it carries
 * the TJ UID rather than a slug: a slug moves with a rename and a UID does not.
 */
@Composable
fun AuthorScreen(
    page: AtlasAuthorPage?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenItem: (String) -> Unit,
    onReport: (ReportTarget, ReportReason) -> Unit,
) {
    if (page?.author == null) {
        Centered(stringResource(R.string.community_loading))
        return
    }
    var reporting by remember { mutableStateOf(false) }
    val author = page.author!!

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(
                start = TujiSpace.S4, end = TujiSpace.S4,
                top = TujiSpace.S3, bottom = bottomPadding + TujiSpace.S6,
            ),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                    Text(author.name, style = TujiType.h2, color = TujiColor.Ink)
                    // The UID under the nickname: a nickname can change and be
                    // reused, and this is the id a report actually carries.
                    Text(author.handle, style = TujiType.monoLabel, color = TujiColor.Ink3)
                    author.bio?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = TujiType.body, color = TujiColor.Ink2)
                    }
                    author.publishedCount?.let {
                        Text(
                            stringResource(R.string.community_author_published, it),
                            style = TujiType.bodySm,
                            color = TujiColor.Ink3,
                        )
                    }
                }
            }
            items(page.items, key = { it.id }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(TujiColor.Paper2)
                        .tujiClickable { onOpenItem(entry.slug) }
                        .padding(TujiSpace.S2),
                    horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp).background(TujiColor.Paper3)) {
                        AsyncImage(
                            model = entry.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.lemma,
                            style = TujiType.bodyStrong,
                            color = TujiColor.Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        entry.displayZhHant?.let {
                            Text(it, style = TujiType.bodySm, color = TujiColor.Ink3, maxLines = 1)
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.community_report),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tujiClickable { reporting = true }
                        .padding(TujiSpace.S3),
                )
            }
        }

        if (reporting) {
            ReportSheet(
                onPick = { reason ->
                    reporting = false
                    onReport(ReportTarget.Author(author.handle), reason)
                },
                onDismiss = { reporting = false },
            )
        }
    }
}
