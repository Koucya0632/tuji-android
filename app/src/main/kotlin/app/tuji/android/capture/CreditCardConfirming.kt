package app.tuji.android.capture

import app.tuji.android.core.model.CreditConfirmRequest
import app.tuji.android.core.network.CreditRepository

/** What the server answered to a 罐頭點數 confirm. */
data class CreditConfirmed(
    val itemId: String,
    /** The server-side fill-in the confirm started; [app.tuji.android.core.model.isEnrichingState] says whether it is still open. */
    val fulfillmentState: String,
)

/**
 * The 罐頭點數 half of 生成佇列's network edge, split out like iOS's
 * `CreditCardConfirming` so a test can drive a credit job without the server.
 */
interface CreditCardConfirming {
    /** Idempotent per operation and candidate — see [CreditConfirmRequest]. */
    suspend fun confirm(request: CreditConfirmRequest): CreditConfirmed

    suspend fun fulfillmentState(operationId: String): String
}

class LiveCreditCardConfirming(private val repository: CreditRepository) : CreditCardConfirming {
    override suspend fun confirm(request: CreditConfirmRequest): CreditConfirmed {
        val result = repository.confirm(request)
        return CreditConfirmed(result.item.id, result.operation.fulfillmentState)
    }

    override suspend fun fulfillmentState(operationId: String): String = repository.read(operationId).fulfillmentState
}
