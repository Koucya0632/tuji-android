package app.tuji.android.update

/** The version somebody answered 稍後再說 to, and when. */
data class SnoozedAppUpdate(val versionCode: Int, val atMillis: Long)

/**
 * Whether to say "there is a new version" — as three pure questions.
 *
 * iOS's `AppUpdatePolicy`, with one difference in what is compared: Google
 * Play reports the waiting build's `versionCode`, not a display name, so the
 * numbers here are version codes and need no parsing.
 *
 * The questions are separate because they fail differently. Asking Play too
 * often wastes a call; prompting too often is the same sentence in the way on
 * every launch, and people learn to dismiss that without reading it.
 *
 * There is no forced update here, on purpose: a "too old to use" gate needs a
 * server-side source of truth, and the only one there is is Play itself.
 */
object AppUpdatePolicy {

    /** How often Play is asked. Its own answer is cached longer than this is worth beating. */
    const val CHECK_INTERVAL_MILLIS = 24L * 60 * 60 * 1000

    /**
     * How long one version stays quiet after 稍後再說.
     *
     * Not forever — "later" usually means "not right now" — and not until the
     * next launch either, which would make the button mean nothing.
     */
    const val SNOOZE_INTERVAL_MILLIS = 3L * 24 * 60 * 60 * 1000

    /** Never asked (`null`) means ask. */
    fun shouldCheck(lastCheckedAtMillis: Long?, nowMillis: Long): Boolean {
        if (lastCheckedAtMillis == null) return true
        // A clock set backwards leaves a timestamp in the future. Ask, rather
        // than let one bad value switch the check off for good.
        if (nowMillis < lastCheckedAtMillis) return true
        return nowMillis - lastCheckedAtMillis >= CHECK_INTERVAL_MILLIS
    }

    /**
     * True only for a build that is actually newer than the one installed.
     *
     * A snooze is for one version: a newer build than the one that was put off
     * is a new question, and asks again at once.
     */
    fun shouldPrompt(
        installedVersionCode: Int,
        availableVersionCode: Int,
        snoozed: SnoozedAppUpdate?,
        nowMillis: Long,
    ): Boolean {
        if (availableVersionCode <= installedVersionCode) return false
        if (snoozed == null || snoozed.versionCode != availableVersionCode) return true
        if (nowMillis < snoozed.atMillis) return true
        return nowMillis - snoozed.atMillis >= SNOOZE_INTERVAL_MILLIS
    }

    /**
     * Having found a new version is not the same as it being the moment to say so.
     *
     * A study session and the first-launch tour are both things somebody is in
     * the middle of, and an update can wait — the prompt is not lost, it stands
     * back until they are done.
     */
    fun mayPresent(pendingVersionCode: Int?, studyFocusActive: Boolean, tourRunning: Boolean): Boolean =
        pendingVersionCode != null && !studyFocusActive && !tourRunning
}
