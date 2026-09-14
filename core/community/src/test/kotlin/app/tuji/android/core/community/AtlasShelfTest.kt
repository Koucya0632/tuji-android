package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasImageSummary
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AtlasShelfTest {

    private fun image(id: String, at: String, deleted: Boolean = false) =
        AtlasImageSummary(id = id, status = "cards_ready", createdAt = at, deletedAt = if (deleted) "x" else null)

    private fun item(id: String, imageId: String, lang: TargetLanguage, review: String? = null) =
        AtlasItem(id = id, imageId = imageId, targetLanguage = lang, lemma = id, reviewStatus = review)

    private val images = listOf(image("a", "2026-09-01"), image("b", "2026-09-03"), image("c", "2026-09-02"), image("gone", "2026-09-04", deleted = true))
    private val items = listOf(item("ia", "a", TargetLanguage.JA), item("ib", "b", TargetLanguage.EN))

    @Test fun `the other language is hidden and counted, and a photo with no card always shows`() {
        val rows = AtlasShelf.rows(images, items, TargetLanguage.JA)
        assertEquals("newest first; b is English, gone is deleted", listOf("c", "a"), rows.map { it.id })
        assertEquals(1, AtlasShelf.hiddenCount(images, rows))
    }

    /** Switching 英↔日 is not deleting — the empty shelf has to say where the cards went. */
    @Test fun `an empty language with cards elsewhere says so`() {
        val onlyEnglish = listOf(image("b", "2026-09-03"))
        val rows = AtlasShelf.rows(onlyEnglish, items, TargetLanguage.JA)
        assertEquals(ShelfState.HiddenElsewhere(1), AtlasShelf.state(rows, AtlasShelf.hiddenCount(onlyEnglish, rows), loading = false, failed = false))
    }

    @Test fun `a failed sync is not an empty shelf`() {
        assertEquals(ShelfState.Failed, AtlasShelf.state(emptyList(), 0, loading = false, failed = true))
        assertEquals(ShelfState.Loading, AtlasShelf.state(emptyList(), 0, loading = true, failed = true))
        assertEquals(ShelfState.Empty, AtlasShelf.state(emptyList(), 0, loading = false, failed = false))
    }

    @Test fun `a batch warns as strongly as its strongest card`() {
        val rows = AtlasShelf.rows(
            listOf(image("a", "1"), image("b", "2"), image("c", "3")),
            listOf(
                item("ia", "a", TargetLanguage.JA, "draft"),
                item("ib", "b", TargetLanguage.JA, "pending_auto"),
                item("ic", "c", TargetLanguage.JA, "approved"),
            ),
            TargetLanguage.JA,
        )
        assertEquals(DeleteWarning.CancelsReview, AtlasShelf.warning(rows, setOf("a", "b")))
        assertEquals(DeleteWarning.TakesDownFromPublic, AtlasShelf.warning(rows, setOf("a", "b", "c")))
        assertEquals(DeleteWarning.PrivateOnly, AtlasShelf.warning(rows, emptySet()))
    }

    @Test fun `only a live public card can be withdrawn`() {
        assertTrue(ReviewStatus.of("approved").canWithdraw)
        assertFalse(ReviewStatus.of("takedown").canWithdraw)
        assertEquals(ReviewStatus.Draft, ReviewStatus.of(null))
        assertEquals(ReviewStatus.Pending, ReviewStatus.of("pending_review"))
    }
}
