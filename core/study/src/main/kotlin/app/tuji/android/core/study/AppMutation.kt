package app.tuji.android.core.study

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Something the user did that other screens draw, named by what they did
 * rather than by which stores it happens to touch.
 *
 * The rule "this write refreshes those screens" used to be restated at each
 * producer: two counters in the shell, a lambda per view model, and a direct
 * store call in the capture queue — and they had drifted from iOS's
 * `AtlasMutation`, which this mirrors and extends to the consumption and study
 * sides. Producers now say what happened; [targets] owns the consequences.
 */
sealed interface AppMutation {

    // ── 圖鑑管理: the account's own cards ──

    /** 自製圖鑑 cards were deleted. Frees a slot, so the entitlement that gates capture goes stale with it. */
    data object ItemsDeleted : AppMutation

    /** A capture finished generating its cards. A new word, and one slot fewer. */
    data object CaptureCompleted : AppMutation

    /** A card was put up for 物見 review. Nothing in the account's own study moves. */
    data object ItemPublished : AppMutation

    /** 取消公開 on a card. It stays in 圖鑑 and every saver keeps their progress; only the public row goes. */
    data object ItemWithdrawn : AppMutation

    /** A card's 景點 or its 放到景點頁 changed. Neither 圖鑑 nor 物見's wall shows a spot. */
    data object ItemSpotChanged : AppMutation

    // ── 圖鑑管理: the account's own 合集 ──

    data object CollectionPublished : AppMutation

    data object CollectionWithdrawn : AppMutation

    /** Only reaches the wall if it was on it. */
    data class CollectionDeleted(val wasPublic: Boolean) : AppMutation

    /** Title or description. A draft is not on the wall, so re-reading it buys nothing. */
    data class CollectionEdited(val isPublic: Boolean) : AppMutation

    /** Same condition as [CollectionEdited]. */
    data class CollectionAvatarChanged(val isPublic: Boolean) : AppMutation

    // ── 物見: other people's cards ──

    /** 加入學習 or 停止學習 on one public card: it joins or leaves 已收進 and the study queue. */
    data object CommunityItemSaved : AppMutation

    /** 全部加入學習 on a 合集. */
    data object CollectionLearned : AppMutation

    /** 收藏 or 取消收藏 on a 合集. Puts nothing in the reader's 圖鑑. */
    data object CollectionBookmarked : AppMutation

    // ── 我 and study ──

    /** The name, bio or face other people see. */
    data object ProfileSaved : AppMutation

    /** A 學新字 or 複習 session closed: every badge's score moved. */
    data object StudySessionClosed : AppMutation

    /** 清除學習進度. */
    data object ProgressCleared : AppMutation

    /** Play granted a purchase: the plan, or a pack of points. */
    data object PurchaseDelivered : AppMutation
}

/** The whole policy, in one place. */
val AppMutation.targets: Set<RefreshTarget>
    get() = when (this) {
        // iOS `changesOwnAtlas`: the grid (cards surface as words there), the
        // counters, the study stats and the quota. Mastery as well on a
        // delete, because the deleted words' scores leave every badge.
        AppMutation.ItemsDeleted -> setOf(
            RefreshTarget.PersonalShelves,
            RefreshTarget.Progress,
            RefreshTarget.Stats,
            RefreshTarget.Mastery,
            RefreshTarget.Entitlement,
        )
        // A new card has no score yet, so no mastery.
        AppMutation.CaptureCompleted -> setOf(
            RefreshTarget.PersonalShelves,
            RefreshTarget.Progress,
            RefreshTarget.Stats,
            RefreshTarget.Entitlement,
        )
        // iOS `invalidatesPublicFeed`. Not the learning stores: the card is
        // in the author's 圖鑑 and queue whether or not it is public.
        AppMutation.ItemPublished, AppMutation.ItemWithdrawn -> setOf(RefreshTarget.CommunityFeed)
        // The store that changed it re-reads itself; nothing else shows it.
        AppMutation.ItemSpotChanged -> emptySet()

        AppMutation.CollectionPublished, AppMutation.CollectionWithdrawn ->
            setOf(RefreshTarget.MyCollections, RefreshTarget.CommunityFeed)
        // 圖鑑管理's list already dropped it; only the wall can still show it.
        is AppMutation.CollectionDeleted -> if (wasPublic) setOf(RefreshTarget.CommunityFeed) else emptySet()
        is AppMutation.CollectionEdited ->
            setOf(RefreshTarget.MyCollections) + if (isPublic) setOf(RefreshTarget.CommunityFeed) else emptySet()
        is AppMutation.CollectionAvatarChanged ->
            setOf(RefreshTarget.MyCollections) + if (isPublic) setOf(RefreshTarget.CommunityFeed) else emptySet()

        // iOS `CommunityLearningRefresh`: the words, and what studying them moves.
        AppMutation.CommunityItemSaved, AppMutation.CollectionLearned -> setOf(
            RefreshTarget.PersonalShelves,
            RefreshTarget.Mastery,
            RefreshTarget.Progress,
        )
        AppMutation.CollectionBookmarked -> setOf(RefreshTarget.CommunitySaved)

        // 我's row and 物見's own-page row both draw the name and face.
        AppMutation.ProfileSaved -> setOf(RefreshTarget.Account, RefreshTarget.CommunityMe)
        // 待複習 is re-read on arrival at 今日, which is where a session ends.
        AppMutation.StudySessionClosed -> setOf(RefreshTarget.Mastery, RefreshTarget.Progress)
        AppMutation.ProgressCleared -> setOf(RefreshTarget.Mastery, RefreshTarget.Progress, RefreshTarget.Stats)
        AppMutation.PurchaseDelivered -> setOf(RefreshTarget.Entitlement, RefreshTarget.CreditBalance)
    }

/**
 * Where mutations go: one per app, so a producer that outlives every screen
 * (生成佇列, Play's purchase listener) and one inside a view model post to the
 * same place.
 *
 * A queue rather than a broadcast. The shell is its one reader, and between an
 * activity going and the next one composing there is no reader at all — a
 * capture finishing then must still reach 圖鑑 when it comes back, which a
 * `SharedFlow` would have dropped on the floor.
 */
class Invalidator {
    private val pending = Channel<Set<RefreshTarget>>(Channel.UNLIMITED)

    /** What to re-read, one mutation's worth at a time. */
    val targets: Flow<Set<RefreshTarget>> = pending.receiveAsFlow()

    fun post(mutation: AppMutation) {
        val targets = mutation.targets
        if (targets.isNotEmpty()) pending.trySend(targets)
    }
}
