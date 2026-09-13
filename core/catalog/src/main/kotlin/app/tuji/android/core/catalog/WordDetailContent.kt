package app.tuji.android.core.catalog

import app.tuji.android.core.model.WordDetail

/** The 字詞資料 card's pills. */
enum class WordDetailTab { Definition, Forms, Origin, Collocations }

/**
 * What 單字詳情 has to show for one entry — iOS's `WordDetailSections`, the
 * parts of it that are decisions rather than drawing.
 */
object WordDetailContent {

    /**
     * The pills, in iOS's order, each only when it has something behind it.
     *
     * 譯義 is there whenever the definition card would draw anything: a
     * definition in the language being learned, or — with 中文 shown — the
     * gloss or the 中文 釋義. A 自製 card with only Chinese content still shows
     * its meaning rather than collapsing to a headword.
     */
    fun tabs(word: WordDetail, showChinese: Boolean): List<WordDetailTab> = buildList {
        if (definitionHasContent(word, showChinese)) add(WordDetailTab.Definition)
        if (word.forms.isNotEmpty()) add(WordDetailTab.Forms)
        if (!word.etymology.isNullOrBlank()) add(WordDetailTab.Origin)
        if (word.collocations.isNotEmpty()) add(WordDetailTab.Collocations)
    }

    private fun definitionHasContent(word: WordDetail, showChinese: Boolean): Boolean {
        if (!word.targetDefinition.isNullOrBlank()) return true
        if (!showChinese) return false
        return !word.chineseDefinition.isNullOrBlank() || !word.chinese.isNullOrBlank()
    }

    /** The example sentences the page draws: at most three, as on iOS. */
    const val MAX_EXAMPLES = 3

    private val labels: Map<String, Map<String, String>> = mapOf(
        "noun" to mapOf("zh-Hant" to "名詞", "zh-Hans" to "名词", "ja" to "名詞", "en" to "noun"),
        "verb" to mapOf("zh-Hant" to "動詞", "zh-Hans" to "动词", "ja" to "動詞", "en" to "verb"),
        "phrasal verb" to mapOf("zh-Hant" to "片語動詞", "zh-Hans" to "短语动词", "ja" to "句動詞", "en" to "phrasal verb"),
        "adjective" to mapOf("zh-Hant" to "形容詞", "zh-Hans" to "形容词", "ja" to "形容詞", "en" to "adjective"),
        "adverb" to mapOf("zh-Hant" to "副詞", "zh-Hans" to "副词", "ja" to "副詞", "en" to "adverb"),
        "pronoun" to mapOf("zh-Hant" to "代名詞", "zh-Hans" to "代词", "ja" to "代名詞", "en" to "pronoun"),
        "preposition" to mapOf("zh-Hant" to "介系詞", "zh-Hans" to "介词", "ja" to "前置詞", "en" to "preposition"),
        "conjunction" to mapOf("zh-Hant" to "連接詞", "zh-Hans" to "连词", "ja" to "接続詞", "en" to "conjunction"),
        "interjection" to mapOf("zh-Hant" to "感嘆詞", "zh-Hans" to "感叹词", "ja" to "間投詞", "en" to "interjection"),
        "determiner" to mapOf("zh-Hant" to "限定詞", "zh-Hans" to "限定词", "ja" to "限定詞", "en" to "determiner"),
        "numeral" to mapOf("zh-Hant" to "數詞", "zh-Hans" to "数词", "ja" to "数詞", "en" to "numeral"),
        "phrase" to mapOf("zh-Hant" to "片語", "zh-Hans" to "短语", "ja" to "句", "en" to "phrase"),
        "expression" to mapOf("zh-Hant" to "慣用語", "zh-Hans" to "惯用语", "ja" to "表現", "en" to "expression"),
    )

    private val aliases: Map<String, String> = mapOf(
        "n." to "noun", "n" to "noun",
        "v." to "verb", "v" to "verb",
        "adj." to "adjective", "adj" to "adjective",
        "adv." to "adverb", "adv" to "adverb",
        "prep." to "preposition", "prep" to "preposition",
        "conj." to "conjunction", "conj" to "conjunction",
        "pron." to "pronoun", "pron" to "pronoun",
        "interj." to "interjection", "interj" to "interjection",
    )

    /**
     * The part of speech in the interface language.
     *
     * The model writes canonical English (`noun`, `adj.`, `noun / verb`) for
     * dictionary and 自製 entries alike. The common set is translated; anything
     * else passes through as written rather than disappearing, and a compound
     * is joined with 「／」.
     */
    fun partOfSpeech(raw: String, uiLang: String): String {
        val parts = raw.split('/', ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return raw
        return parts.joinToString("／") { part ->
            val canonical = aliases[part.lowercase()] ?: part.lowercase()
            labels[canonical]?.get(uiLang) ?: part
        }
    }
}
