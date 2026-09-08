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
import app.tuji.android.core.model.AtlasCollectionDetail
import coil3.compose.AsyncImage

/** One 合集, and the words in it. */
@Composable
fun CollectionScreen(
    detail: AtlasCollectionDetail?,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenItem: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
    onReport: (ReportTarget, ReportReason) -> Unit,
) {
    val collection = detail?.collection
    if (collection == null) {
        Centered(stringResource(R.string.community_loading))
        return
    }
    var reporting by remember { mutableStateOf(false) }

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
                    Text(collection.title, style = TujiType.h2, color = TujiColor.Ink)
                    collection.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = TujiType.body, color = TujiColor.Ink2)
                    }
                    collection.author?.let { author ->
                        Text(
                            author.name,
                            style = TujiType.bodySmStrong,
                            color = TujiColor.Accumulation,
                            modifier = Modifier.tujiClickable { onOpenAuthor(author.handle) },
                        )
                    }
                    detail.access?.let { access ->
                        // The server's own count of how much of this collection
                        // the reader is already studying — shown rather than
                        // recomputed, because the two namespaces that hold that
                        // answer are not both on this device.
                        Text(
                            stringResource(
                                R.string.community_collection_learning,
                                access.learningCount, access.totalCount,
                            ),
                            style = TujiType.monoLabel,
                            color = TujiColor.Ink3,
                        )
                        if (access.isSaved) {
                            Text(
                                stringResource(R.string.community_collection_saved),
                                style = TujiType.bodySm,
                                color = TujiColor.Accumulation,
                            )
                        }
                    }
                }
            }
            items(detail.items, key = { it.id }) { entry ->
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
                    onReport(ReportTarget.Collection(collection.slug), reason)
                },
                onDismiss = { reporting = false },
            )
        }
    }
}
