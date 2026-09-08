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
     * [catalogReady] holds a guest (and a signed-in user) on the splash until
     * the catalogue has loaded — landing on an empty 首頁 reads as a broken app
     * rather than a loading one.
     */
    fun destination(context: LaunchContext, catalogReady: Boolean = true): LaunchDestination =
        when (val account = context.account) {
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
