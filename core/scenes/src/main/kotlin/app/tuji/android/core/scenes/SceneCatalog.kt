package app.tuji.android.core.scenes

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The 場面 bundled with the app. Everything 「進店前看一眼」 needs ships inside
// the APK, so it opens and plays in airplane mode.
//
// The ids are listed in `scenes/index.json`: the groups there are the display
// order. `released` names the scenes a release build shows; the rest are drafts
// only debug builds list. Reading the files is the caller's (`read` takes a
// file name under `scenes/`), so this decides without an Android runtime.

data class SceneGroup(val id: String, val title: LocalizedText, val scenes: List<PracticeScene>)

@Serializable
data class SceneIndex(val groups: List<Group>, val released: List<SceneId>) {
    @Serializable
    data class Group(val id: String, val title: LocalizedText, val scenes: List<SceneId>)

    val ids: List<SceneId> get() = groups.flatMap { it.scenes }
}

class SceneCatalog(groups: List<SceneGroup>) {
    val groups: List<SceneGroup> = groups.filter { it.scenes.isNotEmpty() }

    /** Every scene in display order. */
    val scenes: List<PracticeScene> get() = groups.flatMap { it.scenes }

    fun scene(id: SceneId): PracticeScene? = scenes.firstOrNull { it.id == id }

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        val empty = SceneCatalog(emptyList())

        /** One unnamed group, for tests and previews. */
        fun of(scenes: List<PracticeScene>) = SceneCatalog(listOf(SceneGroup("all", LocalizedText("", "", ""), scenes)))

        fun index(read: (String) -> String): SceneIndex = json.decodeFromString(SceneIndex.serializer(), read("index.json"))

        fun decode(id: SceneId, read: (String) -> String): PracticeScene =
            json.decodeFromString(PracticeScene.serializer(), read(id.raw + ".json"))

        /** Every scene in the index, drafts included, decoded strictly. */
        fun loadAll(read: (String) -> String): SceneCatalog {
            val index = index(read)
            return SceneCatalog(index.groups.map { group -> SceneGroup(group.id, group.title, group.scenes.map { decode(it, read) }) })
        }

        /**
         * What the screens read: drafts only when `showsDrafts`. A scene that
         * fails to decode is left out and reported; the unit tests decode them
         * strictly so that never ships.
         */
        fun load(read: (String) -> String, showsDrafts: Boolean, onError: (String, Throwable) -> Unit = { _, _ -> }): SceneCatalog {
            val index = runCatching { index(read) }.getOrElse {
                onError("index.json", it)
                return empty
            }
            val shown = (if (showsDrafts) index.ids else index.released).toSet()
            return SceneCatalog(index.groups.map { group ->
                SceneGroup(group.id, group.title, group.scenes.filter { it in shown }.mapNotNull { id ->
                    runCatching { decode(id, read) }.onFailure { onError(id.raw, it) }.getOrNull()
                })
            })
        }
    }
}

/** From the third scene on, practice is for 永久會員. 進店前看一眼 stays open to everyone. */
fun PracticeScene.canPractise(isMember: Boolean): Boolean = when (access) {
    SceneAccess.Free -> true
    SceneAccess.Lifetime -> isMember
}

/** How many 問句 one practice of the default trip has. */
val PracticeScene.practiceCount: Int get() = stage?.defaultRoute?.questions?.size ?: questions.size

/** About half a minute a question, rounded up: 6 questions is 3 minutes. */
val PracticeScene.minutes: Int get() = (practiceCount + 1) / 2

/**
 * The 1-based number practice reopens on, counted along the 問句 that measure
 * the scene: the saved cursor's, else the first unpractised.
 */
fun PracticeScene.resumeNumber(book: SceneProgressBook): Int {
    val counted = counted(book)
    val cursor = book.cursors[id]?.let { questions.getOrNull(it)?.id }
    val position = cursor?.let { id -> counted.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
        ?: counted.indexOfFirst { book.progress(it.id).stage == QuestionStage.New }.takeIf { it >= 0 }
        ?: 0
    return position + 1
}
