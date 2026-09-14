package app.tuji.android.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.community.CollectionIdentityTile
import app.tuji.android.core.community.CollectionAuthoringRules
import app.tuji.android.core.community.ReviewStatus
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiRowDivider
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiStatusEdgeLabel
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.AtlasMyCollection
import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.profile.FormInput

/** 圖鑑管理's 合集 — iOS's `AtlasMyCollectionsView`. */
@Composable
internal fun MyCollectionsPane(
    state: MyCollectionsViewModel.State,
    onRetry: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        val visible = state.visible
        when {
            state.loading && state.collections.isEmpty() ->
                Line(stringResource(R.string.atlas_loading))
            state.failed && state.collections.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
            ) {
                Text(stringResource(R.string.atlas_failed), style = TujiType.label, color = TujiColor.Ink3)
                Text(
                    stringResource(R.string.retry),
                    style = TujiType.bodyStrong,
                    color = TujiColor.Ink,
                    modifier = Modifier.background(TujiColor.Current).tujiClickable(onClick = onRetry).padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S2),
                )
            }
            visible.isEmpty() -> Line(
                stringResource(if (state.language == TargetLanguage.JA) R.string.collections_empty_ja else R.string.collections_empty_en),
            )
            else -> visible.forEachIndexed { index, collection ->
                if (index > 0) TujiRowDivider()
                MyCollectionRow(collection, onOpen = { onOpen(collection.id) })
            }
        }
    }
}

@Composable
private fun MyCollectionRow(collection: AtlasMyCollection, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().tujiClickable(onClick = onOpen).padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionIdentityTile(collection.asTile(), Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(collection.title, style = TujiType.h3, color = TujiColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.community_count, collection.itemCount), style = TujiType.label, color = TujiColor.Ink3)
        }
        val review = ReviewStatus.of(collection.reviewStatus)
        TujiStatusEdgeLabel(text = review.label(), edge = review.edge())
    }
}

/** The identity tile reads the public shape; an own collection has the same three facts it needs. */
internal fun AtlasMyCollection.asTile() = AtlasPublicCollection(
    id = id,
    slug = slug ?: id,
    title = title,
    avatarColor = avatarColor,
    avatarImageUrl = avatarImageUrl,
)

internal fun ReviewStatus.edge(): Color = when (this) {
    ReviewStatus.Approved -> TujiColor.Accumulation
    ReviewStatus.Pending -> TujiColor.Current
    ReviewStatus.Rejected, ReviewStatus.Takedown -> TujiColor.Alert
    ReviewStatus.Draft, ReviewStatus.Withdrawn -> TujiColor.Ink3
}

@Composable
private fun Line(text: String) {
    Text(text, style = TujiType.bodySm, color = TujiColor.Ink3, modifier = Modifier.fillMaxWidth().padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S4))
}

/** 建立合集 — iOS's `AtlasCollectionCreateSheet`: a title, an optional 簡介, and the language it is in. */
@Composable
internal fun CreateCollectionSheet(
    state: MyCollectionsViewModel.State,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) = TujiWindow(onDismiss = onDismiss) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    val canCreate = !state.creating && CollectionAuthoringRules.titleValid(title)
    Box(
        Modifier.fillMaxSize().background(TujiColor.Scrim).tujiClickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                .tujiClickable {}
                .navigationBarsPadding()
                .imePadding()
                .padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        ) {
            Text(stringResource(R.string.collections_create), style = TujiType.h2, color = TujiColor.Ink)
            Label(stringResource(R.string.collections_field_title))
            FormInput(value = title, onValueChange = { title = it }, placeholder = stringResource(R.string.collections_title_placeholder), valid = title.trim().length <= CollectionAuthoringRules.TITLE_MAX, enabled = !state.creating)
            Label(stringResource(R.string.collections_field_about_optional))
            FormInput(value = description, onValueChange = { description = it }, placeholder = stringResource(R.string.collections_about_placeholder), valid = true, enabled = !state.creating, singleLine = false)
            Text(stringResource(R.string.collections_create_footer), style = TujiType.label, color = TujiColor.Ink3)
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Label(stringResource(R.string.collections_field_language))
                Text(
                    stringResource(if (state.language == TargetLanguage.JA) R.string.direction_zh_ja else R.string.direction_zh_en),
                    style = TujiType.body,
                    color = TujiColor.Ink2,
                )
            }
            if (state.createFailed) {
                Text(stringResource(R.string.manage_action_failed), style = TujiType.label, color = TujiColor.Alert)
            }
            Text(
                stringResource(if (state.creating) R.string.collections_creating else R.string.collections_create_confirm),
                style = TujiType.h3,
                color = TujiColor.Ink,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .alpha(if (canCreate) 1f else 0.5f)
                    .background(TujiColor.Current)
                    .tujiClickable(enabled = canCreate) { onCreate(title, description) }
                    .padding(vertical = TujiSpace.S3),
            )
        }
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, style = TujiType.label, color = TujiColor.Ink3)
}
