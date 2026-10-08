package app.tuji.android.manage

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import app.tuji.android.core.network.TujiJson
import app.tuji.android.core.network.ApiError
import app.tuji.android.membership.MembershipRefusal
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tuji.android.core.community.CollectionAuthoringRules
import app.tuji.android.core.community.ReviewStatus
import app.tuji.android.core.model.AtlasCollectionEdit
import app.tuji.android.core.model.AtlasCollectionMember
import app.tuji.android.core.network.CollectionAuthoring
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 編輯合集 — iOS's `CollectionEditVM` and `CollectionCandidatesModel`: its
 * words, its avatar, what is in it, and whether it is public.
 */
class CollectionEditViewModel(
    val collectionId: String,
    private val authoring: CollectionAuthoring,
    /** Something other people see changed — 物見, the author's page, 我的合集. */
    private val onChanged: () -> Unit = {},
    private val scope: CoroutineScope? = null,
    /** A write refused by plan (402) — 會員方案, beside the usual failure line. */
    private val onNeedsMembership: () -> Unit = {},
) : ViewModel() {

    /** Which action's failure to say. */
    enum class Failure { Save, Avatar, Member, Submit, Withdraw }

    /**
     * Why 加入卡片 was refused, when the server said — iOS shows its
     * `already_member` line, else the server's own sentence, else the generic one.
     */
    sealed interface AddRefusal {
        data object AlreadyMember : AddRefusal
        data class Said(val message: String) : AddRefusal
    }

    data class State(
        val collection: AtlasCollectionEdit? = null,
        val members: List<AtlasCollectionMember> = emptyList(),
        val loading: Boolean = true,
        val failed: Boolean = false,
        val title: String = "",
        val description: String = "",
        val coverId: String? = null,
        val avatarPreviewUrl: String? = null,
        val uploadingAvatar: Boolean = false,
        val savingMeta: Boolean = false,
        val metaSaved: Boolean = false,
        val submitting: Boolean = false,
        /** Null until a submit lands; then whether it went straight to 物見 or waits for review. */
        val published: Boolean? = null,
        val withdrawing: Boolean = false,
        val failure: Failure? = null,
        val candidates: List<AtlasCollectionMember> = emptyList(),
        val candidatesLoading: Boolean = false,
        val candidatesFailed: Boolean = false,
        /** Added from the picker in this visit, for its ✓. */
        val added: Set<String> = emptySet(),
        /** The last add's refusal, when the server explained it. */
        val addRefusal: AddRefusal? = null,
    ) {
        val review: ReviewStatus get() = ReviewStatus.of(collection?.reviewStatus)
        /** The words differ from what the server holds — what 儲存 lights up for, and what leaving asks about. */
        val isMetaDirty: Boolean
            get() = collection != null &&
                (title.trim() != collection.title.trim() || description.trim() != collection.description.orEmpty().trim())
        val canSaveMeta: Boolean get() = !savingMeta && CollectionAuthoringRules.titleValid(title) && isMetaDirty
        val canSubmit: Boolean get() = CollectionAuthoringRules.canSubmit(review, members, submitting)
        val canWithdraw: Boolean get() = !withdrawing && review.canWithdraw
        val unpublishedCount: Int get() = CollectionAuthoringRules.unpublishedCount(members)
        val available: List<AtlasCollectionMember>
            get() = CollectionAuthoringRules.available(candidates, members.map { it.id }.toSet() - added)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val work: CoroutineScope get() = scope ?: viewModelScope

    fun load() {
        _state.value = _state.value.copy(loading = true, failed = false)
        work.launch { reload(full = true) }
    }

    fun setTitle(value: String) {
        _state.value = _state.value.copy(title = value, metaSaved = false)
    }

    fun setDescription(value: String) {
        _state.value = _state.value.copy(description = value, metaSaved = false)
    }

    /** @param onSaved only after the server took it — 儲存並離開 leaves on this. */
    fun saveMeta(onSaved: () -> Unit = {}) {
        val now = _state.value
        if (!now.canSaveMeta) return
        _state.value = now.copy(savingMeta = true, metaSaved = false, failure = null)
        work.launch {
            val ok = attempt { writeMeta(now) }.isSuccess
            _state.value = _state.value.copy(
                savingMeta = false,
                metaSaved = ok,
                failure = if (ok) null else Failure.Save,
                // What was written is now what the server holds, so 儲存 goes dark.
                collection = if (ok) {
                    _state.value.collection?.copy(title = now.title.trim(), description = now.description.trim().ifEmpty { null })
                } else {
                    _state.value.collection
                },
            )
            if (ok) {
                onChanged()
                onSaved()
            }
        }
    }

    /** Uploaded as soon as it is cropped, as on iOS: the avatar is its own write. */
    fun uploadAvatar(jpeg: ByteArray) {
        if (_state.value.uploadingAvatar) return
        _state.value = _state.value.copy(uploadingAvatar = true, failure = null)
        work.launch {
            attempt { authoring.uploadCollectionAvatar(collectionId, jpeg) }
                .onSuccess { r ->
                    _state.value = _state.value.copy(
                        uploadingAvatar = false,
                        avatarPreviewUrl = r.avatarPreviewUrl ?: r.avatarImageUrl ?: _state.value.avatarPreviewUrl,
                        collection = _state.value.collection?.copy(avatarColor = r.avatarColor ?: _state.value.collection?.avatarColor),
                    )
                    onChanged()
                }
                .onFailure { _state.value = _state.value.copy(uploadingAvatar = false, failure = Failure.Avatar) }
        }
    }

    fun loadCandidates() {
        val language = _state.value.collection?.targetLanguage ?: return
        _state.value = _state.value.copy(candidatesLoading = true, candidatesFailed = false, added = emptySet())
        work.launch {
            attempt { authoring.collectionCandidates(language) }
                .onSuccess { _state.value = _state.value.copy(candidates = it, candidatesLoading = false) }
                .onFailure { _state.value = _state.value.copy(candidatesLoading = false, candidatesFailed = true) }
        }
    }

    /** Marked added at once, and unmarked if the server refuses — a tile that stays ✓ over nothing is worse than a second tap. */
    fun addMember(itemId: String) {
        if (itemId in _state.value.added) return
        _state.value = _state.value.copy(added = _state.value.added + itemId, failure = null, addRefusal = null)
        work.launch {
            val result = attempt { authoring.addCollectionItem(collectionId, itemId) }
            if (result.isSuccess) {
                reload(full = false)
            } else {
                _state.value = _state.value.copy(
                    added = _state.value.added - itemId,
                    failure = Failure.Member,
                    addRefusal = refusal(result.exceptionOrNull()),
                )
            }
        }
    }

    fun removeMember(itemId: String) {
        _state.value = _state.value.copy(failure = null)
        work.launch {
            if (attempt { authoring.removeCollectionItem(collectionId, itemId) }.isSuccess) {
                val removed = _state.value.members.firstOrNull { it.id == itemId }
                if (removed?.publicItemId != null && removed.publicItemId == _state.value.coverId) {
                    _state.value = _state.value.copy(coverId = null)
                }
                reload(full = false)
            } else {
                _state.value = _state.value.copy(failure = Failure.Member)
            }
        }
    }

    /** The words are saved first, so what goes to review is what is on screen. */
    fun submit() {
        val now = _state.value
        if (!now.canSubmit) return
        _state.value = now.copy(submitting = true, published = null, failure = null)
        work.launch {
            val result = attempt {
                writeMeta(now)
                authoring.publishCollection(collectionId)
            }
            result
                .onSuccess { r ->
                    _state.value = _state.value.copy(submitting = false, published = r.published)
                    onChanged()
                    reload(full = true)
                }
                .onFailure { _state.value = _state.value.copy(submitting = false, failure = Failure.Submit) }
        }
    }

    fun withdraw() {
        if (!_state.value.canWithdraw) return
        _state.value = _state.value.copy(withdrawing = true, published = null, failure = null)
        work.launch {
            val ok = attempt { authoring.withdrawCollection(collectionId) }.isSuccess
            _state.value = _state.value.copy(withdrawing = false, failure = if (ok) null else Failure.Withdraw)
            if (ok) {
                onChanged()
                reload(full = true)
            }
        }
    }

    private suspend fun writeMeta(from: State) {
        authoring.updateCollection(
            id = collectionId,
            title = from.title.trim(),
            description = from.description.trim().ifEmpty { null },
            coverPublicItemId = from.coverId,
        )
    }

    /** @param full also resets the words and avatar — only on arrival and after a state change, never over what is being typed. */
    private suspend fun reload(full: Boolean) {
        attempt { authoring.collectionForEdit(collectionId) }
            .onSuccess { r ->
                val now = _state.value
                // Typed-but-unsaved words survive a reload, as on iOS — a
                // publish or withdraw must not wipe what is in the fields.
                val keepsTypedMeta = now.isMetaDirty
                _state.value = if (full) {
                    now.copy(
                        collection = r.collection,
                        members = r.items,
                        loading = false,
                        failed = false,
                        title = if (keepsTypedMeta) now.title else r.collection.title,
                        description = if (keepsTypedMeta) now.description else r.collection.description.orEmpty(),
                        coverId = r.collection.coverPublicItemId ?: r.items.firstOrNull()?.publicItemId,
                        avatarPreviewUrl = r.collection.avatarPreviewUrl,
                    )
                } else {
                    now.copy(members = r.items, coverId = now.coverId ?: r.items.firstOrNull()?.publicItemId)
                }
            }
            .onFailure {
                _state.value = if (full && _state.value.collection == null) {
                    _state.value.copy(loading = false, failed = true)
                } else {
                    _state.value.copy(loading = false, failure = Failure.Member)
                }
            }
    }

    private suspend fun <T> attempt(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Exception) {
        Log.w(TAG, "collection edit call failed: $collectionId", failure)
        if (MembershipRefusal.isRefusal(failure)) onNeedsMembership()
        Result.failure(failure)
    }

    private fun refusal(error: Throwable?): AddRefusal? {
        val body = (error as? ApiError.Http)?.body ?: return null
        val json = runCatching { TujiJson.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        if (json["error"]?.jsonPrimitive?.contentOrNull == "already_member") return AddRefusal.AlreadyMember
        return json["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let(AddRefusal::Said)
    }

    private companion object {
        const val TAG = "TujiCollectionEdit"
    }
}
