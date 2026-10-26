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

/**
 * 主題組合 — a named group of public themes for one way of being in Japan
 * (留學生, 打工度假, 旅遊), offered to someone who does not know what to study.
 *
 * Served inside `/api/categories`, already named in the request's language and
 * trimmed to the themes that response serves. The account never records which
 * one was chosen: choosing re-ticks the themes in one go (tuji-ios ADR-0020).
 */
@Serializable
data class TopicPreset(
    val id: String,
    val name: String,
    val categoryIds: List<String> = emptyList(),
    /** The themes a brand-new account starts from. Read the flag, never the order. */
    val isDefault: Boolean = false,
)

/** `presets` is absent from a server that predates 主題組合, so it defaults to none. */
@Serializable
data class CategoriesResponse(
    val categories: List<Category> = emptyList(),
    val presets: List<TopicPreset> = emptyList(),
)
