package app.tuji.android.core.network

import app.tuji.android.core.model.AtlasPublicItem
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyMode
import app.tuji.android.core.model.StudyQueueResponse
import app.tuji.android.core.model.TravelResponse
import app.tuji.android.core.model.TravelSpotPhotos

/** 跟著 Moira 去旅遊, as a role: the map, and one 景點's study queue. */
interface TravelAccess {
    suspend fun destinations(lang: String, learning: LearningDirection): TravelResponse

    suspend fun spotQueue(
        spotId: String,
        mode: StudyMode,
        limit: Int,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse
}

/** 「大家拍的」 at a 景點 — public, the same for everyone. */
fun interface SpotPhotosReading {
    suspend fun spotPhotos(spotId: String): List<AtlasPublicItem>
}

class TravelRepository(private val api: TujiApiClient) : TravelAccess, SpotPhotosReading {
    override suspend fun spotPhotos(spotId: String): List<AtlasPublicItem> =
        api.get<TravelSpotPhotos>(Endpoint.TravelSpotPhotos(spotId)).items

    override suspend fun destinations(lang: String, learning: LearningDirection): TravelResponse =
        api.get(Endpoint.Travel(lang = lang, learning = learning))

    override suspend fun spotQueue(
        spotId: String,
        mode: StudyMode,
        limit: Int,
        lang: String,
        learning: LearningDirection,
    ): StudyQueueResponse = api.get(
        Endpoint.StudySpotQueue(spotId = spotId, mode = mode, limit = limit, lang = lang, learning = learning),
    )
}
