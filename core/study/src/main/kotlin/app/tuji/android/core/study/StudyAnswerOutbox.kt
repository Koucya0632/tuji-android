package app.tuji.android.core.study

import app.tuji.android.core.model.StudyAnswerPayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Durable outbox for `/api/study/answer` writes that exhausted their in-session
 * retries — offline, or the server down.
 *
 * Before iOS had this, such ratings were counted into the completion screen's
 * 未同步 notice and then **lost**: the session showed as saved while the SRS
 * schedule silently never learned about it. The word stayed 未學 and the daily
 * goal miscounted, and nothing on screen said so.
 *
 * The four properties this has to keep, per the architecture plan §07:
 *
 *  1. **Account-tagged** — a pending answer must never be sent under the next
 *     account that signs in.
 *  2. **Survives a kill** — the write lands on disk before [add] returns, not
 *     when the process feels like it.
 *  3. **Duplicate-safe** — the backend tolerates a repeated answer, so a crash
 *     between "POST succeeded" and "file saved" costs one replay, not one lost
 *     rating. Never trade retry away to avoid a duplicate.
 *  4. **Failure is visible** — a drain that stops leaves its entries in place
 *     and countable, rather than swallowing them.
 *
 * Not thread-safe by design: it is driven from one place at a time, and the
 * reentrancy that actually happens — a launch drain overlapping a foreground
 * drain — is handled by [replaying] rather than by a lock that would suggest
 * more concurrency than exists.
 */
class StudyAnswerOutbox(
    private val file: File,
    private val account: ActiveAccount,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    @Serializable
    private data class Entry(
        val id: String,
        val ownerUserId: String,
        val payload: StudyAnswerPayload,
    )

    private var entries: MutableList<Entry> = load()
    private var replaying = false

    /** What is waiting for the account that is signed in **now**. */
    val pending: List<StudyAnswerPayload>
        get() {
            val owner = account.currentUserId() ?: return emptyList()
            return entries.filter { it.ownerUserId == owner }.map { it.payload }
        }

    val count: Int get() = pending.size

    /**
     * Park an answer whose in-session retries all failed. Persisted before this
     * returns, so a force-quit one line later does not lose it.
     *
     * Refuses when no account is active: an answer with no owner is one that
     * can only ever be replayed under the wrong one.
     */
    fun add(payload: StudyAnswerPayload) {
        val owner = account.currentUserId() ?: return
        entries.add(Entry(UUID.randomUUID().toString(), owner, payload))
        save()
    }

    /**
     * Re-send everything in order.
     *
     * Successes leave the outbox; the **first failure stops the pass** — the
     * next entry would meet the same network — and keeps the rest for the next
     * trigger. Re-checks the account on every iteration, because a sign-out
     * mid-replay must not let the following answers through, and must not let a
     * late success remove an entry belonging to whoever signed in after.
     */
    suspend fun replay(submit: AnswerSubmitting) {
        if (replaying) return
        val owner = account.currentUserId() ?: return
        replaying = true
        try {
            while (account.currentUserId() == owner) {
                val entry = entries.firstOrNull { it.ownerUserId == owner } ?: return
                val stamped = entry.payload.copy(ownerUserId = owner)
                try {
                    submit.submit(stamped)
                } catch (e: Throwable) {
                    // Left in place on purpose. A drain that clears what it
                    // could not send is a drain that loses data quietly.
                    return
                }
                // The account may have changed while that POST was in flight.
                if (account.currentUserId() != owner) return
                val index = entries.indexOfFirst { it.id == entry.id }
                if (index < 0) return
                entries.removeAt(index)
                save()
            }
        } finally {
            replaying = false
        }
    }

    /**
     * Sign-out is a hard account boundary.
     *
     * Cleared even though entries are owner-tagged: the tag stops a *replay*
     * under the wrong account, but it does not stop the previous account's
     * answers sitting on the device, or reaching a backup.
     */
    fun reset() {
        entries.clear()
        save()
    }

    // Disk

    private fun load(): MutableList<Entry> {
        if (!file.exists()) return mutableListOf()
        val text = runCatching { file.readText() }.getOrNull() ?: return mutableListOf()

        runCatching { json.decodeFromString<List<Entry>>(text) }
            .onSuccess { return it.toMutableList() }

        // A file from before answers were account-tagged cannot be attributed
        // to anyone. Quarantined rather than replayed under whoever happens to
        // sign in after the update — and rather than deleted, because it is
        // still somebody's data.
        val looksLikeLegacy = runCatching {
            json.decodeFromString<List<StudyAnswerPayload>>(text)
        }.isSuccess
        if (looksLikeLegacy) {
            val quarantine = File(file.parentFile, file.name + ".unowned")
            quarantine.delete()
            runCatching { file.renameTo(quarantine) }
        }
        return mutableListOf()
    }

    private fun save() {
        runCatching {
            file.parentFile?.mkdirs()
            // Written beside the target and moved into place: a half-written
            // outbox is worse than none, because it reads as "nothing pending".
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(entries.toList()))
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        }
    }
}
