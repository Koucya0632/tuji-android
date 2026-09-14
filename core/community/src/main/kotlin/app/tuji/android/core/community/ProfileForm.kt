package app.tuji.android.core.community

import app.tuji.android.core.model.UserMe

/**
 * 編輯個人資料's rules — iOS's `EditProfileVM`, without the screen.
 *
 * Everything on the form is public: the UID is machine-minted and immutable,
 * and no name is ever written that the user did not type here. So the rules
 * are only about the draft — what counts as a change, and what the server
 * will refuse before it is asked.
 */
data class ProfileDraft(
    val nickname: String = "",
    val bio: String = "",
    /** An https URL, or [ProfileForm.DEFAULT_AVATAR]. */
    val avatar: String = ProfileForm.DEFAULT_AVATAR,
    /** A cropped photo waiting for 儲存. It is what gets uploaded, so it is what makes the form dirty. */
    val hasPendingImage: Boolean = false,
    /** What the server had when the screen opened. Null until it answered. */
    val saved: ProfileFields? = null,
) {
    // Trimmed the way the server trims, and counted the way it counts —
    // UTF-16 units, JavaScript's `length` — so the counter never says 1 left
    // over a string the route rejects.
    val trimmedNickname: String get() = nickname.trim()
    val trimmedBio: String get() = bio.trim()

    val nicknameValid: Boolean get() = trimmedNickname.length <= ProfileForm.NICKNAME_MAX
    val bioValid: Boolean get() = trimmedBio.length <= ProfileForm.BIO_MAX
    val bioRemaining: Int get() = ProfileForm.BIO_MAX - trimmedBio.length

    val dirty: Boolean
        get() {
            val saved = saved ?: return false
            return hasPendingImage || ProfileFields(trimmedNickname, trimmedBio, avatar) != saved
        }

    val hasCustomAvatar: Boolean get() = hasPendingImage || ProfileForm.isPicture(avatar)

    fun canSave(saving: Boolean): Boolean = dirty && !saving && nicknameValid && bioValid

    /**
     * Whether 儲存 should send the reset. Only when the black cat was chosen
     * over a picture that was saved — a staged photo travels as bytes, and a
     * reset sent beside it would contradict it.
     */
    val resetsAvatar: Boolean
        get() = !hasPendingImage && avatar == ProfileForm.DEFAULT_AVATAR && saved?.avatar != ProfileForm.DEFAULT_AVATAR

    fun stagingImage(): ProfileDraft = copy(hasPendingImage = true)

    /** Choosing the cat clears a staged photo, or it would win at 儲存 and silently undo the choice. */
    fun usingDefaultAvatar(): ProfileDraft = copy(hasPendingImage = false, avatar = ProfileForm.DEFAULT_AVATAR)
}

/** A profile as the server has it — what an edit is compared against. */
data class ProfileFields(val nickname: String, val bio: String, val avatar: String)

object ProfileForm {
    const val NICKNAME_MAX = 20
    const val BIO_MAX = 80

    /** The one built-in avatar. Every value that is not an https URL means it. */
    const val DEFAULT_AVATAR = "face"

    fun isPicture(avatar: String?): Boolean = avatar?.startsWith("https://") == true

    /**
     * The form as it opens. A failed read leaves the fields empty and the draft
     * unsaved-against-nothing, so 儲存 stays off rather than publishing blanks
     * over a profile the screen never saw.
     */
    fun seed(server: UserMe?): ProfileDraft {
        server ?: return ProfileDraft()
        val fields = ProfileFields(
            nickname = server.nickname?.trim().orEmpty(),
            bio = server.bio?.trim().orEmpty(),
            avatar = server.avatar?.takeIf(::isPicture) ?: DEFAULT_AVATAR,
        )
        return ProfileDraft(nickname = fields.nickname, bio = fields.bio, avatar = fields.avatar, saved = fields)
    }
}

/**
 * Why 儲存 failed, in words the reader can act on.
 *
 * The route answers in zh-Hant only, so its `message` cannot be shown to a
 * reader in English or Japanese; its `error` code is what gets read.
 */
enum class ProfileEditFailure {
    NicknameTooLong,
    /** A link, personal information, or something unfit to show. */
    NicknameRejected,
    BioTooLong,
    BioRejected,
    AvatarRejected,
    /** The photo check itself is down — try again later, not a different photo. */
    ModerationUnavailable,
    InvalidImage,
    Failed,
    ;

    companion object {
        private val code = Regex(""""error"\s*:\s*"([a-z_]+)"""")

        fun from(body: String?): ProfileEditFailure = when (body?.let { code.find(it)?.groupValues?.get(1) }) {
            "nickname_too_long" -> NicknameTooLong
            "nickname_rejected", "invalid_nickname" -> NicknameRejected
            "bio_too_long" -> BioTooLong
            "bio_rejected", "invalid_bio" -> BioRejected
            "avatar_rejected" -> AvatarRejected
            "moderation_unavailable" -> ModerationUnavailable
            "invalid_image" -> InvalidImage
            else -> Failed
        }
    }
}
