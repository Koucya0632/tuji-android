package app.tuji.android.capture

import android.content.Context
import android.util.Log
import app.tuji.android.core.model.CaptureJobRecord
import java.io.File
import kotlinx.serialization.json.Json

/**
 * Where a queued capture lives while it is not in memory.
 *
 * The point is an app kill. A capture is committed the moment the user taps
 * 建立卡片 — the photograph is already uploaded and the name is already typed —
 * so losing the job because Android reclaimed the process loses work the user
 * has every reason to think is done. Journalled, it comes back on the next
 * launch and finishes.
 */
interface CaptureJobJournal {
    fun save(record: CaptureJobRecord)
    fun remove(id: String)
    fun removeAll()
    fun restore(): List<CaptureJobRecord>
}

/**
 * One JSON file per job in a directory of its own.
 *
 * Not DataStore and not a database. A job is a handful of records with a
 * lifetime measured in seconds, and everything that reads them is this class;
 * a schema would be ceremony around a directory listing.
 */
class FileCaptureJobJournal(context: Context) : CaptureJobJournal {

    private val dir = File(context.filesDir, "capture-jobs").also { it.mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    override fun save(record: CaptureJobRecord) {
        runCatching { File(dir, "${record.id}.json").writeText(json.encodeToString(record)) }
            .onFailure { Log.w(TAG, "could not journal ${record.id}", it) }
    }

    override fun remove(id: String) {
        File(dir, "$id.json").delete()
    }

    override fun removeAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    override fun restore(): List<CaptureJobRecord> =
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().mapNotNull { file ->
            runCatching { json.decodeFromString<CaptureJobRecord>(file.readText()) }.getOrElse {
                // A half-written record is not worth keeping and is not worth
                // crashing over: the photo is still on the server.
                Log.w(TAG, "dropping unreadable job ${file.name}", it)
                file.delete()
                null
            }
        }

    private companion object {
        const val TAG = "CaptureJobJournal"
    }
}
