package app.tuji.android.scenes

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.util.Log
import app.tuji.android.BuildConfig
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.scenes.PracticeScene
import app.tuji.android.core.scenes.SceneCatalog
import app.tuji.android.core.scenes.ScenePracticeSession
import app.tuji.android.core.scenes.SceneProgressStore
import app.tuji.android.core.scenes.SceneResponse
import app.tuji.android.core.scenes.SpokenLine
import app.tuji.android.core.scenes.StageReply
import app.tuji.android.study.TtsSpeaker
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 場面練習's app-side pieces, one per process: the catalog read out of the
 * APK's assets, this device's progress files, and the player for the bundled
 * clips. Everything that decides lives in `core:scenes`.
 *
 * Debug builds list the drafts as well as the released scenes, as on iOS.
 */
class Scenes(private val context: Context, private val speech: TtsSpeaker) {
    val catalog: SceneCatalog by lazy {
        SceneCatalog.load(read = ::read, showsDrafts = BuildConfig.DEBUG) { id, error -> Log.e(TAG, "scene $id failed to load", error) }
    }

    val progress = SceneProgressStore(File(context.filesDir, "scenes")) { Log.e(TAG, it) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * The pass on screen, kept here rather than in the composable so opening a
     * word from it and coming back finds the same 問句 in the same state. A new
     * scene or trip, or a finished pass, starts over.
     */
    private var active: Pair<String, ScenePracticeSession>? = null

    fun session(scene: PracticeScene, routeId: String?): ScenePracticeSession {
        val key = scene.id.raw + "|" + routeId
        active?.let { (k, s) -> if (k == key && s.phase.value !is ScenePracticeSession.Phase.Finished) return s }
        return ScenePracticeSession(scene, progress, scene.stage?.route(routeId)).also { active = key to it }
    }

    /** Leaving the practice screen on purpose (✕ or 完成) ends the pass. */
    fun endSession() {
        active = null
    }
    private var player: MediaPlayer? = null

    private fun read(file: String): String = context.assets.open("scenes/$file").bufferedReader().use { it.readText() }

    fun play(line: SpokenLine, rate: Float = 1f) = playClip(line.audio, line.text, rate)

    fun play(response: SceneResponse) {
        val clip = response.clip ?: return
        playClip(clip.audio, response.ja)
    }

    /** A reply the traveller says, from its clip when there is one. */
    fun say(reply: StageReply) = playClip(reply.clip, reply.ja)

    fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        speech.stop()
    }

    private fun playClip(file: String, fallback: String, rate: Float = 1f) {
        stop()
        val started = runCatching {
            context.assets.openFd("scenes/audio/$file").use { fd ->
                MediaPlayer().apply {
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    prepare()
                    if (rate != 1f) playbackParams = PlaybackParams().setSpeed(rate)
                    setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                    start()
                }
            }
        }.onFailure { Log.w(TAG, "clip $file not played", it) }.getOrNull()
        if (started != null) {
            player = started
        } else {
            scope.launch { speech.speak(fallback, TargetLanguage.JA, "jp") }
        }
    }

    /** A stage image under `assets/scenes/images`, as a URI Coil reads. */
    fun imageUri(name: String): String {
        val file = imageFiles[name] ?: "$name.jpg"
        return "file:///android_asset/scenes/images/$file"
    }

    private val imageFiles: Map<String, String> by lazy {
        context.assets.list("scenes/images").orEmpty().associateBy { it.substringBeforeLast('.') }
    }

    companion object {
        private const val TAG = "Scenes"
    }
}
