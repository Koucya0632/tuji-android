package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.TargetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthorShelfTest {

    private fun item(slug: String, lang: TargetLanguage?) =
        AtlasPublicItem(id = slug, slug = slug, lemma = slug, targetLanguage = lang)

    private val collection = AtlasPublicCollection(id = "c", slug = "c", title = "c")

    @Test fun `languages are grouped in order of first appearance`() {
        val groups = AuthorShelf.groups(
            listOf(item("a", TargetLanguage.JA), item("b", TargetLanguage.EN), item("c", TargetLanguage.JA)),
        )
        assertEquals(listOf(TargetLanguage.JA, TargetLanguage.EN), groups.map { it.language })
        assertEquals(listOf("a", "c"), groups.first().items.map { it.slug })
    }

    @Test fun `an untagged item joins the English group rather than a nameless one`() {
        val groups = AuthorShelf.groups(listOf(item("a", TargetLanguage.EN), item("b", null)))
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().items.size)
    }

    @Test fun `no collections means no switch, and the items stand alone`() {
        assertFalse(AuthorShelf.showsSegments(emptyList()))
        assertEquals(AuthorSegment.Items, AuthorShelf.defaultSegment(emptyList()))
        assertEquals(
            "a choice left over from a page that had collections cannot strand this one",
            AuthorSegment.Items,
            AuthorShelf.visible(AuthorSegment.Collections, emptyList()),
        )
    }

    @Test fun `collections lead when there are any`() {
        assertTrue(AuthorShelf.showsSegments(listOf(collection)))
        assertEquals(AuthorSegment.Collections, AuthorShelf.defaultSegment(listOf(collection)))
        assertEquals(AuthorSegment.Items, AuthorShelf.visible(AuthorSegment.Items, listOf(collection)))
    }
}
