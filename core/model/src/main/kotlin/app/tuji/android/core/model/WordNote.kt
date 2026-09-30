package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * 個人筆記 — `/api/users/word-notes` (tuji-web `lib/word-notes`).
 *
 * `available` is false under membership policy v1; `canWrite` is false for a
 * non-member, who can still read and delete what they wrote. Neither is read
 * for access — [MemberAccess] decides that off the entitlement, which a
 * purchase refreshes and this response does not.
 */
@Serializable
data class WordNote(
    val wordId: String,
    val body: String,
    val updatedAt: String = "",
)

@Serializable
data class WordNotesResponse(
    val available: Boolean = false,
    val canWrite: Boolean = false,
    val maxLength: Int? = null,
    val notes: List<WordNote> = emptyList(),
)

@Serializable
data class WordNoteSaveResponse(val note: WordNote)

@Serializable
data class WordNotePayload(val body: String)
