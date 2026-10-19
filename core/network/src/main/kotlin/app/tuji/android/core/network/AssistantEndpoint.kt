package app.tuji.android.core.network

/**
 * 助手's routes — iOS's `vocabulary*` and `creditLedger` endpoint cases.
 *
 * Every one is private and fresh: an offer carries the caller's balance, and a
 * record is somebody's own card. Generating waits on a model call, so it gets
 * the slow timeout.
 */
sealed class AssistantEndpoint(override val descriptor: EndpointDescriptor) : Endpoint {

    /** 點數明細: one page of the ledger, newest first, with the wallet as of the read. */
    data object CreditLedger : AssistantEndpoint(EndpointDescriptor("/api/credits/ledger", policy = EndpointPolicy.PrivateFresh))

    data object VocabularyOffer : AssistantEndpoint(EndpointDescriptor("/api/assistant/vocabulary", policy = EndpointPolicy.PrivateFresh))

    data object VocabularyGenerate : AssistantEndpoint(
        EndpointDescriptor("/api/assistant/vocabulary", policy = EndpointPolicy.PrivateFreshSlow),
    )

    data class VocabularyRecord(val id: String) : AssistantEndpoint(
        EndpointDescriptor("/api/assistant/vocabulary/records/$id", policy = EndpointPolicy.PrivateFresh),
    )

    data class VocabularyRecords(val learning: String, val favorite: Boolean, val cursor: String?) : AssistantEndpoint(
        EndpointDescriptor(
            "/api/assistant/vocabulary/records",
            query = listOf("learningDirection" to learning, "favorite" to favorite.toString()) +
                listOfNotNull(cursor?.let { "cursor" to it }),
            policy = EndpointPolicy.PrivateFresh,
        ),
    )
}
