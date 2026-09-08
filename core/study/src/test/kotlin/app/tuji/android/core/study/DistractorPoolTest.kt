package app.tuji.android.core.study

import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The four fairness rules, and the assembly around them.
 *
 * These are the rules the module exists for, and on iOS every one of them was
 * only assertable through a seeded shuffle, by absence — the most valuable
 * logic behind the least testable door. Here they are a returned value.
 */
class DistractorPoolTest {

    private fun word(term: String, gloss: String, lang: TargetLanguage = TargetLanguage.EN) = Word(
        id = term.replace(' ', '-'),
        word = term,
        chinese = gloss,
        targetLanguage = lang,
    )

    private fun item(
        term: String,
        gloss: String,
        choices: List<String>? = null,
        lang: TargetLanguage = TargetLanguage.EN,
    ) = StudyQueueItem(
        card = StudyCard(id = "c-$term"),
        word = StudyQueueWord(
            id = term.replace(' ', '-'), word = term, chinese = gloss,
            imageUrl = "https://img.test/x.webp", pronunciation = "—",
            targetLanguage = lang, category = "kitchen",
        ),
        choices = choices,
    )

    private fun pool(answer: String, gloss: String, others: List<Word>) =
        DistractorPool(answer = answer, gloss = gloss, pool = others)

    // The four rules

    @Test
    fun `a plain unrelated word is fair`() {
        val p = pool("pan", "平底鍋", listOf(word("spoon", "湯匙")))
        assertEquals(DistractorFairness.Fair, p.fairness("spoon"))
    }

    @Test
    fun `the answer itself is never a distractor`() {
        val p = pool("pan", "平底鍋", emptyList())
        assertEquals(DistractorFairness.SameTerm, p.fairness("pan"))
        assertEquals(DistractorFairness.SameTerm, p.fairness("PAN"))
    }

    @Test
    fun `one term's tokens containing the other's is unfair`() {
        // A learner who knows "knife" could legitimately pick "kitchen knife".
        val p = pool("knife", "刀", emptyList())
        assertEquals(DistractorFairness.TokenSubset, p.fairness("kitchen knife"))
        assertEquals(
            DistractorFairness.TokenSubset,
            pool("kitchen knife", "菜刀", emptyList()).fairness("knife"),
        )
    }

    @Test
    fun `a CJK term containing the other is unfair`() {
        // CJK has no token boundaries, so substring stands in.
        val p = pool("時計", "時鐘", emptyList())
        assertEquals(DistractorFairness.CjkSubstring, p.fairness("腕時計"))
    }

    @Test
    fun `sharing a chinese gloss is unfair`() {
        // Two terms with no words in common that the dictionary nonetheless
        // translates identically. The category-scoped server draw is exactly
        // what produces this pair.
        val p = pool("wok", "平底鍋", listOf(word("frying pan", "平底鍋")))
        assertEquals(DistractorFairness.SharedGloss, p.fairness("frying pan"))
    }

    @Test
    fun `the rules are ordered, and the first one to fire is the answer`() {
        // pan / frying pan is the pair the module's own doc names, and it is
        // caught by TokenSubset before the gloss rule is ever consulted. The
        // *outcome* is the same — unfair — but the reported reason is the
        // earlier rule, and a test that expected the gloss here would be
        // asserting an order the code does not have.
        val p = pool("pan", "平底鍋", listOf(word("frying pan", "平底鍋")))
        assertEquals(DistractorFairness.TokenSubset, p.fairness("frying pan"))
        assertNotEquals(DistractorFairness.Fair, p.fairness("frying pan"))
    }

    @Test
    fun `a shared gloss inside a packed list still counts`() {
        // The dictionary packs synonyms as 「鍋子 / 湯鍋」.
        val p = pool("pot", "鍋子 / 湯鍋", listOf(word("saucepan", "醬汁鍋／湯鍋")))
        assertEquals(DistractorFairness.SharedGloss, p.fairness("saucepan"))
    }

    @Test
    fun `an answer with no gloss falls back to the other rules only`() {
        val p = pool("pan", "", listOf(word("frying pan", "平底鍋")))
        // TokenSubset still fires; the gloss rule cannot.
        assertEquals(DistractorFairness.TokenSubset, p.fairness("frying pan"))
        assertEquals(DistractorFairness.Fair, p.fairness("spoon"))
    }

    // Assembly

    @Test
    fun `the unfair server distractor is scrubbed and the set topped up`() {
        val choices = studyChoices(
            item = item("pan", "平底鍋", choices = listOf("frying pan", "spoon", "ladle")),
            pool = listOf(
                word("frying pan", "平底鍋"),
                word("spoon", "湯匙"),
                word("ladle", "杓子"),
                word("whisk", "打蛋器"),
                word("sieve", "篩子"),
            ),
            session = TargetLanguage.EN,
        )
        assertEquals(4, choices.size)
        assertTrue("the answer is always there", "pan" in choices)
        assertFalse("both translate to 平底鍋", "frying pan" in choices)
        assertEquals("no duplicates", choices.size, choices.toSet().size)
    }

    @Test
    fun `the order is stable for the same variant and moves with it`() {
        val subject = item("pan", "平底鍋", choices = listOf("spoon", "ladle", "whisk"))
        val p = listOf(word("spoon", "湯匙"), word("ladle", "杓子"), word("whisk", "打蛋器"))

        val a = studyChoices(subject, p, TargetLanguage.EN, variant = 0)
        val b = studyChoices(subject, p, TargetLanguage.EN, variant = 0)
        assertEquals("a redraw must not move the options", a, b)

        // A requeued question re-shuffles, or remembering "the answer was C"
        // stands in for knowing the word.
        val moved = (0..5).map { studyChoices(subject, p, TargetLanguage.EN, variant = it) }
        assertTrue("some variant must differ", moved.any { it != a })
    }

    @Test
    fun `a custom card with no server choices is built entirely from the pool`() {
        val choices = studyChoices(
            item = item("我的杯子", "杯子", choices = null, lang = TargetLanguage.JA),
            pool = listOf(
                word("湯呑み", "茶杯", TargetLanguage.JA),
                word("急須", "茶壺", TargetLanguage.JA),
                word("箸", "筷子", TargetLanguage.JA),
                word("spoon", "湯匙", TargetLanguage.EN),
            ),
            session = TargetLanguage.JA,
        )
        assertEquals(4, choices.size)
        assertTrue("我的杯子" in choices)
        assertFalse("the top-up filters by language first", "spoon" in choices)
    }

    @Test
    fun `a thin same-language pool widens rather than leaving the quiz short`() {
        val choices = studyChoices(
            item = item("箸", "筷子", choices = null, lang = TargetLanguage.JA),
            pool = listOf(
                word("spoon", "湯匙"),
                word("fork", "叉子"),
                word("plate", "盤子"),
            ),
            session = TargetLanguage.JA,
        )
        // A brand-new account has no Japanese pool yet. Plausible-ish beats
        // a one-option quiz.
        assertEquals(4, choices.size)
        assertTrue("箸" in choices)
    }

    // The seeded source

    @Test
    fun `the stable hash does not move between runs`() {
        // Not a platform hash: anything derived from it would change between
        // launches, so "the answer was the third one" would sometimes hold.
        assertEquals(studyStableHash("bath-mat"), studyStableHash("bath-mat"))
        assertNotEquals(studyStableHash("bath-mat"), studyStableHash("bath-ladle"))
    }

    @Test
    fun `the same seed gives the same shuffle`() {
        val input = (1..12).toList()
        assertEquals(
            input.shuffled(SeededRandom(42)),
            input.shuffled(SeededRandom(42)),
        )
        assertNotEquals(
            input.shuffled(SeededRandom(42)),
            input.shuffled(SeededRandom(43)),
        )
    }

    @Test
    fun `the shuffle is a permutation, not a sample`() {
        val input = (1..50).toList()
        assertEquals(input.toSet(), input.shuffled(SeededRandom(7)).toSet())
        assertEquals(input.size, input.shuffled(SeededRandom(7)).size)
    }
}
