package app.tuji.android.membership

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiScreenTitle
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.credits.tierCard

/**
 * 會員方案 — where every lock and every 402 in this app leads. iOS's `PaywallView`.
 *
 * Under 罐頭點數 billing it is iOS's 「會員與罐頭點數」: the wallet first, then
 * 永久會員 with what the membership itself opens. Otherwise, the two plan cards,
 * each with only its own benefits, so nobody reads Pro's list as part of
 * 永久會員's.
 *
 * There is no buy button: Play Billing is not set up (ADR-0001), and a button
 * that cannot do its job is worse than none. What the page does instead is say
 * that — and that a membership bought on iPhone already counts here, since it
 * belongs to the Tuji account, not to the store.
 */
@Composable
fun MembershipScreen(
    offer: MembershipOffer,
    /** The 罐頭點數 wallet card. Drawn when billing is on points and the account is known. */
    walletCard: (@Composable () -> Unit)? = null,
) {
    val credits = offer.credits && walletCard != null
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        TujiScreenTitle(stringResource(if (credits) R.string.membership_title_credits else R.string.membership_title))
        Column(
            Modifier.padding(horizontal = TujiSpace.S4).padding(bottom = TujiSpace.S6),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                TujiGlyph.Crown()
                // Under points billing there is no Pro to sell, so the line
                // only speaks while 永久會員 is still to be had.
                if (!credits || (offer.showsLifetime && !offer.ownsLifetime)) {
                    Text(stringResource(offer.headline), style = TujiType.h3, color = TujiColor.Ink)
                }
            }

            if (credits) {
                walletCard?.invoke()
                if (offer.showsLifetime) {
                    PlanCard(
                        title = stringResource(R.string.membership_lifetime),
                        caption = null,
                        rows = offer.lifetimeRows,
                        owned = stringResource(R.string.membership_owns_lifetime).takeIf { offer.ownsLifetime },
                        tier = true,
                    )
                }
            } else {
                if (offer.showsLifetime) {
                    PlanCard(
                        title = stringResource(R.string.membership_lifetime),
                        caption = stringResource(R.string.membership_lifetime_once),
                        rows = offer.lifetimeRows,
                        owned = stringResource(R.string.membership_owns_lifetime).takeIf { offer.ownsLifetime },
                    )
                }
                if (offer.ownsPro) PlanCard(
                    title = "Tuji Pro",
                    // Under v2 Pro contains 永久會員, and says so before listing
                    // only what it adds.
                    caption = stringResource(R.string.membership_pro_includes).takeIf { offer.showsLifetime },
                    rows = offer.proRows,
                    owned = stringResource(R.string.membership_owns_pro).takeIf { offer.ownsPro },
                )
                Text(stringResource(R.string.credit_pro_closed), style = TujiType.bodySm, color = TujiColor.Ink2)
            }

            // Where the buy buttons stand on iOS.
            if (!offer.ownsPro && !(credits && offer.ownsLifetime)) {
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
            Legal(showsLifetime = offer.showsLifetime)
        }
    }
}

@Composable
private fun PlanCard(title: String, caption: String?, rows: List<Int>, owned: String?, tier: Boolean = false) {
    Column(
        if (tier) {
            Modifier.tierCard()
        } else {
            Modifier
                .fillMaxWidth()
                .background(TujiColor.Paper)
                .border(TujiBorder.Bw1, TujiColor.Rule)
                .padding(TujiSpace.S4)
        },
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

/** iOS's `legal`: what 永久會員 is, and the two documents every paywall links. */
@Composable
private fun Legal(showsLifetime: Boolean) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        if (showsLifetime) {
            Text(stringResource(R.string.membership_lifetime_note), style = TujiType.label, color = TujiColor.Ink3)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            Text(
                stringResource(R.string.membership_terms),
                style = TujiType.label,
                color = TujiColor.BrandSecondary,
                modifier = Modifier.tujiClickable { open(TERMS_URL) },
            )
            Text(
                stringResource(R.string.membership_privacy),
                style = TujiType.label,
                color = TujiColor.BrandSecondary,
                modifier = Modifier.tujiClickable { open(PRIVACY_URL) },
            )
        }
    }
}

/** The same documents iOS links. */
private const val TERMS_URL = "https://tuji.nexflow.team/terms"
private const val PRIVACY_URL = "https://tuji.nexflow.team/privacy"
