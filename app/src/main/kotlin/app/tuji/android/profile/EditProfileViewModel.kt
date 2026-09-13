package app.tuji.android.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.ProfileDraft
import app.tuji.android.core.community.ProfileEditFailure
import app.tuji.android.core.community.ProfileForm
import app.tuji.android.core.model.AtlasAuthor
import app.tuji.android.core.network.AccountReading
import app.tuji.android.core.network.ApiError
import app.tuji.android.core.network.ProfileEditing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 編輯個人資料 — iOS's `EditProfileVM`: the form, its two network calls, and
 * nothing the screen draws.
 *
 * The profile is read from the server as the screen opens rather than seeded
 * from the session: 簽名 has no other read path, and the session's copy of the
 * rest can lag the server.
 */
class EditProfileViewModel(
    private val accounts: AccountReading,
    private val profiles: ProfileEditing,
    /** The identity everyone now sees — for the session, 我, and 物見's row to mirror. */
    private val onSaved: (AtlasAuthor) -> Unit = {},
    private val scope: CoroutineScope? = null,
) : ViewModel() {

    class State(
        val draft: ProfileDraft = ProfileDraft(),
        /** Server truth for the UID; the one field here nobody can change. */
        val uid: String? = null,
        val loading: Boolean = true,
        val saving: Boolean = false,
        /** The cropped photo waiting for 儲存 — what the avatar previews and what is uploaded. */
        val pendingImage: ByteArray? = null,
        val failure: ProfileEditFailure? = null,
    ) {
        val canSave: Boolean get() = !loading && draft.canSave(saving)

        fun copy(
            draft: ProfileDraft = this.draft,
            uid: String? = this.uid,
            loading: Boolean = this.loading,
            saving: Boolean = this.saving,
            pendingImage: ByteArray? = this.pendingImage,
            failure: ProfileEditFailure? = this.failure,
        ) = State(draft, uid, loading, saving, pendingImage, failure)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun load() {
        work.launch {
            val me = try {
                accounts.me()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "profile read failed", failure)
                null
            }
            _state.value = _state.value.copy(draft = ProfileForm.seed(me), uid = me?.username, loading = false)
        }
    }

    fun setNickname(value: String) = edit { it.copy(nickname = value) }

    fun setBio(value: String) = edit { it.copy(bio = value) }

    /** A photo, cropped and encoded. Nothing is sent until 儲存. */
    fun stageImage(jpeg: ByteArray) {
        _state.value = _state.value.copy(draft = _state.value.draft.stagingImage(), pendingImage = jpeg, failure = null)
    }

    fun useDefaultAvatar() {
        _state.value = _state.value.copy(draft = _state.value.draft.usingDefaultAvatar(), pendingImage = null, failure = null)
    }

    fun save() {
        val now = _state.value
        if (!now.canSave) return
        _state.value = now.copy(saving = true, failure = null)
        val draft = now.draft
        work.launch {
            val author = try {
                profiles.editProfile(
                    nickname = draft.trimmedNickname,
                    bio = draft.trimmedBio,
                    resetAvatar = draft.resetsAvatar,
                    image = now.pendingImage,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "profile save failed", failure)
                _state.value = _state.value.copy(
                    saving = false,
                    failure = ProfileEditFailure.from((failure as? ApiError.Http)?.body),
                )
                return@launch
            }
            _state.value = _state.value.copy(saving = false)
            onSaved(author)
        }
    }

    private fun edit(change: (ProfileDraft) -> ProfileDraft) {
        _state.value = _state.value.copy(draft = change(_state.value.draft), failure = null)
    }

    private companion object {
        const val TAG = "TujiEditProfile"
    }
}
