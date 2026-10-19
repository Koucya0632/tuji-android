package app.tuji.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyWireTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test fun `a card is read by its mode`() {
        val record = json.decodeFromString(
            VocabularyRecord.serializer(),
            """
            {"id":"r1","status":"completed","charged":3,"favorite":false,"createdAt":"2026-10-19","templateVersion":"v1",
             "result":{"outcome":"ok","message":"","candidates":[],
               "content":{"mode":"mnemonic","headword":{"text":"猫","reading":"ねこ","partOfSpeech":"名詞","meaning":"貓"},
                 "association":"a","technique":"t","retelling":"r","example":{"text":"猫がいる"},
                 "quiz":{"question":"q","answer":"a","hint":"h"}}}}
            """.trimIndent(),
        )
        val content = record.result?.content
        assertTrue(content is VocabularyMnemonic)
        assertEquals("ねこ", content?.headword?.reading)
    }

    @Test fun `an offer closed for members says why`() {
        val offer = json.decodeFromString(
            VocabularyOffer.serializer(),
            """{"enabled":false,"reason":"membership","available":0,"pricingVersion":"p1","modes":[{"mode":"lookup","minPoints":1,"maxPoints":3}]}""",
        )
        assertEquals(VocabularyOffer.Reason.Membership, offer.reason)
        assertEquals(3, offer.modes.single().maxPoints)
    }
}
