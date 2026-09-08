package app.tuji.android.core.study

import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.WordImageKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageChoicePairTest {

    private fun word(
        id: String,
        label: String = id,
        gloss: String = id,
        category: String = "bedroom",
        image: String? = "https://img.test/$id.webp",
        language: TargetLanguage? = TargetLanguage.JA,
    ) = Word(
        id = id, word = label, chinese = gloss, imageUrl = image,
        category = category, targetLanguage = language,
    )

    private fun item(id: String, label: String = id, gloss: String = id) = StudyQueueItem(
        card = StudyCard(id = "c-$id"),
        word = StudyQueueWord(
            id = id, word = label, chinese = gloss,
            imageUrl = "https://img.test/$id.webp",
            pronunciation = "", category = "bedroom",
            targetLanguage = TargetLanguage.JA,
        ),
    )

    private fun pick(
        item: StudyQueueItem = item("lamp"),
        pool: List<Word>,
        mentioned: Set<String> = emptySet(),
        queued: Set<String> = emptySet(),
        variant: Int = 0,
    ) = ImageChoicePair.options(
        item = item, pool = pool, session = TargetLanguage.JA,
        mentionedWordIds = mentioned, queuedWordIds = queued, variant = variant,
    )

    @Test fun `the answer and one distractor, both present`() {
        val out = pick(pool = listOf(word("lamp"), word("sofa", "ソファ", "沙發")))
        assertNotNull(out)
        assertEquals(2, out!!.size)
        assertTrue("the answer must be one of them", out.any { it.id == "lamp" })
        assertTrue(out.any { it.id == "sofa" })
    }

    @Test fun `a word the sentence also names is never the distractor`() {
        // The rule that stops both pictures being correct: the B1 sentence for
        // 空気清浄機 also names 窓, so a user who heard it perfectly could pick
        // the window and be marked wrong.
        val pool = listOf(word("air-purifier"), word("window", "窓", "窗戶"))
        val out = pick(
            item = item("air-purifier", "空気清浄機", "空氣清淨機"),
            pool = pool,
            mentioned = setOf("air-purifier", "window"),
        )
        assertNull("no fair distractor is left, so 選字 takes it", out)
    }

    @Test fun `a card still queued this session is not a free look`() {
        val pool = listOf(word("lamp"), word("sofa", "ソファ", "沙發"))
        assertNull(pick(pool = pool, queued = setOf("sofa")))
    }

    @Test fun `a photograph never stands beside a cutout`() {
        val custom = word("mine", "私の棚", "我的架子", category = "custom")
        assertNull("one cut-out and one photo is answerable without listening", pick(pool = listOf(word("lamp"), custom)))
        assertEquals(WordImageKind.Photograph, WordImageKind.of("custom"))
        assertEquals(WordImageKind.Photograph, WordImageKind.of("community"))
        assertEquals(WordImageKind.Cutout, WordImageKind.of("bedroom"))
    }

    @Test fun `an unfair label is unfair as a picture too`() {
        // DistractorPool already refuses 平底鍋/フライパン for labels; two
        // photographs of the same pan are no fairer.
        val pool = listOf(
            word("pan", "フライパン", "平底鍋"),
            word("frying-pan", "フライパン", "平底鍋"),
        )
        assertNull(pick(item = item("pan", "フライパン", "平底鍋"), pool = pool))
    }

    @Test fun `a word without a picture cannot be a picture option`() {
        val pool = listOf(word("lamp"), word("sofa", "ソファ", "沙發", image = null))
        assertNull(pick(pool = pool))
    }

    @Test fun `a card without a picture is not asked as 聽句`() {
        val blank = item("lamp").let { it.copy(word = it.word.copy(imageUrl = "")) }
        assertNull(pick(item = blank, pool = listOf(word("sofa", "ソファ", "沙發"))))
    }

    @Test fun `the other language's catalogue is not drawn from`() {
        val english = word("sofa", "sofa", "沙發", language = TargetLanguage.EN)
        assertNull(pick(pool = listOf(word("lamp"), english)))
    }

    @Test fun `an empty pool falls back rather than failing`() {
        assertNull(pick(pool = emptyList()))
    }

    @Test fun `the order does not move between two identical calls`() {
        val pool = (0 until 8).map { word("w$it", "語$it", "詞$it") }
        val a = pick(pool = pool)!!.map { it.id }
        val b = pick(pool = pool)!!.map { it.id }
        assertEquals("an unseeded shuffle would jump under the user's thumb", a, b)
    }

    @Test fun `a retest redraws instead of letting position stand in for the word`() {
        val pool = (0 until 12).map { word("w$it", "語$it", "詞$it") }
        // Across variants, not between two of them: with only two options a
        // single pair can repeat by luck, and asserting on one comparison
        // would fail for a reason that is not a regression.
        val draws = (0 until 6).map { v -> pick(pool = pool, variant = v)!!.map { it.id } }
        assertTrue("variant must change the draw", draws.toSet().size > 1)
        draws.forEach { assertTrue("the answer never drops out", "lamp" in it) }
    }

    @Test fun `the distractor carries its own image, not the answer's`() {
        val out = pick(pool = listOf(word("lamp"), word("sofa", "ソファ", "沙發")))!!
        val distractor = out.first { it.id == "sofa" }
        assertEquals("https://img.test/sofa.webp", distractor.imageUrl)
    }
}
