package app.tuji.android.membership

import app.tuji.android.core.network.ApiError

/**
 * How a write to a member feature ended — iOS's `MemberWriteOutcome`.
 *
 * Carries no server text: [Failed] is drawn as the app's own line, for the
 * reason `WordDetailViewModel.State.Failed` gives.
 */
enum class MemberWriteOutcome {
    Done,

    /** 402: buying something would allow it — open 會員方案. */
    NeedsUpgrade,

    /** 429: at the top tier's ceiling — only removing something helps. */
    AtLimit,

    /** 404: already gone, deleted on another device. */
    Missing,

    Failed,
    ;

    companion object {
        fun from(error: Throwable): MemberWriteOutcome = when ((error as? ApiError.Http)?.status) {
            402 -> NeedsUpgrade
            429 -> AtLimit
            404 -> Missing
            else -> Failed
        }
    }
}
