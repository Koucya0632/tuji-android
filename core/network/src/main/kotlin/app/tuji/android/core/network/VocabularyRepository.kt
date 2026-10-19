package app.tuji.android.core.network

import app.tuji.android.core.model.CreditLedgerPage
import app.tuji.android.core.model.VocabularyInput
import app.tuji.android.core.model.VocabularyOffer
import app.tuji.android.core.model.VocabularyRecord
import app.tuji.android.core.model.VocabularyRecords
import kotlinx.serialization.Serializable

/** AI 查詞's calls — iOS's `VocabularyStudying`. An interface so the model is tested without a server. */
interface VocabularyStudying {
    suspend fun offer(): VocabularyOffer
    suspend fun generate(input: VocabularyInput): VocabularyRecord
    suspend fun record(id: String): VocabularyRecord
    suspend fun records(learning: String, favorite: Boolean, cursor: String?): VocabularyRecords
    suspend fun favorite(id: String, value: Boolean): VocabularyRecord
    suspend fun delete(id: String)
}

class VocabularyRepository(private val api: TujiApiClient) : VocabularyStudying {
    override suspend fun offer(): VocabularyOffer = api.get(AssistantEndpoint.VocabularyOffer)

    override suspend fun generate(input: VocabularyInput): VocabularyRecord =
        api.post<Envelope>(AssistantEndpoint.VocabularyGenerate, input).record

    override suspend fun record(id: String): VocabularyRecord =
        api.get<Envelope>(AssistantEndpoint.VocabularyRecord(id)).record

    override suspend fun records(learning: String, favorite: Boolean, cursor: String?): VocabularyRecords =
        api.get(AssistantEndpoint.VocabularyRecords(learning, favorite, cursor))

    override suspend fun favorite(id: String, value: Boolean): VocabularyRecord =
        api.patch<Envelope>(AssistantEndpoint.VocabularyRecord(id), Favorite(value)).record

    override suspend fun delete(id: String) {
        api.delete<kotlinx.serialization.json.JsonElement>(AssistantEndpoint.VocabularyRecord(id))
    }

    @Serializable private data class Envelope(val record: VocabularyRecord)
    @Serializable private data class Favorite(val favorite: Boolean)
}

/** 點數明細's one read, on its own so a screen that only reads the ledger needs nothing else. */
fun interface CreditStatementReading {
    suspend fun loadStatement(): CreditLedgerPage
}

class CreditLedgerRepository(private val api: TujiApiClient) : CreditStatementReading {
    override suspend fun loadStatement(): CreditLedgerPage = api.get(AssistantEndpoint.CreditLedger)
}
