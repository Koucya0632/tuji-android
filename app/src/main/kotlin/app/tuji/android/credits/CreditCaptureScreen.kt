package app.tuji.android.credits

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Context
import app.tuji.android.R
import app.tuji.android.capture.CaptureIntake
import app.tuji.android.capture.CaptureSource
import app.tuji.android.capture.LibraryGlyph
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.MascotSpeechBubble
import app.tuji.android.core.design.TujiBorder
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.design.TujiPrompt
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiStatusEdgeLabel
import app.tuji.android.core.design.TujiTextField
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.CreditCandidate
import app.tuji.android.core.model.CreditConfirmRequest
import app.tuji.android.core.model.CreditOperation
import app.tuji.android.core.model.CreditQuote
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.network.AtlasAuthoring
import app.tuji.android.core.network.CreditRepository
import app.tuji.android.core.network.TujiApiClient
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * 點數版「拍照新增」— iOS's `CreditCaptureView`. Steps run top-to-bottom:
 *   1. 選辨識方式（普通／高精度，價格寫在選項上）
 *   2. 拍照或從相簿選 → 裁切；照片留在手機上
 *   3. 開始識別 — 上傳、報價、扣點一次做完
 *   4. 候選結果；普通識別後可補差價升級高精度
 *   5. 確認並生成卡片 → 生成佇列 takes over and the screen closes
 *
 * State and money rules live in [CreditCaptureModel]; this only renders them.
 */
@Composable
fun CreditCaptureScreen(
    api: TujiApiClient,
    atlas: AtlasAuthoring,
    owner: String,
    currentOwner: () -> String?,
    direction: LearningDirection,
    uiLanguage: UiLanguage,
    enqueue: (CreditConfirmRequest, String, String?) -> Unit,
    queuedOperations: () -> Set<String>,
    onOpenWallet: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val model = remember(owner) {
        CreditCaptureModel(
            CreditRepository(api), atlas, owner, currentOwner,
            context.getSharedPreferences("credit-journal", Context.MODE_PRIVATE), scope,
            enqueue = enqueue, queuedOperations = queuedOperations,
        )
    }
    val state by model.state.collectAsStateWithLifecycle()
    var intake by remember { mutableStateOf<CaptureSource?>(null) }
    var showPrecisionInfo by remember { mutableStateOf(false) }
    val language = direction.targetLanguage.name.lowercase()
    val glossLanguage = uiLanguage.wire.takeIf { it in listOf("en", "ja") && it != language }
    // The run keeps the language it was paid for.
    val target = TargetLanguage.entries.firstOrNull { it.name.lowercase() == state.operation?.targetLanguage } ?: direction.targetLanguage
    val secondField = CreditCaptureModel.secondField(uiLanguage, target)

    LaunchedEffect(model) { model.load() }
    val operation = state.operation
    LaunchedEffect(model, operation?.id, operation?.state) {
        // The server starts the run on accept and a 普通 run finishes in ~2s, so look early.
        var wait = 1_000L
        while (model.state.value.operation?.isRunning == true) {
            delay(wait)
            model.poll()
            wait = 2_000L
        }
    }

    Column(Modifier.fillMaxSize()) {
        StepIndicator(total = 5, current = state.step)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TujiSpace.S4, vertical = TujiSpace.S3),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
        ) {
            WalletRow(state.wallet?.available, onOpenWallet)
            StatusMessage(state)
            when {
                state.recognitionInFlight ->
                    if (operation != null) RecognizingPanel(state, operation, model) else ReadyPanel(state, model, language, glossLanguage, onOpenWallet)
                state.pending != null -> PendingPanel(state, model)
                state.quote != null -> QuotePanel(state, state.quote!!, model)
                operation != null -> OperationPanel(state, operation, model, secondField, language, glossLanguage, onOpenWallet, onDone)
                state.photo != null -> ReadyPanel(state, model, language, glossLanguage, onOpenWallet)
                else -> SourcePanel(
                    state = state,
                    model = model,
                    disabled = state.busy || !state.operationsEnabled,
                    onInfo = { showPrecisionInfo = true },
                    onPick = { intake = it },
                )
            }
        }
    }

    intake?.let { source ->
        CaptureIntake(
            source = source,
            onPhoto = model::setPhoto,
            onFailed = { model.fail("photo_unavailable") },
            onClose = { intake = null },
        )
    }
    if (showPrecisionInfo) {
        TujiPrompt(
            title = stringResource(R.string.credit_precision_title),
            message = stringResource(R.string.credit_precision_info),
            detail = stringResource(R.string.credit_precision_info_detail),
            confirm = stringResource(R.string.credit_got_it),
            cancel = null,
            onConfirm = { showPrecisionInfo = false },
            onCancel = { showPrecisionInfo = false },
        )
    }
}

// MARK: - Chrome

@Composable
private fun StepIndicator(total: Int, current: Int) {
    Row(
        Modifier.fillMaxWidth().height(TujiBorder.Bw3),
        horizontalArrangement = Arrangement.spacedBy(TujiBorder.Bw1),
    ) {
        repeat(total) { index ->
            val fill by animateColorAsState(
                when {
                    index < current -> TujiColor.Ink
                    index == current -> TujiColor.Current
                    else -> TujiColor.Paper2
                },
                TujiMotion.ease(TujiMotion.D2),
                label = "step",
            )
            Box(Modifier.weight(1f).fillMaxSize().background(fill))
        }
    }
}

@Composable
private fun WalletRow(available: Int?, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(TujiColor.Paper2)
            .tujiClickable(onClick = onOpen)
            .padding(horizontal = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.credit_can), null, Modifier.size(28.dp), tint = TujiColor.Ink)
        Text(stringResource(R.string.credit_title), style = TujiType.label, color = TujiColor.Ink3)
        Spacer(Modifier.weight(1f))
        Text(available?.toString() ?: "—", style = TujiType.h3, color = TujiColor.Ink)
        TujiGlyph.ArrowRight(size = 12.dp, tint = TujiColor.Ink3)
    }
}

@Composable
private fun StatusMessage(state: CreditCaptureModel.State) {
    val code = state.error
    if (code != null) {
        Text(
            stringResource(creditErrorResource(code)),
            style = TujiType.label,
            color = TujiColor.Alert,
            modifier = Modifier.fillMaxWidth().background(TujiColor.Alert.copy(alpha = 0.12f)).padding(TujiSpace.S3),
        )
    } else if (state.catalog != null && !state.operationsEnabled) {
        Text(stringResource(R.string.credit_ai_paused), style = TujiType.label, color = TujiColor.Ink3)
    }
}

/** 罐頭 ×N. [plus] marks a top-up rather than a full price. */
@Composable
private fun Price(points: Int?, plus: Boolean = false, tint: Color = TujiColor.Ink) {
    val label = stringResource(R.string.credit_cost, points ?: 0)
    Row(
        Modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (plus) Text("+", style = TujiType.label, color = tint)
        Icon(painterResource(R.drawable.credit_can), null, Modifier.size(20.dp), tint = tint)
        Text("×${points?.toString() ?: "—"}", style = TujiType.label, color = tint)
    }
}

/** iOS's `TujiNavTextAction`: a text-only action in a panel's header. */
@Composable
private fun TextAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        style = TujiType.bodyStrong,
        color = if (enabled) TujiColor.Ink else TujiColor.Ink3,
        modifier = Modifier.tujiClickable(enabled = enabled, onClick = onClick).padding(vertical = TujiSpace.S1),
    )
}

@Composable
private fun PanelHeader(title: String, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TujiType.label, color = TujiColor.Ink3, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

// MARK: - 1–2. Mode + source

@Composable
private fun SourcePanel(
    state: CreditCaptureModel.State,
    model: CreditCaptureModel,
    disabled: Boolean,
    onInfo: () -> Unit,
    onPick: (CaptureSource) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.credit_choose_mode), style = TujiType.h3, color = TujiColor.Ink, modifier = Modifier.weight(1f))
            val info = stringResource(R.string.credit_precision_info_label)
            Box(
                Modifier
                    .size(32.dp)
                    .semantics { contentDescription = info }
                    .tujiClickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(18.dp).border(1.5.dp, TujiColor.Ink3, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Text("?", style = TujiType.label, color = TujiColor.Ink3) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            ModeRadio(state.mode == CreditCaptureModel.Mode.Primary, stringResource(R.string.capture_mode_primary), state.recognitionPrice) {
                model.setMode(CreditCaptureModel.Mode.Primary)
            }
            ModeRadio(state.mode == CreditCaptureModel.Mode.Precision, stringResource(R.string.credit_precision_title), state.precisionPrice) {
                model.setMode(CreditCaptureModel.Mode.Precision)
            }
        }
        Text(stringResource(R.string.credit_upload_after_start), style = TujiType.label, color = TujiColor.Ink3)
        TujiButton(
            text = stringResource(R.string.credit_take_photo),
            onClick = { onPick(CaptureSource.Camera) },
            enabled = !disabled,
            leading = { TujiGlyph.Camera(size = 16.dp, tint = if (disabled) TujiColor.Ink3 else TujiColor.Ink) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(TujiColor.Paper2)
                .tujiClickable(enabled = !disabled) { onPick(CaptureSource.Library) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ink = if (disabled) TujiColor.Ink3 else TujiColor.Ink
            LibraryGlyph(18.dp, ink)
            Spacer(Modifier.width(TujiSpace.S2))
            Text(stringResource(R.string.capture_from_library), style = TujiType.h3, color = ink)
        }
    }
}

@Composable
private fun ModeRadio(selected: Boolean, title: String, points: Int?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(TujiColor.Paper2)
            .border(1.5.dp, if (selected) TujiColor.Ink else Color.Transparent)
            .semantics { this.selected = selected }
            .tujiClickable(onClick = onClick)
            .padding(horizontal = TujiSpace.S3),
        horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp).border(1.5.dp, if (selected) TujiColor.Ink else TujiColor.Ink3, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).background(TujiColor.Ink, CircleShape))
        }
        Text(title, style = TujiType.body, color = TujiColor.Ink, modifier = Modifier.weight(1f))
        Price(points)
    }
}

// MARK: - 3. Ready to start

@Composable
private fun ReadyPanel(
    state: CreditCaptureModel.State,
    model: CreditCaptureModel,
    language: String,
    gloss: String?,
    onOpenWallet: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        PanelHeader(
            stringResource(if (state.mode == CreditCaptureModel.Mode.Primary) R.string.capture_mode_primary else R.string.credit_precision_title),
        ) { TextAction(stringResource(R.string.credit_another_photo), enabled = !state.busy, onClick = model::reset) }
        PhotoFrame(state)
        when {
            state.busy -> {
                val label = stringResource(R.string.capture_uploading)
                TujiIndeterminateBar(label = label)
                Text(label, style = TujiType.label, color = TujiColor.Ink3)
            }
            state.affordable(state.price) -> Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(if (state.operationsEnabled) TujiColor.BrandPrimary else TujiColor.Paper3)
                    .tujiClickable(enabled = state.operationsEnabled) { model.start(language, gloss) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TujiGlyph.Sparkles(size = 18.dp, tint = TujiColor.Ink)
                Spacer(Modifier.width(TujiSpace.S2))
                Text(stringResource(R.string.credit_start), style = TujiType.h3, color = TujiColor.Ink)
                Spacer(Modifier.width(TujiSpace.S3))
                Price(state.price)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
                Text(stringResource(R.string.credit_not_enough), style = TujiType.label, color = TujiColor.Alert)
                TujiButton(text = stringResource(R.string.credit_top_up), onClick = onOpenWallet, style = TujiButtonStyle.Secondary)
            }
        }
    }
}

/** The photo on screen: the local crop while this session has it, else the upload. */
@Composable
private fun PhotoFrame(state: CreditCaptureModel.State) {
    val photo = state.photo
    val bitmap = remember(photo) { photo?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() } }
    Box(Modifier.fillMaxWidth().height(240.dp).background(TujiColor.Paper2), contentAlignment = Alignment.Center) {
        val url = state.image?.imageUrl ?: state.image?.thumbUrl
        when {
            bitmap != null -> Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            url != null -> AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else -> LibraryGlyph(28.dp, TujiColor.Ink3)
        }
    }
}

// MARK: - 4. Recognition + results

@Composable
private fun OperationPanel(
    state: CreditCaptureModel.State,
    operation: CreditOperation,
    model: CreditCaptureModel,
    secondField: CreditCaptureModel.SecondField,
    language: String,
    gloss: String?,
    onOpenWallet: () -> Unit,
    onDone: () -> Unit,
) {
    when {
        operation.isRunning -> RecognizingPanel(state, operation, model)
        operation.state == "released" -> Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            PhotoFrame(state)
            Text(stringResource(R.string.credit_not_finished), style = TujiType.body, color = TujiColor.Ink)
            TujiButton(text = stringResource(R.string.credit_another_photo), onClick = model::reset)
        }
        operation.confirmedItemId != null -> Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
            Text(stringResource(R.string.credit_saved_sync_failed), style = TujiType.body, color = TujiColor.Ink)
            TujiButton(text = stringResource(R.string.credit_sync_cards), onClick = { model.syncCards(onDone) }, enabled = !state.busy)
        }
        else -> ResultPanel(state, operation, model, secondField, language, gloss, onOpenWallet, onDone)
    }
}

/** AI 辨識中 — no spinner, the cat after 3s. */
@Composable
private fun RecognizingPanel(state: CreditCaptureModel.State, operation: CreditOperation, model: CreditCaptureModel) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(operation.id) {
        slow = false
        delay(3_000)
        slow = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
        PhotoFrame(state)
        val label = stringResource(R.string.capture_uploading)
        TujiIndeterminateBar(label = label)
        Text(label, style = TujiType.label, color = TujiColor.Ink3)
        AnimatedVisibility(visible = slow, enter = fadeIn(TujiMotion.ease(TujiMotion.D2)), exit = fadeOut(TujiMotion.ease(TujiMotion.D2))) {
            MascotSpeechBubble(pose = MascotPose.Think, text = stringResource(R.string.capture_recognizing_slow))
        }
        if (operation.state == "reserved") {
            TextAction(stringResource(R.string.cancel), enabled = !state.busy, onClick = model::cancelOperation)
        }
    }
}

@Composable
private fun ResultPanel(
    state: CreditCaptureModel.State,
    operation: CreditOperation,
    model: CreditCaptureModel,
    secondField: CreditCaptureModel.SecondField,
    language: String,
    gloss: String?,
    onOpenWallet: () -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        PanelHeader(stringResource(R.string.credit_candidates)) {
            TextAction(stringResource(R.string.credit_another_photo), enabled = !state.busy, onClick = model::reset)
        }
        PhotoFrame(state)
        ModeRow(state, model, language, gloss, onOpenWallet)
        val all = operation.result?.candidates.orEmpty()
        CandidateGroup(all.filter { it.level != "fine" }, state, model)
        CandidateGroup(all.filter { it.level == "fine" }, state, model)
        if (state.selectedCandidateId != null) CorrectionForm(state, model, secondField)
        TujiButton(
            text = stringResource(R.string.credit_confirm_card),
            onClick = { if (model.confirm(secondField)) onDone() },
            enabled = state.canConfirm,
            leading = { TujiGlyph.Check(size = 14.dp, tint = if (state.canConfirm) TujiColor.Ink else TujiColor.Ink3) },
        )
    }
}

/** 普通／高精度. A run already paid for is a switch; 高精度 not yet run is the top-up. */
@Composable
private fun ModeRow(
    state: CreditCaptureModel.State,
    model: CreditCaptureModel,
    language: String,
    gloss: String?,
    onOpenWallet: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        ModeTab(
            stringResource(R.string.capture_mode_primary),
            selected = state.shownMode == CreditCaptureModel.Mode.Primary,
            enabled = !state.busy && state.run(CreditCaptureModel.Mode.Primary) != null,
            modifier = Modifier.weight(1f),
        ) { model.show(CreditCaptureModel.Mode.Primary) }
        if (state.run(CreditCaptureModel.Mode.Precision) != null || !state.canUpgrade) {
            ModeTab(
                stringResource(R.string.credit_precision_title),
                selected = state.shownMode == CreditCaptureModel.Mode.Precision,
                enabled = !state.busy && state.run(CreditCaptureModel.Mode.Precision) != null,
                modifier = Modifier.weight(1f),
            ) { model.show(CreditCaptureModel.Mode.Precision) }
        } else {
            val enabled = !state.busy && state.operationsEnabled
            Row(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(1.dp, TujiColor.Ink)
                    .tujiClickable(enabled = enabled) {
                        if (state.affordable(state.upgradePrice)) model.upgrade(language, gloss) else onOpenWallet()
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ink = if (enabled) TujiColor.Ink else TujiColor.Ink3
                Text(stringResource(R.string.credit_upgrade), style = TujiType.label, color = ink)
                Spacer(Modifier.width(5.dp))
                Price(state.upgradePrice, plus = true, tint = ink)
            }
        }
    }
}

@Composable
private fun ModeTab(title: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .background(if (selected) TujiColor.Ink else TujiColor.Paper2)
            .tujiClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = TujiType.label, color = if (selected) TujiColor.Paper else TujiColor.Ink)
    }
}

@Composable
private fun CandidateGroup(rows: List<CreditCandidate>, state: CreditCaptureModel.State, model: CreditCaptureModel) {
    if (rows.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // iOS's adaptive grid: as many 120-wide columns as fit.
        val gap = TujiSpace.S2
        val columns = ((maxWidth + gap) / (120.dp + gap)).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.chunked(columns).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    line.forEach { candidate ->
                        val selected = state.selectedCandidateId == candidate.id
                        Column(
                            Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp)
                                .background(if (selected) TujiColor.Ink else TujiColor.Paper2)
                                .semantics { this.selected = selected }
                                .tujiClickable(enabled = !state.busy) { model.select(candidate) }
                                .padding(horizontal = TujiSpace.S3, vertical = TujiSpace.S2),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                        ) {
                            val ink = if (selected) TujiColor.Paper else TujiColor.Ink
                            Text(candidate.label, style = TujiType.bodySm, color = ink)
                            Text(candidate.gloss ?: candidate.zhHant, style = TujiType.label, color = ink.copy(alpha = 0.7f))
                        }
                    }
                    repeat(columns - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 人工校正 — the free flow's two names, prefilled from the chosen candidate. */
@Composable
private fun CorrectionForm(state: CreditCaptureModel.State, model: CreditCaptureModel, secondField: CreditCaptureModel.SecondField) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S4)) {
        Text(stringResource(R.string.credit_correction), style = TujiType.label, color = TujiColor.Ink3)
        Field(stringResource(R.string.credit_image_name), state.lemma, state.isStillSuggested(CreditCaptureModel.SuggestedField.Lemma)) {
            model.edit(lemma = it)
        }
        when (secondField) {
            CreditCaptureModel.SecondField.ChineseName ->
                Field(stringResource(R.string.credit_chinese_name), state.displayZhHant, state.isStillSuggested(CreditCaptureModel.SuggestedField.ZhHant)) {
                    model.edit(zhHant = it)
                }
            CreditCaptureModel.SecondField.Gloss ->
                Field(stringResource(R.string.credit_chinese_name), state.displayGloss, state.isStillSuggested(CreditCaptureModel.SuggestedField.Gloss)) {
                    model.edit(gloss = it)
                }
            CreditCaptureModel.SecondField.Hidden -> Unit
        }
    }
}

@Composable
private fun Field(label: String, value: String, suggested: Boolean, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = TujiType.label, color = TujiColor.Ink3)
            if (suggested) TujiStatusEdgeLabel(text = stringResource(R.string.credit_ai_suggested), edge = TujiColor.Current)
        }
        TujiTextField(value = value, onValueChange = onChange, placeholder = "", modifier = Modifier.fillMaxWidth())
    }
}

// MARK: - Exceptions

/** The server priced differently from the screen (price change mid-session): ask, don't charge. */
@Composable
private fun QuotePanel(state: CreditCaptureModel.State, quote: CreditQuote, model: CreditCaptureModel) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Text(stringResource(R.string.credit_cost, quote.points), style = TujiType.h3, color = TujiColor.Ink)
        TujiButton(
            text = stringResource(R.string.credit_confirm),
            onClick = model::accept,
            enabled = !state.busy && state.affordable(quote.points),
        )
        TextAction(stringResource(R.string.cancel), enabled = !state.busy, onClick = model::cancelQuote)
    }
}

@Composable
private fun PendingPanel(state: CreditCaptureModel.State, model: CreditCaptureModel) {
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S3)) {
        Text(stringResource(R.string.credit_pending_sync), style = TujiType.body, color = TujiColor.Ink)
        TujiButton(text = stringResource(R.string.credit_retry), onClick = model::accept, enabled = !state.busy)
    }
}
