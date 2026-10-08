package app.tuji.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CreditBenefits(
    val monthlyClaimed: Boolean, val checkedInToday: Boolean, val checkInGrantedThisMonth: Int, val hasLifetime: Boolean,
    /** At least one study answer today (the server's day). Null from a server that predates it. */
    val studiedToday: Boolean? = null,
)
@Serializable
data class CreditWallet(
    val available: Int, val reserved: Int, val paidAvailable: Int, val giftAvailable: Int,
    val walletVersion: String, val environment: String, val reconciliationRequired: Boolean, val benefits: CreditBenefits,
    val monthlyAvailable: Int = 0, val checkInAvailable: Int = 0,
) {
    fun isNewerThan(previous: CreditWallet): Boolean = environment != previous.environment ||
        walletVersion.length > previous.walletVersion.length ||
        (walletVersion.length == previous.walletVersion.length && walletVersion >= previous.walletVersion)
}
@Serializable
data class CreditPack(val productId: String, val points: Int)
/**
 * The amounts the server charges and grants. The recognition prices are shown
 * before anything is uploaded; a quote that disagrees is never accepted silently.
 */
@Serializable
data class CreditPolicy(
    val recognition: Int? = null, val precision: Int? = null, val precisionUpgrade: Int? = null,
    val checkInDaily: Int? = null, val checkInMonthlyCap: Int? = null,
)
@Serializable
data class CreditCatalog(
    val billingMode: String, val environment: String, val purchaseEnabled: Boolean, val proNewPurchaseEnabled: Boolean,
    val operationsEnabled: Boolean, val monthlyEnabled: Boolean, val checkInEnabled: Boolean, val packs: List<CreditPack>,
    val policy: CreditPolicy? = null,
)
/** [level] is "primary" or "fine" — the two rows of the candidate grid. */
@Serializable
data class CreditCandidate(val id: String, val label: String, val zhHant: String, val gloss: String? = null, val level: String? = null)
@Serializable
data class CreditResult(val candidates: List<CreditCandidate>)
@Serializable
data class CreditOperation(
    val id: String, val state: String, val feature: String, val targetLanguage: String, val imageId: String, val points: Int,
    val confirmedItemId: String? = null, val fulfillmentState: String, val result: CreditResult? = null,
) {
    val isRunning: Boolean get() = state in listOf("reserved", "running", "reconciling")
    val needsPolling: Boolean get() = state in listOf("reserved", "running", "reconciling") ||
        (state == "committed" && fulfillmentState in listOf("pending", "running", "reconciling"))
}
@Serializable
data class CreditQuoteInput(val targetLanguage: String, val feature: String)
@Serializable
data class CreditQuote(val id: String, val points: Int, val expiresAt: String, val input: CreditQuoteInput)
@Serializable
data class PendingCreditAcceptance(val quoteId: String, val key: String)

/**
 * What 確認並生成卡片 sends for a 罐頭點數 result. The server binds one card per
 * operation and answers a repeat with the same candidate by returning that card,
 * so — unlike the free flow's confirm — resending this is safe.
 */
@Serializable
data class CreditConfirmRequest(
    val operationId: String,
    val candidateId: String,
    val lemma: String,
    val displayZhHant: String,
    /** Only for a cross-language capture; null keeps the candidate's own. */
    val displayGloss: String? = null,
)

/** Whether a fill-in in this state can still land. */
fun isEnrichingState(fulfillmentState: String): Boolean = fulfillmentState in listOf("pending", "running", "reconciling")
