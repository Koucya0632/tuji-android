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
 * The roster is **empty today** and that is correct: nothing account-scoped
 * exists yet. It is here now rather than later because the stores that will
 * need it — the study outbox, the atlas capture queue, the block list — all
 * arrive in milestones where sign-out is not the thing being edited.
 */
interface AccountScopedStore {
    /** Drop everything belonging to the signed-out account. */
    fun reset()
}
