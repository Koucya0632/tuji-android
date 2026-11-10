package app.tuji.android.core.scenes

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * One `SceneProgressBook` per account, one file each: `<directory>/<account id>.json`.
 *
 * Only this device keeps it (the first version does not sync, as on iOS).
 * Signing out drops the book in memory and leaves the file, so the next
 * account starts from its own file and switching back finds this one.
 */
class SceneProgressStore(private val directory: File, private val log: (String) -> Unit = {}) {
    private val json = Json { ignoreUnknownKeys = true; allowStructuredMapKeys = true }
    private val _book = MutableStateFlow(SceneProgressBook())
    val book: StateFlow<SceneProgressBook> = _book.asStateFlow()

    var accountId: String? = null
        private set

    /** Load `account`'s book. The same account again is a no-op, so an unsaved edit is never read back over. */
    @Synchronized
    fun use(account: String) {
        if (account == accountId) return
        accountId = account
        _book.value = read(account) ?: SceneProgressBook()
    }

    @Synchronized
    fun update(change: (SceneProgressBook) -> SceneProgressBook) {
        val account = accountId ?: run {
            log("scene progress changed with no account")
            return
        }
        val next = change(_book.value)
        _book.value = next
        write(next, account)
    }

    /** Sign-out. The book in memory is the signed-out account's; its file stays. */
    @Synchronized
    fun reset() {
        accountId = null
        _book.value = SceneProgressBook()
    }

    private fun file(account: String) = File(directory, account.lowercase() + ".json")

    private fun read(account: String): SceneProgressBook? {
        val file = file(account)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString(SceneProgressBook.serializer(), file.readText()) }
            .onFailure { log("scene progress unreadable: ${it.message}") }
            .getOrNull()
    }

    private fun write(book: SceneProgressBook, account: String) {
        runCatching {
            directory.mkdirs()
            val target = file(account)
            val temp = File(directory, target.name + ".tmp")
            temp.writeText(json.encodeToString(SceneProgressBook.serializer(), book))
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        }.onFailure { log("scene progress not saved: ${it.message}") }
    }
}
