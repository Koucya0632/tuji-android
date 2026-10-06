package app.tuji.android.credits

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.capture.CameraController
import app.tuji.android.capture.CameraFrame
import app.tuji.android.core.design.*
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.network.*
import app.tuji.android.profile.PhotoCodec
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CreditsScreen(
    api: TujiApiClient, atlas: AtlasAuthoring, owner: String,
    currentOwner: () -> String?, direction: LearningDirection, capture: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(owner) {
        CreditCaptureModel(CreditRepository(api), atlas, owner, currentOwner,
            context.getSharedPreferences("credit-journal", Context.MODE_PRIVATE), scope)
    }
    val state by model.state.collectAsStateWithLifecycle()
    var precision by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
    var cameraAllowed by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val camera = remember { CameraController() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraAllowed = it; showCamera = it }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { model.upload {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 8 * 1024 * 1024)
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: error("photo_unavailable")
            }
            PhotoCodec.captureJpeg(bytes)
        } }
    }
    LaunchedEffect(model) { model.load() }
    val operation = state.operation
    LaunchedEffect(model, operation?.id, operation?.state, operation?.fulfillmentState) {
        while (operation?.needsPolling == true) { delay(3_000); model.poll() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.credit_can), null, Modifier.size(40.dp))
            Text(stringResource(R.string.credit_title), style = TujiType.h3)
        }
        if (!capture) {
            Text(stringResource(R.string.credit_slots))
            Text(stringResource(R.string.credit_ai_points))
            Text(stringResource(R.string.credit_pro_closed))
        }
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
        if (capture) {
            val canStart = !state.busy && state.pending == null && state.catalog?.operationsEnabled == true && state.wallet?.reconciliationRequired == false
            Text(stringResource(R.string.credit_upload_note))
            state.image?.imageUrl?.let { url ->
                AsyncImage(model = url, contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(220.dp))
            }
            TujiButton(stringResource(R.string.credit_choose_photo), { picker.launch("image/*") }, enabled = canStart)
            TujiButton(stringResource(R.string.credit_take_photo), {
                if (cameraAllowed) showCamera = !showCamera else permission.launch(Manifest.permission.CAMERA)
            }, enabled = canStart)
            if (showCamera && cameraAllowed) {
                CameraFrame(camera, Modifier.fillMaxWidth().height(240.dp))
                TujiButton(stringResource(R.string.capture_shutter), {
                    model.upload { PhotoCodec.captureJpeg(camera.takePhoto(context)) }
                }, enabled = canStart)
            }
            Row {
                Checkbox(precision, { precision = it }, enabled = canStart)
                Text(stringResource(R.string.credit_precision))
            }
            if (state.image != null || operation != null) {
                TujiButton(stringResource(if (precision) R.string.credit_precision_quote else R.string.credit_standard_quote), {
                    model.quote(precision, direction.targetLanguage.name.lowercase(), null)
                }, enabled = canStart)
            }
            if (state.pending != null) {
                Text(stringResource(R.string.credit_pending_sync))
                TujiButton(stringResource(R.string.credit_retry), model::accept, enabled = !state.busy)
            }
            operation?.let { value ->
                Text(stringResource(when (value.state) {
                    "reserved" -> R.string.credit_waiting
                    "running", "reconciling" -> R.string.credit_processing
                    "released", "cancelled", "failed" -> R.string.credit_released
                    else -> if (value.confirmedItemId == null) R.string.credit_results else R.string.credit_saved
                }))
                if (value.state == "reserved") TujiButton(stringResource(R.string.credit_cancel_task), model::cancel, enabled = !state.busy)
                if (value.state == "committed" && value.confirmedItemId == null) {
                    value.result?.candidates?.forEach { candidate ->
                        TujiButton(candidate.label + " · " + candidate.zhHant, { model.confirm(candidate.id) }, enabled = !state.busy)
                    }
                }
                if (value.confirmedItemId != null) {
                    Text(stringResource(when (value.fulfillmentState) {
                        "completed" -> R.string.credit_details_ready
                        "compensated" -> R.string.credit_compensated
                        "superseded" -> R.string.credit_superseded
                        else -> R.string.credit_details_waiting
                    }))
                    TujiButton(stringResource(R.string.credit_sync_cards), model::cards, enabled = !state.busy)
                }
            }
            Text(stringResource(R.string.credit_recent))
            state.history.forEach { value ->
                TextButton({ model.select(value) }, enabled = !state.busy) {
                    Text(value.result?.candidates?.firstOrNull()?.label ?: stringResource(R.string.credit_task))
                }
            }
        }
        state.quote?.let { quote ->
            AlertDialog(onDismissRequest = model::dismissQuote,
                title = { Text(stringResource(R.string.credit_cost, quote.points)) },
                text = { Text(stringResource(R.string.credit_included)) },
                confirmButton = { TextButton(model::accept, enabled = !state.busy) { Text(stringResource(R.string.credit_confirm)) } },
                dismissButton = { TextButton(model::dismissQuote, enabled = !state.busy) { Text(stringResource(R.string.credit_dismiss)) } })
        }
    }
}

private fun creditErrorResource(code: String): Int = when (code) {
    "insufficient_credits" -> R.string.credit_insufficient
    "capacity_full" -> R.string.credit_capacity
    "quote_expired" -> R.string.credit_expired
    "credits_reconciliation_required" -> R.string.credit_refund_hold
    "operation_busy" -> R.string.credit_busy
    "upload_limit" -> R.string.credit_upload_limit
    else -> R.string.credit_unavailable
}
