package app.tuji.android.core.model

import app.tuji.android.core.model.CheckInDecision.Reward
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins which 打卡 reward shows — the same cases as iOS's `CheckInRewardTests`. */
class CheckInDecisionTest {
    private fun catalog(billingMode: String = "credits", enabled: Boolean = true, policy: CreditPolicy? = CreditPolicy(10, 300)) =
        CreditCatalog(billingMode, "sandbox", true, false, true, true, enabled, emptyList(), policy)

    private fun wallet(
        checkedIn: Boolean = false,
        granted: Int = 0,
        lifetime: Boolean = true,
        studied: Boolean? = true,
    ) = CreditWallet(1000, 0, 0, 1000, "1", "sandbox", false, CreditBenefits(true, checkedIn, granted, lifetime, studied))

    private fun reward(catalog: CreditCatalog? = catalog(), wallet: CreditWallet? = wallet(), fallback: Boolean = false) =
        CheckInDecision.reward(catalog, wallet, fallback)

    @Test fun `no catalog, or check-in paused for a points account, shows nothing`() {
        assertEquals(Reward.Hidden, reward(catalog = null))
        assertEquals(Reward.Hidden, reward(catalog = catalog(enabled = false)))
        assertEquals(Reward.Hidden, reward(wallet = null))
    }

    @Test fun `an account not on points billing is offered the upgrade`() {
        assertEquals(Reward.Locked(10), reward(catalog = catalog(billingMode = "legacy"), wallet = null))
        assertEquals(Reward.Locked(10), reward(wallet = wallet(lifetime = false)))
    }

    @Test fun `claimed today wins over everything after it`() {
        assertEquals(Reward.Claimed, reward(wallet = wallet(checkedIn = true, granted = 300, studied = false)))
    }

    @Test fun `a full month is capped whether or not today was studied`() {
        assertEquals(Reward.Capped(300), reward(wallet = wallet(granted = 300)))
        assertEquals(Reward.Capped(300), reward(wallet = wallet(granted = 300, studied = false)))
    }

    @Test fun `studied and unclaimed is claimable, never more than the month has left`() {
        assertEquals(Reward.Claimable(10), reward())
        assertEquals(Reward.Claimable(5), reward(wallet = wallet(granted = 295)))
    }

    @Test fun `eligible but nothing studied today asks for one answer`() {
        assertEquals(Reward.NeedsStudy(10), reward(wallet = wallet(studied = false)))
    }

    @Test fun `an older server without studiedToday falls back to the streak's count`() {
        assertEquals(Reward.NeedsStudy(10), reward(wallet = wallet(studied = null), fallback = false))
        assertEquals(Reward.Claimable(10), reward(wallet = wallet(studied = null), fallback = true))
    }

    @Test fun `amounts default when the catalog sends no policy`() {
        assertEquals(Reward.Claimable(CheckInDecision.DEFAULT_DAILY), reward(catalog = catalog(policy = null)))
        assertEquals(Reward.Claimable(20), reward(catalog = catalog(policy = CreditPolicy(20, 300))))
    }

    @Test fun `a finish screen shows what today earned, never an upgrade or a study prompt`() {
        listOf(Reward.Claimable(10), Reward.Claimed, Reward.Capped(300)).forEach {
            assertEquals(it, CheckInDecision.finishReward(it))
        }
        listOf(Reward.Hidden, Reward.Locked(10), Reward.NeedsStudy(10)).forEach {
            assertEquals(Reward.Hidden, CheckInDecision.finishReward(it))
        }
    }

    @Test fun `a wallet with studiedToday decodes, and one without still does`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val base = """"monthlyClaimed":true,"checkedInToday":false,"checkInGrantedThisMonth":0,"hasLifetime":true"""
        assertEquals(true, json.decodeFromString<CreditBenefits>("{$base,\"studiedToday\":true}").studiedToday)
        assertEquals(null, json.decodeFromString<CreditBenefits>("{$base}").studiedToday)
    }
}

class CheckInChipAndMonthGridTest {
    @Test fun `the chip's dot means points to collect, and nothing else`() {
        org.junit.Assert.assertTrue(CheckInDecision.chipBadge(Reward.Claimable(10)))
        listOf(Reward.Hidden, Reward.Locked(10), Reward.NeedsStudy(10), Reward.Claimed, Reward.Capped(300)).forEach {
            org.junit.Assert.assertFalse(CheckInDecision.chipBadge(it))
        }
    }

    @Test fun `October 2026 starts on a Thursday`() {
        val sunday = MonthGrid.of("2026-10", java.time.DayOfWeek.SUNDAY)!!
        assertEquals(listOf(null, null, null, null, 1), sunday.cells.take(5))
        assertEquals(31, sunday.cells.filterNotNull().size)
        val monday = MonthGrid.of("2026-10", java.time.DayOfWeek.MONDAY)!!
        assertEquals(listOf(null, null, null, 1), monday.cells.take(4))
    }

    @Test fun `a month that starts on the first weekday has no blanks`() {
        // 2026-11-01 is a Sunday.
        assertEquals(1, MonthGrid.of("2026-11", java.time.DayOfWeek.SUNDAY)!!.cells.first())
    }

    @Test fun `February knows about leap years`() {
        assertEquals(29, MonthGrid.of("2028-02", java.time.DayOfWeek.SUNDAY)!!.cells.filterNotNull().size)
        assertEquals(28, MonthGrid.of("2026-02", java.time.DayOfWeek.SUNDAY)!!.cells.filterNotNull().size)
    }

    @Test fun `dates are zero-padded`() {
        assertEquals("2026-10-07", MonthGrid.of("2026-10", java.time.DayOfWeek.SUNDAY)!!.date(7))
    }

    @Test fun `month arithmetic crosses years`() {
        assertEquals("2025-12", MonthGrid.shift("2026-01", -1))
        assertEquals("2027-01", MonthGrid.shift("2026-12", 1))
        assertEquals(13, MonthGrid.monthsBefore("2025-09", "2026-10"))
        assertEquals(0, MonthGrid.monthsBefore("2026-10", "2026-10"))
    }

    @Test fun `a malformed month is refused`() {
        assertEquals(null, MonthGrid.of("2026-13", java.time.DayOfWeek.SUNDAY))
        assertEquals(null, MonthGrid.parse("nope"))
    }

    @Test fun `a calendar month decodes`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val month = json.decodeFromString<StudyCalendarMonth>(
            """{"month":"2026-10","timezone":"Asia/Taipei","today":"2026-10-07","studiedDays":["2026-10-01","2026-10-07"],
               "streak":{"current":1,"longest":4,"totalDays":9,"todayCount":3,"lastStudyDate":"2026-10-07"}}""",
        )
        assertEquals(listOf("2026-10-01", "2026-10-07"), month.studiedDays)
        assertEquals(3, month.streak.todayCount)
    }
}
