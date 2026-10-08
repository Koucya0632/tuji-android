package app.tuji.android.study

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiIconButton
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordSpeaking
import kotlinx.coroutines.launch

/**
 * Say an example sentence — iOS's `PronunciationButton(subject: .sentence(…), size: 32)`.
 *
 * Synthesised, as iOS's is: a sentence carries no clip of its own here. Drawn
 * only when a voice for the language is installed, so the button never offers
 * a sound it cannot make. Lit while it speaks.
 */
@Composable
fun SentenceSpeakerButton(
    sentence: String,
    language: TargetLanguage,
    speech: WordSpeaking?,
    accent: String,
    modifier: Modifier = Modifier,
) {
    if (speech == null || !speech.canSpeak(language)) return
    val scope = rememberCoroutineScope()
    var speaking by remember(sentence) { mutableStateOf(false) }
    TujiIconButton(
        label = stringResource(R.string.study_play_sentence),
        onClick = {
            if (!speaking) {
                speaking = true
                scope.launch {
                    try { speech.speak(sentence, language, accent) } finally { speaking = false }
                }
            }
        },
        modifier = modifier,
        size = 32.dp,
        ground = if (speaking) TujiColor.Current else TujiColor.Paper,
    ) {
        TujiGlyph.Speaker(size = 14.dp, tint = TujiColor.Ink)
    }
}
