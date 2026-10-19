package app.tuji.android.core.network

import app.tuji.android.core.model.Story
import app.tuji.android.core.model.StoryEvent
import app.tuji.android.core.model.StoryInterrupted
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryRepositoryTest {

    @Test fun `each kind of line becomes its event`() {
        assertEquals(StoryEvent.Opening("T", "I"), StoryRepository.event("""{"type":"opening","title":"T","intro":"I"}"""))
        val segment = StoryRepository.event("""{"type":"segment","segment":{"speaker":"cat","text":"にゃ","wordIds":[]}}""")
        assertEquals(Story.Speaker.Cat, (segment as StoryEvent.Segment).segment.speaker)
        val done = StoryRepository.event(
            """{"type":"done","story":{"title":"T","intro":"I","segments":[]},"charged":4,"available":10,"minPoints":2,"maxPoints":8}""",
        )
        assertEquals(4, (done as StoryEvent.Done).result.charged)
    }

    @Test fun `a line from a newer server is skipped`() {
        assertNull(StoryRepository.event("""{"type":"progress","percent":40}"""))
    }

    @Test fun `a whole answer in one body is the finished story`() {
        val event = StoryRepository.event("""{"story":{"title":"T","intro":"I","segments":[]},"charged":3,"available":7}""")
        assertTrue(event is StoryEvent.Done)
    }

    @Test fun `an error line stops the story with its code`() {
        val error = runCatching { StoryRepository.event("""{"type":"error","error":"rate_limited"}""") }.exceptionOrNull()
        assertEquals("rate_limited", (error as StoryInterrupted).code)
    }
}
