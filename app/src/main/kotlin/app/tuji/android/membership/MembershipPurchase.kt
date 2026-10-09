package app.tuji.android.membership

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.billing.PlayBilling
import app.tuji.android.billing.PlayDelivery
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable

/** The buy side of 會員方案: what Play sells this account, and what to do when it is tapped. */
class MembershipPurchase(
    val state: PlayBilling.State,
    val onBuy: (productId: String) -> Unit,
    /** 重新載入方案: ask Play for the products again. */
    val onReload: () -> Unit,
    /** 恢復購買: send anything Play holds that the server has not granted yet. */
    val onRestore: () -> Unit,
) {
    val lifetime: PlayBilling.Offer? get() = state.offers[PlayDelivery.LIFETIME]
}

/**
 * 永久會員's buy button, or why there is none. Never a card with no way to buy
 * and no word about it — iOS's rule, and the reason a load failure has its own
 * line and a retry.
 */
@Composable
internal fun LifetimeBuy(purchase: MembershipPurchase) {
    val offer = purchase.lifetime
    when {
        offer != null -> BuyRow(
            title = stringResource(R.string.membership_lifetime),
            caption = stringResource(R.string.membership_lifetime_once),
            price = offer.price,
            busy = purchase.state.purchasing == offer.productId,
            enabled = purchase.state.purchasing == null,
            onClick = { purchase.onBuy(offer.productId) },
        )
        purchase.state.loading -> TujiIndeterminateBar(Modifier.fillMaxWidth())
        else -> Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            Text(stringResource(R.string.billing_load_failed), style = TujiType.label, color = TujiColor.Ink3)
            TujiButton(
                text = stringResource(R.string.billing_reload),
                onClick = purchase.onReload,
                style = TujiButtonStyle.Secondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** iOS's 加購 rows: one per pack the server sells this account and Play has a price for. */
@Composable
fun CreditPackButtons(purchase: MembershipPurchase) {
    val state = purchase.state
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        state.packs.forEach { pack ->
            val offer = state.offers[pack.productId] ?: return@forEach
            BuyRow(
                title = stringResource(R.string.billing_buy_points, pack.points),
                caption = null,
                price = offer.price,
                busy = state.purchasing == pack.productId,
                enabled = state.purchasing == null,
                onClick = { purchase.onBuy(pack.productId) },
            )
        }
    }
}

/** iOS's `planButton`: the product on the left, its store price on the right, on brand brown. */
@Composable
internal fun BuyRow(title: String, caption: String?, price: String, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (enabled || busy) TujiColor.BrandSecondary else TujiColor.Ink3)
            .tujiClickable(enabled = enabled, onClick = onClick)
            .padding(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TujiType.bodyStrong, color = TujiColor.BrandPrimary)
            caption?.let { Text(it, style = TujiType.label, color = Color.White.copy(alpha = 0.85f)) }
        }
        Spacer(Modifier.width(TujiSpace.S3))
        if (busy) {
            TujiIndeterminateBar(Modifier.width(56.dp), track = TujiColor.Paper.copy(alpha = 0.2f), fill = TujiColor.Paper)
        } else {
            Text(price, style = TujiType.bodyStrong, color = Color.White)
        }
    }
}

/** What the last purchase came to, in a sentence. */
@Composable
internal fun PurchaseNotice(notice: PlayBilling.Notice?) {
    val (text, alert) = when (notice ?: return) {
        PlayBilling.Notice.Delivered -> R.string.billing_notice_delivered to false
        PlayBilling.Notice.Pending -> R.string.billing_notice_pending to false
        PlayBilling.Notice.AlreadyOwned -> R.string.billing_notice_already_owned to false
        PlayBilling.Notice.SyncPending -> R.string.billing_notice_sync_pending to true
        PlayBilling.Notice.Failed -> R.string.billing_notice_failed to true
        PlayBilling.Notice.Unavailable -> R.string.billing_notice_unavailable to true
    }
    Text(
        stringResource(text),
        style = TujiType.label,
        color = if (alert) TujiColor.Alert else TujiColor.Accumulation,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (alert) TujiColor.Alert.copy(alpha = 0.12f) else TujiColor.Paper2)
            .padding(TujiSpace.S3),
    )
}

/** iOS's 恢復購買. */
@Composable
internal fun RestoreLink(purchase: MembershipPurchase) {
    Text(
        stringResource(R.string.billing_restore),
        style = TujiType.bodySmStrong,
        color = TujiColor.BrandSecondary,
        modifier = Modifier
            .tujiClickable(enabled = purchase.state.purchasing == null, onClick = purchase.onRestore)
            .padding(vertical = TujiSpace.S1),
    )
}
