package app.tuji.android.core.scenes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The machines, as tuji-ios's `SceneMachineTests` holds them. */
class SceneMachineTest {
    private fun SceneMachine.sendAll(vararg actions: MachineAction): SceneMachine =
        actions.fold(this) { machine, action -> machine.send(action) }

    private val payment = SceneMachine.make(MachineKind.Payment, total = 598, item = "弁当")

    @Test fun `cash short of the total shows the shortfall and cannot be settled`() {
        val m = payment.sendAll(MachineAction.Choose(PaymentMethod.Cash), MachineAction.Insert(500))
        assertTrue(m.screen.info!!.contains("不足 ¥98"))
        assertFalse(m.screen.buttons.any { it.action == MachineAction.Settle })
        assertNull(m.sendAll(MachineAction.Settle).outcome)
    }

    @Test fun `a thousand yen for 598 gives 402 change`() {
        val m = payment.sendAll(MachineAction.Choose(PaymentMethod.Cash), MachineAction.Insert(1000), MachineAction.Settle)
        assertTrue(m.screen.paper!!.contains("おつり　¥402"))
        assertEquals(MachineOutcome.Paid(PaymentMethod.Cash, 402), m.sendAll(MachineAction.Take).outcome)
    }

    @Test fun `going back from cash returns the money, and the card still pays`() {
        val m = payment.sendAll(MachineAction.Choose(PaymentMethod.Cash), MachineAction.Insert(500), MachineAction.Back)
        assertEquals("お支払い方法を選択してください", m.screen.headline)
        assertEquals(0, (m as Payment).inserted)
        val paid = m.sendAll(MachineAction.Choose(PaymentMethod.Card), MachineAction.TapCard, MachineAction.Take)
        assertEquals(MachineOutcome.Paid(PaymentMethod.Card, 0), paid.outcome)
    }

    @Test fun `tapping or taking twice changes nothing`() {
        val done = payment.sendAll(MachineAction.Choose(PaymentMethod.Card), MachineAction.TapCard, MachineAction.Take)
        assertEquals(done, done.sendAll(MachineAction.Take, MachineAction.TapCard, MachineAction.Back))
    }

    @Test fun `each age answer ends on its own screen`() {
        val titles = mapOf(AgeAnswer.Yes to "年齢確認が完了しました", AgeAnswer.No to "係員が確認します", AgeAnswer.Cancel to "年齢確認を取り消しました")
        titles.forEach { (answer, title) ->
            val m = AgeConfirm().send(MachineAction.AnswerAge(answer))
            assertEquals(MachineOutcome.Age(answer), m.outcome)
            assertEquals(title, m.screen.headline)
        }
    }

    @Test fun `a ticket machine sells the scene's own menu and fits only what the trip wanted`() {
        val spec = StageMachine(
            kind = MachineKind.FoodTicket,
            menu = listOf(MachineMenuItem("oomori", "大盛", 100), MachineMenuItem("kaedama", "替玉", 150)),
            expect = "oomori",
        )
        val m = spec.makeMachine()
        assertEquals(listOf("大盛　¥100", "替玉　¥150"), m.screen.buttons.map { (it.role as MachineControl.Role.OnScreen).label })
        val done = m.sendAll(MachineAction.PickDish(0), MachineAction.PayDishInCash, MachineAction.Insert(1000), MachineAction.IssueTicket, MachineAction.Take)
        assertEquals(MachineOutcome.Ticket("oomori", 900), done.outcome)
        assertTrue(spec.fits(MachineOutcome.Ticket("oomori", 900)))
        assertFalse(spec.fits(MachineOutcome.Ticket("kaedama", 850)))
    }

    @Test fun `the tablet records the confirmed count and total, and emptying the cart goes back`() {
        val m = OrderTablet().sendAll(
            MachineAction.PickCategory(TabletCategory.Drinks), MachineAction.AddToCart, MachineAction.More,
            MachineAction.Review,
        )
        assertEquals("アイス烏龍茶 × 2　¥560", m.screen.info)
        assertEquals(MachineOutcome.Ordered("アイス烏龍茶", 2, 560), m.sendAll(MachineAction.PlaceOrder).outcome)
        val empty = OrderTablet().sendAll(MachineAction.AddToCart, MachineAction.Fewer)
        assertEquals("メニュー", empty.screen.headline)
        assertNull(empty.outcome)
    }

    @Test fun `the kiosk shows the price difference and a number`() {
        val m = SceneMachine.make(MachineKind.Kiosk, total = 400, item = "ハンバーガー")
        assertEquals("ハンバーガー（セットにする）\n合計 ¥700", m.sendAll(MachineAction.PickOption(0)).screen.info)
        val single = m.sendAll(MachineAction.PickOption(1))
        assertEquals("ハンバーガー\n合計 ¥400", single.screen.info)
        val placed = single.sendAll(MachineAction.ConfirmOrder)
        assertEquals(MachineOutcome.KioskOrder("single", 400), placed.outcome)
        assertTrue(placed.screen.paper!!.contains("受付番号　021"))
    }
}
