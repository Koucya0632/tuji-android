package app.tuji.android.core.network

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.CategoriesResponse
import app.tuji.android.core.model.WordDetail
import app.tuji.android.core.model.WordsListResponse

/**
 * The catalogue, as a role rather than a client.
 *
 * A screen asks for words; it does not ask for an HTTP client that happens to
 * know how to fetch words. That is the seam ADR-0001 argues for, and it is what
 * lets a UI test hand over a list of rows without standing up a transport.
 */
interface CatalogReading {
    suspend fun words(lang: String, learning: LearningDirection): WordsListResponse

    /** One entry in full — the detail screen's payload, not the list's. */
    suspend fun word(id: String, lang: String, learning: LearningDirection): WordDetail

    /**
     * The shelves. Their names live in the database, not in a local table:
     * a hard-coded copy on the client is a third place to forget when the
     * copy changes.
     */
    suspend fun categories(lang: String): CategoriesResponse
}

class CatalogRepository(private val api: TujiApiClient) : CatalogReading {
    override suspend fun words(lang: String, learning: LearningDirection): WordsListResponse =
        api.get(Endpoint.Words(lang = lang, learning = learning))

    override suspend fun word(id: String, lang: String, learning: LearningDirection): WordDetail =
        api.get(Endpoint.Word(id = id, lang = lang, learning = learning))

    override suspend fun categories(lang: String): CategoriesResponse =
        api.get(Endpoint.Categories(lang = lang))
}
