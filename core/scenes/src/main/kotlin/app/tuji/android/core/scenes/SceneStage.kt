package app.tuji.android.core.scenes

import java.security.MessageDigest
import kotlinx.serialization.Serializable

// The immersive staging of a 場面: a photo background says where the traveller
// is, props sit on the counter, and each 問句 is answered by doing something. A
// listening 問句 offers short replies to say; a screen 問句 hands over a
// working machine. It lives in the scene's own JSON beside the questions it
// stages, keyed by question id, so progress keeps keying on the same ids.

@Serializable
data class SceneStage(
    /** The trips through this place. The first that is not optional is the one first practice takes. */
    val routes: List<StageRoute>,
    /** Keyed by question id. */
    val steps: Map<String, StageStep>,
) {
    val defaultRoute: StageRoute? get() = routes.firstOrNull { !it.isOptional } ?: routes.firstOrNull()

    fun route(id: String?): StageRoute? = id?.let { wanted -> routes.firstOrNull { it.id == wanted } }
}

/**
 * One trip: why the traveller is here, and the 問句 they meet, in order. 問句
 * on a route not taken stay unpractised and are not counted as done.
 */
@Serializable
data class StageRoute(
    val id: String,
    /** How the scene page names this trip. */
    val title: LocalizedText,
    /** What the traveller is here to do, said before anything plays. */
    val intent: LocalizedText,
    /** The button that walks in and starts the first line. */
    val enter: LocalizedText,
    val questions: List<SceneQuestionId>,
    /** Practised only when chosen from the scene page. */
    val optional: Boolean? = null,
) {
    val isOptional: Boolean get() = optional ?: false
}

@Serializable
data class StageStep(
    /** A drawable name. */
    val background: String,
    /** Props on the counter when the 問句 starts, by drawable name. */
    val props: List<String>? = null,
    /** A line about this moment, when the intent alone does not cover it. */
    val note: LocalizedText? = null,
    val replies: List<StageReply>? = null,
    val machine: StageMachine? = null,
    /** A paper slip on the counter. Its Japanese is the paper's own. */
    val card: StageCard? = null,
) {
    /** The replies in the order one phrasing shows them. */
    fun replies(phrasing: PhrasingId): List<StageReply> = mixed(replies.orEmpty(), phrasing) { it.id }
}

@Serializable
data class StageCard(val title: String, val lines: List<String>)

/**
 * A short thing to say back. `fits` says whether it fits this trip: one that
 * does not still shows what happens, then the traveller tries again.
 */
@Serializable
data class StageReply(
    val id: String,
    val ja: String,
    val meaning: LocalizedText,
    val fits: Boolean,
    /** What the traveller sees happen. */
    val result: LocalizedText,
    /** Props that appear as the result. */
    val show: List<String>? = null,
    /** False for something done rather than said: shown by its meaning, not read aloud. */
    val says: Boolean? = null,
) {
    val isSpoken: Boolean get() = says ?: true

    /**
     * Its clip's file name, named by the Japanese so every scene that uses the
     * same reply shares one (`generate-scene-audio.mjs` names it alike).
     */
    val clip: String
        get() {
            val digest = MessageDigest.getInstance("SHA-256").digest(ja.toByteArray(Charsets.UTF_8))
            return "reply-" + digest.joinToString("") { "%02x".format(it) }.take(12) + ".mp3"
        }
}

@Serializable
data class StageMachine(
    val kind: MachineKind,
    val total: Int? = null,
    val item: String? = null,
    /** The 食券機's items or the kiosk's upgrades. */
    val menu: List<MachineMenuItem>? = null,
    /** What the screen's title bar says, when not the machine's usual one. */
    val brand: String? = null,
    /** What finishing it should look like. Absent, any finish counts. */
    val expect: String? = null,
) {
    fun makeMachine(): SceneMachine = SceneMachine.make(kind, total, item, menu)

    /** Whether this finish is the one the trip wanted. */
    fun fits(outcome: MachineOutcome): Boolean {
        val expect = expect ?: return true
        return when (outcome) {
            is MachineOutcome.Paid -> outcome.method.wire == expect
            is MachineOutcome.Age -> outcome.answer == AgeAnswer.Yes && expect == "yes"
            is MachineOutcome.Ticket -> outcome.item == expect
            is MachineOutcome.KioskOrder -> outcome.option == expect
            is MachineOutcome.Ordered -> true
        }
    }
}

/**
 * `items` in an order fixed by the phrasing: stable while that phrasing is on
 * screen, different from one 問法 to the next. FNV-1a then a splitmix64 finish,
 * matching tuji-ios bit for bit so both apps show the same order.
 */
fun <T> mixed(items: List<T>, phrasing: PhrasingId, id: (T) -> String): List<T> {
    fun rank(item: T): ULong {
        var z = (phrasing.raw + "|" + id(item)).toByteArray(Charsets.UTF_8)
            .fold(14_695_981_039_346_656_037uL) { hash, byte -> (hash xor byte.toUByte().toULong()) * 1_099_511_628_211uL }
        z += 0x9E3779B97F4A7C15uL
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }
    return items.sortedBy { rank(it) }
}
