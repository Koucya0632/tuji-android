package app.tuji.android.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.tuji.android.R
import app.tuji.android.TujiApplication
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiFace
import app.tuji.android.core.design.TujiNavBar
import app.tuji.android.core.design.TujiTheme
import app.tuji.android.core.model.FuriganaSegment
import app.tuji.android.core.model.Story
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.rememberDeviceLanguage

/**
 * A finished story at a chosen place, for looking at the stage without
 * spending points — iOS's `StoryStageDemo`. Debug builds only:
 *
 *     adb shell am start -n app.tuji.android.debug/app.tuji.android.assistant.StoryStageDemoActivity --es scene office
 *
 * Wrapped in the same nav bar as `StoryScreen`, so the stage gets the height it
 * has in the real flow.
 */
class StoryStageDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val scene = intent.getStringExtra("scene") ?: "supermarket"
        val speech = (application as TujiApplication).speech
        setContent {
            TujiTheme(face = TujiFace.forUiLanguage(rememberDeviceLanguage().wire)) {
                Column(Modifier.fillMaxSize().background(TujiColor.Paper).safeDrawingPadding()) {
                    TujiNavBar(
                        onLeading = ::finish,
                        leadingLabel = stringResource(R.string.atlas_back),
                        title = stringResource(R.string.assistant_feature_story),
                        showsRule = true,
                    )
                    StoryStage(
                        story = STORY,
                        sceneId = scene,
                        language = TargetLanguage.JA,
                        picked = PICKED,
                        writing = StoryBuilderModel.Writing.Idle,
                        speech = speech,
                        accent = "",
                        onOpenWord = {},
                        onClose = ::finish,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun line(speaker: Story.Speaker, text: String, ruby: List<Pair<String, String?>>?, translation: String?) =
    Story.Segment(
        speaker = speaker,
        text = text,
        furigana = ruby?.map { FuriganaSegment(it.first, it.second) },
        translation = translation,
    )

// Sample story content, not interface copy.
private val STORY = Story(
    title = "和Moira一起買蘋果",
    intro = "你和Moira走進超市，在水果區看到一籃紅色的蘋果。你想買一些回家，Moira陪你一起挑選。",
    segments = listOf(
        line(
            Story.Speaker.Cat,
            "このりんご、美味しそうですね。",
            listOf("この" to null, "りんご" to null, "、" to null, "美味" to "おい", "しそうですね。" to null),
            "這個蘋果看起來很好吃呢。",
        ),
        line(Story.Speaker.You, "いくつ買いますか。", listOf("いくつ" to null, "買" to "か", "いますか。" to null), "要買幾個？"),
        line(Story.Speaker.Narrator, "Moira 拿起一顆蘋果，仔細看了看。", null, null),
        line(Story.Speaker.Cat, "三つ買いましょう。", listOf("三" to "みっ", "つ" to null, "買" to "か", "いましょう。" to null), "買三個吧。"),
        line(Story.Speaker.You, "はい、そうしましょう。", null, "好，就這麼辦。"),
    ),
)

private val PICKED = listOf(
    Word(id = "demo-ringo", word = "りんご", chinese = "蘋果", targetLanguage = TargetLanguage.JA),
    Word(id = "demo-kau", word = "買う", chinese = "買", targetLanguage = TargetLanguage.JA, reading = "かう"),
)
