package app.tuji.android.study

/**
 * One-time lessons a study screen has already taught.
 *
 * A seam rather than a direct read of `OnboardingStore`, because "has the user
 * been shown this once" is a fact the view model *decides against* and a test
 * has to be able to set — and the alternative, a `SharedPreferences` call
 * inside the view model, makes every test that touches 複習 depend on a device.
 *
 * It is `var`, not a pair of methods: the caller is answering a question about
 * the user, and `hints.reviewHintTaught = true` is that sentence.
 */
interface StudyHints {

    /**
     * Whether the user has ever turned a 複習 card over to see the hint.
     *
     * Gates the 8-second nudge. The affordance is invisible by design, so the
     * line exists to teach it once; kept on after that it would be a permanent
     * caption for something the user already knows, on the one screen where
     * every pixel is either the question or an answer.
     */
    var reviewHintTaught: Boolean
}

/**
 * The default for tests and for any build that has not wired storage: nothing
 * has been taught, and saying it has does not outlive the object.
 */
class InMemoryStudyHints(override var reviewHintTaught: Boolean = false) : StudyHints
