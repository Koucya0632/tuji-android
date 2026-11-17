package app.tuji.android.atlas

import app.tuji.android.core.model.CategoriesResponse
import app.tuji.android.core.model.Category
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.WordsListResponse
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.CatalogReading
import app.tuji.android.core.network.Endpoint
import app.tuji.android.core.study.SpokenVoice
import app.tuji.android.core.study.SynthesisVoice
import app.tuji.android.core.study.TopicPresetChoice
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Runs the actual app store on an isolated device with local fixture data. */
class CatalogDirectionDeviceTest {
    private class Fake : CatalogReading {
        var fail = false
        override suspend fun words(lang: String, learning: LearningDirection): WordsListResponse {
            if (fail) error("simulated failure")
            val id = if (learning == LearningDirection.ZH_EN) "rental-bond" else "mug"
            return WordsListResponse(listOf(Word(id = id, word = id)))
        }
        override suspend fun categories(lang: String, learning: LearningDirection): CategoriesResponse {
            val id = if (learning == LearningDirection.ZH_EN) "australia-housing" else "japan"
            return CategoriesResponse(categories = listOf(Category(id = id, name = id)))
        }
        override suspend fun word(id: String, lang: String, learning: LearningDirection): WordDetail = error("unused")
    }

    @Test
    fun switchingCountryUsesItsOwnCategoriesAndClearsStaleDataOnFailure() = runBlocking {
        val fake = Fake()
        val store = CatalogStore(fake)
        store.load(LearningDirection.ZH_EN)
        assertEquals(listOf("australia-housing"), store.contents.value.categories.map { it.id })
        store.load(LearningDirection.ZH_JA)
        assertEquals(listOf("japan"), store.contents.value.categories.map { it.id })
        assertEquals(listOf("mug"), store.words.map { it.id })
        fake.fail = true
        store.load(LearningDirection.ZH_EN)
        assertTrue(store.words.isEmpty())
        assertTrue(store.contents.value.categories.isEmpty())
        assertEquals("zh-en", Endpoint.Categories("zh-Hant", LearningDirection.ZH_EN).descriptor.query.toMap()["learning"])
        assertEquals("zh-ja", Endpoint.Categories("zh-Hant", LearningDirection.ZH_JA).descriptor.query.toMap()["learning"])
    }

    @Test
    fun hiddenRegionSelectionsAndAustralianFallbackStaySafe() {
        val visible = setOf("kitchen", "bedroom")
        val selected = setOf("australia-housing", "kitchen")
        assertEquals(setOf("australia-housing"), TopicPresetChoice.replacingVisible(selected, visible, emptySet()))
        assertTrue(TopicPresetChoice.hasOnlyHiddenSelection(setOf("australia-housing"), visible))
        assertNull(SpokenVoice.clip(mapOf("en-US" to "us.mp3"), LearningDirection.ZH_EN, "au"))
        assertEquals("en-AU", SynthesisVoice.locale(TargetLanguage.EN, "au").toLanguageTag())
        assertFalse(SynthesisVoice.supports(TargetLanguage.EN, "au", 0))
    }
}
