package app.tuji.android.wordlists

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyCard
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueItem
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.StudyQueueWord
import app.tuji.android.core.model.WordList
import app.tuji.android.core.model.WordListDetailResponse
import app.tuji.android.core.model.WordListStats
import app.tuji.android.core.model.WordListsResponse
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.WordListsAccess
import app.tuji.android.membership.MemberWriteOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListsTest {

    private val en = LearningDirection.entries.first()
    private val other = LearningDirection.entries.last()

    private class Server(var lists: List<WordList> = emptyList()) : WordListsAccess {
        var reads = 0
        var lastOrder: List<String>? = null
        var writeError: Exception? = null
        var lastQueue: Triple<String, StudyMode, Int>? = null
        var detail: WordListDetailResponse? = null
        val learnings = mutableListOf<LearningDirection>()

        override suspend fun wordLists(learning: LearningDirection, containing: String?): WordListsResponse {
            reads++
            learnings += learning
            return WordListsResponse(available = true, canCreate = true, lists = lists)
        }

        override suspend fun wordList(id: String) = detail ?: throw ApiError.Http(404, null)

        override suspend fun createWordList(learning: LearningDirection, name: String): WordList {
            writeError?.let { throw it }
            return WordList(id = "new", name = name).also { lists = lists + it }
        }

        override suspend fun renameWordList(id: String, name: String) { writeError?.let { throw it } }

        override suspend fun deleteWordList(id: String) {
            writeError?.let { throw it }
            lists = lists.filterNot { it.id == id }
        }

        override suspend fun reorderWordLists(learning: LearningDirection, ids: List<String>) {
            writeError?.let { throw it }
            lastOrder = ids
        }

        override suspend fun setWordInList(listId: String, wordId: String, present: Boolean) {
            writeError?.let { throw it }
            detail = detail?.let { d -> d.copy(wordIds = if (present) d.wordIds + wordId else d.wordIds - wordId) }
        }

        override suspend fun wordListQueue(
            listId: String, mode: StudyMode, limit: Int, lang: String, learning: LearningDirection,
        ): StudyQueueResponse {
            lastQueue = Triple(listId, mode, limit)
            val a = StudyQueueItem(card = StudyCard(id = "c1"), word = StudyQueueWord(id = "w1", word = "cat", chinese = "貓", imageUrl = "", pronunciation = "", category = "animals"))
            val b = StudyQueueItem(card = StudyCard(id = "c2"), word = StudyQueueWord(id = "w1", word = "cat", chinese = "貓", imageUrl = "", pronunciation = "", category = "animals"))
            return StudyQueueResponse(queue = listOf(a, b))
        }
    }

    private fun list(id: String, count: Int = 0) = WordList(id = id, name = id, wordCount = count)

    @Test fun `lists are read once per language`() = runTest {
        val server = Server(listOf(list("a")))
        var direction = en
        val store = WordListsStore(server) { direction }
        store.loadIfNeeded()
        store.loadIfNeeded()
        assertEquals(1, server.reads)
        direction = other
        store.loadIfNeeded()
        assertEquals(listOf(en, other), server.learnings)
    }

    @Test fun `moving is optimistic and put back when refused`() = runTest {
        val server = Server(listOf(list("a"), list("b"), list("c")))
        val store = WordListsStore(server) { en }
        store.loadIfNeeded()
        assertEquals(MemberWriteOutcome.Done, store.move("c", -1))
        assertEquals(listOf("a", "c", "b"), server.lastOrder)

        server.writeError = ApiError.Http(500, null)
        assertEquals(MemberWriteOutcome.Failed, store.move("a", 1))
        assertEquals(listOf("a", "b", "c"), store.state.value.lists.map { it.id })
    }

    @Test fun `a move off either end does nothing`() = runTest {
        val server = Server(listOf(list("a"), list("b")))
        val store = WordListsStore(server) { en }
        store.loadIfNeeded()
        assertEquals(MemberWriteOutcome.Done, store.move("a", -1))
        assertEquals(null, server.lastOrder)
    }

    @Test fun `deleting a list already gone is done`() = runTest {
        val server = Server(listOf(list("a")))
        val store = WordListsStore(server) { en }
        store.loadIfNeeded()
        server.writeError = ApiError.Http(404, null)
        assertEquals(MemberWriteOutcome.Done, store.delete("a"))
        assertTrue(store.state.value.lists.isEmpty())
    }

    @Test fun `a refused create says to upgrade`() = runTest {
        val store = WordListsStore(Server().apply { writeError = ApiError.Http(402, null) }) { en }
        assertEquals(MemberWriteOutcome.NeedsUpgrade, store.create("kitchen").first)
    }

    @Test fun `ticking a word moves the count and the tick`() = runTest {
        val server = Server(listOf(list("a", count = 2)))
        val store = WordListsStore(server) { en }
        store.loadIfNeeded()
        assertEquals(MemberWriteOutcome.Done, store.setWord("oven", "a", present = true))
        assertEquals(3, store.state.value.lists.single().wordCount)
        assertEquals(true, store.state.value.lists.single().containsWord)
    }

    @Test fun `a list queue asks for the list and keeps one card per word`() = runTest {
        val server = Server()
        val queue = WordListQueue(server, "l1")
        val response = queue.queue(StudyMode.Review, 30, 0, listOf("fruits"), "zh-Hant", en)
        assertEquals(Triple("l1", StudyMode.Review, 30), server.lastQueue)
        assertEquals(1, response.queue.size)
    }

    @Test fun `only modes with something in them are offered`() {
        fun detail(total: Int, seen: Int, due: Int, canStudy: Boolean = true) = WordListDetailResponse(
            list = list("a"), stats = WordListStats(total, seen, due), canStudy = canStudy,
        )
        assertEquals(listOf(StudyMode.New, StudyMode.Review), WordListDetailViewModel.studyModes(detail(5, 2, 1)))
        assertEquals(listOf(StudyMode.Review), WordListDetailViewModel.studyModes(detail(5, 5, 1)))
        assertEquals(emptyList<StudyMode>(), WordListDetailViewModel.studyModes(detail(5, 5, 0)))
        assertEquals(emptyList<StudyMode>(), WordListDetailViewModel.studyModes(detail(5, 2, 1, canStudy = false)))
    }

    @Test fun `a list deleted elsewhere is missing, and a refused removal puts the word back`() = runTest {
        val server = Server()
        val gone = WordListDetailViewModel("a", server, scope = this)
        gone.refresh()
        assertEquals(WordListDetailViewModel.State.Missing, gone.state.value)

        server.detail = WordListDetailResponse(list = list("a"), wordIds = listOf("oven", "pan"), canEdit = true)
        val vm = WordListDetailViewModel("a", server, scope = this)
        vm.refresh()
        server.writeError = ApiError.Http(500, null)
        assertEquals(MemberWriteOutcome.Failed, vm.remove("oven"))
        val loaded = vm.state.value as WordListDetailViewModel.State.Loaded
        assertEquals(listOf("oven", "pan"), loaded.detail.wordIds)
    }
}
