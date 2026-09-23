package app.tuji.android.core.model

/**
 * Where the app goes on launch, as a decision over three facts.
 *
 * Pure on purpose. iOS put this behind `RootView`, where it could only be
 * exercised by launching the app — and the rule it encodes is not obvious:
 * **an unchosen learning direction gates every account state**, signed out,
 * guest and signed in alike. A signed-in user with no direction does not go to
 * the main app; they go and pick one, because every screen after that point is
 * scoped to a language.
 *
 * [LaunchAccountState] mirrors the auth state rather than reusing it, so this
 * module stays free of the auth stack — the same split iOS makes.
 */
sealed interface LaunchAccountState {
    data object Checking : LaunchAccountState
    data object SignedOut : LaunchAccountState
    data object Guest : LaunchAccountState
    data class SignedIn(val userId: String, val setupDone: Boolean) : LaunchAccountState
}

sealed interface LaunchDestination {
    data object Splash : LaunchDestination
    data object LearningDirection : LaunchDestination
    data object Onboarding : LaunchDestination
    data object Welcome : LaunchDestination
    data class Setup(val userId: String) : LaunchDestination
    data object Main : LaunchDestination
}

data class LaunchContext(
    val account: LaunchAccountState,
    val learningDirectionSelected: Boolean,
    val introDone: Boolean,
)

object LaunchRouting {
    /**
     * The shortest time the launch mark stays up, however fast the account
     * resolves. iOS keeps this number in
     * `LaunchCoordinator(minimumSplashDuration: .milliseconds(600))`.
     *
     * It is not decoration. `TujiBrandLockup`'s entrance takes 650ms end to end
     * (70 hold + 140 hole + 100 pause + 340 spring), and a signed-out launch
     * resolves in a few tens of milliseconds — so without a floor the crossfade
     * to the next screen starts while the cat is still inside the hole, and
     * nobody ever sees the animation the lockup exists to play.
     */
    const val MINIMUM_SPLASH_MS = 600L

    /**
     * [launchReady] is [MINIMUM_SPLASH_MS] having elapsed. iOS spells the same
     * gate as `guard launchReady else { return .splash }`, the first line of
     * `LaunchDestination.resolve` — this port dropped it, which is the whole
     * reason the entrance was invisible on device.
     *
     * [catalogReady] holds a guest (and a signed-in user) on the splash until
     * the catalogue has loaded — landing on an empty 首頁 reads as a broken app
     * rather than a loading one.
     */
    fun destination(
        context: LaunchContext,
        catalogReady: Boolean = true,
        launchReady: Boolean = true,
    ): LaunchDestination {
        if (!launchReady) return LaunchDestination.Splash

        return when (val account = context.account) {
            is LaunchAccountState.Checking -> LaunchDestination.Splash

            is LaunchAccountState.SignedOut -> when {
                !context.learningDirectionSelected -> LaunchDestination.LearningDirection
                context.introDone -> LaunchDestination.Welcome
                else -> LaunchDestination.Onboarding
            }

            is LaunchAccountState.Guest -> when {
                !context.learningDirectionSelected -> LaunchDestination.LearningDirection
                catalogReady -> LaunchDestination.Main
                else -> LaunchDestination.Splash
            }

            is LaunchAccountState.SignedIn -> when {
                !context.learningDirectionSelected -> LaunchDestination.LearningDirection
                !account.setupDone -> LaunchDestination.Setup(account.userId)
                catalogReady -> LaunchDestination.Main
                else -> LaunchDestination.Splash
            }
        }
    }
}
