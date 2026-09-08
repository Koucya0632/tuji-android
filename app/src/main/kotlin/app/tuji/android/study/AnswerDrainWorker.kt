package app.tuji.android.study

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.tuji.android.TujiApplication
import java.util.concurrent.TimeUnit

/**
 * Drains the parked answers when the network comes back.
 *
 * WorkManager handles the three situations this layer actually defends
 * against — the process being killed, connectivity returning, and a reboot —
 * and none of those can be handled by a retry loop living inside a screen.
 *
 * **It is not storage.** The payloads are on disk in `StudyAnswerOutbox`
 * before this worker is ever enqueued; WorkManager only decides *when* to try
 * again. Putting the answers in the work request's input data would look
 * equivalent and would quietly cap them at 10 KB.
 */
class AnswerDrainWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? TujiApplication ?: return Result.success()
        val before = app.answerOutbox.count
        if (before == 0) return Result.success()

        app.answerOutbox.replay(app.answerSubmitting)

        // A drain that could not finish asks to be run again rather than
        // reporting success — the outbox keeping its entries is correct, but
        // silence about them is not.
        return if (app.answerOutbox.count == 0) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "tuji-answer-drain"

        /**
         * Enqueued at launch and whenever an answer is parked.
         * `KEEP` rather than `REPLACE`: overlapping triggers should coalesce,
         * not restart the backoff each time.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<AnswerDrainWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
