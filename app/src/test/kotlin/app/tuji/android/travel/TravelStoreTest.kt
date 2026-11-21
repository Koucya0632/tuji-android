package app.tuji.android.travel

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.TravelCity
import app.tuji.android.core.model.TravelDestination
import app.tuji.android.core.model.TravelGrid
import app.tuji.android.core.model.TravelPoint
import app.tuji.android.core.model.TravelResponse
import app.tuji.android.core.model.TravelSpot
import app.tuji.android.core.network.TravelAccess
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** iOS `TravelTests`' store half: once per language pair, and a failed first load is asked again. */
class TravelStoreTest {
    private val japan = TravelDestination(
        id = "japan", name = "日本", grid = TravelGrid(14, 16),
        cities = listOf(
            TravelCity(
                id = "tokyo", name = "東京", regionId = "kanto",
                spots = listOf(
                    TravelSpot(id = "akihabara", name = "秋葉原", at = TravelPoint(0.5, 0.5), open = true),
                    TravelSpot(id = "asakusa", name = "淺草", at = TravelPoint(0.2, 0.2)),
                ),
            ),
        ),
    )

    private class Server : TravelAccess {
        val answers = mutableMapOf<LearningDirection, Result<TravelResponse>>()
        val asked = mutableListOf<String>()

        override suspend fun destinations(lang: String, learning: LearningDirection): TravelResponse {
            asked += "$lang|${learning.wire}"
            return (answers[learning] ?: Result.success(TravelResponse())).getOrThrow()
        }

        override suspend fun spotQueue(spotId: String, mode: StudyMode, limit: Int, lang: String, learning: LearningDirection) =
            StudyQueueResponse(queue = emptyList())
    }

    @Test
    fun `loads once per language pair and again when it changes`() = runTest {
        val server = Server().apply { answers[LearningDirection.ZH_JA] = Result.success(TravelResponse(listOf(japan))) }
        val store = TravelStore(server)

        store.loadIfNeeded("zh-Hant", LearningDirection.ZH_JA)
        store.loadIfNeeded("zh-Hant", LearningDirection.ZH_JA)
        assertEquals(1, server.asked.size)
        assertEquals("秋葉原", store.state.value.spot("akihabara")?.name)
        assertEquals(2, store.state.value.city("tokyo", "japan")?.spots?.size)

        // 英文: the server answers nothing, which is an answer — the entry goes.
        store.loadIfNeeded("zh-Hant", LearningDirection.ZH_EN)
        assertEquals(2, server.asked.size)
        assertTrue(store.state.value.destinations.isEmpty())
        assertEquals(TravelStore.Phase.Loaded, store.state.value.phase)
    }

    @Test
    fun `a failed first load is asked again`() = runTest {
        val server = Server().apply { answers[LearningDirection.ZH_JA] = Result.failure(IOException("offline")) }
        val store = TravelStore(server)

        store.loadIfNeeded("zh-Hant", LearningDirection.ZH_JA)
        assertEquals(TravelStore.Phase.Failed, store.state.value.phase)

        server.answers[LearningDirection.ZH_JA] = Result.success(TravelResponse(listOf(japan)))
        store.loadIfNeeded("zh-Hant", LearningDirection.ZH_JA)
        assertEquals(TravelStore.Phase.Loaded, store.state.value.phase)
        assertEquals(1, store.state.value.destinations.size)
    }
}
