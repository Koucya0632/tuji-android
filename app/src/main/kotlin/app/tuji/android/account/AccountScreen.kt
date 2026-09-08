package app.tuji.android.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import app.tuji.android.R
import app.tuji.android.core.billing.PurchaseGate
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.LearningDirection

/**
 * 我的 — the account, the plan, and the settings that exist yet.
 *
 * Deliberately short. Everything on it is either a fact the server sent or a
 * control that works; a settings list with three rows that do nothing is how a
 * screen stops being read.
 */
@Composable
fun AccountScreen(
    state: AccountViewModel.State,
    direction: LearningDirection,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenPaywall: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Spacer(Modifier.height(TujiSpace.S3))

        state.me?.let { me ->
            Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                Text(me.displayName, style = TujiType.h2, color = TujiColor.Ink)
                // The UID under the name: it is the id that reports, blocks and
                // support requests actually carry, and a nickname can change.
                me.username?.let {
                    Text(it, style = TujiType.monoLabel, color = TujiColor.Ink3)
                }
                me.email?.let {
                    Text(it, style = TujiType.bodySm, color = TujiColor.Ink3)
                }
            }
        }

        PlanCard(state = state, onOpenPaywall = onOpenPaywall)

        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(
                stringResource(R.string.me_settings),
                style = TujiType.label,
                color = TujiColor.Ink3,
            )
            Row(
                Modifier.fillMaxWidth().padding(vertical = TujiSpace.S2),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.me_language),
                    style = TujiType.body,
                    color = TujiColor.Ink,
                )
                Text(
                    direction.wire,
                    style = TujiType.monoLabel,
                    color = TujiColor.Ink3,
                )
            }
        }

        TujiButton(
            text = stringResource(R.string.me_sign_out),
            style = TujiButtonStyle.Secondary,
            onClick = onSignOut,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
    }
}

@Composable
private fun PlanCard(state: AccountViewModel.State, onOpenPaywall: () -> Unit) {
    val entitlement = state.entitlement
    Column(
        Modifier
            .fillMaxWidth()
            .background(TujiColor.Paper2)
            .padding(TujiSpace.S3),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S2),
    ) {
        Text(
            stringResource(
                if (entitlement?.isPro == true) R.string.me_plan_pro else R.string.me_plan_free,
            ),
            style = TujiType.h3,
            color = TujiColor.Ink,
        )
        entitlement?.subscriptionExpiresAt?.let {
            Text(
                stringResource(R.string.me_expires, it.take(10)),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
            )
        }
        entitlement?.let {
            Text(
                stringResource(
                    R.string.me_usage_slots,
                    it.usage.atlasSlots, it.atlasSlotsLimit,
                ),
                style = TujiType.monoLabel,
                color = TujiColor.Ink3,
            )
            Text(
                stringResource(
                    R.string.me_usage_saved,
                    it.usage.savedItems, it.savedItemsLimit,
                ),
                style = TujiType.monoLabel,
                color = TujiColor.Ink3,
            )
        }

        // Only offered when there is something to sell *and* a way to sell it.
        // The verdict is null until the entitlement lands, and nothing is
        // claimed before then.
        when (state.purchase) {
            PurchaseGate.Verdict.Allowed ->
                if (state.billingAvailable) {
                    TujiButton(
                        text = stringResource(R.string.paywall_cta),
                        onClick = onOpenPaywall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    // Said, not greyed. A disabled button invites a tap that
                    // teaches nothing; a sentence says why and what changes it.
                    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                        Text(
                            stringResource(R.string.paywall_unavailable),
                            style = TujiType.bodySmStrong,
                            color = TujiColor.Ink2,
                        )
                        Text(
                            stringResource(R.string.paywall_unavailable_why),
                            style = TujiType.bodySm,
                            color = TujiColor.Ink3,
                        )
                    }
                }

            PurchaseGate.Verdict.AlreadySubscribed -> Text(
                stringResource(R.string.paywall_already),
                style = TujiType.bodySm,
                color = TujiColor.Accumulation,
            )

            is PurchaseGate.Verdict.ManagedElsewhere -> Text(
                elsewhereMessage((state.purchase as PurchaseGate.Verdict.ManagedElsewhere).source),
                style = TujiType.bodySm,
                color = TujiColor.Ink2,
            )

            null -> Unit
        }
    }
}

/** Where to manage an existing subscription — see ADR-0001. */
@Composable
private fun elsewhereMessage(source: String): String =
    if (source == PurchaseGate.APP_STORE) {
        stringResource(R.string.paywall_elsewhere_appstore)
    } else {
        stringResource(R.string.paywall_elsewhere_other, source)
    }
