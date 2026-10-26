package app.tuji.android.core.study

import app.tuji.android.core.model.TopicPreset

/**
 * What choosing a 主題組合 does to the 學習主題, and when one reads as chosen.
 *
 * The account never records a preset (tuji-ios CONTEXT.md, 主題組合): choosing
 * one is re-ticking the themes by hand in one go, and "chosen" is read back off
 * the ticks. Both rules live here so the picker and Setup cannot each grow
 * their own version of "which ids are the public ones". iOS's
 * `TopicPresetChoice`.
 */
object TopicPresetChoice {
    /**
     * The sources — 自製圖鑑 and 物見收藏 — are not topics. A preset never
     * touches them.
     */
    val SOURCE_IDS: Set<String> = StudyCategoryDefaults.ATLAS_IDS.toSet()

    /** The preset's themes that exist in this catalogue. */
    fun themes(preset: TopicPreset, catalogIds: Set<String>): Set<String> =
        preset.categoryIds.toSet().intersect(catalogIds) - SOURCE_IDS

    /**
     * The selection after choosing [preset]: its public themes replace the
     * current public ones; the sources stay exactly as they were. Choosing a
     * second preset therefore replaces the first rather than stacking.
     */
    fun applying(preset: TopicPreset, selection: Set<String>, catalogIds: Set<String>): Set<String> =
        selection.intersect(SOURCE_IDS) + themes(preset, catalogIds)

    /**
     * Chosen only while the ticked public themes are exactly the preset's —
     * one more or one fewer and it is not.
     */
    fun isChosen(preset: TopicPreset, selection: Set<String>, catalogIds: Set<String>): Boolean {
        val themes = themes(preset, catalogIds)
        return themes.isNotEmpty() && selection - SOURCE_IDS == themes
    }

    /**
     * Locked when any one of its themes is: a preset is chosen whole or not at
     * all, never half-applied.
     */
    fun isLocked(preset: TopicPreset, catalogIds: Set<String>, isThemeLocked: (String) -> Boolean): Boolean =
        themes(preset, catalogIds).any(isThemeLocked)
}
