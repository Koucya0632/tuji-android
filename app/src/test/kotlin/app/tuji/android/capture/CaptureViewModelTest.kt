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
import org.junit.Assert.assertNull
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

    /** What `confirm` handed to 生成佇列, which is now all it does. */
    private val enqueued = mutableListOf<Triple<String, AtlasConfirmPayload, String?>>()

    private fun vm(fake: AtlasAuthoring) = CaptureViewModel(
        authoring = fake,
        direction = LearningDirection.ZH_JA,
        scope = TestScope(dispatcher),
        enqueue = { imageId, payload, thumb -> enqueued += Triple(imageId, payload, thumb) },
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

    @Test fun `confirming hands the capture to the queue and leaves`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = listOf(candidate("a", label = "マグカップ")))
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); advanceUntilIdle()

        // **Nothing was created here.** confirm and createCards belong to the
        // queue now, which is the whole point: the screen is free to close.
        assertEquals(0, fake.confirms)
        assertEquals(0, fake.cardCalls)
        assertEquals(CaptureViewModel.Step.Queued, vm.step.value)
        assertEquals(1, enqueued.size)
        assertEquals("マグカップ", enqueued.single().second.lemma)
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

        val payload = enqueued.single().second
        assertEquals("やかん", payload.lemma)
        assertEquals("a hand-typed word still needs a primary label", "やかん", payload.primaryLabel)
    }

    @Test fun `a failed upload says so instead of a blank screen`() = runTest(dispatcher) {
        val vm = vm(Fake(failUpload = true))
        vm.submit(ByteArray(4)); advanceUntilIdle()
        assertTrue(vm.step.value is CaptureViewModel.Step.Failed)
    }

    @Test fun `a second confirm tap does not queue the same capture twice`() = runTest(dispatcher) {
        val fake = Fake(uploadCandidates = listOf(candidate("a")))
        val vm = vm(fake)
        vm.submit(ByteArray(4)); advanceUntilIdle()
        vm.confirm(); vm.confirm()
        advanceUntilIdle()
        // The first tap leaves `Naming`, so the second has nothing to confirm.
        assertEquals(1, enqueued.size)
    }
}
