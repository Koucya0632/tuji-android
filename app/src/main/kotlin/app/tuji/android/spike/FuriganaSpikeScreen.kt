package app.tuji.android.spike

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import app.tuji.android.core.design.CjkCascadeProbe
import app.tuji.android.core.design.FuriganaHeadword
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiFace
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiTheme
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.TujiTypefaces
import app.tuji.android.core.model.FuriganaSegment
import app.tuji.android.core.model.HeadwordDisplay
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.asHeadworded
import app.tuji.android.core.model.headwordDisplay
import app.tuji.android.core.network.CatalogReading
import androidx.compose.foundation.background

/**
 * M0's gate, on one screen.
 *
 * The時程計劃書 puts this in week one rather than after the UI exists, and the
 * reason is that it is the only technical unknown that could still overturn the
 * interface approach. Two things are being checked, and neither is visible from
 * a green build:
 *
 *  1. **The furigana split draws.** Real rows from `/api/words`, not fixtures —
 *     including the multi-character block (風呂 read as ふろ) that a
 *     per-character format could not represent.
 *  2. **The CJK cascade holds.** Latin must stay Plus Jakarta while kanji come
 *     from GenSenRounded. Android has no `cascadeList`; if the header line below
 *     renders its Latin in a different face from the pure-Latin line under it,
 *     the fallback did not take and the whole text layer needs another plan.
 */
@Composable
fun FuriganaSpikeScreen(catalog: CatalogReading) {
    var state by remember { mutableStateOf<SpikeState>(SpikeState.Loading) }
    val insets = WindowInsets.systemBars.asPaddingValues()

    LaunchedEffect(catalog) {
        state = try {
            val response = catalog.words(lang = "zh-Hant", learning = LearningDirection.ZH_JA)
            SpikeState.Loaded(response.words)
        } catch (e: Throwable) {
            SpikeState.Failed(e.message ?: e::class.simpleName ?: "unknown")
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TujiColor.Paper)
            .padding(horizontal = TujiSpace.S4),
    ) {
        Spacer(Modifier.height(insets.calculateTopPadding() + TujiSpace.S4))
        CjkCascadeProbe()
        Spacer(Modifier.height(TujiSpace.S5))

        when (val s = state) {
            SpikeState.Loading -> Text("載入中…", style = TujiType.body, color = TujiColor.Ink3)
            is SpikeState.Failed -> Text(
                "讀取失敗：${s.message}",
                style = TujiType.body,
                color = TujiColor.Alert,
            )
            is SpikeState.Loaded -> WordList(s.words, insets.calculateBottomPadding())
        }
    }
}

private sealed interface SpikeState {
    data object Loading : SpikeState
    data class Loaded(val words: List<Word>) : SpikeState
    data class Failed(val message: String) : SpikeState
}

@Composable
private fun WordList(words: List<Word>, bottomInset: Dp) {
    val withRuby = remember(words) {
        words.filter { it.asHeadworded().headwordDisplay(TargetLanguage.JA) is HeadwordDisplay.Ruby }
    }
    Column {
        Text(
            "${words.size} 個詞，其中 ${withRuby.size} 個有 furigana 切分",
            style = TujiType.monoLabel,
            color = TujiColor.Ink3,
        )
        Spacer(Modifier.height(TujiSpace.S3))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            contentPadding = PaddingValues(bottom = bottomInset),
        ) {
            items(withRuby, key = { it.id }) { word ->
                val display = word.asHeadworded().headwordDisplay(TargetLanguage.JA)
                if (display is HeadwordDisplay.Ruby) {
                    Column(Modifier.fillMaxWidth()) {
                        FuriganaHeadword(display.segments)
                        word.chinese?.let {
                            Text(it, style = TujiType.bodySm, color = TujiColor.Ink3)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFBF7EF)
@Composable
private fun FuriganaPreview() {
    TujiTheme(face = TujiFace.JP) {
        Column(
            Modifier
                .background(Color(0xFFFBF7EF))
                .padding(TujiSpace.S4),
            verticalArrangement = Arrangement.spacedBy(TujiSpace.S5),
        ) {
            // The four iOS previews, so a divergence shows up as a picture
            // rather than as a bug report.
            FuriganaHeadword(
                listOf(
                    FuriganaSegment("歯", "は"),
                    FuriganaSegment("磨", "みが"),
                    FuriganaSegment("き", null),
                    FuriganaSegment("粉", "こ"),
                )
            )
            FuriganaHeadword(
                listOf(
                    FuriganaSegment("目", "め"),
                    FuriganaSegment("覚", "ざ"),
                    FuriganaSegment("ま", null),
                    FuriganaSegment("し", null),
                    FuriganaSegment("時計", "どけい"),
                )
            )
            FuriganaHeadword(listOf(FuriganaSegment("豆板醤", "トウバンジャン")))
        }
    }
}
