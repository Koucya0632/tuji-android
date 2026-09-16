package app.tuji.android.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.ProfileAvatar
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSkeletonRows
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSegmented
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.TargetLanguage

/** 探索 or 已收藏. */
enum class CommunityShelf { Explore, Saved }

/**
 * 物見 — other people's collections, and the version of you they see.
 *
 * iOS's `AtlasPublicFeedView`: a row that opens this account's own public page,
 * then 探索／已收藏 over lists of 合集. No 拍照 button here — the camera is in
 * the middle of the tab bar now, one tap from every tab — and no loose list of
 * single words: a word is reached through its collection or its author.
 */
@Composable
fun CommunityScreen(
    explore: CommunityViewModel.Shelf,
    saved: CommunityViewModel.Shelf,
    me: AtlasAuthor?,
    isGuest: Boolean,
    language: TargetLanguage,
    onShowSaved: () -> Unit,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onOpenCollection: (String) -> Unit,
    onOpenMyPage: (String) -> Unit,
) {
    var shelf by rememberSaveable { mutableStateOf(CommunityShelf.Explore) }
    // Asked for the first time 已收藏 is opened, and again on each return to
    // it: a collection saved in a detail screen belongs on it by then.
    LaunchedEffect(shelf, isGuest) {
        if (shelf == CommunityShelf.Saved && !isGuest) onShowSaved()
    }

    Column(Modifier.fillMaxSize().padding(top = TujiSpace.S3)) {
        // Guests have no public page, and a row that fails to load is simply
        // not there — the list below must not pay for it.
        me?.let { author ->
            MyPageRow(author) { onOpenMyPage(author.handle) }
            RowRule()
            Spacer(Modifier.height(TujiSpace.S3))
        }
        TujiSegmented(
            options = listOf(
                CommunityShelf.Explore to stringResource(R.string.community_shelf_explore),
                CommunityShelf.Saved to stringResource(R.string.community_shelf_saved),
            ),
            selected = shelf,
            onSelect = { shelf = it },
        )
        Spacer(Modifier.height(TujiSpace.S3))

        Box(Modifier.weight(1f)) {
            when (shelf) {
                CommunityShelf.Explore -> ShelfList(
                    state = explore,
                    empty = stringResource(R.string.community_explore_empty),
                    onRetry = onRetry,
                    onOpenCollection = onOpenCollection,
                )
                CommunityShelf.Saved -> if (isGuest) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.community_saved_guest), style = TujiType.bodySm, color = TujiColor.Ink3, textAlign = TextAlign.Center)
                        TujiButton(text = stringResource(R.string.auth_sign_in), onClick = onSignIn)
                    }
                } else {
                    ShelfList(
                        state = saved,
                        empty = stringResource(
                            if (language == TargetLanguage.JA) R.string.community_saved_empty_ja else R.string.community_saved_empty_en,
                        ),
                        onRetry = onShowSaved,
                        onOpenCollection = onOpenCollection,
                    )
                }
            }
        }
    }
}

/**
 * This account as other people see it: the two numbers its public page shows,
 * in the words that page uses. It sits beside other people's work because
 * that is the shelf it is on.
 */
@Composable
private fun MyPageRow(author: AtlasAuthor, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .tujiClickable(onClick = onOpen)
            .padding(horizontal = TujiSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(avatar = author.avatar, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(author.name, style = TujiType.h3, color = TujiColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${stringResource(R.string.community_stat_published)} ${author.publishedCount ?: 0} · " +
                    "${stringResource(R.string.community_stat_saves)} ${author.saveCount ?: 0}",
                style = TujiType.label,
                color = TujiColor.Ink3,
                maxLines = 1,
            )
        }
        TujiGlyph.ArrowLeft(size = 16.dp, tint = TujiColor.Ink3, modifier = Modifier.padding(start = TujiSpace.S2).rotate(180f))
    }
}

@Composable
private fun ShelfList(
    state: CommunityViewModel.Shelf,
    empty: String,
    onRetry: () -> Unit,
    onOpenCollection: (String) -> Unit,
) {
    when {
        state.loading ->
            TujiSkeletonRows(count = 3, height = 88.dp, label = stringResource(R.string.community_loading))
        state.failed -> Column(
            Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.community_failed), style = TujiType.bodySm, color = TujiColor.Ink3, textAlign = TextAlign.Center)
            TujiButton(text = stringResource(R.string.retry), onClick = onRetry)
        }
        state.collections.isEmpty() -> Centered(empty)
        else -> LazyColumn(contentPadding = PaddingValues(top = TujiSpace.S1, bottom = TujiSpace.S5)) {
            itemsIndexed(state.collections, key = { _, c -> c.id }) { index, collection ->
                if (index > 0) RowRule()
                CollectionRow(collection, onOpen = { onOpenCollection(collection.slug) })
            }
        }
    }
}

@Composable
internal fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.body, color = TujiColor.Ink3, textAlign = TextAlign.Center)
    }
}
