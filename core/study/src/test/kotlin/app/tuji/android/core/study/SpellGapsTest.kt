package app.tuji.android.core.study

import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 挖空拼字 — the placement contract.
 *
 * These are the rules that were tuned against the real corpus (530 English
 * headwords) on iOS before the feature was written, and the ones a change is
 * most likely to break silently: a cut that starts at the first letter, two
 * cuts with nothing left between them, or a family loose enough to ask
 * `bana[n]a`. **The board looks fine in all three cases; only the question is
 * ruined** — which is why these are asserted rather than eyeballed.
 *
 * Ported alongside `TujiTests/SpellGapsTests.swift`, same corpus and same
 * expectations, so a divergence between the platforms shows up here rather
 * than on someone's screen.
 */
class SpellGapsTest {

    /**
     * A spread of shapes: short, long, multi-token, suffix-heavy, doubled
     * consonants, and the ones that fall through to a bare vowel.
     */
    private val corpus = listOf(
        "apple", "preservative", "refrigerator", "umbrella", "coffee",
        "chocolate", "necessary", "air conditioner", "toothbrush", "dishwasher",
        "bag", "oven", "comfortable", "television", "restaurant",
        "cutting board", "banana", "bus", "washing machine", "bathtub",
    )

    private fun plan(term: String): SpellGaps =
        requireNotNull(SpellGaps.of(term)) { "$term should carry a gap-fill" }

    private fun item(term: String, reading: String? = null) = StudyQueueItem(
        card = StudyCard(id = "c-$term"),
        word = StudyQueueWord(
            id = "w-$term", word = term, chinese = "—",
            imageUrl = "https://img.test/w.webp", pronunciation = "—",
            reading = reading, category = "misc",
        ),
    )

    // Invariants

    @Test fun `the visible text and the answers rebuild the word`() {
        for (term in corpus) {
            val plan = plan(term)
            assertEquals(term, plan.term)
            assertEquals(plan.gaps.size + 1, plan.segments.size)
            val rebuilt = buildString {
                plan.segments.forEachIndexed { index, segment ->
                    append(segment)
                    if (index < plan.gaps.size) append(plan.gaps[index].answer)
                }
            }
            assertEquals("$term does not rebuild from its own pieces", term, rebuilt)
        }
    }

    @Test fun `every answer is in the pool and the pool repeats nothing`() {
        for (term in corpus) {
            val plan = plan(term)
            for (answer in plan.answers) {
                assertTrue("$term: $answer missing from the pool", answer in plan.options)
            }
            assertEquals("$term: duplicate option", plan.options.size, plan.options.toSet().size)
            // Two holes wanting the same chunk would print the option twice and
            // read as a mistake in the pool.
            assertEquals("$term: duplicate answer", plan.answers.size, plan.answers.toSet().size)
        }
    }

    @Test fun `a gap never eats the opening the whole word or a space`() {
        for (term in corpus) {
            val plan = plan(term)
            val letters = term.count { !it.isWhitespace() }
            for (gap in plan.gaps) {
                assertTrue("$term: a gap starts at the first letter", gap.start > 0)
                assertTrue("$term: the gap is the whole word", gap.length < letters)
                val spansASpace = (gap.start until gap.end).any { term[it].isWhitespace() }
                assertTrue("$term: a gap spans a space", !spansASpace)
            }
            val blanked = plan.gaps.sumOf { it.length }
            assertTrue("$term: too much of the word is gone", blanked <= letters * 0.55)
        }
    }

    @Test fun `gaps never touch`() {
        for (term in corpus) {
            val plan = plan(term)
            plan.gaps.zipWithNext { left, right ->
                // Adjacent holes render as one wide hole, so at least one
                // visible letter has to survive between them.
                assertTrue("$term: two gaps run together", right.start > left.end)
            }
            assertTrue(plan.segments.drop(1).dropLast(1).all { it.isNotEmpty() })
        }
    }

    // How many holes

    @Test fun `the word length sets the target and a shortage lowers it`() {
        // ≤5 letters → 1, 6–9 → 2, ≥10 → 3. umbrella is 8 letters so it asks
        // for 2 — but only one place in it is worth cutting, and a shortage
        // lowers the count rather than inventing a bad hole.
        val counts = listOf("bag", "apple", "comfortable", "refrigerator", "umbrella")
            .map { plan(it).gaps.size }
        assertEquals(listOf(1, 1, 2, 3, 1), counts)
    }

    @Test fun `at most one hole is a bare vowel`() {
        for (term in corpus) {
            val singles = plan(term).gaps.filter { it.answer.length == 1 }
            assertTrue("$term: more than one bare-vowel hole", singles.size <= 1)
        }
    }

    // Which chunk, and what it is asked against

    @Test fun `a confusable chunk is preferred and brings its own family`() {
        // The r-controlled vowels are the classic English spelling error and
        // the shape the whole feature was built around.
        val preservative = plan("preservative")
        assertTrue("er" in preservative.answers)
        // Three holes share one capped pool, so each family contributes a
        // look-alike rather than all of its members.
        assertTrue(listOf("ar", "or", "ur", "ir").any { it in preservative.options })

        // A word with a single hole does get the whole family to choose from —
        // that is the five-option board the design started from.
        val apple = plan("apple")
        assertEquals(listOf("le"), apple.answers)
        assertTrue(listOf("el", "al", "il").all { it in apple.options })

        // A doubled consonant is cut as the pair, never as one letter — a bare
        // `bana[n]a` asking n/nn is the question this rule exists to prevent.
        assertEquals(listOf("el"), plan("umbrella").answers)

        // Suffix families are worth cutting even at the very end of the word.
        assertTrue("sion" in plan("television").answers)
        assertTrue("able" in plan("comfortable").answers)
    }

    @Test fun `a word with no confusable chunk still gets a vowel`() {
        val bag = plan("bag")
        assertEquals(listOf("a"), bag.answers)
        // beg / big / bog / bug are all real words, so these are honest
        // distractors rather than filler.
        assertEquals(listOf("a", "e", "i", "o", "u"), bag.options.sorted())
    }

    @Test fun `the option count follows the hole count`() {
        // answers + 4 distractors, capped at 8.
        for (term in corpus) {
            val plan = plan(term)
            assertEquals(term, minOf(plan.gaps.size + 4, 8), plan.options.size)
        }
    }

    // What cannot be asked this way

    @Test fun `acronyms and vowelless words fall through`() {
        // Nothing in these can be cut without the prompt becoming a riddle;
        // SpellForm sends them to the tile board instead.
        for (term in listOf("MRT", "MSG", "TV", "thyme", "ox")) {
            assertNull("$term should not get a gap-fill", SpellGaps.of(term))
        }
    }

    // Stability across renders and retries

    @Test fun `placement is pure and repeatable`() {
        for (term in corpus) {
            assertEquals(term, SpellGaps.of(term), SpellGaps.of(term))
        }
    }

    @Test fun `a retry reshuffles the pool and leaves the holes where they were`() {
        val item = item("preservative")
        val first = SpellGaps.options(item, attempt = 0)
        val second = SpellGaps.options(item, attempt = 1)

        assertTrue(first.isNotEmpty())
        assertEquals(first.toSet(), second.toSet())
        assertNotEquals("staring at the same pool again is not a retry", first, second)
        // Same order for the same attempt, so a recomposition does not
        // reshuffle the pool under the user's finger.
        assertEquals(first, SpellGaps.options(item, attempt = 0))
        // The gaps themselves never move: the chunk they got wrong is the one
        // worth asking again.
        assertEquals(SpellGaps.of("preservative")!!.gaps, SpellGaps.of("preservative")!!.gaps)
    }

    // Which board a word takes

    @Test fun `english takes the gaps and a kana reading takes the tiles`() {
        assertTrue(
            "an English word should take the gap board",
            SpellForm.of(item("preservative")) is SpellForm.Gaps,
        )
        // 林檎 is quizzed on its kana reading — no orthographic confusables to
        // cut, so it keeps the whole-string tile board.
        assertTrue(
            "a kana reading should take the tile board",
            SpellForm.of(item("林檎", reading = "りんご")) is SpellForm.Tiles,
        )
        // ねこ is a Term too, but it is not Latin script.
        assertTrue(
            "a kana term should take the tile board",
            SpellForm.of(item("ねこ", reading = "ねこ")) is SpellForm.Tiles,
        )
        // One unit left to arrange is no question at all — the ladder has
        // always skipped these, and the gate reads the same predicate.
        assertNull(SpellForm.of(item("め", reading = "め")))
    }

    @Test fun `the pool is longer than the slots on a gap-fill and exactly as long on tiles`() {
        // Why "is the board full" has to ask the form rather than count the
        // pool: a gap-fill's pool carries distractors that fit in no slot.
        val gaps = SpellForm.of(item("preservative")) as SpellForm.Gaps
        assertEquals(gaps.plan.gaps.size, gaps.slotCount)
        assertTrue(gaps.plan.options.size > gaps.slotCount)

        val tiles = SpellForm.of(item("ねこ", reading = "ねこ")) as SpellForm.Tiles
        assertEquals(tiles.board.unitCount, tiles.slotCount)
    }
}
