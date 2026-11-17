package app.tuji.android.atlas

import app.tuji.android.core.model.CategoriesResponse
import app.tuji.android.core.model.Category
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.WordsListResponse
import app.tuji.android.core.network.CatalogReading
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogStoreDirectionTest {
    private class Fake : CatalogReading {
        val categoryCalls = mutableListOf<LearningDirection>()
        var failWordsFor: LearningDirection? = null
        var failCategories = false
        var enWordId = "rental-bond"
        var oldWordGate: CompletableDeferred<Unit>? = null

        override suspend fun words(lang: String, learning: LearningDirection): WordsListResponse {
            if (learning == LearningDirection.ZH_EN) oldWordGate?.await()
            if (learning == failWordsFor) error("unavailable")
            val id = if (learning == LearningDirection.ZH_EN) enWordId else "mug"
            val category = if (learning == LearningDirection.ZH_EN) "rental" else "japan"
            return WordsListResponse(listOf(Word(id = id, word = id, category = category)))
        }

        override suspend fun categories(lang: String, learning: LearningDirection): CategoriesResponse {
            categoryCalls += learning
            if (failCategories) error("unavailable")
            val id = if (learning == LearningDirection.ZH_EN) "rental" else "japan"
            return CategoriesResponse(categories = listOf(Category(id = id, name = id)))
        }

        override suspend fun word(id: String, lang: String, learning: LearningDirection): WordDetail = error("unused")
    }

    @Test
    fun `switching direction fetches separate words and shelves even when UI language is unchanged`() = runTest {
        val fake = Fake()
        val store = CatalogStore(fake)
        store.load(LearningDirection.ZH_EN)
        assertEquals(listOf("rental"), store.contents.value.categories.map { it.id })
        store.load(LearningDirection.ZH_JA)
        assertEquals(listOf("japan"), store.contents.value.categories.map { it.id })
        assertEquals(listOf("mug"), store.words.map { it.id })
        assertEquals(listOf(LearningDirection.ZH_EN, LearningDirection.ZH_JA), fake.categoryCalls)
    }

    @Test
    fun `failed direction change does not show the previous country`() = runTest {
        val fake = Fake()
        val store = CatalogStore(fake)
        store.load(LearningDirection.ZH_EN)
        fake.failWordsFor = LearningDirection.ZH_JA
        store.load(LearningDirection.ZH_JA)
        assertTrue(store.words.isEmpty())
        assertTrue(store.contents.value.categories.isEmpty())
    }

    @Test
    fun `failed foreground refresh keeps last same-country snapshot`() = runTest {
        val fake = Fake()
        val store = CatalogStore(fake)
        store.load(LearningDirection.ZH_EN)
        fake.failWordsFor = LearningDirection.ZH_EN
        store.load(LearningDirection.ZH_EN, force = true)
        assertEquals(listOf("rental-bond"), store.words.map { it.id })
        assertEquals(listOf("rental"), store.contents.value.categories.map { it.id })
    }

    @Test
    fun `forced foreground refresh replaces the same-country snapshot`() = runTest {
        val fake = Fake()
        val store = CatalogStore(fake)
        store.load(LearningDirection.ZH_EN)
        fake.enWordId = "new-word"
        store.load(LearningDirection.ZH_EN)
        assertEquals(listOf("rental-bond"), store.words.map { it.id })
        store.load(LearningDirection.ZH_EN, force = true)
        assertEquals(listOf("new-word"), store.words.map { it.id })
    }

    @Test
    fun `switching UI language still invalidates both words and categories`() = runTest {
        val fake = Fake()
        val store = CatalogStore(fake)
        store.load(LearningDirection.ZH_EN)
        store.retune("ja")
        assertTrue(store.words.isEmpty())
        assertTrue(store.contents.value.categories.isEmpty())
        store.load(LearningDirection.ZH_EN)
        assertEquals(2, fake.categoryCalls.size)
        assertEquals(listOf("rental"), store.contents.value.categories.map { it.id })
    }

    @Test
    fun `category failures retry and do not keep another country`() = runTest {
        val fake = Fake()
        val store = CatalogStore(fake)
        store.load(LearningDirection.ZH_EN)
        fake.failCategories = true
        store.load(LearningDirection.ZH_JA)
        assertTrue(store.contents.value.categories.isEmpty())
        fake.failCategories = false
        store.load(LearningDirection.ZH_JA)
        assertEquals(listOf("japan"), store.contents.value.categories.map { it.id })
    }

    @Test
    fun `late older request cannot overwrite the new direction`() = runTest {
        val fake = Fake()
        val gate = CompletableDeferred<Unit>()
        fake.oldWordGate = gate
        val store = CatalogStore(fake)
        val old = launch { store.load(LearningDirection.ZH_EN) }
        runCurrent()
        val current = launch { store.load(LearningDirection.ZH_JA) }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()
        old.join()
        current.join()
        assertEquals(listOf("mug"), store.words.map { it.id })
        assertEquals(listOf("japan"), store.contents.value.categories.map { it.id })
        assertEquals(listOf(LearningDirection.ZH_JA), fake.categoryCalls)
    }
}
