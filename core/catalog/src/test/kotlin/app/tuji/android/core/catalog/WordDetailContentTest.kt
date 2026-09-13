package app.tuji.android.core.catalog

import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.WordForm
import org.junit.Assert.assertEquals
import org.junit.Test

class WordDetailContentTest {

    private fun word(
        chinese: String? = null,
        chineseDefinition: String? = null,
        targetDefinition: String? = null,
        forms: List<WordForm> = emptyList(),
        etymology: String? = null,
        collocations: List<String> = emptyList(),
    ) = WordDetail(
        id = "w",
        word = "w",
        chinese = chinese,
        chineseDefinition = chineseDefinition,
        targetDefinition = targetDefinition,
        forms = forms,
        etymology = etymology,
        collocations = collocations,
    )

    @Test fun `the pills follow iOS's order and only appear with content`() {
        val full = word(
            targetDefinition = "a dish",
            forms = listOf(WordForm("複數", "dishes")),
            etymology = "Old English disc",
            collocations = listOf("wash the dishes"),
        )
        assertEquals(
            listOf(WordDetailTab.Definition, WordDetailTab.Forms, WordDetailTab.Origin, WordDetailTab.Collocations),
            WordDetailContent.tabs(full, showChinese = true),
        )
        assertEquals(listOf(WordDetailTab.Origin), WordDetailContent.tabs(word(etymology = "x"), showChinese = true))
    }

    /** A 自製 card with only a Chinese gloss still has a meaning to show. */
    @Test fun `a Chinese-only definition counts while Chinese is shown`() {
        val customCard = word(chinese = "卡通")
        assertEquals(listOf(WordDetailTab.Definition), WordDetailContent.tabs(customCard, showChinese = true))
        assertEquals(emptyList<WordDetailTab>(), WordDetailContent.tabs(customCard, showChinese = false))
    }

    @Test fun `a definition in the language being learned is shown whatever the Chinese setting`() {
        assertEquals(listOf(WordDetailTab.Definition), WordDetailContent.tabs(word(targetDefinition = "皿"), showChinese = false))
    }

    @Test fun `parts of speech read in the interface language`() {
        assertEquals("名詞", WordDetailContent.partOfSpeech("noun", "zh-Hant"))
        assertEquals("形容词", WordDetailContent.partOfSpeech("adj.", "zh-Hans"))
        assertEquals("動詞", WordDetailContent.partOfSpeech("Verb", "ja"))
    }

    @Test fun `a compound is joined and an unknown label passes through`() {
        assertEquals("名詞／動詞", WordDetailContent.partOfSpeech("noun / verb", "zh-Hant"))
        assertEquals("counter", WordDetailContent.partOfSpeech("counter", "zh-Hant"))
    }
}
