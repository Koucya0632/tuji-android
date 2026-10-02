package app.tuji.android.atlas

import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.Membership
import app.tuji.android.core.model.WordInsightConfusable
import app.tuji.android.core.model.WordInsights
import app.tuji.android.core.model.WordInsightsResponse
import app.tuji.android.core.network.WordInsightsReading
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WordInsightsStoreTest {

    private val direction = LearningDirection.entries.first()
    private val free = Entitlement(membership = Membership(tier = "free", policy = "v2"))
    private val lifetime = Entitlement(membership = Membership(tier = "lifetime", policy = "v2"))
    private val v1 = Entitlement(membership = Membership(tier = "pro", policy = "v1"))

    private val sample = WordInsights(
        confusables = listOf(WordInsightConfusable(term = "stove", catalogId = "stove", distinction = "…")),
        lockedMistakesCount = 2,
    )

    private class Server(val answer: WordInsights? = null, var fail: Boolean = false) : WordInsightsReading {
        val asked = mutableListOf<String>()
        override suspend fun insights(id: String, lang: String, learning: LearningDirection): WordInsightsResponse {
            asked += id
            if (fail) throw IOException("offline")
            return WordInsightsResponse(available = true, insights = answer)
        }
    }

    private fun WordInsightsStore.answer(id: String, entitlement: Entitlement?) =
        answers.value[WordInsightsStore.key(id, direction, "zh-Hant", entitlement)]

    @Test fun `asks once per word and tier`() = runTest {
        val server = Server(sample)
        val store = WordInsightsStore(server)
        store.load("oven", direction, "zh-Hant", free)
        store.load("oven", direction, "zh-Hant", free)
        assertEquals(listOf("oven"), server.asked)
        assertEquals(sample, store.answer("oven", free))

        // Becoming a member is a new key, so the unlocked text is fetched.
        store.load("oven", direction, "zh-Hant", lifetime)
        assertEquals(listOf("oven", "oven"), server.asked)
    }

    @Test fun `policy v1, no entitlement, 自製 and 物見 cost no request`() = runTest {
        val server = Server(sample)
        val store = WordInsightsStore(server)
        store.load("oven", direction, "zh-Hant", v1)
        store.load("oven", direction, "zh-Hant", null)
        store.load("atlas:abc", direction, "zh-Hant", free)
        store.load("saved:kettle", direction, "zh-Hant", free)
        assertTrue(server.asked.isEmpty())
    }

    @Test fun `a word with none is remembered as none`() = runTest {
        val server = Server(answer = null)
        val store = WordInsightsStore(server)
        store.load("oven", direction, "zh-Hant", free)
        assertTrue(WordInsightsStore.key("oven", direction, "zh-Hant", free) in store.answers.value)
        assertNull(store.answer("oven", free))
    }

    @Test fun `a failure is asked again, and reset forgets everything`() = runTest {
        val server = Server(sample, fail = true)
        val store = WordInsightsStore(server)
        store.load("oven", direction, "zh-Hant", free)
        assertTrue(store.answers.value.isEmpty())
        server.fail = false
        store.load("oven", direction, "zh-Hant", free)
        assertEquals(sample, store.answer("oven", free))
        store.reset()
        assertTrue(store.answers.value.isEmpty())
    }
}
