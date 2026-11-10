package app.tuji.android.core.scenes

// Which 場面 今天's card offers: one due for review today, else one half done,
// else the next one never opened. When every scene is done the card offers the
// last one practised, for 進店前看一眼. And the 圖鑑 → 場面 links, read off the
// 問句's `wordIds` so the content never names topics itself.

data class SceneCardPick(val scene: PracticeScene, val state: State, val practised: List<Boolean>) {
    sealed interface State {
        data object NotStarted : State

        /** `at` is the 1-based number of the question practice reopens on. */
        data class InProgress(val at: Int) : State
        data object ReviewDue : State
        data class AllDone(val status: SceneStatus) : State
    }
}

object SceneCards {
    /** `canPractise` leaves out scenes this account cannot open, so the card never leads to a lock. */
    fun pick(
        scenes: List<PracticeScene>,
        book: SceneProgressBook,
        today: LocalDay,
        canPractise: (PracticeScene) -> Boolean,
    ): SceneCardPick? {
        val statuses = scenes.filter(canPractise).map { it to ScenePractice.status(it, book, today) }
        fun card(scene: PracticeScene, state: SceneCardPick.State) = SceneCardPick(scene, state, scene.practised(book))
        statuses.firstOrNull { it.second is SceneStatus.ReviewDue }?.let { return card(it.first, SceneCardPick.State.ReviewDue) }
        statuses.firstOrNull { it.second is SceneStatus.InProgress }
            ?.let { return card(it.first, SceneCardPick.State.InProgress(it.first.resumeNumber(book))) }
        statuses.firstOrNull { it.second == SceneStatus.NotStarted }?.let { return card(it.first, SceneCardPick.State.NotStarted) }
        val last = statuses.firstOrNull { it.first.id == book.lastSceneId } ?: statuses.firstOrNull()
        return last?.let { card(it.first, SceneCardPick.State.AllDone(it.second)) }
    }
}

data class SceneMention(val scene: PracticeScene, val questions: List<SceneQuestion>)

object SceneLinks {
    /**
     * The scenes whose 問句 reference `wordId`, each with those 問句. A word page
     * opened from one scene leaves that scene out, so it does not lead back.
     */
    fun mentions(wordId: String, catalog: SceneCatalog, excluding: SceneId?): List<SceneMention> =
        catalog.scenes.mapNotNull { scene ->
            if (scene.id == excluding) return@mapNotNull null
            val questions = scene.questions.filter { wordId in it.wordIds }
            if (questions.isEmpty()) null else SceneMention(scene, questions)
        }

    /** Up to `limit` scenes that reference words of `topic`, those referencing the most first, ties in catalog order. */
    fun scenesForTopic(topic: String, topicOf: Map<String, String>, catalog: SceneCatalog, limit: Int = 2): List<PracticeScene> {
        val scenes = catalog.scenes
        val hits = scenes.map { scene -> scene.questions.flatMap { it.wordIds }.toSet().count { topicOf[it] == topic } }
        return scenes.indices
            .filter { hits[it] > 0 }
            .sortedWith(compareBy({ -hits[it] }, { it }))
            .take(limit)
            .map { scenes[it] }
    }
}
