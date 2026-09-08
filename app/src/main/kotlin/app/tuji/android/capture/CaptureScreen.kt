package app.tuji.android.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiTextField
import app.tuji.android.core.design.TujiType
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
        is CaptureViewModel.Step.Framing -> Framing(vm)
        is CaptureViewModel.Step.Uploading ->
            Centered(stringResource(R.string.capture_uploading))

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
                    scope.launch {
                        runCatching { controller.takePhoto(context) }
                            .onSuccess(vm::submit)
                    }
                },
        )
    }
}

@Composable
private fun Naming(
    step: CaptureViewModel.Step.Naming,
    vm: CaptureViewModel,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val draft = step.draft
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
                    enabled = !step.busy,
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
            enabled = draft.isComplete && !step.busy,
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

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = TujiType.body, color = TujiColor.Ink3)
    }
}
