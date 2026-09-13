package app.tuji.android.account

import androidx.compose.foundation.background
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.design.TujiGlyph
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import app.tuji.android.core.study.MasteryDistribution
import app.tuji.android.core.study.CategoryStat
import app.tuji.android.core.model.Category
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.tuji.android.R
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import app.tuji.android.core.design.ProfileAvatar
import app.tuji.android.core.design.TujiStatusEdgeLabel
import app.tuji.android.core.model.UserMe
import app.tuji.android.core.study.CompletionReadout
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType

/**
 * 我的 — who you are (lightest), then what you have built up.
 *
 * iOS's order: an identity *row*, not a hero — a big centred name was the app
 * telling you about yourself — and then the progress that is the point of the
 * tab. The plan card that used to sit between them is gone with its
 * developer's voice; 方案 is a label on the row, and the paywall it opens on
 * iOS arrives with P5.
 */
@Composable
fun AccountScreen(
    state: AccountViewModel.State,
    isGuest: Boolean,
    completion: CompletionReadout,
    progress: ProgressStore.Snapshot,
    spread: MasteryDistribution,
    masteryLoaded: Boolean,
    categories: List<Category>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Spacer(Modifier.height(TujiSpace.S3))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            val label = stringResource(R.string.settings_title)
            Box(
                Modifier
                    .size(44.dp)
                    .semantics { contentDescription = label }
                    .tujiClickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                TujiGlyph.Gear(tint = TujiColor.Ink)
            }
        }

        IdentityRow(me = state.me, isGuest = isGuest, isPro = state.entitlement?.isPro == true)

        // 我的 is no longer a name and a plan — it *is* your progress. The
        // order is width (how far you have come) → depth (how well) → habit
        // (whether you keep showing up) → detail (where exactly).
        Spacer(Modifier.height(TujiSpace.S2))
        CompletionCard(completion)
        MasterySection(spread = spread, loaded = masteryLoaded)
        StreakRow(
            current = progress.streak?.current ?: 0,
            longest = progress.streak?.longest ?: 0,
        )
        HeatmapSection(cells = progress.heatmap, activeDays = progress.activeDays)
        CategoryBreakdown(
            remember(progress.categories, categories) {
                CategoryStat.breakdown(
                    progress = progress.categories,
                    categoryOrder = categories,
                )
            },
        )
        Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
    }
}

/**
 * Avatar, name, UID, plan. No email: it is the one fact here that is private,
 * on the one tab a user hands their phone to someone else to show off.
 */
@Composable
private fun IdentityRow(me: UserMe?, isGuest: Boolean, isPro: Boolean) {
    val name = if (isGuest) stringResource(R.string.me_guest_name) else me?.displayName ?: stringResource(R.string.me_guest_name)
    // The UID, not the nickname: it is what reports, blocks and support
    // requests carry. The email's local part only for an account whose UID has
    // not mirrored yet.
    val handle = when {
        isGuest -> "guest"
        else -> me?.username?.takeIf { it.isNotBlank() } ?: me?.email?.substringBefore('@')
    }
    val tier = if (isPro) "Pro" else "Free"
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$name, $tier" },
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(avatar = if (isGuest) null else me?.avatar, size = 48.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = TujiType.h3, color = TujiColor.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            handle?.let {
                Text(
                    stringResource(R.string.community_author_uid, it),
                    style = TujiType.monoLabel,
                    color = TujiColor.Ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TujiStatusEdgeLabel(text = tier, edge = if (isPro) TujiColor.Accumulation else TujiColor.Ink3)
    }
}
