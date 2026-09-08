package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * The signed-in account.
 *
 * `username` is the immutable TJ UID and `nickname` is what the user chose;
 * the fallback runs nickname → UID, never the other way. That direction was
 * reversed once, and the symptom was a profile page that went grey.
 */
@Serializable
data class UserMe(
    val id: String,
    val username: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val email: String? = null,
    val bio: String? = null,
) {
    val displayName: String
        get() = nickname?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: id.take(8)
}

@Serializable
data class UserMeResponse(val user: UserMe? = null)
