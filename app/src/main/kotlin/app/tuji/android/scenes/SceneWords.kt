package app.tuji.android.scenes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tuji.android.R
import app.tuji.android.core.design.TujiColor
import app.tuji.android.core.design.TujiGlyph
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.design.TujiType
import app.tuji.android.core.design.tujiClickable
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Word
import coil3.compose.AsyncImage

/**
 * A 問句's 相關詞條: its `wordIds` looked up in the catalogue the app already
 * holds. Nothing is bundled, so offline or before the first load the list is
 * shorter or empty, and the screens hide it rather than explain. A lock only
 * marks a topic this account may not study; browsing is never gated.
 */
data class SceneWord(val word: Word, val isLocked: Boolean)

object SceneWords {
    /**
     * Ids the catalogue does not have are dropped; the rest keep the JSON's order.
     * `studyable` null means no gate. The ids name 日文圖鑑 words, so a zh-en
     * learner gets none.
     */
    fun resolve(ids: List<String>, words: List<Word>, studyable: List<String>?, direction: LearningDirection): List<SceneWord> {
        if (direction != LearningDirection.ZH_JA) return emptyList()
        val allowed = studyable?.toSet()
        val byId = words.associateBy { it.id }
        return ids.mapNotNull { id ->
            byId[id]?.let { word -> SceneWord(word, allowed != null && word.category !in allowed) }
        }
    }
}

@Composable
internal fun SceneWordList(words: List<SceneWord>, onOpen: (String) -> Unit) {
    if (words.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
        Text(stringResource(R.string.scene_related_words), style = TujiType.label, color = TujiColor.Ink3)
        words.forEach { item ->
            val word = item.word
            val reading = word.reading?.takeIf { it != word.word }
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).tujiClickable { onOpen(word.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TujiSpace.S3),
            ) {
                Box(Modifier.size(44.dp).background(TujiColor.Paper2).clipToBounds()) {
                    AsyncImage(word.imageUrl, null, Modifier.size(44.dp), contentScale = ContentScale.Crop)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TujiSpace.S1)) {
                        Text(word.word, style = TujiType.bodyStrong, color = TujiColor.Ink)
                        if (item.isLocked) {
                            val label = stringResource(R.string.scene_word_locked)
                            TujiGlyph.Lock(size = 11.dp, tint = TujiColor.Ink3, modifier = Modifier.semantics { contentDescription = label })
                        }
                    }
                    OneLine(listOfNotNull(reading, word.chinese).joinToString(" · "), TujiType.label, TujiColor.Ink3)
                }
                Text("›", style = TujiType.h3, color = TujiColor.Ink3)
            }
        }
    }
}
