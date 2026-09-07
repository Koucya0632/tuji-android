package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cases are the ones the iOS suite pins, because a divergence here is a
 * divergence in what the two apps show for the same row. Assertions are on the
 * *decision*, never on localized copy — CI runs in English and a developer's
 * machine does not.
 */
class HeadwordDisplayTest {

    private fun word(
        word: String,
        pronunciation: String? = null,
        reading: String? = null,
        segments: List<FuriganaSegment>? = null,
        target: TargetLanguage? = null,
    ) = Word(
        id = "t",
        word = word,
        pronunciation = pronunciation,
        reading = reading,
        readingSegments = segments,
        targetLanguage = target,
    ).asHeadworded()

    @Test
    fun `japanese with a split shows ruby`() {
        val segments = listOf(FuriganaSegment("手", "て"), FuriganaSegment("おけ", null))
        val display = word("手おけ", reading = "ておけ", segments = segments, target = TargetLanguage.JA)
            .headwordDisplay(TargetLanguage.JA)
        assertEquals(HeadwordDisplay.Ruby(segments), display)
    }

    @Test
    fun `japanese without a split falls back to the reading line`() {
        val display = word("目覚まし時計", reading = "めざましどけい", target = TargetLanguage.JA)
            .headwordDisplay(TargetLanguage.JA)
        assertEquals(HeadwordDisplay.Line("めざましどけい"), display)
    }

    @Test
    fun `a kana headword is its own reading and prints once`() {
        val display = word("バスマット", reading = "バスマット", target = TargetLanguage.JA)
            .headwordDisplay(TargetLanguage.JA)
        assertEquals(HeadwordDisplay.Plain, display)
    }

    @Test
    fun `english shows IPA on its own line and never ruby`() {
        val display = word("bath mat", pronunciation = "/ˈbæθ mæt/", target = TargetLanguage.EN)
            .headwordDisplay(TargetLanguage.EN)
        assertEquals(HeadwordDisplay.Line("/ˈbæθ mæt/"), display)
    }

    @Test
    fun `an empty segment list is not a split`() {
        val display = word("風呂いす", reading = "ふろいす", segments = emptyList(), target = TargetLanguage.JA)
            .headwordDisplay(TargetLanguage.JA)
        assertEquals(HeadwordDisplay.Line("ふろいす"), display)
    }

    @Test
    fun `an untagged word follows the session, not English`() {
        // The iOS bug this pins: eleven of thirteen call sites forgot the
        // fallback, so an untagged word read as English inside a JA session.
        val segments = listOf(FuriganaSegment("犬", "いぬ"))
        val display = word("犬", segments = segments).headwordDisplay(TargetLanguage.JA)
        assertEquals(HeadwordDisplay.Ruby(segments), display)
    }

    @Test
    fun `a kana reading tags an untagged word as japanese`() {
        val display = word("ねこ", reading = "ねこ").headwordDisplay(TargetLanguage.EN)
        assertEquals(HeadwordDisplay.Plain, display)
    }

    @Test
    fun `whitespace-only reading is no reading`() {
        val display = word("cat", pronunciation = "   ", target = TargetLanguage.EN)
            .headwordDisplay(TargetLanguage.EN)
        assertEquals(HeadwordDisplay.Plain, display)
    }
}
