package app.tuji.android.capture

import app.tuji.android.core.model.AtlasCandidate
import app.tuji.android.core.model.AtlasCard
import app.tuji.android.core.model.AtlasConfirmPayload
import app.tuji.android.core.model.AtlasImageSummary
import app.tuji.android.core.model.AtlasItem
import app.tuji.android.core.model.AtlasModeration
import app.tuji.android.core.model.AtlasPublishResult
import app.tuji.android.core.model.AtlasRecognitionResponse
import app.tuji.android.core.model.AtlasUploadResponse
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.RecognitionMode
import app.tuji.android.core.model.TargetLanguage
import app.tuji.android.core.network.AtlasAuthoring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun candidate(id: String, label: String = id, rank: Int = 0) =
        AtlasCandidate(id = id, label = label, zhHant = "中$id", rank = rank)

    private class Fake(
        val uploadCandidates: List<AtlasCandidate> = emptyList(),
        val escalateCandidates: List<AtlasCandidate> = emptyList(),
        val failUpload: Boolean = false,
        val failConfirm: Boolean = false,
        val failCards: Boolean = false,
        val failPublish: Boolean = false,
        val publishGoesLive: Boolean = true,
    ) : AtlasAuthoring {
        var publishes = 0
        var uploads = 0
        var recognitions = 0
        var confirms = 0
        var cardCalls = 0
        var lastPayload: AtlasConfirmPayload? = null

        override suspend fun uploadImage(
            bytes: ByteArray, filename: String, mimeType: String, targetLanguage: TargetLanguage?,
        ): AtlasUploadResponse {
            uploads += 1
            if (failUpload) throw IOException("no")
            return AtlasUploadResponse(
                image = AtlasImageSummary(id = "img1"),
                candidates = uploadCandidates,
                targetLanguage = targetLanguage,
            )
        }

        override suspend fun recognize(imageId: String, mode: RecognitionMode) =
            AtlasRecognitionResponse(candidates = escalateCandidates).also { recognitions += 1 }

        override suspend fun confirm(imageId: String, payload: AtlasConfirmPayload): AtlasItem {
            confirms += 1
            lastPayload = payload
            if (failConfirm) throw IOException("no")
            return AtlasItem(id = "item1", lemma = payload.lemma)
        }

        override suspend fun createCards(itemId: String, cardTypes: List<String>): List<AtlasCard> {
            cardCalls += 1
            if (failCards) throw IOException("no")
            return cardTypes.map { AtlasCard(id = "$itemId-$it", cardType = it) }
        }

        override suspend fun publish(itemId: String): AtlasPublishResult {
            publishes += 1
            if (failPublish) throw IOException("no")
            return AtlasPublishResult(
                moderation = AtlasModeration(published = publishGoesLive),
            )
        }
    }

    private fun vm(fake: AtlasAuthoring) = CaptureViewModel(
        authoring = fake,
        direction = LearningDirection.ZH_JA,
        scope = TestScope(dispatcher),
    )

    private fun CaptureViewModel.naming() = step.value as CaptureViewModel.Step.Naming

    @Test fun `a photo comes back with candidates already picked`() = runTest(dispatcher) {
        // Recognition runs inside the upload, so one round trip gets both.
        val fake = Fake(uploadCandidates = listOf(candidate("b", rank = 1), candidate("a", rank = 0)))
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()

        val s = vm.naming()
        assertEquals("img1", s.image.id)
        assertEquals("the highest-ranked one is chosen for them", "a", s.draft.selectedCandidateId)
        assertEquals("a", s.draft.lemma)
        assertEquals(1, fake.uploads)
    }

    @Test fun `switching to 精準識別 spends one call, and switching back spends none`() =
        runTest(dispatcher) {
            // The key behind this is shared with 補資料 and the web app.
            val fake = Fake(
                uploadCandidates = listOf(candidate("a")),
                escalateCandidates = listOf(candidate("z")),
            )
            val vm = vm(fake)
            vm.submit(ByteArray(4)); advanceUntilIdle()

            vm.setMode(RecognitionMode.Escalate); advanceUntilIdle()
            assertEquals(1, fake.recognitions)
            assertEquals("z", vm.naming().draft.selectedCandidateId)

            vm.setMode(RecognitionMode.Primary); advanceUntilIdle()
            vm.setMode(RecognitionMode.Escalate); advanceUntilIdle()
            assertEquals("a second look at the same photo is not worth another call", 1, fake.recognitions)
        }

    @Test fun `a recognition that finds nothing is not re-bought`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = listOf(candidate("a")), escalateCandidates = emptyList())
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.setMode(RecognitionMode.Escalate); advanceUntilIdle()
        vm.setMode(RecognitionMode.Primary); advanceUntilIdle()
        vm.setMode(RecognitionMode.Escalate); advanceUntilIdle()
        assertEquals(1, fake.recognitions)
    }

    @Test fun `confirming makes the item and its one card`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = listOf(candidate("a", label = "マグカップ")))
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()

        val made = vm.step.value as CaptureViewModel.Step.Made
        assertEquals("item1", made.item.id)
        // One, not two. The server collapses any request to a single card, and
        // asking for two is how a screen ends up promising 「2 張卡」 for one.
        assertEquals(1, made.cards)
        assertEquals("マグカップ", fake.lastPayload!!.lemma)
    }

    @Test fun `a photo with no name cannot be confirmed`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = emptyList())
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()
        assertEquals(0, fake.confirms)
        assertTrue(vm.step.value is CaptureViewModel.Step.Naming)
    }

    @Test fun `typing a name over an empty recognition still works`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = emptyList())
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.edit(lemma = "やかん", zhHant = "水壺")
        vm.confirm(); advanceUntilIdle()

        val payload = fake.lastPayload!!
        assertEquals("やかん", payload.lemma)
        assertEquals("a hand-typed word still needs a primary label", "やかん", payload.primaryLabel)
    }

    @Test fun `a failed upload says so instead of a blank screen`() = runTest(dispatcher) {
        val vm = vm(Fake(failUpload = true))
        vm.submit(ByteArray(4)); advanceUntilIdle()
        assertTrue(vm.step.value is CaptureViewModel.Step.Failed)
    }

    @Test fun `a failed confirm leaves the form as it was`() = runTest(dispatcher) {
        // Everything the user typed is still on screen; they tap again.
        val fake = Fake(uploadCandidates = listOf(candidate("a")), failConfirm = true)
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()
        val s = vm.naming()
        assertEquals("a", s.draft.selectedCandidateId)
        assertEquals(false, s.busy)
    }

    @Test fun `cards failing does not lose the item that was made`() = runTest(dispatcher) {
        // The item exists on the server. Reporting a total failure would tell
        // the user to try again and produce a second copy.
        val fake = Fake(uploadCandidates = listOf(candidate("a")), failCards = true)
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()
        val made = vm.step.value as CaptureViewModel.Step.Made
        assertEquals("item1", made.item.id)
        assertEquals(0, made.cards)
    }

    @Test fun `publishing is a decision, not a side effect of confirming`() =
        runTest(dispatcher) {
            val fake = Fake(uploadCandidates = listOf(candidate("a")))
            val vm = vm(fake)
            vm.submit(ByteArray(4)); advanceUntilIdle()
            vm.confirm(); advanceUntilIdle()
            // The card exists and nothing is on the public feed yet.
            assertEquals(0, fake.publishes)
        }

    @Test fun `a queued submission is not reported as live`() = runTest(dispatcher) {
        // "It is live" and "someone will look at it" are different sentences,
        // and the feed contradicts the first within a minute.
        val fake = Fake(uploadCandidates = listOf(candidate("a")), publishGoesLive = false)
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()
        vm.publish(); advanceUntilIdle()

        val made = vm.step.value as CaptureViewModel.Step.Made
        assertEquals(CaptureViewModel.PublishOutcome.Queued, made.publish)
    }

    @Test fun `a clean submission says it is live`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = listOf(candidate("a")))
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()
        vm.publish(); advanceUntilIdle()
        assertEquals(
            CaptureViewModel.PublishOutcome.Published,
            (vm.step.value as CaptureViewModel.Step.Made).publish,
        )
    }

    @Test fun `a failed publish does not lose the card`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = listOf(candidate("a")), failPublish = true)
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()
        vm.publish(); advanceUntilIdle()
        val made = vm.step.value as CaptureViewModel.Step.Made
        assertEquals(CaptureViewModel.PublishOutcome.Failed, made.publish)
        assertEquals("the card is still theirs", "item1", made.item.id)
    }

    @Test fun `publishing twice does not offer it twice`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = listOf(candidate("a")))
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()
        vm.publish(); advanceUntilIdle()
        vm.publish(); advanceUntilIdle()
        assertEquals(1, fake.publishes)
    }

    @Test fun `a second confirm tap while sending does not make two items`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = listOf(candidate("a")))
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); vm.confirm()
        advanceUntilIdle()
        assertEquals(1, fake.confirms)
    }
}
