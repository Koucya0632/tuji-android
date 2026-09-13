package app.tuji.android.core.community

import app.tuji.android.core.model.AtlasPublicCollection
import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.TargetLanguage

/** The two halves of an author's public work. */
enum class AuthorSegment { Collections, Items }

/** One language's worth of an author's public items. */
data class LanguageGroup(val language: TargetLanguage, val items: List<AtlasPublicItem>)

/**
 * How 作者主頁 lays out what an author published — iOS's `AuthorProfileVM`
 * rules.
 *
 * A profile is a body of work, not a study feed, so nothing is filtered by the
 * reader's learning direction: the languages are only separated, and the
 * header's 公開項目 count still matches what is on screen.
 */
object AuthorShelf {

    /**
     * Groups by language in order of first appearance. The server sends items
     * newest first, so the language the author published in most recently
     * leads. An untagged item counts as English, as everywhere else.
     */
    fun groups(items: List<AtlasPublicItem>): List<LanguageGroup> =
        items.groupBy { it.targetLanguage ?: TargetLanguage.EN }
            .map { (language, members) -> LanguageGroup(language, members) }

    /**
     * A switch only when there is somewhere to switch to. Most authors have no
     * collections, and a control that advertises an empty room reads as a
     * feature that does not work.
     */
    fun showsSegments(collections: List<AtlasPublicCollection>): Boolean = collections.isNotEmpty()

    /** Curated work is the stronger signal, so it leads when it exists. */
    fun defaultSegment(collections: List<AtlasPublicCollection>): AuthorSegment =
        if (showsSegments(collections)) AuthorSegment.Collections else AuthorSegment.Items

    /** What to draw: the items whenever the switch is absent, so a stale choice cannot strand the page on an empty 合集. */
    fun visible(chosen: AuthorSegment, collections: List<AtlasPublicCollection>): AuthorSegment =
        if (showsSegments(collections)) chosen else AuthorSegment.Items
}
