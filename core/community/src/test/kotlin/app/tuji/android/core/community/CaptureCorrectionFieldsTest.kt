package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasCandidate
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.RecognitionMode
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.model.UiLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureCorrectionFieldsTest {

    @Test fun `only a ja or en interface learning the other language edits a gloss`() {
        val grid = UiLanguage.entries.flatMap { ui ->
            TargetLanguage.entries.map { target -> "$ui-$target" to CaptureCorrectionFields.second(ui, target) }
        }.toMap()
        assertEquals(setOf("Ja-EN", "En-JA"), grid.filterValues { it == CaptureSecondField.Gloss }.keys)
        assertEquals(setOf("Ja-JA", "En-EN"), grid.filterValues { it == CaptureSecondField.Hidden }.keys)
    }

    @Test fun `a chip never shows Chinese to a ja or en interface`() {
        // 英文介面學英文曾在每個候選字下面看到中文——表單上根本沒有的欄位。
        assertEquals("貓", CaptureCorrectionFields.candidateMeaning(CaptureSecondField.ChineseName, "貓", "cat"))
        assertEquals("cat", CaptureCorrectionFields.candidateMeaning(CaptureSecondField.Gloss, "貓", "cat"))
        assertNull(CaptureCorrectionFields.candidateMeaning(CaptureSecondField.Gloss, "貓", null))
        assertNull(CaptureCorrectionFields.candidateMeaning(CaptureSecondField.Hidden, "貓", "cat"))
        assertNull(CaptureCorrectionFields.candidateMeaning(CaptureSecondField.ChineseName, "  ", null))
    }

    @Test fun `a rename without the Chinese field withholds the Chinese name`() {
        for (second in listOf(CaptureSecondField.Gloss, CaptureSecondField.Hidden)) {
            // Untouched: the candidate's Chinese name still describes the card.
            assertEquals("鍵盤", CaptureCorrectionFields.chineseNameToSend(second, "keyboard ", "keyboard", "鍵盤"))
            // Renamed: 鍵盤 now names the thing the user corrected away from.
            assertNull(CaptureCorrectionFields.chineseNameToSend(second, "laptop", "keyboard", "鍵盤"))
        }
        // A Chinese interface edits the name itself, so whatever is there is sent.
        assertEquals("鍵盤", CaptureCorrectionFields.chineseNameToSend(CaptureSecondField.ChineseName, "laptop", "keyboard", "鍵盤"))
        // No candidate behind the word: nothing to be stale against.
        assertEquals("筆電", CaptureCorrectionFields.chineseNameToSend(CaptureSecondField.Hidden, "laptop", null, "筆電"))
    }

    @Test fun `a renamed draft on a monolingual form sends no Chinese name`() {
        val keyboard = AtlasCandidate(id = "a", label = "keyboard", zhHant = "鍵盤", level = AtlasCandidate.LEVEL_PRIMARY, rank = 0, confidence = 0.9)
        val draft = CaptureDraft().withCandidates(RecognitionMode.Primary, listOf(keyboard)).apply(keyboard)
        assertEquals("鍵盤", draft.payload(CaptureSecondField.Hidden).displayZhHant)
        val renamed = draft.copy(lemma = "laptop")
        assertNull(renamed.payload(CaptureSecondField.Hidden).displayZhHant)
        assertEquals("鍵盤", renamed.payload(CaptureSecondField.ChineseName).displayZhHant)
    }

    @Test fun `an item's local name follows the interface language`() {
        val item = AtlasItem(id = "t1", targetLanguage = TargetLanguage.EN, lemma = "cat", displayZhHant = "貓", displayJa = "猫", displayEn = "cat")
        assertEquals("貓", CaptureCorrectionFields.localName(UiLanguage.ZhHant, item))
        assertEquals("猫", CaptureCorrectionFields.localName(UiLanguage.Ja, item))
        // Monolingual: the lemma is already the name.
        assertNull(CaptureCorrectionFields.localName(UiLanguage.En, item))
        // Not filled yet (or an older server): nothing, rather than Chinese.
        assertNull(CaptureCorrectionFields.localName(UiLanguage.Ja, item.copy(displayJa = null)))
        // The server's "no Chinese name yet" stand-in is not a name.
        assertNull(CaptureCorrectionFields.localName(UiLanguage.ZhHant, item.copy(displayZhHant = "cat")))
    }
}
