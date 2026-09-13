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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.atlas.MasteryStore
import app.tuji.android.atlas.TitleRow
import app.tuji.android.atlas.WordDetailSections
import app.tuji.android.atlas.label
import app.tuji.android.atlas.nextReviewLabel
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.community.ReportReason
import app.tuji.android.core.community.ViewerRelationship
import app.tuji.android.core.design.MasteryBar
import app.tuji.android.core.design.ProfileAvatar
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiWindow
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.study.MasteryLevel
import coil3.compose.AsyncImage

/**
 * One published word — iOS's `AtlasPublicDetailView`: the photograph, the
 * word as the word page draws it, who published it and how many people are
 * learning it, and three things the reader can do: 加入學習, 檢舉, 封鎖.
 *
 * Kept from the Android page on purpose: 發布者的筆記, which iOS does not
 * show at all.
 */
@Composable
fun PublicItemScreen(
    state: PublicItemViewModel.State,
    relationship: ViewerRelationship?,
    authorBlocked: Boolean,
    reported: Boolean,
    canPlay: Boolean,
    session: TargetLanguage,
    uiLang: String,
    showChinese: Boolean,
    scores: MasteryStore.Scores,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
    onToggleSave: () -> Unit,
    onPlay: () -> Unit,
    onOpenAuthor: (String) -> Unit,
    onReport: (ReportReason) -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
) {
    val item = state.item
    if (item == null) {
        if (state.loading) {
            Centered(stringResource(R.string.community_loading))
        } else {
            Column(
                Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                verticalArrangement = Arrangement.spacedBy(TujiSpace.S3, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(if (state.notFound) R.string.item_not_found else R.string.community_failed),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink3,
                    textAlign = TextAlign.Center,
                )
                if (!state.notFound) TujiButton(text = stringResource(R.string.retry), onClick = onRetry)
            }
        }
        return
    }

    var askSignIn by remember { mutableStateOf(false) }
    var askStop by remember { mutableStateOf(false) }
    var askBlock by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    val word = item.learningWord

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        if (state.saved) {
            // The taken-in card's own history, under its own namespace.
            val id = CardsSourceRules.SAVED_PREFIX + item.slug
            val score = scores.score(id)
            MasteryBar(
                score = score,
                levelLabel = MasteryLevel.of(score).label(),
                value = if (score != null) "$score" else stringResource(R.string.mastery_no_record),
                nextReview = scores.nextReview(id)?.let { nextReviewLabel(it) },
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(TujiColor.Paper)
                .border(TujiBorder.Bw1, TujiColor.Rule.copy(alpha = 0.2f)),
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(TujiSpace.S3),
            )
        }

        if (word != null) {
            TitleRow(
                word = word,
                session = session,
                uiLang = uiLang,
                playing = state.playing,
                canPlay = canPlay,
                onPlay = onPlay,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.lemma, style = TujiType.h1, color = TujiColor.Ink)
                    LangBadge(item.targetLanguage.badge(), ground = TujiColor.BrandSecondary.copy(alpha = 0.1f), ink = TujiColor.BrandSecondary)
                }
                item.displayZhHant?.let { Text(it, style = TujiType.bodySm, color = TujiColor.Ink2) }
            }
        }

        SocialRow(
            state = state,
            isMine = relationship == ViewerRelationship.Mine,
            onOpenAuthor = onOpenAuthor,
            onPill = {
                when {
                    relationship == ViewerRelationship.Guest -> askSignIn = true
                    state.saved -> askStop = true
                    else -> onToggleSave()
                }
            },
        )

        if (word != null) {
            WordDetailSections(word = word, uiLang = uiLang, showChinese = showChinese)
        }

        // The publisher's own words, labelled as theirs. Without the label it
        // reads as part of the dictionary entry.
        word?.note?.trim()?.takeIf { it.isNotEmpty() }?.let { note ->
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                Text(stringResource(R.string.community_note), style = TujiType.label, color = TujiColor.Ink3)
                Text(note, style = TujiType.body, color = TujiColor.Ink)
            }
        }

        state.error?.let { error ->
            Text(
                stringResource(if (error == PublicItemViewModel.Error.SaveLimit) R.string.collection_learn_limit else R.string.item_action_failed),
                style = TujiType.label,
                color = TujiColor.Alert,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (relationship != ViewerRelationship.Mine) {
            Text(
                stringResource(if (reported) R.string.collection_report_received else R.string.item_report),
                style = TujiType.label,
                color = if (reported) TujiColor.Ink3 else TujiColor.Alert,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .tujiClickable(enabled = !reported) { reporting = true }
                    .padding(vertical = TujiSpace.S2),
            )
        }

        // 檢舉 asks someone else to judge this one word; 封鎖 is the reader's
        // own decision about everything by this author. Different answers to
        // the same moment, so they sit together — and only on someone else's.
        if (relationship == ViewerRelationship.Theirs) {
            Text(
                blockControlLabel(authorBlocked),
                style = TujiType.label,
                color = TujiColor.Ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .tujiClickable { askBlock = true }
                    .padding(vertical = TujiSpace.S2),
            )
        }

        Spacer(Modifier.height(TujiSpace.S6))
    }

    if (askSignIn) {
        TujiPrompt(
            title = stringResource(R.string.item_sign_in_title),
            message = null,
            confirm = stringResource(R.string.auth_sign_in),
            cancel = stringResource(R.string.cancel),
            onConfirm = { askSignIn = false; onSignIn() },
            onCancel = { askSignIn = false },
        )
    }
    if (askStop) {
        TujiPrompt(
            title = stringResource(R.string.item_stop_title),
            message = null,
            confirm = stringResource(R.string.collection_confirm),
            cancel = stringResource(R.string.cancel),
            onConfirm = { askStop = false; onToggleSave() },
            onCancel = { askStop = false },
        )
    }
    if (askBlock) {
        BlockPrompt(
            blocked = authorBlocked,
            onConfirm = { askBlock = false; if (authorBlocked) onUnblock() else onBlock() },
            onCancel = { askBlock = false },
        )
    }
    if (reporting) {
        ReportSheet(
            onPick = { reason -> reporting = false; onReport(reason) },
            onDismiss = { reporting = false },
        )
    }
}

/** The author, how many are learning it, and the one pill — 加入學習, 學習中, or 你的分享. */
@Composable
private fun SocialRow(
    state: PublicItemViewModel.State,
    isMine: Boolean,
    onOpenAuthor: (String) -> Unit,
    onPill: () -> Unit,
) {
    val item = state.item ?: return
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.author?.let { author ->
            Row(
                Modifier.tujiClickable { onOpenAuthor(author.handle) }.padding(vertical = TujiSpace.S1),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileAvatar(avatar = author.avatar, size = 24.dp)
                Text(author.name, style = TujiType.label, color = TujiColor.BrandSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // Unknown until the save route answers — and never for a guest, who
        // cannot ask it. A 0 there would be a claim, not a count.
        state.saveCount?.let {
            Text(stringResource(R.string.item_learning_count, it), style = TujiType.label, color = TujiColor.Ink3, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (isMine) {
            LearnPillLabel(icon = null, text = stringResource(R.string.item_yours), active = true)
        } else {
            LearnPillLabel(
                icon = if (state.saved) ({ TujiGlyph.Check(size = 11.dp, tint = TujiColor.Paper) }) else ({ TujiGlyph.Plus(size = 11.dp, tint = TujiColor.Paper) }),
                text = stringResource(if (state.saved) R.string.community_saved else R.string.community_save),
                active = state.saved,
                modifier = Modifier
                    .alpha(if (state.busy) 0.6f else 1f)
                    .tujiClickable(enabled = !state.busy, onClick = onPill),
            )
        }
    }
}

@Composable
private fun LearnPillLabel(icon: (@Composable () -> Unit)?, text: String, active: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(30.dp)
            .background(if (active) TujiColor.Ink.copy(alpha = 0.64f) else TujiColor.Accumulation)
            .padding(horizontal = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.invoke()
        Text(text, style = TujiType.label, color = TujiColor.Paper, maxLines = 1)
    }
}

/**
 * 檢舉 — pick a reason.
 *
 * A list of reasons rather than a free-text box: the server's one moderation
 * queue sorts on the reason, and a sentence nobody reads is not moderation.
 */
@Composable
fun ReportSheet(onPick: (ReportReason) -> Unit, onDismiss: () -> Unit) = TujiWindow(onDismiss = onDismiss) {
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
                // Swallows taps, so touching the sheet does not dismiss it
                // through the scrim underneath.
                .tujiClickable {}
                .navigationBarsPadding()
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
                text = stringResource(R.string.cancel),
                style = TujiButtonStyle.Secondary,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
