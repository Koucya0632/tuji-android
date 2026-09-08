package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The wire, as `/api/study/queue` actually speaks it on 2026-09-08.
 *
 * It is **mixed**: near-raw rows give `image_url` / `target_language` /
 * `card_type` while `readingSegments`, `cefrLevel` and `audioUrls` in the same
 * object are camelCase. iOS never had to notice, because its decoder rewrites
 * snake keys and leaves camel ones alone. This suite exists because the study
 * flow failed to decode in production against a field that looks entirely
 * ordinary in the iOS source it was ported from.
 */
class StudyQueueWireTest {

    private val json = Json { ignoreUnknownKeys = true; useAlternativeNames = true }

    /** Trimmed from a real response. */
    private val payload = """
    {
      "queue": [
        {
          "card": {
            "id": 77608, "word_id": "access-card", "card_type": "回想卡",
            "deck_key": "image-ja", "front": "", "back": "入館カード"
          },
          "word": {
            "id": "access-card", "word": "入館カード", "chinese": "門禁卡",
            "definition": "用來開啟建築物或管制區域出入口的門禁卡。",
            "image_url": "https://img.nexflow.team/word-images/access-card.webp",
            "pronunciation": "にゅうかんカード", "reading": "にゅうかんカード",
            "readingSegments": [{"ruby":"にゅうかん","text":"入館"},{"ruby":null,"text":"カード"}],
            "target_language": "ja", "category": "office"
          },
          "choices": ["キーボード","名刺","社員証","入館カード"],
          "examples": [
            {"sentence":"入館カードを忘れた。","cefrLevel":"A2",
             "audioUrls":{"ja-JP":"https://img.nexflow.team/x.mp3"},
             "mentionedWordIds":["access-card"]}
          ],
          "mastery": null,
          "state": "new"
        }
      ],
      "stats": {"total":557,"seen":0,"due":0,"new":557,"todayNew":0,"byStatus":[]}
    }
    """.trimIndent()

    @Test
    fun `the snake-cased fields decode`() {
        val item = json.decodeFromString<StudyQueueResponse>(payload).queue.single()
        assertEquals("https://img.nexflow.team/word-images/access-card.webp", item.word.imageUrl)
        assertEquals(TargetLanguage.JA, item.word.targetLanguage)
        assertEquals("回想卡", item.card.cardType)
        assertEquals("image-ja", item.card.deckKey)
    }

    @Test
    fun `the camelCased fields in the same object still decode`() {
        // The payload is mixed, not snake — a blanket naming strategy would
        // have broken exactly these.
        val item = json.decodeFromString<StudyQueueResponse>(payload).queue.single()
        assertEquals(2, item.word.readingSegments!!.size)
        assertEquals("にゅうかん", item.word.readingSegments!!.first().ruby)
        assertEquals("A2", item.examples!!.single().cefrLevel)
        assertEquals(1, item.examples!!.single().audioUrls!!.size)
    }

    @Test
    fun `a numeric card id decodes as a string`() {
        // Public cards carry an integer id; 自製圖鑑 cards carry a uuid.
        val item = json.decodeFromString<StudyQueueResponse>(payload).queue.single()
        assertEquals("77608", item.card.id)
    }

    @Test
    fun `a uuid card id decodes too`() {
        val custom = payload.replace("\"id\": 77608", "\"id\": \"3f1c-uuid\"")
        val item = json.decodeFromString<StudyQueueResponse>(custom).queue.single()
        assertEquals("3f1c-uuid", item.card.id)
    }

    @Test
    fun `unknown fields the backend adds do not sink the payload`() {
        // `state`, `word_id`, `front`, `back` and `byStatus` are all in the real
        // response and none of them are modelled.
        val item = json.decodeFromString<StudyQueueResponse>(payload).queue.single()
        assertEquals("access-card", item.word.id)
        assertNull(item.mastery)
    }

    @Test
    fun `the stats come back with the queue`() {
        val stats = json.decodeFromString<StudyQueueResponse>(payload).stats!!
        assertEquals(557, stats.total)
        assertEquals(0, stats.due)
    }
}
