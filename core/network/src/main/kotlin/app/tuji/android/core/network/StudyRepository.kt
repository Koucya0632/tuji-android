package app.tuji.android.core.network

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.FavoritesResponse
import app.tuji.android.core.model.MasteryListResponse
import app.tuji.android.core.model.ProgressResponse
import app.tuji.android.core.model.UserSettings
import app.tuji.android.core.model.UserSettingsResponse
import app.tuji.android.core.model.StudyAnswerPayload
import app.tuji.android.core.model.WordsListResponse
import app.tuji.android.core.model.StudyAnswerResponse
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.StudyStatsResponse

/**
 * Submitting an answer, as a role.
 *
 * `core:study` declares the shape it needs (`AnswerSubmitting`) and this is the
 * witness. That direction is what keeps the durability rules on the pure-JVM
 * side of the build: the outbox is about *when to give up and what to keep*,
 * and none of that should need an HTTP client to be true.
 */
interface AnswerSubmission {
    suspend fun submitAnswer(payload: StudyAnswerPayload): StudyAnswerResponse
}

/** Reading the day's counts, as a role. */
interface StudyStatsReading {
    suspend fun stats(learning: LearningDirection): StudyStatsResponse
}

/** Every word this account has a score for. */
interface MasteryReading {
    suspend fun mastery(learning: LearningDirection): MasteryListResponse
}

/** The streak, the heatmap and the per-theme rows. */
interface ProgressReading {
    suspend fun progress(learning: LearningDirection): ProgressResponse
}

/**
 * The two things 圖鑑 shows that are not the dictionary.
 *
 * One role rather than two, because they are one question — "what did *this
 * account* add to 圖鑑" — and the screen that asks needs both or neither.
 */
interface PersonalWordsAccess {
    /** 書籤, as word ids. */
    suspend fun favorites(): FavoritesResponse

    /** Mark or unmark one word. Returns nothing worth reading. */
    suspend fun setFavorite(wordId: String, favorite: Boolean)

    /** 已收進 — saved 物見 items, already shaped as words. */
    suspend fun savedWords(lang: String, learning: LearningDirection): WordsListResponse

    /**
     * 我做的 — this account's own cards.
     *
     * A sibling of [savedWords] rather than one call returning both: those two
     * are the creation and the consumption halves of 圖鑑, they live in separate
     * tables, and the quota that stops saving other people's photos from eating
     * your own capture slots depends on them being counted apart.
     */
    suspend fun customWords(lang: String, learning: LearningDirection): WordsListResponse
}

/** The account's settings, both ways. */
interface SettingsAccess {
    suspend fun settings(): UserSettings
    suspend fun saveSettings(settings: UserSettings): UserSettings
}

/**
 * The two irreversible ones.
 *
 * A separate interface from [SettingsAccess] deliberately: these are the only
 * calls in the app that destroy something, and a test that stands in for the
 * settings screen should have to opt into being able to make them.
 */
interface AccountErasure {
    /** Mastery and answer history. Bookmarks, settings and 自製圖鑑 survive. */
    suspend fun clearProgress()

    /** Everything. */
    suspend fun deleteAccount()
}

/** Fetching a session's cards, as a role. */
interface StudyQueueReading {
    suspend fun queue(
        mode: StudyMode,
        limit: Int,
        new: Int,
        categories: List<String>,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse
}

class StudyRepository(private val api: TujiApiClient) :
    AnswerSubmission,
    StudyQueueReading,
    StudyStatsReading,
    MasteryReading,
    ProgressReading,
    SettingsAccess,
    PersonalWordsAccess,
    AccountErasure {

    override suspend fun mastery(learning: LearningDirection): MasteryListResponse =
        api.get(Endpoint.UsersMastery(learning))

    override suspend fun progress(learning: LearningDirection): ProgressResponse =
        api.get(Endpoint.UsersProgress(learning))

    override suspend fun settings(): UserSettings =
        api.get<UserSettingsResponse>(Endpoint.UserSettingsRead).settings

    override suspend fun saveSettings(settings: UserSettings): UserSettings =
        api.post<UserSettingsResponse>(Endpoint.UserSettingsWrite, settings).settings

    override suspend fun favorites(): FavoritesResponse = api.get(Endpoint.UsersFavorites)

    override suspend fun setFavorite(wordId: String, favorite: Boolean) {
        api.post<Unit>(
            Endpoint.UsersFavorites,
            FavoriteWrite(wordId = wordId, favorite = favorite),
        )
    }

    override suspend fun savedWords(
        lang: String,
        learning: LearningDirection,
    ): WordsListResponse = api.get(Endpoint.UsersSavedWords(lang = lang, learning = learning))

    override suspend fun customWords(
        lang: String,
        learning: LearningDirection,
    ): WordsListResponse = api.get(Endpoint.UsersCustomWords(lang = lang, learning = learning))

    override suspend fun clearProgress() {
        api.delete<Unit>(Endpoint.ClearProgress)
    }

    override suspend fun deleteAccount() {
        api.post<Unit>(Endpoint.DeleteAccount, body = null)
    }

    override suspend fun stats(learning: LearningDirection): StudyStatsResponse =
        api.get(Endpoint.StudyStats(learning = learning))

    override suspend fun submitAnswer(payload: StudyAnswerPayload): StudyAnswerResponse =
        api.post(Endpoint.StudyAnswer, payload)

    override suspend fun queue(
        mode: StudyMode,
        limit: Int,
        new: Int,
        categories: List<String>,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse = api.get(
        Endpoint.StudyQueue(
            mode = mode,
            limit = limit,
            new = new,
            categories = categories,
            lang = lang,
            learning = learning,
        )
    )
}

/**
 * The favourites POST body. Both fields are required by the route — sending
 * only the id would be read as a malformed request, not as "toggle it".
 */
@kotlinx.serialization.Serializable
private data class FavoriteWrite(val wordId: String, val favorite: Boolean)
