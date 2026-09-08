package app.tuji.android.community

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicItem
import coil3.compose.AsyncImage

/**
 * 物見 — what other people published, and the way in to publishing your own.
 *
 * M3 shipped this screen with **no camera entry at all**, because publishing did
 * not exist yet and a greyed 拍照 button promises what a build cannot do. M5
 * added the flow, so the entry is here now — gated on the account's remaining
 * 自製圖鑑 slots rather than always drawn, for the same reason.
 */
@Composable
fun CommunityScreen(
    feed: CommunityViewModel.Feed,
    bottomPadding: androidx.compose.ui.unit.Dp,
    /** Remaining 自製圖鑑 slots, or null while the entitlement has not landed. */
    slotsLeft: Int? = null,
    onCapture: (() -> Unit)? = null,
    onOpenItem: (String) -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenAuthor: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // Outside the feed's state, deliberately. Making your own card has
        // nothing to do with whether other people's are readable — and putting
        // it inside the success branch meant a failed feed also took away the
        // camera, which is the one thing on this screen that still works
        // offline right up to the upload.
        if (onCapture != null && slotsLeft != null && slotsLeft > 0) {
            TujiButton(
                text = stringResource(R.string.capture_entry),
                onClick = onCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
            )
        }

        when {
        feed.loading -> Centered(stringResource(R.string.community_loading))
        feed.failed -> Centered(stringResource(R.string.community_failed))
        feed.items.isEmpty() && feed.collections.isEmpty() ->
            Centered(stringResource(R.string.community_empty))

        else -> LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding + TujiSpace.S6),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (feed.collections.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.community_collections))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = TujiSpace.S4),
                        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
                    ) {
                        items(feed.collections, key = { it.id }) {
                            CollectionCard(it) { onOpenCollection(it.slug) }
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.community_items)) }
            items(feed.items, key = { it.id }) { entry ->
                ItemRow(
                    entry = entry,
                    onClick = { onOpenItem(entry.slug) },
                    onAuthor = { entry.author?.handle?.let(onOpenAuthor) },
                )
            }
        }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = TujiType.label,
        color = TujiColor.Ink3,
        modifier = Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
    )
}

@Composable
private fun CollectionCard(collection: AtlasPublicCollection, onClick: () -> Unit) {
    Column(
        Modifier
            .width(180.dp)
            .background(TujiColor.Paper2)
            .tujiClickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 2f).background(TujiColor.Paper3)) {
            collection.coverImageUrl?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(TujiSpace.S2)) {
            Text(
                collection.title,
                style = TujiType.bodyStrong,
                color = TujiColor.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.community_count, collection.itemCount),
                style = TujiType.monoLabel,
                color = TujiColor.Ink3,
            )
        }
    }
}

@Composable
private fun ItemRow(entry: AtlasPublicItem, onClick: () -> Unit, onAuthor: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = TujiSpace.S4)
            .background(TujiColor.Paper2)
            .tujiClickable(onClick = onClick)
            .padding(TujiSpace.S2),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).background(TujiColor.Paper3)) {
            AsyncImage(
                model = entry.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(entry.lemma, style = TujiType.bodyStrong, color = TujiColor.Ink, maxLines = 1)
            entry.displayZhHant?.let {
                Text(it, style = TujiType.bodySm, color = TujiColor.Ink3, maxLines = 1)
            }
            entry.author?.let { author ->
                Text(
                    author.name,
                    style = TujiType.monoLabel,
                    color = TujiColor.Accumulation,
                    modifier = Modifier.tujiClickable(onClick = onAuthor).padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
internal fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.body, color = TujiColor.Ink3)
    }
}
