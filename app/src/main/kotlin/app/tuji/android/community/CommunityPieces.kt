package app.tuji.android.community

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.community.CollectionIdentity
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.TargetLanguage
import coil3.compose.AsyncImage

/**
 * A collection's face: its colour, and its avatar photo over it once loaded —
 * iOS's `CollectionIdentityTile`. The colour is what shows while the photo
 * loads and when there is none, so it has to be the same colour everywhere.
 */
@Composable
fun CollectionIdentityTile(collection: AtlasPublicCollection, modifier: Modifier = Modifier) {
    val hex = remember(collection.id, collection.avatarColor) {
        CollectionIdentity.colorHex(collection.id, collection.avatarColor)
    }
    Box(modifier.background(Color(android.graphics.Color.parseColor(hex)))) {
        collection.avatarImageUrl?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** `JA` / `EN` — which deck a collection or item belongs to. */
internal fun TargetLanguage?.badge(): String = when (this) {
    TargetLanguage.JA -> "JA"
    TargetLanguage.EN -> "EN"
    null -> ""
}

/**
 * One 合集 as a row: its identity, title, author, counts and language —
 * iOS's `AtlasCollectionCard`. The byline is off on an author's own page,
 * where every row would repeat the header.
 */
@Composable
fun CollectionRow(
    collection: AtlasPublicCollection,
    onOpen: () -> Unit,
    showsAuthor: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .tujiClickable(onClick = onOpen)
            .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionIdentityTile(collection, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(collection.title, style = TujiType.h3, color = TujiColor.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            // Nothing when the author has no confirmed public identity — the
            // row must not fall back to printing a handle.
            if (showsAuthor) {
                collection.author?.displayName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = TujiType.bodySm, color = TujiColor.Ink2, maxLines = 1)
                }
            }
            Text(collectionCounts(collection), style = TujiType.label, color = TujiColor.Ink3)
        }
        LangBadge(collection.targetLanguage.badge())
    }
}

@Composable
private fun collectionCounts(collection: AtlasPublicCollection): String {
    val items = stringResource(R.string.community_count, collection.itemCount)
    if (collection.saveCount <= 0) return items
    return "$items · ${stringResource(R.string.community_stat_saves)} ${collection.saveCount}"
}

@Composable
internal fun LangBadge(text: String, ground: Color = TujiColor.Paper2, ink: Color = TujiColor.Ink2) {
    if (text.isEmpty()) return
    Box(Modifier.height(24.dp).background(ground).padding(horizontal = TujiSpace.S2), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.label, color = ink)
    }
}

/** A hairline between rows, inset from the page margin like every list. */
@Composable
internal fun RowRule() {
    Box(Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4).height(TujiBorder.Bw1).background(TujiColor.Rule))
}

/**
 * One member of a collection, as a grid tile — iOS's `AtlasPublicTile`: the
 * photo with its language in the corner, the word, its gloss, and a byline
 * that opens the author when [onOpenAuthor] is given.
 */
@Composable
fun PublicItemTile(
    item: AtlasPublicItem,
    enabled: Boolean,
    onOpen: () -> Unit,
    onOpenAuthor: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.25f)),
    ) {
        Box(Modifier.fillMaxWidth().height(120.dp).background(TujiColor.Paper).tujiClickable(enabled = enabled, onClick = onOpen)) {
            AsyncImage(model = item.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.align(Alignment.TopEnd).padding(TujiSpace.S2)) {
                LangBadge(item.targetLanguage.badge(), ground = TujiColor.BrandSecondary.copy(alpha = 0.1f), ink = TujiColor.BrandSecondary)
            }
        }
        Column(
            Modifier.fillMaxWidth().tujiClickable(enabled = enabled, onClick = onOpen).padding(TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(item.lemma, style = TujiType.bodySmStrong, color = TujiColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.displayZhHant.orEmpty(), style = TujiType.label, color = TujiColor.Ink3, maxLines = 1)
            item.author?.let { author ->
                Text(
                    stringResource(R.string.community_tile_by, author.name),
                    style = TujiType.label,
                    color = if (onOpenAuthor != null) TujiColor.BrandSecondary else TujiColor.Ink3,
                    maxLines = 1,
                    modifier = if (onOpenAuthor != null && enabled) Modifier.tujiClickable(onClick = onOpenAuthor).padding(top = 1.dp) else Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/** A lock and a line: what a collection keeps back until it is saved. */
@Composable
internal fun LockedNote(total: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TujiGlyph.Lock(size = 12.dp, tint = TujiColor.Ink3)
        Text(stringResource(R.string.collection_locked, total), style = TujiType.label, color = TujiColor.Ink3)
    }
}

@Composable
internal fun Gap(height: Dp) = Spacer(Modifier.height(height))
