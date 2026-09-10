package app.tuji.android.core.auth

/**
 * What has to be forgotten when the account changes.
 *
 * On iOS, sign-out reset several app-lifetime singletons by name, in a method
 * whose own comment explained why each one mattered — and nothing anywhere said
 * that a new account-scoped store would have to come there and enrol. The
 * obligation existed only as prose inside the method that discharges it, which
 * is the worst possible place for it: you have to already be editing sign-out
 * to learn that sign-out is what you must edit.
 *
 * Conform, add yourself to the roster the app hands to [AuthService], and
 * sign-out takes care of itself.
 *
 * Three stores are enrolled: the settings, the mastery map and the progress
 * snapshot. All three are application-lifetime — they have to be, because they
 * outlive the screens that read them — and all three hold one account's data,
 * which is the combination that makes a stale one visible: the previous
 * account's streak, their badges, their deck.
 *
 * The seam was written before any of them existed, which is why enrolling costs
 * one line each. The study outbox is deliberately **not** here: it tags each
 * parked answer with the account that made it (`ActiveAccount`), so it survives
 * sign-out on purpose — dropping it would throw away ratings the user cannot
 * re-enter.
 */
interface AccountScopedStore {
    /** Drop everything belonging to the signed-out account. */
    fun reset()
}
