package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TopicChatWireTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test fun `the turn being answered is the newest AI turn of the current topic`() {
        val state = json.decodeFromString(
            TopicChatState.serializer(),
            """
            {"chat":{"id":"c","topicId":"bath","topicName":"浴室","topicVersion":2,"status":"active","answers":1},
             "messages":[
               {"id":"m1","kind":"ai","topicId":"kitchen","topicVersion":1,"ai":{"intent":"opening","line":{"text":"Hi"},"awaitsAnswer":true,"options":[],"keywords":[],"wordIds":[]}},
               {"id":"t","kind":"topic","topicId":"bath","topicVersion":2,"topic":{"name":"浴室"}}
             ],
             "needsOpening":true,"assistsLeft":2,"offer":{"minPoints":1,"maxPoints":3,"available":9}}
            """.trimIndent(),
        )
        // The topic changed and its opening has not arrived: nothing to answer yet.
        assertNull(state.latestTurn)
        assertEquals(3, state.offer.maxPoints)
    }
}
