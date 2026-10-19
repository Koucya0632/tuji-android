package app.tuji.android.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.MascotEmptyState
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiErrorState
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiPullToRefresh
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.model.CreditStatement
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.CreditStatementReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** 點數明細's model — iOS's `CreditStatementModel`. */
class CreditStatementModel(private val repository: CreditStatementReading) {
    enum class Refusal {
        /** Not a 罐頭點數 account: there is no wallet to show. */
        NotEnrolled,
        Unreachable,
    }

    data class State(val statement: CreditStatement? = null, val refusal: Refusal? = null)

    private val mutable = MutableStateFlow(State())
    val state: StateFlow<State> = mutable.asStateFlow()

    suspend fun load() {
        try {
            val statement = CreditStatement.of(repository.loadStatement())
            mutable.value = State(statement = statement)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // What was on screen stays on screen; only a first load that fails
            // has nothing better to show than the reason.
            if (mutable.value.statement != null) return
            val status = (e as? ApiError.Http)?.status ?: if (e === ApiError.NotAuthenticated) 401 else null
            mutable.value = State(refusal = if (status in setOf(401, 403, 404)) Refusal.NotEnrolled else Refusal.Unreachable)
        }
    }
}

/**
 * 點數明細 — the balance, where it came from, and the recent movements. iOS's
 * `CreditStatementView`, reached from 助手 and from AI 查詞's bar: asking AI
 * spends a point or two at a time, and this is where those add up to something
 * a person can check.
 */
@Composable
fun CreditStatementScreen(
    repository: CreditStatementReading,
    locale: Locale,
    onBack: () -> Unit,
    onOpenMembership: () -> Unit,
) {
    val model = remember { CreditStatementModel(repository) }
    val state by model.state.collectAsStateWithLifecycle()
    // Every visit reads again: the point of coming here is to see what the last question cost.
    LaunchedEffect(model) { model.load() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(TujiColor.Paper)) {
        TujiNavBar(
            onLeading = onBack,
            leadingLabel = stringResource(R.string.atlas_back),
            title = stringResource(R.string.credit_statement_title),
            showsRule = true,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val statement = state.statement
            val refusal = state.refusal
            when {
                statement != null -> TujiPullToRefresh(onRefresh = { model.load() }) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = TujiSpace.S4),
                        verticalArrangement = Arrangement.spacedBy(TujiSpace.S5),
                    ) {
                        Balance(statement, locale)
                        Sources(statement, locale)
                        Records(statement, locale)
                    }
                }
                refusal != null -> Column(
                    Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (refusal) {
                        CreditStatementModel.Refusal.NotEnrolled -> MascotEmptyState(
                            title = stringResource(R.string.credit_statement_not_enrolled),
                            message = stringResource(R.string.credit_statement_not_enrolled_detail),
                            pose = MascotPose.Think,
                        )
                        CreditStatementModel.Refusal.Unreachable -> TujiErrorState(
                            title = stringResource(R.string.credit_statement_unreachable),
                            message = stringResource(R.string.credit_statement_unreachable_detail),
                        ) {
                            TujiButton(
                                text = stringResource(R.string.credit_statement_retry),
                                onClick = { scope.launch { model.load() } },
                                modifier = Modifier.width(160.dp),
                            )
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TujiIndeterminateBar(modifier = Modifier.width(40.dp))
                }
            }
        }
        // Pinned under the list rather than at the end of it: the reason to come
        // here is often that the balance is low.
        Column {
            Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
            TujiButton(
                text = stringResource(
                    if (state.refusal == CreditStatementModel.Refusal.NotEnrolled) R.string.credit_statement_see_plans
                    else R.string.credit_statement_buy,
                ),
                onClick = onOpenMembership,
                modifier = Modifier.padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
            )
        }
    }
}

/** The ink block, edge to edge — on this screen the number is the thing. */
@Composable
private fun Balance(statement: CreditStatement, locale: Locale) {
    Row(
        Modifier.fillMaxWidth().background(TujiColor.Ink).padding(TujiSpace.S4),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
            Text(
                stringResource(R.string.credit_statement_available),
                style = TujiType.label,
                color = TujiColor.Paper.copy(alpha = 0.6f),
            )
            Text(
                NumberFormat.getIntegerInstance(locale).format(statement.available),
                style = TujiType.display,
                color = TujiColor.Paper,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(
                if (statement.reserved > 0) {
                    stringResource(R.string.credit_statement_reserved, statement.reserved)
                } else {
                    stringResource(R.string.credit_statement_never_expire)
                },
                style = TujiType.label,
                color = TujiColor.Paper.copy(alpha = 0.6f),
            )
        }
        Icon(painterResource(R.drawable.credit_can), null, Modifier.size(72.dp), tint = TujiColor.Current)
    }
}

@Composable
private fun Sources(statement: CreditStatement, locale: Locale) {
    Column(Modifier.padding(horizontal = TujiSpace.S4)) {
        Heading(stringResource(R.string.credit_statement_sources))
        SourceRow(
            stringResource(R.string.credit_statement_monthly),
            statement.monthlyExpires?.let(::parseMoment)?.let {
                stringResource(R.string.credit_statement_monthly_until, day(it, locale))
            } ?: stringResource(R.string.credit_statement_monthly_detail),
            statement.sources.monthly,
            locale,
        )
        Rule()
        SourceRow(
            stringResource(R.string.credit_statement_check_in),
            stringResource(R.string.credit_statement_check_in_detail),
            statement.sources.checkIn,
            locale,
        )
        Rule()
        SourceRow(
            stringResource(R.string.credit_statement_purchased),
            stringResource(R.string.credit_statement_purchased_detail),
            statement.sources.purchased,
            locale,
        )
        if (statement.sources.otherGift > 0) {
            Rule()
            SourceRow(
                stringResource(R.string.credit_statement_other),
                stringResource(R.string.credit_statement_other_detail),
                statement.sources.otherGift,
                locale,
            )
        }
    }
}

@Composable
private fun SourceRow(title: String, detail: String, points: Int, locale: Locale) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = TujiSpace.S3).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = TujiType.bodyStrong, color = TujiColor.Ink)
            Text(detail, style = TujiType.bodySm, color = TujiColor.Ink3)
        }
        Text(NumberFormat.getIntegerInstance(locale).format(points), style = TujiType.h3, color = TujiColor.Ink)
    }
}

@Composable
private fun Records(statement: CreditStatement, locale: Locale) {
    Column(Modifier.padding(horizontal = TujiSpace.S4)) {
        Heading(stringResource(R.string.credit_statement_records))
        if (statement.records.isEmpty()) {
            Text(
                stringResource(R.string.credit_statement_records_empty),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
                modifier = Modifier.padding(vertical = TujiSpace.S3),
            )
        }
        statement.records.forEachIndexed { index, record ->
            if (index > 0) Rule()
            RecordRow(record, locale)
        }
    }
}

@Composable
private fun RecordRow(record: CreditStatement.Record, locale: Locale) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = TujiSpace.S3).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(activityTitle(record.activity)), style = TujiType.bodyStrong, color = TujiColor.Ink)
            parseMoment(record.createdAt)?.let {
                Text(moment(it, locale), style = TujiType.bodySm, color = TujiColor.Ink3)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Spending is ink and a gain is teal — teal is accumulation
            // everywhere else in the app. Neither is red: using points is what
            // they are for, not a warning.
            Text(
                if (record.amount > 0) "+${record.amount}" else record.amount.toString(),
                style = TujiType.h3,
                color = if (record.amount > 0) TujiColor.Accumulation else TujiColor.Ink,
            )
            Text(
                stringResource(R.string.credit_statement_balance_after, record.balanceAfter),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
            )
        }
    }
}

internal fun activityTitle(activity: CreditStatement.Activity): Int = when (activity) {
    CreditStatement.Activity.AskedAssistant -> R.string.credit_activity_assistant
    CreditStatement.Activity.Recognition -> R.string.credit_activity_recognition
    CreditStatement.Activity.MonthlyGift -> R.string.credit_statement_monthly
    CreditStatement.Activity.CheckIn -> R.string.credit_statement_check_in
    CreditStatement.Activity.Purchase -> R.string.credit_statement_purchased
    CreditStatement.Activity.Gift -> R.string.credit_activity_gift
    CreditStatement.Activity.Expired -> R.string.credit_activity_expired
    CreditStatement.Activity.Refund -> R.string.credit_activity_refund
    CreditStatement.Activity.Returned -> R.string.credit_activity_returned
    CreditStatement.Activity.Other -> R.string.credit_activity_other
}

@Composable
private fun ColumnScope.Heading(text: String) {
    Text(text, style = TujiType.label, color = TujiColor.Ink3, modifier = Modifier.padding(bottom = TujiSpace.S1))
}

@Composable
private fun Rule() {
    Box(Modifier.fillMaxWidth().height(TujiBorder.Bw1).background(TujiColor.Rule))
}

private fun parseMoment(iso: String): Instant? =
    runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(iso) }.getOrNull()

private fun day(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(ZoneId.systemDefault()))

private fun moment(instant: Instant, locale: Locale): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).format(instant.atZone(ZoneId.systemDefault()))
