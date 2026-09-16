package app.tuji.android.atlas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tuji.android.R
import app.tuji.android.core.design.TujiPageLoading
import app.tuji.android.core.design.TujiSpace
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.core.network.AtlasRepository
import app.tuji.android.core.network.CatalogReading
import app.tuji.android.core.model.ClipPlaying

/**
 * One word's full entry, for a surface that is not its page — the pulled-up
 * half of a study sheet.
 *
 * It borrows [WordDetailViewModel] rather than fetching for itself, because
 * that model already knows the one thing worth not re-deriving: a 自製 card
 * comes from the atlas route and a catalogue word from the catalogue one.
 *
 * **Loaded when it is asked for, not before.** The caller composes this only
 * once the sheet has begun to open, so a reader who never drags never pays for
 * the request — which is the same bargain the study queue makes by not
 * carrying 詞塊 for a hundred cards to serve the one that gets opened.
 */
@Composable
internal fun WordDetailPanel(
    wordId: String,
    catalog: CatalogReading,
    atlas: AtlasRepository,
    audio: ClipPlaying,
    speech: WordSpeaking?,
    direction: LearningDirection,
    uiLang: String,
    accent: String,
    showChinese: Boolean,
    session: TargetLanguage,
    modifier: Modifier = Modifier,
) {
    val vm = remember(wordId) {
        WordDetailViewModel(
            catalog = catalog,
            atlas = atlas,
            audio = audio,
            direction = direction,
            uiLang = uiLang,
            accent = accent,
            speech = speech,
        ).also { it.load(wordId) }
    }
    val state by vm.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is WordDetailViewModel.State.Loaded -> Column(
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TujiSpace.S4),
        ) {
            WordDetailSections(
                word = s.word,
                uiLang = uiLang,
                showChinese = showChinese,
                session = session,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // A failure shows nothing rather than an error: the sheet's own summary
        // is still there above it, and the rating below it still works. This
        // half is extra reading, and extra reading that did not arrive is not
        // an error worth interrupting a session for.
        else -> TujiPageLoading(
            modifier = modifier.fillMaxSize(),
            label = stringResource(R.string.atlas_loading),
        )
    }
}
