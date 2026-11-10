package app.tuji.android.core.scenes

import java.text.NumberFormat
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// The machines a 場面 can put in front of the traveller: the customer payment
// screen, the age check, the 食券機, the self-order kiosk and the table tablet.
//
// Each is an immutable value whose `send` returns the next one. What the screen
// shows is `screen`, built from the state; whether the traveller finished, and
// how, is `outcome`. Taps that change nothing return the same value, so nothing
// a view does twice counts twice. The Japanese on the screens is the machine's
// own and is never translated. Mirrors tuji-ios `SceneMachines.swift`.

@Serializable
enum class MachineKind {
    @SerialName("payment") Payment,
    @SerialName("age-confirm") AgeConfirm,
    @SerialName("food-ticket") FoodTicket,
    @SerialName("order-tablet") OrderTablet,
    @SerialName("kiosk") Kiosk,
}

/** One line on a machine's menu. `id` is what a scene's `expect` names. */
@Serializable
data class MachineMenuItem(val id: String, val name: String, val price: Int)

enum class PaymentMethod(val wire: String) { Card("card"), Cash("cash") }

enum class AgeAnswer { Yes, No, Cancel }

enum class TabletCategory(val label: String) { Noodles("麺類"), Drinks("飲み物") }

/** Everything any machine can be told. A machine ignores what it has no use for. */
sealed interface MachineAction {
    data class Choose(val method: PaymentMethod) : MachineAction
    data object Back : MachineAction
    data object TapCard : MachineAction
    data class Insert(val yen: Int) : MachineAction
    data object Settle : MachineAction
    data object Take : MachineAction
    data class AnswerAge(val answer: AgeAnswer) : MachineAction
    data class PickDish(val index: Int) : MachineAction
    data class PickOption(val index: Int) : MachineAction
    data object ConfirmOrder : MachineAction
    data object PayDishInCash : MachineAction
    data object IssueTicket : MachineAction
    data class PickCategory(val category: TabletCategory) : MachineAction
    data object AddToCart : MachineAction
    data object More : MachineAction
    data object Fewer : MachineAction
    data object Review : MachineAction
    data object PlaceOrder : MachineAction
    data object ShowHistory : MachineAction
}

/** What a finished machine did. */
sealed interface MachineOutcome {
    data class Paid(val method: PaymentMethod, val change: Int) : MachineOutcome
    data class Age(val answer: AgeAnswer) : MachineOutcome
    /** `item` is the menu item's id. */
    data class Ticket(val item: String, val change: Int) : MachineOutcome
    /** `option` is the kiosk menu item's id. */
    data class KioskOrder(val option: String, val total: Int) : MachineOutcome
    data class Ordered(val item: String, val count: Int, val total: Int) : MachineOutcome
}

/**
 * One control on a machine. On-screen controls are the touch screen's own
 * buttons, labelled in Japanese; hardware is what the traveller uses with their
 * hands (the card reader, the coin slot, the tray), labelled by the app.
 */
data class MachineControl(
    val action: MachineAction,
    val role: Role,
    val isPrimary: Boolean = false,
    val isEnabled: Boolean = true,
) {
    sealed interface Role {
        data class OnScreen(val label: String) : Role
        data class Hardware(val kind: HardwareKind) : Role
    }

    sealed interface HardwareKind {
        data object CardReader : HardwareKind
        data class CoinSlot(val yen: Int) : HardwareKind
        data class Tray(val withChange: Boolean) : HardwareKind
        data object TicketTray : HardwareKind
    }
}

data class MachineScreen(
    /** The screen's own heading, in the machine's Japanese. */
    val headline: String,
    val info: String? = null,
    /** A printed slip: the receipt or the ticket, shown as paper. */
    val paper: String? = null,
    val buttons: List<MachineControl> = emptyList(),
    val hardware: List<MachineControl> = emptyList(),
)

fun yen(amount: Int): String = "¥" + NumberFormat.getIntegerInstance(Locale.JAPAN).format(amount)

private fun onScreen(action: MachineAction, label: String, primary: Boolean = false, enabled: Boolean = true) =
    MachineControl(action, MachineControl.Role.OnScreen(label), primary, enabled)

private fun hardware(action: MachineAction, kind: MachineControl.HardwareKind) =
    MachineControl(action, MachineControl.Role.Hardware(kind))

sealed interface SceneMachine {
    val screen: MachineScreen
    val outcome: MachineOutcome?
    fun send(action: MachineAction): SceneMachine

    companion object {
        fun make(kind: MachineKind, total: Int?, item: String?, menu: List<MachineMenuItem>? = null): SceneMachine =
            when (kind) {
                MachineKind.Payment -> Payment(total = total ?: 598, item = item ?: "弁当")
                MachineKind.AgeConfirm -> AgeConfirm()
                MachineKind.FoodTicket -> FoodTicket(menu = menu.orEmpty().ifEmpty { FoodTicket.defaultMenu })
                MachineKind.OrderTablet -> OrderTablet()
                MachineKind.Kiosk -> Kiosk(
                    item = item ?: "ハンバーガー",
                    price = total ?: 400,
                    options = menu.orEmpty().ifEmpty { Kiosk.defaultOptions },
                )
            }
    }
}

/**
 * The customer-facing payment screen: choose a method, tap the card or feed
 * cash, pay, take the receipt. 戻る before paying returns any cash put in.
 */
data class Payment(
    val total: Int,
    val item: String,
    val step: Step = Step.Choose,
    val inserted: Int = 0,
) : SceneMachine {
    sealed interface Step {
        data object Choose : Step
        data object Card : Step
        data object Cash : Step
        data class Paid(val method: PaymentMethod) : Step
        data class Done(val method: PaymentMethod) : Step
    }

    val change: Int get() = maxOf(0, inserted - total)

    override val outcome: MachineOutcome?
        get() = (step as? Step.Done)?.let { MachineOutcome.Paid(it.method, if (it.method == PaymentMethod.Cash) change else 0) }

    override fun send(action: MachineAction): Payment = when {
        step == Step.Choose && action is MachineAction.Choose ->
            copy(step = if (action.method == PaymentMethod.Card) Step.Card else Step.Cash)
        (step == Step.Card || step == Step.Cash) && action == MachineAction.Back -> copy(step = Step.Choose, inserted = 0)
        step == Step.Card && action == MachineAction.TapCard -> copy(step = Step.Paid(PaymentMethod.Card))
        step == Step.Cash && action is MachineAction.Insert && action.yen > 0 -> copy(inserted = inserted + action.yen)
        step == Step.Cash && action == MachineAction.Settle && inserted >= total -> copy(step = Step.Paid(PaymentMethod.Cash))
        step is Step.Paid && action == MachineAction.Take -> copy(step = Step.Done(step.method))
        else -> this
    }

    override val screen: MachineScreen
        get() {
            val back = onScreen(MachineAction.Back, "戻る")
            return when (val step = step) {
                Step.Choose -> MachineScreen(
                    headline = "お支払い方法を選択してください",
                    info = "お買上げ 1 点　合計 ${yen(total)}",
                    buttons = listOf(
                        onScreen(MachineAction.Choose(PaymentMethod.Card), "クレジットカード", primary = true),
                        onScreen(MachineAction.Choose(PaymentMethod.Cash), "現金"),
                    ),
                )
                Step.Card -> MachineScreen(
                    headline = "カードをかざしてください",
                    info = "お支払い金額 ${yen(total)}",
                    buttons = listOf(back),
                    hardware = listOf(hardware(MachineAction.TapCard, MachineControl.HardwareKind.CardReader)),
                )
                Step.Cash -> {
                    val short = total - inserted
                    val balance = if (short > 0) "不足 ${yen(short)}" else "おつり ${yen(-short)}"
                    MachineScreen(
                        headline = "現金を入れてください",
                        info = "合計 ${yen(total)}　お預り ${yen(inserted)}\n$balance",
                        buttons = (if (short <= 0) listOf(onScreen(MachineAction.Settle, "精算する", primary = true)) else emptyList()) + back,
                        hardware = listOf(100, 500, 1000).map {
                            hardware(MachineAction.Insert(it), MachineControl.HardwareKind.CoinSlot(it))
                        },
                    )
                }
                is Step.Paid -> {
                    val cash = step.method == PaymentMethod.Cash
                    MachineScreen(
                        headline = if (cash) "おつりとレシートをお取りください" else "レシートをお取りください",
                        paper = "お買上げ明細\n$item　${yen(total)}\n" +
                            if (cash) "お預り　${yen(inserted)}\nおつり　${yen(change)}" else "クレジット",
                        hardware = listOf(hardware(MachineAction.Take, MachineControl.HardwareKind.Tray(withChange = cash))),
                    )
                }
                is Step.Done -> MachineScreen(headline = "ありがとうございました", info = "お会計が完了しました")
            }
        }
}

/** The age check shown when the basket has alcohol. */
data class AgeConfirm(val answer: AgeAnswer? = null) : SceneMachine {
    override val outcome: MachineOutcome? get() = answer?.let { MachineOutcome.Age(it) }

    override fun send(action: MachineAction): AgeConfirm =
        if (answer == null && action is MachineAction.AnswerAge) copy(answer = action.answer) else this

    override val screen: MachineScreen
        get() = when (answer) {
            null -> MachineScreen(
                headline = "年齢確認",
                info = "お酒をご購入のお客様へ\n20歳以上ですか？",
                buttons = listOf(
                    onScreen(MachineAction.AnswerAge(AgeAnswer.Yes), "はい", primary = true),
                    onScreen(MachineAction.AnswerAge(AgeAnswer.No), "いいえ"),
                    onScreen(MachineAction.AnswerAge(AgeAnswer.Cancel), "取消"),
                ),
            )
            AgeAnswer.Yes -> MachineScreen(headline = "年齢確認が完了しました", info = "お会計へお進みください")
            AgeAnswer.No -> MachineScreen(headline = "係員が確認します", info = "この商品はお会計を保留しています")
            AgeAnswer.Cancel -> MachineScreen(headline = "年齢確認を取り消しました", info = "係員にお声がけください")
        }
}

/**
 * The 食券機 at a ramen shop's door: pick an item, confirm, feed ¥1,000 notes,
 * take the ticket and change. The menu comes from the scene.
 */
data class FoodTicket(
    val menu: List<MachineMenuItem>,
    val step: Step = Step.Choose,
    val inserted: Int = 0,
) : SceneMachine {
    companion object {
        val defaultMenu = listOf(
            MachineMenuItem("shoyu", "醤油ラーメン", 900),
            MachineMenuItem("miso", "味噌ラーメン", 980),
            MachineMenuItem("gyoza", "餃子", 380),
        )
    }

    sealed interface Step {
        data object Choose : Step
        data class Confirm(val index: Int) : Step
        data class Cash(val index: Int) : Step
        data class Issued(val index: Int) : Step
        data class Done(val index: Int) : Step
    }

    override val outcome: MachineOutcome?
        get() = (step as? Step.Done)?.let { MachineOutcome.Ticket(menu[it.index].id, inserted - menu[it.index].price) }

    override fun send(action: MachineAction): FoodTicket {
        val step = step
        return when {
            step == Step.Choose && action is MachineAction.PickDish && action.index in menu.indices ->
                copy(step = Step.Confirm(action.index))
            step is Step.Confirm && action == MachineAction.Back -> copy(step = Step.Choose)
            step is Step.Confirm && action == MachineAction.PayDishInCash -> copy(step = Step.Cash(step.index))
            step is Step.Cash && action is MachineAction.Insert && action.yen > 0 && inserted < menu[step.index].price ->
                copy(inserted = inserted + action.yen)
            step is Step.Cash && action == MachineAction.IssueTicket && inserted >= menu[step.index].price ->
                copy(step = Step.Issued(step.index))
            step is Step.Issued && action == MachineAction.Take -> copy(step = Step.Done(step.index))
            else -> this
        }
    }

    override val screen: MachineScreen
        get() = when (val step = step) {
            Step.Choose -> MachineScreen(
                headline = "食券をお選びください",
                info = "メニュー",
                buttons = menu.mapIndexed { index, item ->
                    onScreen(MachineAction.PickDish(index), "${item.name}　${yen(item.price)}")
                },
            )
            is Step.Confirm -> {
                val item = menu[step.index]
                MachineScreen(
                    headline = "ご購入内容の確認",
                    info = "${item.name} × 1　${yen(item.price)}",
                    buttons = listOf(
                        onScreen(MachineAction.PayDishInCash, "現金で支払う", primary = true),
                        onScreen(MachineAction.Back, "戻る"),
                    ),
                )
            }
            is Step.Cash -> {
                val price = menu[step.index].price
                val enough = inserted >= price
                MachineScreen(
                    headline = "現金を入れてください",
                    info = "合計 ${yen(price)}　お預り ${yen(inserted)}" +
                        if (enough) "\nおつり ${yen(inserted - price)}" else "\n不足 ${yen(price - inserted)}",
                    buttons = if (enough) listOf(onScreen(MachineAction.IssueTicket, "発券する", primary = true)) else emptyList(),
                    hardware = if (enough) emptyList() else listOf(
                        hardware(MachineAction.Insert(1000), MachineControl.HardwareKind.CoinSlot(1000)),
                    ),
                )
            }
            is Step.Issued -> {
                val item = menu[step.index]
                MachineScreen(
                    headline = "食券とおつりをお取りください",
                    paper = "食券　No. 023\n${item.name}　${yen(item.price)}\nおつり　${yen(inserted - item.price)}",
                    hardware = listOf(hardware(MachineAction.Take, MachineControl.HardwareKind.TicketTray)),
                )
            }
            is Step.Done -> MachineScreen(headline = "食券を店員にお渡しください", info = "${menu[step.index].name} × 1")
        }
}

/**
 * A self-order kiosk at a fast-food counter: it offers to make the item a set,
 * shows the price difference, takes the order and gives a number.
 */
data class Kiosk(
    val item: String,
    val price: Int,
    val options: List<MachineMenuItem>,
    val step: Step = Step.Offer,
) : SceneMachine {
    companion object {
        val defaultOptions = listOf(
            MachineMenuItem("set", "セットにする", 300),
            MachineMenuItem("single", "単品のまま", 0),
        )
    }

    sealed interface Step {
        data object Offer : Step
        data class Confirm(val index: Int) : Step
        data class Placed(val index: Int) : Step
    }

    private fun total(index: Int) = price + options[index].price

    override val outcome: MachineOutcome?
        get() = (step as? Step.Placed)?.let { MachineOutcome.KioskOrder(options[it.index].id, total(it.index)) }

    override fun send(action: MachineAction): Kiosk {
        val step = step
        return when {
            step == Step.Offer && action is MachineAction.PickOption && action.index in options.indices ->
                copy(step = Step.Confirm(action.index))
            step is Step.Confirm && action == MachineAction.Back -> copy(step = Step.Offer)
            step is Step.Confirm && action == MachineAction.ConfirmOrder -> copy(step = Step.Placed(step.index))
            else -> this
        }
    }

    override val screen: MachineScreen
        get() = when (val step = step) {
            Step.Offer -> MachineScreen(
                headline = "セットにしますか？",
                info = "$item　${yen(price)}",
                buttons = options.mapIndexed { index, option ->
                    val label = if (option.price > 0) "${option.name}　+${yen(option.price)}" else option.name
                    onScreen(MachineAction.PickOption(index), label)
                },
            )
            is Step.Confirm -> {
                val option = options[step.index]
                val line = if (option.price > 0) "$item（${option.name}）" else item
                MachineScreen(
                    headline = "ご注文内容の確認",
                    info = "$line\n合計 ${yen(total(step.index))}",
                    buttons = listOf(
                        onScreen(MachineAction.ConfirmOrder, "注文する", primary = true),
                        onScreen(MachineAction.Back, "戻る"),
                    ),
                )
            }
            is Step.Placed -> MachineScreen(
                headline = "ご注文ありがとうございます",
                info = "番号をお呼びするまでお待ちください",
                paper = "受付番号　021\n合計　${yen(total(step.index))}",
            )
        }
}

/**
 * The table tablet: pick a category and a dish, set how many, confirm, send,
 * then look at the order history.
 */
data class OrderTablet(
    val step: Step = Step.Browse,
    val category: TabletCategory = TabletCategory.Noodles,
    val cartCategory: TabletCategory? = null,
    val quantity: Int = 0,
) : SceneMachine {
    companion object {
        val items: Map<TabletCategory, Pair<String, Int>> = mapOf(
            TabletCategory.Noodles to ("きつねうどん" to 680),
            TabletCategory.Drinks to ("アイス烏龍茶" to 280),
        )
        const val MAX_COUNT = 9
    }

    enum class Step { Browse, Cart, Confirm, Sent, History }

    private val cartItem: Pair<String, Int>? get() = cartCategory?.let { items[it] }

    override val outcome: MachineOutcome?
        get() {
            if (step != Step.Sent && step != Step.History) return null
            val (name, price) = cartItem ?: return null
            return MachineOutcome.Ordered(name, quantity, price * quantity)
        }

    override fun send(action: MachineAction): OrderTablet = when {
        step == Step.Browse && action is MachineAction.PickCategory -> copy(category = action.category)
        step == Step.Browse && action == MachineAction.AddToCart -> copy(cartCategory = category, quantity = 1, step = Step.Cart)
        step == Step.Cart && action == MachineAction.More && quantity < MAX_COUNT -> copy(quantity = quantity + 1)
        step == Step.Cart && action == MachineAction.Fewer ->
            if (quantity - 1 <= 0) copy(quantity = 0, cartCategory = null, step = Step.Browse) else copy(quantity = quantity - 1)
        step == Step.Cart && action == MachineAction.Review -> copy(step = Step.Confirm)
        step == Step.Confirm && action == MachineAction.Back -> copy(step = Step.Cart)
        step == Step.Confirm && action == MachineAction.PlaceOrder -> copy(step = Step.Sent)
        step == Step.Sent && action == MachineAction.ShowHistory -> copy(step = Step.History)
        else -> this
    }

    override val screen: MachineScreen
        get() = when (step) {
            Step.Browse -> {
                val (name, price) = items[category] ?: ("" to 0)
                MachineScreen(
                    headline = "メニュー",
                    info = "${category.label}　／　注文かご $quantity 点",
                    buttons = TabletCategory.entries.map {
                        onScreen(MachineAction.PickCategory(it), it.label, primary = it == category)
                    } + onScreen(MachineAction.AddToCart, "$name　${yen(price)}"),
                )
            }
            Step.Cart -> {
                val (name, price) = cartItem ?: ("" to 0)
                MachineScreen(
                    headline = "注文かご",
                    info = "$name × $quantity　小計 ${yen(price * quantity)}",
                    buttons = listOf(
                        onScreen(MachineAction.Fewer, "数量を減らす"),
                        onScreen(MachineAction.More, "数量を増やす", enabled = quantity < MAX_COUNT),
                        onScreen(MachineAction.Review, "注文内容を確認", primary = true),
                    ),
                )
            }
            Step.Confirm -> {
                val (name, price) = cartItem ?: ("" to 0)
                MachineScreen(
                    headline = "この内容で注文しますか？",
                    info = "$name × $quantity　${yen(price * quantity)}",
                    buttons = listOf(
                        onScreen(MachineAction.PlaceOrder, "注文する", primary = true),
                        onScreen(MachineAction.Back, "戻る"),
                    ),
                )
            }
            Step.Sent, Step.History -> {
                val (name, price) = cartItem ?: ("" to 0)
                MachineScreen(
                    headline = if (step == Step.Sent) "ご注文を受け付けました" else "注文履歴",
                    info = "注文番号 001\n$name × $quantity　${yen(price * quantity)}",
                    buttons = if (step == Step.Sent) listOf(onScreen(MachineAction.ShowHistory, "注文履歴を見る")) else emptyList(),
                )
            }
        }
}
