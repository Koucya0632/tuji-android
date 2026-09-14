package app.tuji.android.atlas

import app.tuji.android.core.model.CategoriesResponse
import app.tuji.android.core.model.ClipPlayback
import app.tuji.android.core.model.ClipPlaying
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.WordSpeaking
import app.tuji.android.core.model.WordsListResponse
import app.tuji.android.core.network.AtlasItemReading
import app.tuji.android.core.network.CatalogReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The pronunciation button's fallback: iOS's `SpeechService` when a word has no clip. */
@OptIn(ExperimentalCoroutinesApi::class)
class WordDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class Catalog(val word: WordDetail) : CatalogReading {
        override suspend fun words(lang: String, learning: LearningDirection) = WordsListResponse(words = emptyList())
        override suspend fun word(id: String, lang: String, learning: LearningDirection) = word
        override suspend fun categories(lang: String) = CategoriesResponse()
    }

    private class Player : ClipPlaying {
        val played = mutableListOf<String?>()
        override fun canPlay(url: String?, online: Boolean) = url != null
        override suspend fun play(url: String?, rate: Float): ClipPlayback { played += url; return ClipPlayback.Finished }
        override fun stop() = Unit
    }

    private class Voice(val languages: Set<TargetLanguage>) : WordSpeaking {
        val spoken = mutableListOf<Triple<String, TargetLanguage, String>>()
        override fun canSpeak(language: TargetLanguage) = language in languages
        override suspend fun speak(text: String, language: TargetLanguage, accent: String) { spoken += Triple(text, language, accent) }
        override fun stop() = Unit
    }

    private fun vm(word: WordDetail, player: Player, voice: WordSpeaking?) = WordDetailViewModel(
        catalog = Catalog(word),
        atlas = object : AtlasItemReading { override suspend fun itemDetail(itemId: String, lang: String) = word },
        audio = player,
        direction = LearningDirection.ZH_JA,
        uiLang = "zh-Hant",
        accent = "uk",
        speech = voice,
        scope = TestScope(dispatcher),
    )

    private val noClip = WordDetail(id = "atlas:1", word = "アニメーション", targetLanguage = TargetLanguage.JA)

    @Test fun `a word with no recording is read by the system voice in its own language`() = runTest(dispatcher) {
        val player = Player()
        val voice = Voice(setOf(TargetLanguage.JA))
        val vm = vm(noClip, player, voice)
        vm.load(noClip.id); advanceUntilIdle()
        assertTrue(vm.canPlay(noClip))
        vm.play(); advanceUntilIdle()
        assertEquals(listOf(Triple("アニメーション", TargetLanguage.JA, "uk")), voice.spoken)
        assertTrue(player.played.isEmpty())
    }

    @Test fun `a recording always wins over the voice`() = runTest(dispatcher) {
        val clip = noClip.copy(audioUrls = mapOf("ja-JP" to "https://clip/ja.mp3"))
        val player = Player()
        val voice = Voice(setOf(TargetLanguage.JA))
        val vm = vm(clip, player, voice)
        vm.load(clip.id); advanceUntilIdle()
        vm.play(); advanceUntilIdle()
        assertEquals(1, player.played.size)
        assertTrue(voice.spoken.isEmpty())
    }

    /** No voice installed for the language is the same answer as before: no button. */
    @Test fun `no clip and no voice for the language means no button`() = runTest(dispatcher) {
        val vm = vm(noClip, Player(), Voice(setOf(TargetLanguage.EN)))
        vm.load(noClip.id); advanceUntilIdle()
        assertFalse(vm.canPlay(noClip))
    }
}
