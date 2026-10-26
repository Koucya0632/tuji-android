package app.tuji.android.core.study

import app.tuji.android.core.model.TopicPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主題組合 replaces the public themes and leaves the sources alone, and reads
 * as chosen only while the ticks match it exactly (tuji-ios ADR-0020).
 */
class TopicPresetChoiceTest {

    private val catalog = setOf("campus", "bank", "restaurant", "kitchen", "custom", "community")
    private val student = TopicPreset("student", "留學生", listOf("campus", "bank", "retired-theme"), isDefault = true)
    private val travel = TopicPreset("travel", "旅遊", listOf("restaurant"))

    @Test fun `choosing replaces the public themes and keeps the sources as they were`() {
        val after = TopicPresetChoice.applying(student, setOf("kitchen", "custom"), catalog)
        assertEquals(setOf("campus", "bank", "custom"), after)
    }

    @Test fun `a second preset replaces the first rather than stacking`() {
        val first = TopicPresetChoice.applying(student, setOf("custom", "community"), catalog)
        assertEquals(setOf("restaurant", "custom", "community"), TopicPresetChoice.applying(travel, first, catalog))
    }

    @Test fun `a theme the catalogue does not serve is never ticked`() {
        assertEquals(setOf("campus", "bank"), TopicPresetChoice.themes(student, catalog))
    }

    @Test fun `chosen only while the public ticks are exactly the preset's`() {
        assertTrue(TopicPresetChoice.isChosen(student, setOf("campus", "bank", "community"), catalog))
        assertFalse(TopicPresetChoice.isChosen(student, setOf("campus", "bank", "kitchen"), catalog))
        assertFalse(TopicPresetChoice.isChosen(student, setOf("campus"), catalog))
    }

    @Test fun `a preset with nothing in this catalogue is never chosen`() {
        val gone = TopicPreset("gone", "x", listOf("retired-theme"))
        assertFalse(TopicPresetChoice.isChosen(gone, emptySet(), catalog))
    }

    @Test fun `one locked theme locks the whole preset`() {
        assertTrue(TopicPresetChoice.isLocked(student, catalog) { it == "bank" })
        assertFalse(TopicPresetChoice.isLocked(student, catalog) { it == "kitchen" })
    }
}
