package app.tuji.android.core.study

import app.tuji.android.core.model.StudyChoiceCandidate
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.Word
import app.tuji.android.core.model.language
import java.text.Normalizer
import java.util.Locale

fun choiceKey(label: String): String = Normalizer.normalize(label, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).filterNot { it.isWhitespace() || Character.getType(it) == Character.DASH_PUNCTUATION.toInt() }

private fun choiceTokens(label: String): Set<String> = Normalizer.normalize(label, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotEmpty() }.toSet()
private fun choiceGlosses(gloss: String): Set<String> = Normalizer.normalize(gloss, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).split(*"/、,，;；".toCharArray()).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
private val choiceAliases = StudyChoiceData.aliases.map { group -> group.map(::choiceKey).toSet() }
fun choiceAliasesConflict(a: String, b: String): Boolean = choiceAliases.any { choiceKey(a) in it && choiceKey(b) in it }

fun choicesConflict(a: StudyChoiceCandidate, b: StudyChoiceCandidate): Boolean {
    val ak = choiceKey(a.label); val bk = choiceKey(b.label)
    if (ak.isEmpty() || bk.isEmpty() || ak == bk) return true
    if (a.wordId.isNotEmpty() && a.wordId == b.wordId) return true
    if (choiceAliasesConflict(a.label, b.label)) return true
    if (a.exclusions.any { choiceKey(it) == bk } || b.exclusions.any { choiceKey(it) == ak }) return true
    val at = choiceTokens(a.label); val bt = choiceTokens(b.label)
    if (at.isNotEmpty() && bt.isNotEmpty() && (at.containsAll(bt) || bt.containsAll(at))) return true
    if ((ak + bk).any { it.code in 0x3040..0x30FF || it.code in 0x4E00..0x9FFF } && (ak.contains(bk) || bk.contains(ak))) return true
    return choiceGlosses(a.gloss).any { it in choiceGlosses(b.gloss) }
}

private val choiceOrder = compareBy<StudyChoiceCandidate> { it.tier }.thenByDescending { it.weight }.thenBy { choiceKey(it.label) }

fun prepareChoiceCandidates(target: StudyChoiceCandidate, input: List<StudyChoiceCandidate>): List<StudyChoiceCandidate> {
    val merged = linkedMapOf<String, StudyChoiceCandidate>()
    for (c in input) {
        val key = "${c.language}:${choiceKey(c.label)}"
        val old = merged[key]
        if (old == null) { merged[key] = c; continue }
        val preferred = if (choiceOrder.compare(old, c) <= 0) old else c
        merged[key] = preferred.copy(gloss = listOf(old.gloss, c.gloss).filter { it.isNotEmpty() }.joinToString(" / "),
            exclusions = (old.exclusions + c.exclusions).distinct())
    }
    val result = mutableListOf<StudyChoiceCandidate>()
    val counts = mutableMapOf<Int, Int>()
    for (c in merged.values.sortedWith(choiceOrder)) {
        if (c.language != target.language || !c.weight.isFinite() || c.weight <= 0 || c.tier !in 1..4 ||
            (counts[c.tier] ?: 0) >= 12 || choicesConflict(target, c) || result.any { choicesConflict(it, c) }) continue
        result += c
        counts[c.tier] = (counts[c.tier] ?: 0) + 1
    }
    return result
}

class ChoiceRandom(seed: Long) {
    private var state = seed and 0xffffffffL
    fun next(): Double { state = (state * 1664525 + 1013904223) and 0xffffffffL; return state.toDouble() / 4294967296.0 }
}
fun choiceHash(text: String): Long {
    var hash = 2166136261L
    for (b in text.toByteArray(Charsets.UTF_8)) hash = ((hash xor (b.toLong() and 255)) * 16777619) and 0xffffffffL
    return hash
}

fun assembleStudyChoices(target: StudyChoiceCandidate, candidates: List<StudyChoiceCandidate>, seed: Long, previous: List<String> = emptyList()): List<String> {
    val pool = prepareChoiceCandidates(target, candidates + StudyChoiceData.reserve)
    val random = ChoiceRandom(seed)
    val picked = mutableListOf<StudyChoiceCandidate>()
    val old = previous.filter { choiceKey(it) != choiceKey(target.label) }.map(::choiceKey).toSet()
    fun draw(limit: Int, freshOnly: Boolean) {
        for (tier in 1..4) {
            val available = pool.filter { it.tier == tier && it !in picked && (!freshOnly || choiceKey(it.label) !in old) }.toMutableList()
            while (picked.size < limit && available.isNotEmpty()) {
                var ticket = random.next() * available.sumOf { it.weight }
                var i = 0
                while (i < available.size - 1) {
                    ticket -= available[i].weight
                    if (ticket < 0) break
                    i++
                }
                picked += available.removeAt(i)
            }
        }
    }
    if (old.isNotEmpty()) draw(2, true)
    draw(3, false)
    check(picked.size == 3) { "Insufficient fair study choices: ${target.wordId}" }
    val result = (listOf(target.label) + picked.map { it.label }).toMutableList()
    for (i in result.lastIndex downTo 1) {
        val j = (random.next() * (i + 1)).toInt()
        val temp = result[i]; result[i] = result[j]; result[j] = temp
    }
    return result
}

/** One instance per round. A catalogue refresh cannot change an open question. */
class StudyChoiceSession(private val seed: Long = kotlin.random.Random.nextLong(0, 0x100000000L)) {
    private val snapshots = mutableMapOf<String, List<String>>()
    private val previous = mutableMapOf<String, List<String>>()
    fun choices(item: StudyQueueItem, pool: List<Word>, session: TargetLanguage, variant: Int): List<String> {
        val wordKey = "${item.word.language(session).name.lowercase(Locale.ROOT)}:${item.id}"
        val key = "$wordKey:$variant"
        snapshots[key]?.let { return it }
        val result = studyChoices(item, pool, session, variant, (seed + choiceHash(key)) and 0xffffffffL, previous[wordKey].orEmpty())
        snapshots[key] = result
        previous[wordKey] = result
        return result
    }
}
