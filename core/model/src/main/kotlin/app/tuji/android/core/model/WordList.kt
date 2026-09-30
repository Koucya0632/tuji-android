package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * 個人詞表 — `/api/users/word-lists` (tuji-web `lib/word-lists`).
 *
 * The server decides everything a screen gates on: whether a list is [locked]
 * (past the cap after a downgrade), and what the account may do with one list
 * (`canEdit`, `canStudy`). The app only draws those answers. Whether the
 * feature shows at all is [MemberAccess].
 */
@Serializable
data class WordList(
    val id: String,
    val name: String,
    val targetLanguage: String = "",
    val position: Int = 0,
    val wordCount: Int = 0,
    /** Past the list cap after a downgrade: readable and deletable only. */
    val locked: Boolean = false,
    /** Present only on a listing asked about one word (the 加入詞表 sheet). */
    val containsWord: Boolean? = null,
)

@Serializable
data class WordListLimits(val lists: Int, val words: Int)

@Serializable
data class WordListsResponse(
    val available: Boolean = false,
    val tier: String? = null,
    val canCreate: Boolean? = null,
    val limits: WordListLimits? = null,
    val lists: List<WordList> = emptyList(),
)

@Serializable
data class WordListStats(val total: Int = 0, val seen: Int = 0, val due: Int = 0) {
    /** Never studied, in this list's deck. */
    val unseen: Int get() = (total - seen).coerceAtLeast(0)
}

@Serializable
data class WordListDetailResponse(
    val list: WordList,
    val wordIds: List<String> = emptyList(),
    val stats: WordListStats = WordListStats(),
    val canEdit: Boolean = false,
    val canStudy: Boolean = false,
    val wordLimit: Int = 0,
)

@Serializable
data class WordListCreateResponse(val list: WordList)

@Serializable
data class WordListNamePayload(val name: String)

@Serializable
data class WordListWordPayload(val wordId: String, val add: Boolean)

@Serializable
data class WordListOrderPayload(val ids: List<String>)
