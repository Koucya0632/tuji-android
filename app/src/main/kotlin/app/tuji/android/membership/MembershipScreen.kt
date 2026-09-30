package app.tuji.android.membership

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType

/**
 * 會員方案 — where every lock and every 402 in this app leads.
 *
 * iOS's paywall, as two cards: 永久會員 and Tuji Pro, each with only its own
 * benefits, so nobody reads Pro's list as part of 永久會員's. There is no buy
 * button: Play Billing is not set up (ADR-0001), and a button that cannot do
 * its job is worse than none. What the page does instead is say that — and
 * that a membership bought on iPhone already counts here, since it belongs to
 * the Tuji account, not to the store.
 */
@Composable
fun MembershipScreen(offer: MembershipOffer) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TujiScreenTitle(stringResource(R.string.membership_title))
        Column(
            Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S6),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            Text(stringResource(offer.headline), style = TujiType.h3, color = TujiColor.Ink)

            if (offer.showsLifetime) {
                PlanCard(
                    title = stringResource(R.string.membership_lifetime),
                    caption = stringResource(R.string.membership_lifetime_once),
                    rows = offer.lifetimeRows,
                    owned = stringResource(R.string.membership_owns_lifetime).takeIf { offer.ownsLifetime },
                )
            }
            PlanCard(
                title = "Tuji Pro",
                // Under v2 Pro contains 永久會員, and says so before listing
                // only what it adds.
                caption = stringResource(R.string.membership_pro_includes).takeIf { offer.showsLifetime },
                rows = offer.proRows,
                owned = stringResource(R.string.membership_owns_pro).takeIf { offer.ownsPro },
            )

            if (!offer.ownsPro) {
                Text(
                    stringResource(R.string.membership_android_soon),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TujiColor.Paper2)
                        .padding(TujiSpace.S3),
                )
            }
            if (offer.showsLifetime) {
                Text(
                    stringResource(R.string.membership_lifetime_note),
                    style = TujiType.label,
                    color = TujiColor.Ink3,
                )
            }
        }
    }
}

@Composable
private fun PlanCard(title: String, caption: String?, rows: List<Int>, owned: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper)
            .border(TujiBorder.Bw1, TujiColor.Rule)
            .padding(TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TujiType.h3, color = TujiColor.Ink)
            caption?.let { Text(it, style = TujiType.label, color = TujiColor.Ink3) }
        }
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
                TujiGlyph.Check(size = 14.dp, tint = TujiColor.Accumulation)
                Text(stringResource(row), style = TujiType.bodySm, color = TujiColor.Ink)
            }
        }
        owned?.let {
            Spacer(Modifier.height(TujiSpace.S1))
            Text(it, style = TujiType.bodySmStrong, color = TujiColor.Accumulation)
        }
    }
}
