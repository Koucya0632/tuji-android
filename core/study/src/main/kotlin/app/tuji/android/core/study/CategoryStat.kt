package app.tuji.android.core.study

import app.tuji.android.core.model.Category
import app.tuji.android.core.model.CategoryProgress

/**
 * One theme's row in 我的進度's breakdown, and the join that produces the list.
 *
 * The order is the join's whole point: rows follow the **catalogue's** order,
 * not whatever order the progress endpoint answered in, so the list does not
 * reshuffle itself between loads.
 */
data class CategoryStat(
    val id: String,
    val name: String,
    val learned: Int,
    val total: Int,
) {
    val ratio: Double get() = if (total > 0) learned.toDouble() / total else 0.0

    companion object {
        /**
         * @param selected the user's chosen 學習主題. Empty means "none picked",
         *   which shows **everything** rather than nothing — the same reading
         *   完成度 gives an empty selection.
         * @param categoryOrder the catalogue. Empty is the cold-open case: the
         *   rows fall back to their raw ids as names. Ugly, and deliberately
         *   so — it reads as a loading artefact rather than as a theme whose
         *   name is missing.
         */
        fun breakdown(
            progress: List<CategoryProgress>,
            selected: List<String> = emptyList(),
            categoryOrder: List<Category> = emptyList(),
            name: (Category) -> String = { it.nameZh ?: it.name },
        ): List<CategoryStat> {
            val scoped =
                if (selected.isEmpty()) progress else progress.filter { it.category in selected }
            // A row reading 0 / 0 is not progress, it is noise.
            val withWords = scoped.filter { it.total > 0 }
            if (withWords.isEmpty()) return emptyList()

            val byId = withWords.associateBy { it.category }
            if (categoryOrder.isEmpty()) {
                return withWords.map {
                    CategoryStat(it.category, it.category, it.seen, it.total)
                }
            }
            return categoryOrder.mapNotNull { category ->
                byId[category.id]?.let {
                    CategoryStat(category.id, name(category), it.seen, it.total)
                }
            }
        }
    }
}

/**
 * How busy one day of the heatmap was.
 *
 * Four bands, not a continuous ramp: the grid is read as a texture, and a
 * gradient over 42 cells is a picture nobody can decode back into counts.
 */
enum class HeatmapBand {
    None,
    Light,
    Medium,
    Heavy,
    ;

    companion object {
        fun of(count: Int): HeatmapBand = when {
            count < 1 -> None
            count <= 4 -> Light
            count <= 12 -> Medium
            else -> Heavy
        }
    }
}
