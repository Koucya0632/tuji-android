package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/** `GET /api/users/progress` — the streak, the heatmap and the per-theme rows. */
@Serializable
data class ProgressResponse(
    val streak: StudyStreak? = null,
    val heatmap: List<HeatmapCell> = emptyList(),
    val categories: List<CategoryProgress> = emptyList(),
)

@Serializable
data class StudyStreak(
    val current: Int = 0,
    val longest: Int = 0,
    val totalDays: Int = 0,
    val todayCount: Int = 0,
    val lastStudyDate: String? = null,
)

/**
 * One day of the 42-cell grid.
 *
 * [future] marks the cells after today in the current week. They are part of
 * the grid's shape — the week has to keep its seven columns — but they are not
 * days the user failed to study, and drawing them the same as a zero would say
 * that they were.
 */
@Serializable
data class HeatmapCell(val count: Int = 0, val future: Boolean = false)

/**
 * One theme's dictionary completion.
 *
 * [total] is published cards in the theme; [seen] is those with a `user_cards`
 * row — studied at least once, however long ago and whatever the score has
 * decayed to since.
 */
@Serializable
data class CategoryProgress(val category: String, val total: Int = 0, val seen: Int = 0)
