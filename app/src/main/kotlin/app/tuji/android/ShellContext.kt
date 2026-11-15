package app.tuji.android

import app.tuji.android.account.AccountViewModel
import app.tuji.android.account.ProgressStore
import app.tuji.android.atlas.CardsSourceStore
import app.tuji.android.atlas.CatalogStore
import app.tuji.android.atlas.MasteryStore
import app.tuji.android.atlas.SearchViewModel
import app.tuji.android.community.CommunitySearchViewModel
import app.tuji.android.community.CommunityViewModel
import app.tuji.android.core.catalog.CardsSourceRules
import app.tuji.android.core.model.Entitlement
import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.MemberAccess
import app.tuji.android.core.model.MemberAccessLevel
import app.tuji.android.core.model.MemberFeature
import app.tuji.android.core.model.MembershipTier
import app.tuji.android.core.model.UiLanguage
import app.tuji.android.core.model.UserSettings
import app.tuji.android.core.model.Word
import app.tuji.android.core.study.CompletionReadout
import app.tuji.android.core.study.TodayInputs
import app.tuji.android.manage.AtlasManageViewModel
import app.tuji.android.manage.MyCollectionsViewModel
import app.tuji.android.today.TodayViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * What every page of the signed-in shell is handed.
 *
 * Before this, each route arm in `TujiRoot` re-plumbed the same few things
 * from the root's locals — the stack and the guard against a page that had
 * already left, 會員方案, the entitlement, the two languages, the learning
 * stores, the account id — and a feature that added a page added it to a
 * 2,500-line composable. A feature's host reads them here instead.
 *
 * A view of state the shell already holds, rebuilt as it recomposes; not a
 * store of its own. No container behind it: see [TujiApplication].
 */
class ShellContext(
    /** The repositories and stores, each built once. */
    val app: TujiApplication,
    private val stack: () -> NavStack,
    private val setStack: (NavStack) -> Unit,
    val settings: UserSettings,
    /** The interface language: what this app draws, and the `lang` the server writes in. */
    val uiLanguage: UiLanguage,
    /** Who is signed in, as of this composition. See [currentOwner] for a callback's answer. */
    val ownerId: String?,
    val entitlement: Entitlement?,
    val learning: LearningData,
    val models: ShellModels,
    /** The shell's own: for work that must finish after the page that started it has gone. */
    val scope: CoroutineScope,
) {
    // ── Navigation ──

    /** The stack as it is now — read at call time, so a callback sees the latest. */
    val nav: NavStack get() = stack()

    /** Move the stack from the shell itself: the tab bar, a tab's own page, a sheet over 今日. */
    fun navigate(change: NavStack.() -> NavStack) = setStack(stack().change())

    fun push(route: AppRoute) = navigate { push(route) }

    /** The stack as [page] may move it — only while it is the one on top. */
    fun from(page: AppRoute): PageNav = PageNav(page, this)

    /**
     * Where every lock and every 402 leads. From anywhere, at any time — a
     * refusal can land after its page has gone and still has a page to show —
     * but not twice: two refusals in a row would otherwise stack two copies.
     */
    fun openMembership() = navigate {
        if (current == AccountRoute.Membership) this else push(AccountRoute.Membership)
    }

    /** A card from a grid that mixes the dictionary with 已收進. See [cardRoute]. */
    fun openCard(id: String) = push(cardRoute(id))

    // ── The plan ──

    /** The themes this account may study; null when it is not gated. */
    val studyable: List<String>? get() = entitlement?.membership?.studyableCategories

    val tier: MembershipTier get() = entitlement?.membershipTier ?: MembershipTier.Free

    fun access(feature: MemberFeature, hasOwnData: Boolean = false): MemberAccessLevel =
        MemberAccess.level(feature, entitlement, hasOwnData)

    /**
     * Whether a write would be refused by plan (402). The button goes where
     * that answer would have sent them, rather than asking and failing.
     */
    fun locked(feature: MemberFeature): Boolean = access(feature) == MemberAccessLevel.Locked

    // ── Languages ──

    /** The deck being learned. */
    val direction: LearningDirection get() = settings.direction

    /** [uiLanguage]'s wire value. */
    val uiLang: String get() = uiLanguage.wire

    // ── The account ──

    /** Who is signed in *now* — for a callback that may run after the composition that made it. */
    fun currentOwner(): String? = app.auth.session.value.signedInUser?.id

    /** This account's public handle, once 我's account read has landed. */
    val myUid: String? get() = models.accountState.me?.username

    companion object {
        /**
         * A 已收進 card belongs to somebody else, and its page has an author, a
         * 取消收藏 and a 檢舉 that the dictionary's entry has none of. The id
         * says which — for 圖鑑's grid and for a theme page alike.
         */
        fun cardRoute(id: String): AppRoute.Page =
            CardsSourceRules.savedSlug(id)?.let { CommunityRoute.Item(it) } ?: AtlasRoute.Word(id)
    }
}

/** The stack as one page may move it. Every move is dropped once the page is no longer on top. */
class PageNav internal constructor(val page: AppRoute, private val shell: ShellContext) {

    fun move(change: NavStack.() -> NavStack) = shell.navigate { from(page, change) }

    fun push(route: AppRoute) = move { push(route) }

    fun pop() = move { pop() }

    /** This page goes, [route] takes its place: back from [route] does not land here. */
    fun replace(route: AppRoute) = move { pop().push(route) }

    fun openWord(wordId: String, fromScene: String? = null) = push(AtlasRoute.Word(wordId, fromScene))

    fun openCard(id: String) = push(ShellContext.cardRoute(id))
}

/** The learning stores' answers, read once by the shell for every page that draws them. */
class LearningData(
    val catalog: CatalogStore.Contents,
    val scores: MasteryStore.Scores,
    val personal: CardsSourceStore.Personal,
    val progress: ProgressStore.Snapshot,
    /** 學習主題 as picked, narrowed to what this account may study. */
    val studyCategories: List<String>,
    /** Every word the selection can reach: the catalogue's, plus 自定義 and 物見's. */
    val studyWords: List<Word>,
    /** 完成度, once, for every screen that prints it. */
    val completion: CompletionReadout,
    /** 今日's numbers — 學新字's quota tapers by its backlog. */
    val today: TodayInputs,
)

/**
 * The view models held above the pages, so a page pushed over another and
 * popped finds them as it left them, and a write on one page shows on the
 * page behind.
 */
class ShellModels(
    /** The one holder of the entitlement for the whole shell. */
    val account: AccountViewModel,
    val accountState: AccountViewModel.State,
    val today: TodayViewModel,
    val community: CommunityViewModel,
    val communitySearch: CommunitySearchViewModel,
    val search: SearchViewModel,
    val manage: AtlasManageViewModel,
    val myCollections: MyCollectionsViewModel,
)
