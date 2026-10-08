package app.tuji.android.credits

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.*
import app.tuji.android.core.network.*

/** 罐頭點數 — the wallet: balance, where it comes from, and 每日簽到. */
@Composable
fun CreditsScreen(api: TujiApiClient, atlas: AtlasAuthoring, owner: String, currentOwner: () -> String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(owner) {
        CreditCaptureModel(CreditRepository(api), atlas, owner, currentOwner,
            context.getSharedPreferences("credit-journal", Context.MODE_PRIVATE), scope)
    }
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.load() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.credit_can), null, Modifier.size(48.dp), tint = TujiColor.Ink)
            Text(stringResource(R.string.credit_title), style = TujiType.h3)
        }
        Text(stringResource(R.string.membership_benefit_slots_200_credits))
        Text(stringResource(R.string.credit_pro_closed))
        state.wallet?.let { wallet ->
            Text(wallet.available.toString(), style = TujiType.h3)
            Text(stringResource(R.string.credit_balances, wallet.reserved, wallet.giftAvailable, wallet.paidAvailable))
            if (wallet.reconciliationRequired) Text(stringResource(R.string.credit_refund_hold))
            Text(stringResource(R.string.credit_monthly_auto))
            Text(stringResource(R.string.credit_free_balance, wallet.monthlyAvailable, wallet.checkInAvailable))
            Text(stringResource(R.string.credit_monthly_reset_note))
            TujiButton(stringResource(if (wallet.benefits.checkedInToday) R.string.credit_checked_in else R.string.credit_check_in),
                { model.claim(false) }, enabled = !state.busy && state.catalog?.checkInEnabled == true && wallet.benefits.hasLifetime && !wallet.benefits.checkedInToday && wallet.benefits.checkInGrantedThisMonth < 300)
            Text(stringResource(R.string.credit_check_in_note))
        }
        TujiButton(stringResource(R.string.credit_retry), model::load, enabled = !state.busy)
        state.error?.let { Text(stringResource(creditErrorResource(it)), color = MaterialTheme.colorScheme.error) }
    }
}

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
