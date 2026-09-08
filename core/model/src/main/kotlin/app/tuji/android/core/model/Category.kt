package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * One shelf of the 圖鑑.
 *
 * `color` is a Tailwind gradient class the web app draws with; it is carried
 * only so the payload round-trips, and nothing on Android reads it. The names
 * the UI shows come from [name] / [nameZh] rather than a local table — the real
 * source of those strings is the database, not `lib/categories.ts`, and a
 * hard-coded copy here would be a third place to forget.
 */
@Serializable
data class Category(
    val id: String,
    /** English name. */
    val name: String,
    /** 繁中 name. */
    val nameZh: String? = null,
    val emoji: String? = null,
    val description: String? = null,
    val descriptionEn: String? = null,
    val imageUrl: String? = null,
)

@Serializable
data class CategoriesResponse(val categories: List<Category> = emptyList())
