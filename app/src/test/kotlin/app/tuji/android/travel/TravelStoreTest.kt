package app.tuji.android.travel

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.StudyQueueWord
import app.tuji.android.core.model.TravelCity
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelGrid
import app.tuji.android.core.model.TravelPoint
import app.tuji.android.core.model.TravelResponse
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.network.TravelAccess
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

/**
 * The travel map as the app holds it, and a 景點's study queue.
 *
 * THE RED LINES:
 *  - An empty answer is an answer: it is what hides the entry, and it must not
 *    be asked for again on every return to 物見.
 *  - Another language's map is never shown under this one.
 *  - A spot's queue asks for the spot — never a theme — and keeps one card per word.
 */
class TravelStoreTest {

    private val ja = LearningDirection.ZH_JA
    private val en = LearningDirection.entries.first { it != LearningDirection.ZH_JA }

    private val japan = TravelDestination(
        id = "japan",
        name = "日本",
        grid = TravelGrid(14, 16),
        cities = listOf(
            TravelCity(
                id = "tokyo", name = "東京", regionId = "kanto", aspect = 1.44f,
                spots = listOf(TravelSpot(id = "akihabara", name = "秋葉原", at = TravelPoint(0.6f, 0.4f), open = true, free = true)),
            ),
        ),
    )

    private class Server : TravelAccess {
        val answers = mutableMapOf<LearningDirection, Result<TravelResponse>>()
        val asked = mutableListOf<Pair<String, LearningDirection>>()
        var lastQueue: Triple<String, StudyMode, Int>? = null

        override suspend fun destinations(lang: String, learning: LearningDirection): TravelResponse {
            asked += lang to learning
            return (answers[learning] ?: Result.success(TravelResponse())).getOrThrow()
        }

        override suspend fun spotQueue(
            spotId: String, mode: StudyMode, limit: Int, lang: String, learning: LearningDirection,
        ): StudyQueueResponse {
            lastQueue = Triple(spotId, mode, limit)
            fun card(id: String) = StudyQueueItem(
                card = StudyCard(id = id),
                word = StudyQueueWord(id = "w1", word = "ガチャ", chinese = "扭蛋", imageUrl = "", pronunciation = "", category = "collecting-hobbies"),
            )
            return StudyQueueResponse(queue = listOf(card("c1"), card("c2")))
        }
    }

    @Test fun `the map is read once per language pair, and again when it changes`() = runTest {
        val server = Server().apply { answers[ja] = Result.success(TravelResponse(listOf(japan))) }
        val store = TravelStore(server)

        store.loadIfNeeded("zh-Hant", ja)
        store.loadIfNeeded("zh-Hant", ja)
        assertEquals(1, server.asked.size)
        assertEquals("秋葉原", store.state.value.spot("akihabara")?.name)
        assertEquals("tokyo", store.state.value.cityOf("akihabara")?.id)
        assertEquals(1, store.state.value.city("tokyo", "japan")?.spots?.size)

        // 英文: the server answers nothing, which is an answer — the entry goes.
        store.loadIfNeeded("zh-Hant", en)
        assertEquals(2, server.asked.size)
        assertEquals(emptyList<TravelDestination>(), store.state.value.destinations)
        assertEquals(TravelStore.Phase.Loaded, store.state.value.phase)

        // …and it is not asked for again on the next visit.
        store.loadIfNeeded("zh-Hant", en)
        assertEquals(2, server.asked.size)

        // A different interface language is a different map.
        store.loadIfNeeded("en", ja)
        assertEquals(3, server.asked.size)
    }

    @Test fun `a failed first load is failed, not empty, and is asked again`() = runTest {
        val server = Server().apply { answers[ja] = Result.failure(IOException("offline")) }
        val store = TravelStore(server)

        store.loadIfNeeded("zh-Hant", ja)
        assertEquals(TravelStore.Phase.Failed, store.state.value.phase)

        server.answers[ja] = Result.success(TravelResponse(listOf(japan)))
        store.loadIfNeeded("zh-Hant", ja)
        assertEquals(TravelStore.Phase.Loaded, store.state.value.phase)
        assertEquals(1, store.state.value.destinations.size)
    }

    @Test fun `a failed reload keeps the map already shown`() = runTest {
        val server = Server().apply { answers[ja] = Result.success(TravelResponse(listOf(japan))) }
        val store = TravelStore(server)
        store.loadIfNeeded("zh-Hant", ja)

        server.answers[ja] = Result.failure(IOException("offline"))
        store.reload("zh-Hant", ja)
        assertEquals(TravelStore.Phase.Loaded, store.state.value.phase)
        assertEquals(1, store.state.value.destinations.size)
    }

    @Test fun `a place that is not in the answer is simply absent`() = runTest {
        val store = TravelStore(Server())
        store.loadIfNeeded("zh-Hant", ja)
        assertNull(store.state.value.destination("japan"))
        assertNull(store.state.value.spot("akihabara"))
        assertNull(store.state.value.cityOf("akihabara"))
    }

    @Test fun `a spot queue asks for the spot and keeps one card per word`() = runTest {
        val server = Server()
        val queue = SpotQueue(server, "akihabara").queue(
            mode = StudyMode.New, limit = 0, new = 0, categories = listOf("fruits"), lang = "zh-Hant", learning = ja,
        )
        // A limit of zero would ask the server for nothing; one is the floor.
        assertEquals(Triple("akihabara", StudyMode.New, 1), server.lastQueue)
        assertEquals(listOf("c1"), queue.queue.map { it.card.id })
    }
}
