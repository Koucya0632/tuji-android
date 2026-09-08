package app.tuji.android.study

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import app.tuji.android.core.study.SentencePlayback
import app.tuji.android.core.study.SentencePlaying
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * The real [SentencePlaying]: an on-disk clip cache and one [MediaPlayer].
 *
 * **The clip is downloaded before it is played, not streamed.** Streaming would
 * be less code, but then a clip is only ever available while online and 聽句
 * would vanish the moment the train enters a tunnel — while the answer outbox
 * next door exists precisely so the rest of 複習 keeps working there. A file on
 * disk plays on a plane.
 *
 * Nothing evicts the cache yet. The clips are a few tens of KB and a session
 * touches a handful; when that stops being true the fix is a size cap here, not
 * at the call site.
 */
class ClipPlayer(private val context: Context) : SentencePlaying {

    private val cacheDir = File(context.cacheDir, "sentence-clips").apply { mkdirs() }
    private var player: MediaPlayer? = null

    /**
     * Which play this is. A clip that finishes after the card moved on must not
     * start the *new* card's clock, and `stop()` is not enough on its own —
     * the completion callback can already be in flight when it runs.
     */
    private val generation = AtomicInteger(0)

    override fun canPlay(url: String?, online: Boolean): Boolean {
        if (url.isNullOrBlank()) return false
        return cacheFile(url).exists() || online
    }

    override suspend fun play(url: String?, rate: Float): SentencePlayback {
        if (url.isNullOrBlank()) return SentencePlayback.Failed
        val mine = generation.incrementAndGet()

        val file = try {
            withContext(Dispatchers.IO) { download(url) }
        } catch (e: Exception) {
            Log.w(TAG, "clip download failed: $url", e)
            return SentencePlayback.Failed
        }
        if (generation.get() != mine) return SentencePlayback.Failed

        return withContext(Dispatchers.Main) { start(file, rate, mine) }
    }

    override fun stop() {
        generation.incrementAndGet()
        release()
    }

    private fun release() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }

    /** Returns the cached file, fetching it first if this is the first ask. */
    private fun download(url: String): File {
        val target = cacheFile(url)
        if (target.exists() && target.length() > 0) return target

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            // Into a temp file and then renamed: an interrupted download must
            // not leave a truncated file that `canPlay` will happily report as
            // cached forever after.
            val partial = File(target.path + ".part")
            connection.inputStream.use { input ->
                partial.outputStream().use { output -> input.copyTo(output) }
            }
            check(partial.renameTo(target)) { "could not place ${target.name}" }
        } finally {
            connection.disconnect()
        }
        return target
    }

    private suspend fun start(file: File, rate: Float, mine: Int): SentencePlayback =
        suspendCancellableCoroutine { cont ->
            release()
            val mp = MediaPlayer()
            player = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                mp.setDataSource(file.path)
                mp.setOnCompletionListener { finish(cont, mine, SentencePlayback.Finished) }
                mp.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "playback error what=$what extra=$extra")
                    finish(cont, mine, SentencePlayback.Failed)
                    true
                }
                mp.prepare()
                // Set after prepare: on some devices `playbackParams` before
                // prepare throws, and 慢讀 silently becoming normal speed is
                // a worse outcome than a caught exception.
                if (rate != 1f) {
                    runCatching { mp.playbackParams = mp.playbackParams.setSpeed(rate) }
                }
                mp.start()
            } catch (e: Exception) {
                Log.w(TAG, "could not start ${file.name}", e)
                finish(cont, mine, SentencePlayback.Failed)
            }
            cont.invokeOnCancellation { stop() }
        }

    /** Resume once, and only for the play that is still current. */
    private fun finish(
        cont: CancellableContinuation<SentencePlayback>,
        mine: Int,
        outcome: SentencePlayback,
    ) {
        if (!cont.isActive) return
        cont.resume(if (generation.get() == mine) outcome else SentencePlayback.Failed)
    }

    private fun cacheFile(url: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return File(cacheDir, digest.joinToString("") { "%02x".format(it) }.take(32) + ".mp3")
    }

    private companion object {
        const val TAG = "ClipPlayer"
    }
}

/**
 * Whether the device currently has usable internet.
 *
 * Asked per card rather than held, because it moves: freezing it when the queue
 * loaded would decide a whole session's questions against the network as it was
 * a minute ago.
 */
fun Context.isOnline(): Boolean {
    val manager = getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
