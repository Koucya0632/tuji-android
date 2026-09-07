package app.tuji.android.core.network

import app.tuji.android.core.model.LearningDirection
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
}

class CatalogRepository(private val api: TujiApiClient) : CatalogReading {
    override suspend fun words(lang: String, learning: LearningDirection): WordsListResponse =
        api.get(Endpoint.Words(lang = lang, learning = learning))
}
