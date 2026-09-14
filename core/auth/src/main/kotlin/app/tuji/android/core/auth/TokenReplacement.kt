package app.tuji.android.core.auth

/**
 * Whether a token the server refused still needs replacing.
 *
 * Several requests go out together — a screen's reload, a reconnect — so
 * several 401s come back together. Each asks for a replacement, one at a time.
 * The first spends the refresh token; the rest arrive to find the current token
 * already different from the one they were refused with, and must take it
 * rather than refresh again: a second refresh with a spent token is the
 * server's cue to treat the session as stolen.
 */
enum class TokenReplacement {
    /** The current token is not the refused one. Use it. */
    UseCurrent,

    /** The current token is the refused one, or there is none. Refresh. */
    Refresh,
    ;

    companion object {
        /**
         * @param current the token the session holds now, or null when it holds none.
         * @param rejected the token the server refused, or null when the refused
         *   request carried none.
         */
        fun decide(current: String?, rejected: String?): TokenReplacement =
            if (current != null && current != rejected) UseCurrent else Refresh
    }
}
