package app.tuji.android.credits

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.*
import app.tuji.android.core.network.*

/**
 * 罐頭點數 — iOS's `CreditWalletView`, on the paywall as its first card: the
 * balance, where it comes from, and 每日簽到.
 *
 * No point packs: Android cannot take payments yet (ADR-0001), and a buy button
 * that cannot do its job is worse than none. The page around this card says
 * where they can be bought.
 */
@Composable
fun CreditWalletCard(api: TujiApiClient, atlas: AtlasAuthoring, owner: String, currentOwner: () -> String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(owner) {
        CreditCaptureModel(CreditRepository(api), atlas, owner, currentOwner,
            context.getSharedPreferences("credit-journal", Context.MODE_PRIVATE), scope)
    }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.load() }
    val wallet = state.wallet
    Column(Modifier.tierCard(), verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.credit_can), null, Modifier.size(48.dp), tint = TujiColor.Ink)
            Column {
                Text(stringResource(R.string.credit_title), style = TujiType.h3, color = TujiColor.Ink)
                Text(wallet?.available?.toString() ?: "—", style = TujiType.h3, color = TujiColor.Ink)
            }
        }
        if (wallet != null) {
            Text(
                stringResource(R.string.credit_balances, wallet.reserved, wallet.giftAvailable, wallet.paidAvailable),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
            if (wallet.reconciliationRequired) {
                Text(stringResource(R.string.credit_refund_hold), style = TujiType.label, color = TujiColor.Ink)
            }
        }
        val benefits = wallet?.benefits
        // Studying is the check-in: no answer today, nothing to collect yet.
        val canCheckIn = !state.busy && state.catalog?.checkInEnabled == true && benefits?.hasLifetime == true &&
            !benefits.checkedInToday && benefits.studiedToday != false && benefits.checkInGrantedThisMonth < 300
        TujiButton(
            text = stringResource(if (benefits?.checkedInToday == true) R.string.credit_checked_in else R.string.credit_check_in),
            onClick = { model.claim(false) },
            enabled = canCheckIn,
            style = TujiButtonStyle.Secondary,
        )
        Text(stringResource(R.string.credit_monthly_auto), style = TujiType.body, color = TujiColor.Ink)
        Text(
            stringResource(R.string.credit_free_balance, wallet?.monthlyAvailable ?: 0, wallet?.checkInAvailable ?: 0),
            style = TujiType.label,
            color = TujiColor.Ink3,
        )
        Text(stringResource(R.string.credit_monthly_reset_note), style = TujiType.label, color = TujiColor.Ink3)
        Text(stringResource(R.string.credit_check_in_note), style = TujiType.label, color = TujiColor.Ink3)
        state.error?.let { Text(stringResource(creditErrorResource(it)), style = TujiType.label, color = TujiColor.Alert) }
        Text(
            stringResource(R.string.credit_retry),
            style = TujiType.bodySmStrong,
            color = if (state.busy) TujiColor.Ink3 else TujiColor.Ink,
            modifier = Modifier.tujiClickable(enabled = !state.busy, onClick = model::load).padding(vertical = TujiSpace.S1),
        )
    }
}

/** iOS's `tierCard()`: a paper card with a rule, one per thing the paywall sells. */
fun Modifier.tierCard(): Modifier = this
    .fillMaxWidth()
    .background(TujiColor.Paper2)
    .border(TujiBorder.Bw1, TujiColor.Rule)
    .padding(TujiSpace.S3)

internal fun creditErrorResource(code: String): Int = when (code) {
    "insufficient_credits" -> R.string.credit_insufficient
    "capacity_full" -> R.string.credit_capacity
    "quote_expired" -> R.string.credit_expired
    "credits_reconciliation_required" -> R.string.credit_refund_hold
    "operation_busy" -> R.string.credit_busy
    "upload_limit" -> R.string.credit_upload_limit
    CreditCaptureModel.PRICE_CHANGED -> R.string.credit_price_changed
    CreditCaptureModel.POLL_FAILED -> R.string.credit_poll_failed
    "photo_unavailable" -> R.string.credit_photo_unavailable
    else -> R.string.credit_unavailable
}
