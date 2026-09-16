package app.tuji.android.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import app.tuji.android.core.design.MascotPose
import app.tuji.android.core.design.MascotSpeechBubble
import app.tuji.android.core.design.TujiMotion
import app.tuji.android.core.model.AtlasImageSummary
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiButton
import app.tuji.android.core.design.TujiButtonStyle
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiIndeterminateBar
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiStatusBlocker
import app.tuji.android.core.design.TujiTextField
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.rememberTujiHaptics
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.RecognitionMode
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch

/**
 * 自製圖鑑 — photo to card.
 *
 * The camera is held here and the flow is held in [CaptureViewModel]: the view
 * model takes **bytes**, never a camera, which is the whole reason the four
 * network steps can be walked in a test.
 */
@Composable
fun CaptureScreen(
    vm: CaptureViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onDone: () -> Unit,
) {
    val step by vm.step.collectAsStateWithLifecycle()

    when (val s = step) {
        // The upload keeps the camera on screen with the status over it, the
        // way iOS keeps the source panel under its toast. It also removes a
        // black flash the old full-screen label had, because the preview is
        // never torn down and rebuilt between the shutter and the result.
        is CaptureViewModel.Step.Framing, is CaptureViewModel.Step.Uploading -> {
            Framing(vm)
            TujiStatusBlocker(
                visible = s is CaptureViewModel.Step.Uploading,
                title = stringResource(R.string.capture_recognizing),
                detail = stringResource(R.string.capture_recognizing_detail),
            )
        }

        is CaptureViewModel.Step.Failed -> Column(
            Modifier.fillMaxSize().padding(TujiSpace.S4),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.capture_failed),
                style = TujiType.body,
                color = TujiColor.Ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TujiSpace.S3))
            TujiButton(text = stringResource(R.string.capture_retake), onClick = vm::reset)
        }

        is CaptureViewModel.Step.Naming -> Naming(s, vm, bottomPadding)

        is CaptureViewModel.Step.Made -> Column(
            Modifier.fillMaxSize().padding(TujiSpace.S4),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.capture_made), style = TujiType.h1, color = TujiColor.Ink)
            Spacer(Modifier.height(TujiSpace.S2))
            Text(
                stringResource(R.string.capture_made_cards, s.item.lemma, s.cards),
                style = TujiType.body,
                color = TujiColor.Ink2,
            )
            Spacer(Modifier.height(TujiSpace.S4))

            // Publishing is offered, never automatic. And the three outcomes
            // are three different sentences — "live" and "queued for review"
            // are not the same fact, and saying the first when the second is
            // true is a lie the feed contradicts a minute later.
            when (s.publish) {
                null -> TujiButton(
                    text = stringResource(
                        if (s.publishing) R.string.capture_publishing else R.string.capture_publish,
                    ),
                    onClick = vm::publish,
                    enabled = !s.publishing,
                )

                CaptureViewModel.PublishOutcome.Published -> Text(
                    stringResource(R.string.capture_published),
                    style = TujiType.bodySmStrong,
                    color = TujiColor.Accumulation,
                )

                CaptureViewModel.PublishOutcome.Queued -> Text(
                    stringResource(R.string.capture_queued),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink2,
                    textAlign = TextAlign.Center,
                )

                CaptureViewModel.PublishOutcome.Failed -> Text(
                    stringResource(R.string.capture_publish_failed),
                    style = TujiType.bodySm,
                    color = TujiColor.Ink2,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(TujiSpace.S4))
            TujiButton(text = stringResource(R.string.capture_made_again), onClick = vm::reset)
            Spacer(Modifier.height(TujiSpace.S2))
            TujiButton(
                text = stringResource(R.string.study_close),
                style = TujiButtonStyle.Secondary,
                onClick = onDone,
            )
        }
    }
}

@Composable
private fun Framing(vm: CaptureViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { CameraController() }
    val haptics = rememberTujiHaptics()

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }

    // Asked when the camera is opened, not at launch: a permission requested
    // before the feature is visible is a permission most people decline.
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(TujiSpace.S4),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.capture_permission),
                style = TujiType.body,
                color = TujiColor.Ink2,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(TujiSpace.S3))
            TujiButton(
                text = stringResource(R.string.capture_permission_grant),
                onClick = { ask.launch(Manifest.permission.CAMERA) },
            )
        }
        return
    }

    Box(Modifier.fillMaxSize().background(TujiColor.Ink)) {
        CameraFrame(controller = controller, modifier = Modifier.fillMaxSize())
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = TujiSpace.S6)
                .size(76.dp)
                .clip(CircleShape)
                .background(TujiColor.Paper)
                .tujiClickable {
                    // The heavier of the two taps. A shutter is the one control
                    // here that commits to something, and the screen it leads
                    // to takes a second to arrive.
                    haptics.firm()
                    scope.launch {
                        runCatching { controller.takePhoto(context) }
                            .onSuccess(vm::submit)
                    }
                },
        )
    }
}

/**
 * 辨識中 — iOS's `recognizingPanel`.
 *
 * **No spinner.** A determinate-looking bar for work of unknown length is a
 * lie, and a spinner is the platform's own idle mark; the sweeping rule is this
 * app's. The cat only arrives after three seconds, which is C.11's
 * "waiting > 3s" clause — before that the wait is short enough that a character
 * turning up to acknowledge it would be the slower thing on screen.
 */
@Composable
private fun RecognizingPanel(image: AtlasImageSummary) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(image.id) {
        slow = false
        delay(SLOW_AFTER)
        slow = true
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S4),
    ) {
        Spacer(Modifier.height(TujiSpace.S2))
        Box(Modifier.fillMaxWidth().height(240.dp).background(TujiColor.Paper2)) {
            AsyncImage(
                model = image.thumbUrl ?: image.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        val label = stringResource(R.string.capture_uploading)
        TujiIndeterminateBar(label = label)
        Text(label, style = TujiType.label, color = TujiColor.Ink3)
        AnimatedVisibility(
            visible = slow,
            enter = fadeIn(TujiMotion.ease(TujiMotion.D2)),
            exit = fadeOut(TujiMotion.ease(TujiMotion.D2)),
        ) {
            MascotSpeechBubble(
                pose = MascotPose.Think,
                text = stringResource(R.string.capture_recognizing_slow),
            )
        }
    }
}

@Composable
private fun Naming(
    step: CaptureViewModel.Step.Naming,
    vm: CaptureViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val draft = step.draft
    // Re-recognising **replaces the form**, which is what iOS's
    // `recognizingPanel` does: the candidates, the two names and the mode
    // buttons are all about to be answers to a different question, so leaving
    // them on screen under a card invites the user to read stale ones.
    if (step.busy == CaptureViewModel.Step.Work.Recognizing) {
        RecognizingPanel(step.image)
        return
    }
    // Making the cards is a wait iOS does not have — there, 確認並生成卡片 hands
    // the work to `AtlasCaptureQueue` and closes the sheet at once. Until that
    // queue exists here the wait is real, so it is at least sealed off and
    // named rather than left as one grey button.
    TujiStatusBlocker(
        visible = step.busy == CaptureViewModel.Step.Work.Creating,
        title = stringResource(R.string.capture_creating),
        detail = stringResource(R.string.capture_creating_detail),
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TujiSpace.S4),
        verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
    ) {
        Spacer(Modifier.height(TujiSpace.S2))
        Box(
            Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(TujiColor.Paper2),
        ) {
            AsyncImage(
                model = step.image.thumbUrl ?: step.image.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Two depths, and tapping between them is free after the first look —
        // see CaptureDraft.needsFetch.
        Row(horizontalArrangement = Arrangement.spacedBy(TujiSpace.S2)) {
            listOf(
                RecognitionMode.Primary to R.string.capture_mode_primary,
                RecognitionMode.Escalate to R.string.capture_mode_escalate,
            ).forEach { (mode, label) ->
                TujiButton(
                    text = stringResource(label),
                    style = if (draft.mode == mode) TujiButtonStyle.Primary else TujiButtonStyle.Secondary,
                    enabled = step.busy == null,
                    onClick = { vm.setMode(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (draft.candidates.isEmpty()) {
            Text(
                stringResource(R.string.capture_no_candidates),
                style = TujiType.bodySm,
                color = TujiColor.Ink3,
            )
        } else {
            draft.candidates.forEach { candidate ->
                val chosen = candidate.id == draft.selectedCandidateId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (chosen) TujiColor.Current else TujiColor.Paper2)
                        .tujiClickable { vm.pick(candidate.id) }
                        .padding(TujiSpace.S3),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(candidate.label, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        candidate.zhHant?.let {
                            Text(it, style = TujiType.bodySm, color = TujiColor.Ink3)
                        }
                    }
                    Text(
                        "${(candidate.confidence * 100).toInt()}%",
                        style = TujiType.monoLabel,
                        color = TujiColor.Ink3,
                    )
                }
            }
        }

        TujiTextField(
            value = draft.lemma,
            onValueChange = { vm.edit(lemma = it) },
            placeholder = stringResource(R.string.capture_lemma),
            modifier = Modifier.fillMaxWidth(),
        )
        TujiTextField(
            value = draft.displayZhHant,
            onValueChange = { vm.edit(zhHant = it) },
            placeholder = stringResource(R.string.capture_zh),
            modifier = Modifier.fillMaxWidth(),
        )

        TujiButton(
            text = stringResource(R.string.capture_confirm),
            onClick = vm::confirm,
            enabled = draft.isComplete && step.busy == null,
            modifier = Modifier.fillMaxWidth(),
        )
        TujiButton(
            text = stringResource(R.string.capture_retake),
            style = TujiButtonStyle.Secondary,
            onClick = vm::reset,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(bottomPadding + TujiSpace.S6))
    }
}

/** C.11: a wait only earns a sentence about itself after three seconds. */
private const val SLOW_AFTER = 3_000L
