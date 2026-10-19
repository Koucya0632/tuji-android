package app.tuji.android.core.model

import kotlinx.serialization.Serializable

/**
 * 點數明細 — what the account holds, where it came from, and where it went.
 *
 * iOS's `CreditStatement`. The server keeps one append-only ledger for every
 * point movement (`/api/credits/ledger`) and returns the current wallet beside
 * each page. This turns that pair into what the screen prints, as a value, so
 * which movements are worth a row, what each one is called, and the balance
 * after it are asked without a screen.
 */
@Serializable
data class CreditLedgerPage(val entries: List<Entry> = emptyList(), val wallet: CreditWallet) {
    @Serializable
    data class Entry(
        val id: String,
        /** grant / reserve / commit / release / expire / refund / refund_reverse / compensate. */
        val kind: String,
        /** The change in points held. Zero for a reserve or a release. */
        val amount: Int,
        val reference: String = "",
        val createdAt: String = "",
    )
}

data class CreditStatement(
    val available: Int,
    /** Held by an operation still running. Not spendable, not yet spent. */
    val reserved: Int,
    val sources: Sources,
    /** When this month's gift lapses, as the wire's ISO 8601. */
    val monthlyExpires: String?,
    val records: List<Record>,
) {
    /** What a ledger movement was, in the app's own vocabulary. */
    enum class Activity {
        AskedAssistant, Recognition, MonthlyGift, CheckIn, Purchase, Gift, Expired, Refund, Returned, Other;

        companion object {
            fun of(kind: String, reference: String): Activity = when (kind) {
                "commit" -> when {
                    reference.startsWith("assistant:") -> AskedAssistant
                    reference.startsWith("ai:") -> Recognition
                    else -> Other
                }
                "grant" -> when {
                    reference.startsWith("benefit:monthly") -> MonthlyGift
                    reference.startsWith("benefit:check_in") -> CheckIn
                    reference.startsWith("apple:") || reference.startsWith("play:") -> Purchase
                    else -> Gift
                }
                "expire" -> Expired
                "refund" -> Refund
                // A failed operation's points coming back, or a refund undone.
                "compensate", "refund_reverse" -> Returned
                else -> Other
            }
        }
    }

    data class Record(
        val id: String,
        val activity: Activity,
        val createdAt: String,
        /** Signed: negative is spent. */
        val amount: Int,
        /** Points held once this movement had happened. */
        val balanceAfter: Int,
    )

    /** Where the spendable points came from; one-off adjustments land in [otherGift]. */
    data class Sources(val monthly: Int, val checkIn: Int, val purchased: Int, val otherGift: Int)

    companion object {
        fun of(page: CreditLedgerPage): CreditStatement {
            val wallet = page.wallet
            // The ledger carries changes, not balances. Walking back from what
            // is held now gives the balance after each one. Held points count —
            // a reservation has not spent anything.
            var balance = wallet.available + wallet.reserved
            val records = buildList {
                for (entry in page.entries) {
                    // A reserve or a release changes no total; it would be a row that says "0 點".
                    if (entry.amount != 0) {
                        add(Record(entry.id, Activity.of(entry.kind, entry.reference), entry.createdAt, entry.amount, balance))
                    }
                    balance -= entry.amount
                }
            }
            return CreditStatement(
                available = wallet.available,
                reserved = wallet.reserved,
                sources = Sources(
                    monthly = wallet.monthlyAvailable,
                    checkIn = wallet.checkInAvailable,
                    purchased = wallet.paidAvailable,
                    otherGift = maxOf(0, wallet.giftAvailable - wallet.monthlyAvailable - wallet.checkInAvailable),
                ),
                monthlyExpires = wallet.nextMonthlyResetAt,
                records = records,
            )
        }
    }
}
