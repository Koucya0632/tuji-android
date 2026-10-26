package app.tuji.android.core.study

import app.tuji.android.core.model.TopicPreset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 先幫你排一份學習節奏 opens with something already ticked, and *which* something
 * is the whole rule: this screen can overwrite an account that has been using
 * the app for months, because the flag that shows it lives on the device.
 */
class SetupChoicesTest {

    private val catalog = setOf(
        "kitchen", "bathroom", "living-room", "office", "custom", "community",
    )

    @Test fun `a new account opens on the beginner three plus both shelves`() {
        val seed = SetupChoices.seed(account = null, catalogIds = catalog, firstThemesFallback = catalog.toList())
        assertEquals(
            setOf("kitchen", "bathroom", "living-room", "custom", "community"),
            seed.topicIds,
        )
        assertEquals(10, seed.dailyGoal)
    }

    @Test fun `an account that has themes opens on its own`() {
        // Reinstalling is not a reason to lose months of settings. Setup runs
        // again because the flag is on the device; what it shows is the
        // account's.
        val account = SetupChoices.AccountThemes(listOf("office", "kitchen"), dailyGoal = 30)
        val seed = SetupChoices.seed(account, catalog, catalog.toList())
        assertEquals(setOf("office", "kitchen"), seed.topicIds)
        assertEquals(30, seed.dailyGoal)
    }

    @Test fun `a retired theme is not carried into a fresh save`() {
        val account = SetupChoices.AccountThemes(listOf("office", "garage-that-was-removed"), dailyGoal = 20)
        val seed = SetupChoices.seed(account, catalog, catalog.toList())
        assertEquals(setOf("office"), seed.topicIds)
    }

    @Test fun `an account whose themes have all been retired starts over`() {
        // Not an empty selection: 完成設定 cannot be tapped with nothing
        // ticked, so an account in this state would be stuck on this screen.
        val account = SetupChoices.AccountThemes(listOf("garage-that-was-removed"), dailyGoal = 20)
        val seed = SetupChoices.seed(account, catalog, catalog.toList())
        assertEquals(
            setOf("kitchen", "bathroom", "living-room", "custom", "community"),
            seed.topicIds,
        )
        assertEquals("and the goal goes back to the default with it", 10, seed.dailyGoal)
    }

    @Test fun `settings that have not arrived yet are not an empty account`() {
        // The window this closes: a returning account whose settings are still
        // in flight looks exactly like a new one. Passing null rather than an
        // empty object is what keeps Setup from saving the beginner trio over
        // somebody's real themes.
        val loading: SetupChoices.AccountThemes? = null
        assertEquals(
            SetupChoices.seed(loading, catalog, catalog.toList()),
            SetupChoices.seed(null, catalog, catalog.toList()),
        )
    }

    @Test fun `a catalogue without the hand-picked three falls back to its own first three`() {
        val other = setOf("garden", "market", "station", "harbour", "custom")
        val seed = SetupChoices.seed(null, other, listOf("garden", "market", "station", "harbour"))
        assertEquals(setOf("garden", "market", "station", "custom"), seed.topicIds)
    }

    @Test fun `the personal shelves are ticked even when they hold nothing`() {
        // A card made on day one that never comes up for review is a card the
        // app lost.
        val seed = SetupChoices.seed(null, catalog, catalog.toList())
        assert("custom" in seed.topicIds)
        assert("community" in seed.topicIds)
    }

    private val presetCatalog = catalog + setOf("campus", "bank")

    @Test fun `a new account opens on the default preset plus both shelves`() {
        val presets = listOf(
            TopicPreset("travel", "旅遊", listOf("office")),
            TopicPreset("student", "留學生", listOf("campus", "bank"), isDefault = true),
        )
        val seed = SetupChoices.seed(null, presetCatalog, presetCatalog.toList(), presets)
        assertEquals(setOf("campus", "bank", "custom", "community"), seed.topicIds)
        assertEquals(10, seed.dailyGoal)
    }

    @Test fun `the default is read off the flag, not the order`() {
        val presets = listOf(
            TopicPreset("travel", "旅遊", listOf("office")),
            TopicPreset("student", "留學生", listOf("campus"), isDefault = true),
        )
        assertEquals(
            setOf("campus", "custom", "community"),
            SetupChoices.seed(null, presetCatalog, presetCatalog.toList(), presets).topicIds,
        )
    }

    @Test fun `a default preset with nothing served falls back to the beginner three`() {
        val presets = listOf(TopicPreset("student", "留學生", listOf("retired-theme"), isDefault = true))
        assertEquals(
            setOf("kitchen", "bathroom", "living-room", "custom", "community"),
            SetupChoices.seed(null, catalog, catalog.toList(), presets).topicIds,
        )
    }

    @Test fun `an account with themes ignores the default preset`() {
        val presets = listOf(TopicPreset("student", "留學生", listOf("campus"), isDefault = true))
        val account = SetupChoices.AccountThemes(listOf("office"), dailyGoal = 30)
        assertEquals(setOf("office"), SetupChoices.seed(account, presetCatalog, presetCatalog.toList(), presets).topicIds)
    }
}
