package app.tuji.android.core.community

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportTargetTest {

    @Test fun `the wire values are the ones the moderation queue stores`() {
        assertEquals(
            listOf("spam", "inappropriate", "copyright", "wrong", "other"),
            ReportReason.entries.map { it.wire },
        )
    }

    @Test fun `an author is addressed by UID and the other two by slug`() {
        // Different id kinds on purpose: a slug moves with a rename, a TJ UID
        // does not — and an author report has to survive one.
        assertEquals("TJ11111111", (ReportTarget.Author("TJ11111111") as ReportTarget.Author).handle)
        assertEquals("atlas-abc", (ReportTarget.Item("atlas-abc") as ReportTarget.Item).slug)
        assertEquals("col-abc", (ReportTarget.Collection("col-abc") as ReportTarget.Collection).slug)
    }

    @Test fun `two reports about the same thing are the same value`() {
        assertEquals(ReportTarget.Item("atlas-abc"), ReportTarget.Item("atlas-abc"))
    }
}
