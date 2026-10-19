package app.tuji.android.core.network

import app.tuji.android.core.model.GrammarInput
import app.tuji.android.core.model.GrammarMode
import app.tuji.android.core.model.GrammarOffer
import app.tuji.android.core.model.GrammarRecord
import app.tuji.android.core.model.GrammarRecords
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** AI 語法's calls — iOS's `GrammarStudying`. */
interface GrammarStudying {
    suspend fun offer(): GrammarOffer
    suspend fun generate(input: GrammarInput): GrammarRecord
    suspend fun record(id: String): GrammarRecord
    suspend fun records(learning: String, favorite: Boolean, mode: GrammarMode?, cursor: String?): GrammarRecords
    suspend fun favorite(id: String, value: Boolean): GrammarRecord
    suspend fun delete(id: String)
}

class GrammarRepository(private val api: TujiApiClient) : GrammarStudying {
    override suspend fun offer(): GrammarOffer = api.get(AssistantEndpoint.GrammarOffer)

    override suspend fun generate(input: GrammarInput): GrammarRecord =
        api.post<Envelope>(AssistantEndpoint.GrammarGenerate, input).record

    override suspend fun record(id: String): GrammarRecord = api.get<Envelope>(AssistantEndpoint.GrammarRecord(id)).record

    override suspend fun records(learning: String, favorite: Boolean, mode: GrammarMode?, cursor: String?): GrammarRecords =
        api.get(AssistantEndpoint.GrammarRecords(learning, favorite, mode?.name?.lowercase(), cursor))

    override suspend fun favorite(id: String, value: Boolean): GrammarRecord =
        api.patch<Envelope>(AssistantEndpoint.GrammarRecord(id), Favorite(value)).record

    override suspend fun delete(id: String) {
        api.delete<JsonElement>(AssistantEndpoint.GrammarRecord(id))
    }

    @Serializable private data class Envelope(val record: GrammarRecord)
    @Serializable private data class Favorite(val favorite: Boolean)
}
