package app.tuji.android.core.community

import app.tuji.android.core.model.UserMe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormTest {

    private val me = UserMe(id = "u1", username = "TJ1", nickname = "阿貓", bio = "喜歡拍照", avatar = "https://img/a.webp")

    @Test fun `an unchanged form cannot be saved`() {
        val draft = ProfileForm.seed(me)
        assertFalse(draft.dirty)
        assertFalse(draft.canSave(saving = false))
    }

    @Test fun `whitespace around a field is not a change`() {
        val draft = ProfileForm.seed(me).copy(nickname = "  阿貓 ", bio = "喜歡拍照\n")
        assertFalse(draft.dirty)
    }

    /** Without the server's copy there is nothing to compare against — and nothing to publish blanks over. */
    @Test fun `a form the server never answered stays unsaveable`() {
        val draft = ProfileForm.seed(null).copy(nickname = "新名字")
        assertFalse(draft.canSave(saving = false))
    }

    @Test fun `the limits are the server's, counted the way it counts`() {
        val draft = ProfileForm.seed(me).copy(nickname = "a".repeat(21))
        assertTrue(draft.dirty)
        assertFalse(draft.nicknameValid)
        assertFalse(draft.canSave(saving = false))

        val bio = ProfileForm.seed(me).copy(bio = "字".repeat(80))
        assertTrue(bio.bioValid)
        assertEquals(0, bio.bioRemaining)
        assertFalse(bio.copy(bio = "字".repeat(81)).bioValid)
    }

    @Test fun `a staged photo is a change and never travels with a reset`() {
        val draft = ProfileForm.seed(me).stagingImage()
        assertTrue(draft.dirty)
        assertFalse(draft.resetsAvatar)
        assertTrue(draft.hasCustomAvatar)
    }

    /** Otherwise the pending photo would win at 儲存 and silently undo the choice. */
    @Test fun `choosing the cat drops a staged photo and resets a saved one`() {
        val draft = ProfileForm.seed(me).stagingImage().usingDefaultAvatar()
        assertFalse(draft.hasPendingImage)
        assertTrue(draft.resetsAvatar)
        assertFalse(draft.hasCustomAvatar)
    }

    @Test fun `the cat over the cat sends no reset`() {
        val draft = ProfileForm.seed(me.copy(avatar = "face")).usingDefaultAvatar()
        assertFalse(draft.dirty)
        assertFalse(draft.resetsAvatar)
    }

    @Test fun `anything that is not an https URL is the cat`() {
        assertEquals(ProfileForm.DEFAULT_AVATAR, ProfileForm.seed(me.copy(avatar = "cat_03")).avatar)
    }

    @Test fun `the server's code is read, not its zh-Hant message`() {
        assertEquals(
            ProfileEditFailure.NicknameRejected,
            ProfileEditFailure.from("""{"error":"nickname_rejected","message":"暱稱不能包含個人資訊（電話、email、地址等）。"}"""),
        )
        assertEquals(ProfileEditFailure.ModerationUnavailable, ProfileEditFailure.from("""{"error": "moderation_unavailable"}"""))
        assertEquals(ProfileEditFailure.Failed, ProfileEditFailure.from("<html>502</html>"))
        assertEquals(ProfileEditFailure.Failed, ProfileEditFailure.from(null))
    }
}
