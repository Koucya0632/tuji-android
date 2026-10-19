package app.tuji.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CreditStatementTest {

    private fun wallet(available: Int, reserved: Int = 0, gift: Int = 0, paid: Int = 0, monthly: Int = 0, checkIn: Int = 0) =
        CreditWallet(
            available = available, reserved = reserved, paidAvailable = paid, giftAvailable = gift,
            walletVersion = "1", environment = "test", reconciliationRequired = false,
            benefits = CreditBenefits(monthlyClaimed = true, checkedInToday = false, checkInGrantedThisMonth = 0, hasLifetime = true),
            monthlyAvailable = monthly, checkInAvailable = checkIn,
        )

    private fun entry(id: String, kind: String, amount: Int, reference: String = "") =
        CreditLedgerPage.Entry(id, kind, amount, reference, "2026-10-19T08:00:00Z")

    @Test fun `walking back from what is held gives each movement's balance after`() {
        val page = CreditLedgerPage(
            entries = listOf(
                entry("c", "commit", -3, "assistant:vocabulary"),
                entry("r", "reserve", 0),
                entry("g", "grant", 50, "benefit:monthly:2026-10"),
            ),
            wallet = wallet(available = 45, reserved = 2),
        )
        val records = CreditStatement.of(page).records
        // The reserve moved nothing and is not a row.
        assertEquals(listOf("c", "g"), records.map { it.id })
        assertEquals(listOf(47, 50), records.map { it.balanceAfter })
    }

    @Test fun `references name what each movement was`() {
        assertEquals(CreditStatement.Activity.AskedAssistant, CreditStatement.Activity.of("commit", "assistant:story"))
        assertEquals(CreditStatement.Activity.Recognition, CreditStatement.Activity.of("commit", "ai:op"))
        assertEquals(CreditStatement.Activity.CheckIn, CreditStatement.Activity.of("grant", "benefit:check_in:1"))
        assertEquals(CreditStatement.Activity.Purchase, CreditStatement.Activity.of("grant", "play:token"))
        assertEquals(CreditStatement.Activity.Purchase, CreditStatement.Activity.of("grant", "apple:tx"))
        assertEquals(CreditStatement.Activity.Gift, CreditStatement.Activity.of("grant", "admin"))
        assertEquals(CreditStatement.Activity.Returned, CreditStatement.Activity.of("compensate", ""))
        assertEquals(CreditStatement.Activity.Returned, CreditStatement.Activity.of("refund_reverse", ""))
        assertEquals(CreditStatement.Activity.Other, CreditStatement.Activity.of("release", ""))
    }

    @Test fun `one-off gifts are what the gift balance holds beyond monthly and check-in`() {
        val statement = CreditStatement.of(CreditLedgerPage(wallet = wallet(available = 40, gift = 30, paid = 10, monthly = 20, checkIn = 5)))
        assertEquals(CreditStatement.Sources(monthly = 20, checkIn = 5, purchased = 10, otherGift = 5), statement.sources)
    }
}
