package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule worth having a test for: **an unchosen learning direction gates
 * every account state.** On iOS this lives behind `RootView` and can only be
 * exercised by launching the app, which is why it reads as an accident rather
 * than a decision.
 */
class LaunchRoutingTest {

    private fun route(
        account: LaunchAccountState,
        directionSelected: Boolean = true,
        introDone: Boolean = true,
        catalogReady: Boolean = true,
    ) = LaunchRouting.destination(
        LaunchContext(account, directionSelected, introDone),
        catalogReady = catalogReady,
    )

    @Test
    fun `checking shows the splash and nothing else`() {
        // Not "signed out with extra steps": collapsing them flashes Welcome at
        // every signed-in user on every cold start.
        assertEquals(
            LaunchDestination.Splash,
            route(LaunchAccountState.Checking, directionSelected = false, introDone = false),
        )
    }

    @Test
    fun `no direction sends a signed-out user to pick one`() {
        assertEquals(
            LaunchDestination.LearningDirection,
            route(LaunchAccountState.SignedOut, directionSelected = false),
        )
    }

    @Test
    fun `no direction sends a guest to pick one`() {
        assertEquals(
            LaunchDestination.LearningDirection,
            route(LaunchAccountState.Guest, directionSelected = false),
        )
    }

    @Test
    fun `no direction sends even a signed-in user to pick one`() {
        // The surprising arm, and the reason this is worth pinning: every
        // screen past this point is scoped to a language, so an account
        // without one has nowhere to land.
        assertEquals(
            LaunchDestination.LearningDirection,
            route(
                LaunchAccountState.SignedIn(userId = "u1", setupDone = true),
                directionSelected = false,
            ),
        )
    }

    @Test
    fun `a signed-out user with a direction sees the intro until it is done`() {
        assertEquals(LaunchDestination.Onboarding, route(LaunchAccountState.SignedOut, introDone = false))
        assertEquals(LaunchDestination.Welcome, route(LaunchAccountState.SignedOut, introDone = true))
    }

    @Test
    fun `a guest waits on the splash until the catalogue is there`() {
        // Landing on an empty 首頁 reads as broken rather than loading.
        assertEquals(LaunchDestination.Splash, route(LaunchAccountState.Guest, catalogReady = false))
        assertEquals(LaunchDestination.Main, route(LaunchAccountState.Guest, catalogReady = true))
    }

    @Test
    fun `a signed-in user finishes setup before reaching the app`() {
        assertEquals(
            LaunchDestination.Setup("u1"),
            route(LaunchAccountState.SignedIn("u1", setupDone = false)),
        )
        assertEquals(
            LaunchDestination.Main,
            route(LaunchAccountState.SignedIn("u1", setupDone = true)),
        )
    }

    @Test
    fun `the intro does not block a guest or a signed-in user`() {
        // introDone only gates the signed-out branch. A guest arrived by
        // choosing to browse; re-pitching the product at them is noise.
        assertEquals(LaunchDestination.Main, route(LaunchAccountState.Guest, introDone = false))
        assertEquals(
            LaunchDestination.Main,
            route(LaunchAccountState.SignedIn("u1", setupDone = true), introDone = false),
        )
    }
}
