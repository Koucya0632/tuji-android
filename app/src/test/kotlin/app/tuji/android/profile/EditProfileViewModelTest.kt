package app.tuji.android.profile

import app.tuji.android.core.community.ProfileEditFailure
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.model.UserMe
import app.tuji.android.core.network.AccountReading
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.ProfileEditing
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val me = UserMe(id = "u1", username = "TJ1", nickname = "阿貓", bio = "喜歡拍照", avatar = "https://img/a.webp")

    private class Editor(val fail: Throwable? = null) : ProfileEditing {
        val calls = mutableListOf<Triple<String, String, Pair<Boolean, Int?>>>()
        override suspend fun editProfile(nickname: String, bio: String, resetAvatar: Boolean, image: ByteArray?): AtlasAuthor {
            calls += Triple(nickname, bio, resetAvatar to image?.size)
            fail?.let { throw it }
            return AtlasAuthor(handle = "TJ1", displayName = nickname.ifEmpty { "TJ1" }, avatar = if (resetAvatar) "face" else "https://img/b.webp")
        }
    }

    private fun vm(editor: Editor = Editor(), account: () -> UserMe? = { me }, onSaved: (AtlasAuthor) -> Unit = {}) =
        EditProfileViewModel(
            accounts = object : AccountReading { override suspend fun me() = account() },
            profiles = editor,
            onSaved = onSaved,
            scope = TestScope(dispatcher),
        )

    @Test fun `the form opens on the server's profile and cannot save it unchanged`() = runTest(dispatcher) {
        val vm = vm(); vm.load(); advanceUntilIdle()
        val s = vm.state.value
        assertEquals("阿貓", s.draft.nickname)
        assertEquals("TJ1", s.uid)
        assertFalse(s.loading)
        assertFalse(s.canSave)
    }

    @Test fun `a saved edit is sent trimmed and handed back`() = runTest(dispatcher) {
        val editor = Editor()
        var saved: AtlasAuthor? = null
        val vm = vm(editor, onSaved = { saved = it })
        vm.load(); advanceUntilIdle()
        vm.setNickname("  小黑 ")
        vm.save(); advanceUntilIdle()

        assertEquals(Triple("小黑", "喜歡拍照", false to null), editor.calls.single())
        assertEquals("小黑", saved?.displayName)
        assertFalse(vm.state.value.saving)
    }

    /** The photo is what 儲存 uploads; the cat is a reset, never both. */
    @Test fun `a staged photo travels as bytes, and the cat afterwards drops it`() = runTest(dispatcher) {
        val editor = Editor()
        val vm = vm(editor)
        vm.load(); advanceUntilIdle()
        vm.stageImage(ByteArray(3))
        assertTrue(vm.state.value.canSave)
        vm.useDefaultAvatar()
        assertNull(vm.state.value.pendingImage)
        vm.save(); advanceUntilIdle()
        assertEquals(true to null, editor.calls.single().third)
    }

    @Test fun `a refusal is named from its code and keeps the form`() = runTest(dispatcher) {
        var saved = false
        val body = """{"error":"bio_rejected","message":"簽名不能包含網址、連結或不適當內容。"}"""
        val vm = vm(Editor(fail = ApiError.Http(422, body)), onSaved = { saved = true })
        vm.load(); advanceUntilIdle()
        vm.setBio("看我的 IG")
        vm.save(); advanceUntilIdle()

        assertEquals(ProfileEditFailure.BioRejected, vm.state.value.failure)
        assertEquals("看我的 IG", vm.state.value.draft.bio)
        assertFalse(saved)

        vm.setBio("看我")
        assertNull("typing again clears the old complaint", vm.state.value.failure)
    }

    /** Nothing to compare against means nothing to publish over. */
    @Test fun `a profile that could not be read cannot be saved`() = runTest(dispatcher) {
        val editor = Editor()
        val vm = vm(editor, account = { throw IOException("down") })
        vm.load(); advanceUntilIdle()
        vm.setNickname("新名字")
        vm.save(); advanceUntilIdle()
        assertTrue(editor.calls.isEmpty())
        assertFalse(vm.state.value.loading)
    }
}
