package app.tuji.android.core.study

import app.tuji.android.core.model.StudyQueueItem
import kotlin.math.abs

/**
 * 挖空拼字 — the English production step of 學新字.
 *
 * The word is shown almost whole; a few confusable chunks are cut out of it and
 * offered back as one shuffled pool. It replaces the from-scratch tile board
 * for English because re-assembling every letter quizzes "do you remember each
 * character", while English spelling actually goes wrong in a handful of
 * places: the r-controlled vowels (er/ar/or/ur/ir), the vowel teams
 * (ai/ay/ei/ey), the suffix families (-tion/-sion, -able/-ible, -ary/-ery) and
 * the doubled consonants. Cutting exactly those puts the attention where the
 * mistakes are, and a wrong answer shows the learner **which** chunk they got
 * wrong.
 *
 * Japanese keeps the tile board: its 拼字 asks for a kana reading, which has no
 * orthographic confusables to cut. [SpellForm] is where the two meet.
 *
 * Ported from `Tuji/Core/Study/SpellGaps.swift`. The tables are the ones tuned
 * against the real corpus over there, copied rather than re-derived — a second
 * derivation is a second chance to disagree about what `bana[n]a` should do.
 *
 * Ranges are half-open, as they are in Swift: [Gap.end] is one past the last
 * cut character. Kotlin's own `IntRange` is closed, so the two are kept apart
 * by name rather than by a convention someone has to remember.
 */
data class SpellGaps(
    /** The whole word, whitespace intact. */
    val term: String,
    /**
     * The visible text around the gaps: `segments.size == gaps.size + 1`,
     * interleaved segment/gap/segment/… Re-joining them rebuilds [term].
     */
    val segments: List<String>,
    val gaps: List<Gap>,
    /**
     * Every answer plus distractors, in canonical order. The *displayed* order
     * is [options], which shuffles this.
     */
    val options: List<String>,
) {
    /** One cut-out chunk, in the order it appears in the term. */
    data class Gap(val answer: String, val start: Int, val end: Int) {
        val length: Int get() = end - start
    }

    val answers: List<String> get() = gaps.map { it.answer }

    companion object {
        // The confusable table

        /**
         * Vowel teams and suffix families — the errors learners actually make,
         * so they outrank everything else when choosing what to cut.
         */
        private val VOWEL_FAMILIES: List<List<String>> = listOf(
            listOf("tion", "sion", "cian"),
            listOf("cial", "tial", "sial"),
            listOf("ence", "ance"),
            listOf("able", "ible"),
            listOf("ough", "augh"),
            listOf("ture", "sure"),
            listOf("ary", "ery", "ory"),
            listOf("ous", "ious", "eous"),
            listOf("ent", "ant"),
            listOf("ette", "et"),
            listOf("igh", "ie"),
            listOf("ai", "ay", "ei", "ey", "ea"),
            listOf("ee", "ea", "ie", "ei"),
            listOf("oo", "ou", "ue", "ew"),
            listOf("ow", "ou", "au", "aw"),
            listOf("oi", "oy", "oe"),
            listOf("er", "ar", "or", "ur", "ir"),
            listOf("le", "el", "al", "il"),
            listOf("ate", "ite", "ete"),
            listOf("ive", "ife", "ave"),
            listOf("age", "idge"),
        )

        /**
         * Multi-letter consonant ambiguities, including the doubles.
         *
         * Every member here is two letters or more on purpose. Allowing a
         * *single* consonant to be cut produces questions like `bana[n]a` (n/nn)
         * and `cho[c]olate` (c/ck/k/que) — measured against the corpus, and both
         * are worthless: the answer is obvious and the word reads as mangled.
         */
        private val CONSONANT_FAMILIES: List<List<String>> = listOf(
            listOf("tch", "ch", "sh"),
            listOf("sh", "ch", "tch"),
            listOf("dge", "ge"),
            listOf("ck", "k", "que"),
            listOf("ph", "f", "gh"),
            listOf("ce", "se", "ze"),
            listOf("qu", "kw"),
            listOf("ll", "l"), listOf("ss", "s"), listOf("tt", "t"), listOf("pp", "p"), listOf("rr", "r"),
            listOf("mm", "m"), listOf("nn", "n"), listOf("ff", "f"), listOf("cc", "c"), listOf("dd", "d"),
            listOf("gg", "g"), listOf("zz", "z"),
        )

        private val VOWELS = "aeiou".toSet()

        /**
         * Same-length filler when a family is too small to fill the pool. Never
         * a correct answer — the caller drops anything already in the pool.
         */
        private val GENERIC_CHUNKS: Map<Int, List<String>> = mapOf(
            1 to listOf("a", "e", "i", "o", "u"),
            2 to listOf("er", "ar", "or", "ur", "ir", "ee", "ea", "ai", "oo", "ou", "le", "el", "ck", "ll", "ss"),
            3 to listOf("ary", "ery", "ory", "ent", "ant", "ous", "ate", "ive", "age", "ice"),
            4 to listOf("tion", "sion", "able", "ible", "ence", "ance", "ture", "ough"),
        )

        /**
         * How many chunks a word of this length is worth cutting. The actual
         * count can come out lower — a word only has so many confusable places.
         */
        private fun targetGapCount(letters: Int): Int = when {
            letters <= 5 -> 1
            letters <= 9 -> 2
            else -> 3
        }

        /**
         * Never blank away more than this share of the word, or it stops being a
         * gap-fill and becomes the tile board with extra steps.
         */
        private const val MAX_BLANKED_SHARE = 0.55

        /** Distractors on top of the answers: 1 gap → 5 options, 2 → 6, 3 → 7. */
        private const val DISTRACTOR_COUNT = 4
        private const val MAX_OPTIONS = 8

        // Placement

        private data class Candidate(
            val start: Int,
            val end: Int,
            val answer: String,
            val family: List<String>,
            val tier: Int,
            val rank: Int,
        ) {
            val length: Int get() = end - start
        }

        /**
         * Build the gaps for a term, or null when it cannot carry this question
         * (an all-caps acronym, no vowel, fewer than three letters).
         */
        fun of(term: String): SpellGaps? {
            val chars = term.toCharArray()
            val letters = chars.count { !it.isWhitespace() }
            if (letters < 3 || chars.none { it.isLowerCase() }) return null

            val chosen = chooseFamilyGaps(chars, letters).toMutableList()
            if (chosen.size < targetGapCount(letters)) {
                // Top up with at most one bare vowel. Letting vowels fill freely
                // was measured and rejected: it turns `dishwasher` into
                // `d ___ shw ___ sh ___`, which no longer reads as a word.
                vowelCandidates(chars, from = 2)
                    .firstOrNull { fits(it, chosen, letters) }
                    ?.let { chosen.add(it) }
            }
            if (chosen.isEmpty()) {
                // Last resort so no English word is left without a question.
                // Short words land here, and a/e/i/o/u are honest distractors
                // for them (beg / big / bog / bug are all real words).
                val vowel = vowelCandidates(chars, from = 1).firstOrNull() ?: return null
                chosen.add(vowel)
            }
            chosen.sortBy { it.start }
            return assemble(chars, term, chosen)
        }

        /**
         * Greedy fill from the confusable table, re-ranking after each pick so
         * the length-match preference sees what has already been taken.
         */
        private fun chooseFamilyGaps(chars: CharArray, letters: Int): List<Candidate> {
            val all = familyCandidates(chars, letters)
            val want = targetGapCount(letters)
            val middle = chars.size / 2.0
            val chosen = mutableListOf<Candidate>()
            while (chosen.size < want) {
                val lengths = chosen.map { it.length }.toSet()
                val best = all
                    .filter { fits(it, chosen, letters) }
                    .minWithOrNull(compareBy(
                        // ① a gap the same length as the ones already taken — a
                        //    mixed-length pool hints which option belongs where
                        { candidate: Candidate -> if (lengths.isEmpty() || candidate.length in lengths) 0 else 1 },
                        // ② vowel families before consonant ones, ③ table order
                        { it.tier },
                        { it.rank },
                        // ④ nearest the middle of the word — without this every
                        //    gap piles up at the end (`refrigerat ___`)
                        { abs((it.start + it.end) / 2.0 - middle) },
                        // ⑤ position, so the order is total and the result is
                        //    reproducible across runs
                        { it.start },
                    )) ?: break
                chosen.add(best)
            }
            return chosen
        }

        /**
         * Can this candidate join the ones already chosen?
         *
         * Gaps may not touch: at least one visible letter has to survive between
         * them, or two adjacent blanks read as one wide one. Two gaps may not
         * want the same answer either — the pool would show the option twice.
         */
        private fun fits(candidate: Candidate, chosen: List<Candidate>, letters: Int): Boolean {
            val blanked = chosen.fold(candidate.length) { sum, taken -> sum + taken.length }
            if (blanked > letters * MAX_BLANKED_SHARE) return false
            for (taken in chosen) {
                if (candidate.answer == taken.answer) return false
                val clear = candidate.start > taken.end || candidate.end < taken.start
                if (!clear) return false
            }
            return true
        }

        private fun familyCandidates(chars: CharArray, letters: Int): List<Candidate> {
            val out = mutableListOf<Candidate>()
            listOf(VOWEL_FAMILIES, CONSONANT_FAMILIES).forEachIndexed { tier, table ->
                table.forEachIndexed { rank, family ->
                    family.filter { it.length >= 2 }.forEach { member ->
                        matches(member, chars, letters).forEach { (start, end) ->
                            out.add(Candidate(start, end, member, family, tier, rank))
                        }
                    }
                }
            }
            return out
        }

        /**
         * Exact-case occurrences of [member], minus the positions the placement
         * rules forbid. Matching on the original case rather than a lowercased
         * copy keeps `Aquarius`-style capitals out of the answer, so an option
         * always prints exactly as the family declares it.
         */
        private fun matches(member: String, chars: CharArray, letters: Int): List<Pair<Int, Int>> {
            val needle = member.toCharArray()
            if (needle.size >= letters || chars.size <= needle.size) return emptyList()
            val out = mutableListOf<Pair<Int, Int>>()
            // Never start at 0: the opening letters are what makes the word
            // recognisable with a hole in it.
            for (start in 1..(chars.size - needle.size)) {
                val end = start + needle.size
                var ok = true
                for (i in start until end) {
                    if (chars[i].isWhitespace() || chars[i] != needle[i - start]) {
                        ok = false
                        break
                    }
                }
                if (ok) out.add(start to end)
            }
            return out
        }

        /**
         * Single-vowel candidates in reading order. [from] is the earliest index
         * allowed: 2 when topping up beside real gaps, 1 for the last-resort
         * solo gap on a short word.
         *
         * (iOS's doc comment here says "latest first"; its loop appends
         * ascending and both callers take `.first`, so the behaviour is
         * earliest-first. The behaviour is what is ported.)
         */
        private fun vowelCandidates(chars: CharArray, from: Int): List<Candidate> {
            if (chars.size <= from + 1) return emptyList()
            val out = mutableListOf<Candidate>()
            for (index in from until chars.size - 1) {
                if (chars[index] !in VOWELS) continue
                if (chars[index - 1].isWhitespace() || chars[index + 1].isWhitespace()) continue
                out.add(
                    Candidate(
                        start = index,
                        end = index + 1,
                        answer = chars[index].toString(),
                        family = VOWELS.sorted().map { it.toString() },
                        tier = 2,
                        rank = 0,
                    ),
                )
            }
            return out
        }

        private fun assemble(chars: CharArray, term: String, chosen: List<Candidate>): SpellGaps {
            val segments = mutableListOf<String>()
            var cursor = 0
            for (candidate in chosen) {
                segments.add(String(chars, cursor, candidate.start - cursor))
                cursor = candidate.end
            }
            segments.add(String(chars, cursor, chars.size - cursor))
            return SpellGaps(
                term = term,
                segments = segments,
                gaps = chosen.map { Gap(it.answer, it.start, it.end) },
                options = buildOptions(chosen),
            )
        }

        /**
         * Answers first, then distractors drawn round-robin from each gap's own
         * family so every gap contributes a look-alike, topped up from the
         * same-length generic pool when the families run dry.
         */
        private fun buildOptions(chosen: List<Candidate>): List<String> {
            val options = chosen.map { it.answer }.toMutableList()
            val target = minOf(options.size + DISTRACTOR_COUNT, MAX_OPTIONS)

            val queues = chosen.map { candidate ->
                candidate.family.filter { it != candidate.answer }.toMutableList()
            }
            var drained = false
            while (options.size < target && !drained) {
                drained = true
                for (queue in queues) {
                    if (options.size >= target) break
                    if (queue.isEmpty()) continue
                    drained = false
                    val option = queue.removeAt(0)
                    if (option in options) continue
                    options.add(option)
                }
            }

            for (candidate in chosen) {
                if (options.size >= target) break
                for (filler in GENERIC_CHUNKS[candidate.length].orEmpty()) {
                    if (options.size >= target) break
                    if (filler in options) continue
                    options.add(filler)
                }
            }
            return options
        }

        // Display order

        /**
         * The pool as the view draws it — deterministic per (item, attempt) so
         * re-renders do not reshuffle mid-task, but a retry gets a new order.
         * The gaps themselves never move between attempts: the chunk they got
         * wrong is the one worth asking again.
         */
        fun options(item: StudyQueueItem, attempt: Int): List<String> {
            val gaps = of(TileBoard.spellSubject(item).text) ?: return emptyList()
            return gaps.options.shuffled(SeededRandom(studyStableHash("${item.id}#gap#$attempt")))
        }
    }
}
