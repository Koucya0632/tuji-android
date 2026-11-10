package app.tuji.android.core.scenes

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scheduling, phrasing choice, per-account progress and resume — the rules tuji-ios's tests hold. */
class ScenePracticeTest {
    private fun phrasing(id: String, role: String, speed: String = "normal", ambience: Boolean = false) =
        """{"id":"$id","role":"$role","speaker":"clerk","text":"温めますか？","voice":"Aoede","speed":"$speed","ambience":$ambience,"screen":null,"audio":"$id.mp3"}"""

    private fun question(id: String) = """
        {"id":"$id","kind":"listening","goal":{"zh-Hant":"g","zh-Hans":"g","en":"g"},
         "phrasings":[${phrasing("$id.p1", "first")},${phrasing("$id.p2", "review")},${phrasing("$id.p3", "review", "fast", true)},${phrasing("$id.p4", "review")}],
         "choices":[{"id":"a","text":{"zh-Hant":"a","zh-Hans":"a","en":"a"},"correct":true},
                    {"id":"b","text":{"zh-Hant":"b","zh-Hans":"b","en":"b"},"correct":false}],
         "response":{"ja":"お願いします","audio":null,"voice":null},
         "explanation":{"zh-Hant":"e","zh-Hans":"e","en":"e"},"wordIds":[]}
    """.trimIndent()

    private val scene: PracticeScene = SceneCatalog.json.decodeFromString(
        PracticeScene.serializer(),
        """{"schemaVersion":2,"id":"s","revision":1,"access":"free",
            "title":{"zh-Hant":"t","zh-Hans":"t","en":"t"},"summary":{"zh-Hant":"s","zh-Hans":"s","en":"s"},
            "questions":[${listOf("s.q0", "s.q1", "s.q2").joinToString(",") { question(it) }}]}""",
    )

    private val nov8 = LocalDay.of(2026, 11, 8)
    private val nov9 = LocalDay.of(2026, 11, 9)

    private fun store() = SceneProgressStore(Files.createTempDirectory("scenes").toFile()).apply { use("acct") }

    @Test fun `first practice uses the first phrasing, a review the fast one with ambience`() {
        val q = scene.questions[0]
        assertEquals("s.q0.p1", ScenePractice.phrasing(q, QuestionProgress(), PracticeMode.First)?.id?.raw)
        val due = QuestionProgress(QuestionStage.AwaitingReview(nov9), setOf(PhrasingId("s.q0.p1")))
        assertEquals("s.q0.p3", ScenePractice.phrasing(q, due, PracticeMode.Review)?.id?.raw)
    }

    @Test fun `a review never repeats a heard phrasing, and a rehearsal before it is due does not spend one`() {
        val q = scene.questions[0]
        val heard = QuestionProgress(QuestionStage.Reviewed, setOf(PhrasingId("s.q0.p1"), PhrasingId("s.q0.p3")))
        assertEquals("s.q0.p2", ScenePractice.phrasing(q, heard, PracticeMode.Review)?.id?.raw)
        val early = QuestionProgress(QuestionStage.AwaitingReview(nov9))
        assertEquals("s.q0.p1", ScenePractice.phrasing(q, early, PracticeMode.Rehearsal)?.id?.raw)
    }

    @Test fun `a review is due the next local day, and an overdue one is still due`() {
        val step = ScenePractice.plan(scene, SceneProgressBook(), nov8).first()
        val after = ScenePractice.completing(QuestionProgress(), step, nov8)
        assertEquals(QuestionStage.AwaitingReview(nov9), after.stage)
        assertEquals(PracticeMode.Rehearsal, ScenePractice.mode(after, nov8))
        assertEquals(PracticeMode.Review, ScenePractice.mode(after, nov9))
        assertEquals(PracticeMode.Review, ScenePractice.mode(after, LocalDay.of(2026, 11, 20)))
        assertEquals(LocalDay.of(2027, 1, 1), LocalDay.of(2026, 12, 31).addingDays(1))
    }

    @Test fun `a pass that ends on the reveal is recorded, and the next review is tomorrow`() {
        val store = store()
        val session = ScenePracticeSession(scene, store, today = { nov8 })
        repeat(3) {
            session.answer("b")
            session.answer("a")
            session.next()
        }
        val finished = session.phase.value as ScenePracticeSession.Phase.Finished
        assertEquals(SceneStatus.ReviewLater(nov9), finished.summary.status)
        assertEquals(listOf(1, 1, 1), finished.summary.results.map { it.wrongTries })
        assertTrue(store.book.value.cursors.isEmpty())
        assertEquals(SceneId("s"), store.book.value.lastSceneId)
    }

    @Test fun `quitting before the reveal reopens that 問句, from a new session on the same file`() {
        val dir = Files.createTempDirectory("scenes").toFile()
        val first = SceneProgressStore(dir).apply { use("acct") }
        ScenePracticeSession(scene, first, today = { nov8 }).apply {
            answer("a")
            next()
        }
        val again = SceneProgressStore(dir).apply { use("acct") }
        val session = ScenePracticeSession(scene, again, today = { nov8 })
        assertEquals(1, session.currentStep?.questionIndex)
        assertEquals(2, scene.resumeNumber(again.book.value))
    }

    @Test fun `missed or hinted 問句 come first on the summary`() {
        val session = ScenePracticeSession(scene, store(), today = { nov8 })
        session.answer("a"); session.next()
        session.noteHint(); session.answer("a"); session.next()
        session.answer("b"); session.answer("a"); session.next()
        val results = (session.phase.value as ScenePracticeSession.Phase.Finished).summary.results
        assertEquals(listOf("s.q1", "s.q2", "s.q0"), results.map { it.question.id.raw })
    }

    @Test fun `the reply last said on a step is kept, and nothing is said once revealed`() {
        val session = ScenePracticeSession(scene, store(), today = { nov8 })
        val no = StageReply("no", "大丈夫です", LocalizedText("", "", ""), false, LocalizedText("", "", ""))
        val yes = StageReply("yes", "お願いします", LocalizedText("", "", ""), true, LocalizedText("", "", ""))
        session.reply(no)
        assertEquals("no", session.chosenReply(0))
        session.reply(yes)
        assertEquals("yes", session.chosenReply(0))
        session.reply(no)
        assertEquals("yes", session.chosenReply(0))
        assertNull(session.chosenReply(1))
    }

    @Test fun `asking again plays an unheard phrasing and marks it heard`() {
        val store = store()
        val session = ScenePracticeSession(scene, store, today = { nov8 })
        assertEquals("s.q0.p2.mp3", session.askAgain()?.audio)
        assertEquals("s.q0.p3.mp3", session.askAgain()?.audio)
        assertEquals("s.q0.p4.mp3", session.askAgain()?.audio)
        assertNull(session.askAgain())
        assertEquals(3, store.book.value.progress(SceneQuestionId("s.q0")).usedPhrasingIds.size)
    }

    @Test fun `status walks not started, in progress, review due, complete`() {
        var book = SceneProgressBook()
        assertEquals(SceneStatus.NotStarted, ScenePractice.status(scene, book, nov8))
        book = book.copy(questions = mapOf(SceneQuestionId("s.q0") to QuestionProgress(QuestionStage.AwaitingReview(nov9))))
        assertEquals(SceneStatus.InProgress(1, 3), ScenePractice.status(scene, book, nov8))
        assertEquals(SceneStatus.ReviewDue(1), ScenePractice.status(scene, book, nov9))
        book = book.copy(questions = scene.questions.associate { it.id to QuestionProgress(QuestionStage.Reviewed) })
        assertEquals(SceneStatus.Complete, ScenePractice.status(scene, book, nov9))
    }

    @Test fun `the progress file round-trips`() {
        val dir = Files.createTempDirectory("scenes").toFile()
        val store = SceneProgressStore(dir).apply { use("ACCT") }
        store.update { it.copy(questions = mapOf(SceneQuestionId("s.q0") to QuestionProgress(QuestionStage.AwaitingReview(nov9), setOf(PhrasingId("p")))), cursors = mapOf(SceneId("s") to 2)) }
        val back = SceneProgressStore(dir).apply { use("ACCT") }.book.value
        assertEquals(store.book.value, back)
        assertTrue(dir.resolve("acct.json").exists())
    }

    @Test fun `today's card prefers a due review, then a half-done scene, then a new one`() {
        val other = scene.copy(id = SceneId("t"))
        val book = SceneProgressBook(questions = mapOf(SceneQuestionId("s.q0") to QuestionProgress(QuestionStage.AwaitingReview(nov9))))
        assertTrue(SceneCards.pick(listOf(other, scene), book, nov8) { true }!!.state is SceneCardPick.State.InProgress)
        assertEquals(SceneCardPick.State.ReviewDue, SceneCards.pick(listOf(other, scene), book, nov9) { true }!!.state)
        assertEquals(SceneId("t"), SceneCards.pick(listOf(other, scene), SceneProgressBook(), nov8) { true }!!.scene.id)
        assertNull(SceneCards.pick(listOf(scene), SceneProgressBook(), nov8) { false })
    }
}
