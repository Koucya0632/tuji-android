package app.tuji.android.core.study

import app.tuji.android.core.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class StudyChoicesTest {
    private fun word(label: String, gloss: String = "", tier: Int = 1, language: TargetLanguage = TargetLanguage.EN) =
        StudyChoiceCandidate(label, label, language, gloss, tier = tier, weight = 1.0)
    private val target = word("washbasin", "洗手台")
    private val candidates = listOf("spoon", "fork", "plate", "kettle", "towel", "mirror").map { word(it) }
    @Test fun `shared lexical contract`() {
        for (c in studyChoiceContractCases) {
            val a = word(c.a, c.ag); val b = word(c.b, c.bg)
            assertEquals(c.name, c.conflict, choicesConflict(a, b))
            assertEquals(c.name, c.conflict, choicesConflict(b, a))
        }
    }
    @Test fun `portable sampler matches web and iOS`() {
        val rng = ChoiceRandom(42)
        assertEquals(listOf(1083814273L, 378494188L, 2479403867L), (0..2).map { (rng.next() * 4294967296).toLong() })
        assertEquals(3207774618L, choiceHash("en:washbasin:0"))
        assertEquals(listOf("fork", "spoon", "kettle", "washbasin"), assembleStudyChoices(target, candidates, 42))
    }
    @Test fun `tiers and pairwise exclusions apply before drawing`() {
        val choices = assembleStudyChoices(target, listOf(word("spoon"), word("fork", tier = 2), word("kettle", tier = 3)), 42)
        assertEquals(setOf("washbasin", "spoon", "fork", "kettle"), choices.toSet())
        val input = listOf(word("wash basin", "臉盆"), word("sofa"), word("couch")) + (0..20).map { word("term$it") }
        val pool = prepareChoiceCandidates(target, input)
        assertEquals(12, pool.size)
        for ((i, a) in pool.withIndex()) {
            assertFalse(choicesConflict(target, a))
            for (b in pool.drop(i + 1)) assertFalse(choicesConflict(a, b))
        }
    }
    @Test fun `old cache decodes and candidate pool survives round trip`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<StudyQueueItem>("""{"card":{"id":"1"},"word":{"id":"washbasin","word":"washbasin","chinese":"洗手台","image_url":"","pronunciation":"","category":"bath","target_language":"en"},"choices":["wash basin","spoon","fork","washbasin"]}""")
        assertNull(old.choiceCandidates)
        val item = old.copy(choiceCandidates = candidates, choiceExclusions = listOf("wash basin"))
        assertEquals(item, json.decodeFromString<StudyQueueItem>(json.encodeToString(StudyQueueItem.serializer(), item)))
        val session = StudyChoiceSession(42)
        val first = session.choices(item, emptyList(), TargetLanguage.EN, 0)
        assertEquals(first, session.choices(old, emptyList(), TargetLanguage.EN, 0))
        val retry = session.choices(item, emptyList(), TargetLanguage.EN, 1)
        assertTrue(retry.count { it !in first } >= 2)
        assertFalse("wash basin" in retry)
    }
    @Test fun `bilingual reserves cover empty pools and custom words`() {
        for (target in StudyChoiceData.reserve + listOf(word("my object"), word("自分の物", language = TargetLanguage.JA))) {
            val result = assembleStudyChoices(target, emptyList(), 42)
            assertEquals(4, result.size); assertEquals(4, result.map(::choiceKey).toSet().size)
            for (label in result.filter { it != target.label }) assertTrue(StudyChoiceData.reserve.any { it.label == label && it.language == target.language })
        }
    }
    @Test fun `multiple seeds vary sets and answer positions`() {
        val sets = mutableSetOf<String>(); val positions = IntArray(4)
        for (i in 0..255) {
            val result = assembleStudyChoices(target, candidates, choiceHash("round:$i"))
            positions[result.indexOf(target.label)]++
            sets += result.filter { it != target.label }.sorted().joinToString("|")
        }
        assertTrue(sets.size > 10)
        assertTrue(positions.all { it > 30 && it < 100 })
    }
    @Test fun `live exclusions cannot be erased by reserve duplicates`() {
        val blocked = word("spoon", tier = 4).copy(exclusions = listOf("wash basin"))
        for (seed in 0L..31L) assertFalse("spoon" in assembleStudyChoices(target, listOf(blocked), seed))
    }
}
