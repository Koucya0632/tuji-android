package app.tuji.android.core.network

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.StudyMode

/**
 * Every route the app can call, and the facts about each one.
 *
 * iOS has 119 cases here. This is M0's slice; the rest arrive with the
 * milestone that needs them, so an endpoint never exists before something
 * reads it.
 *
 * Deliberately **not** `sealed`. iOS spells this as an enum because Swift needs
 * one to `switch` a case onto its descriptor; here each case carries its own
 * descriptor, so nothing ever matches exhaustively over the set and sealing buys
 * no compile-time check. What it does buy is a transport that cannot be tested
 * against an access policy no shipped route uses yet — and the optional-auth
 * case is precisely the one iOS got wrong.
 */
interface Endpoint {
    val descriptor: EndpointDescriptor

    /** The catalogue for one UI language and one learning direction. */
    data class Words(val lang: String, val learning: LearningDirection) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/words",
            query = listOf("lang" to lang, "learning" to learning.wire),
            policy = EndpointPolicy.PublicCached,
        )
    }

    /**
     * 搜尋, for the matches a local list cannot see: 別名 and full definitions
     * live in tables the client never downloads.
     *
     * **`learning` is identity, not a hint.** The response is cached by URL, and
     * leaving the direction out gave both directions one entry — iOS could
     * switch 學習語言, repeat a search, and be served the other language's rows
     * out of its own cache. The server makes the same argument from its side:
     * with the parameter present it need not read the caller's settings, and
     * the edge is allowed to cache at all.
     */
    data class Search(val q: String, val lang: String, val learning: LearningDirection) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/search",
            query = listOf("q" to q, "lang" to lang, "learning" to learning.wire),
            policy = EndpointPolicy.PublicCached,
        )
    }

    data class Word(val id: String, val lang: String, val learning: LearningDirection) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/words/$id",
            query = listOf("lang" to lang, "learning" to learning.wire),
            policy = EndpointPolicy.PublicCached,
        )
    }

    /**
     * The day's counts, without the cards.
     *
     * A separate route from [StudyQueue] even though that one returns `stats`
     * too: 今日 needs the numbers on every appearance and a queue only when the
     * user actually starts a session. Reading them off a queue fetch would
     * mean drawing twenty cards, with their images, to print a 3.
     */
    data class StudyStats(val learning: LearningDirection) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/study/stats",
            query = listOf("learning" to learning.wire),
            policy = EndpointPolicy.PrivateServerCached,
        )
    }

    /**
     * Every word this account has a score for, in one call.
     *
     * `PrivateFresh`, not server-cached: the map is what 圖鑑 and 單字詳情 draw
     * their badges from, and a cached one would show yesterday's tier for the
     * word the user answered thirty seconds ago — on the screen they opened to
     * check exactly that.
     *
     * `learning` because the two decks score separately: the same word id can
     * hold one score as 中→日 and another as 中→英.
     */
    data class UsersMastery(val learning: LearningDirection) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/mastery",
            query = listOf("learning" to learning.wire),
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /**
     * The streak, the 42-cell heatmap and the per-theme seen/total rows.
     *
     * `PrivateServerCached` rather than fresh, unlike [UsersMastery]: these
     * numbers move on their own — the streak turns over at midnight and the
     * heatmap gains a day — so the server's own short cache is the right place
     * to answer from, and the client re-asks on appearance rather than holding
     * a copy that ages silently.
     */
    data class UsersProgress(val learning: LearningDirection) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/progress",
            query = listOf("learning" to learning.wire),
            policy = EndpointPolicy.PrivateServerCached,
        )
    }

    /** The account's settings. Read on launch, written on every change. */
    data object UserSettingsRead : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/settings",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /**
     * The same path as [UserSettingsRead]; the verb is the caller's, which is
     * how every other write in this file is spelled.
     */
    data object UserSettingsWrite : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/settings",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /**
     * 清除學習進度 — mastery and answer history, keeping bookmarks, settings and
     * 自製圖鑑. `DELETE`, and the copy on the screen has to name what survives:
     * a user who reads "clear progress" and loses their photographs will not
     * come back.
     */
    data object ClearProgress : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/progress",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /**
     * 書籤 — read, and written on the same path with a different verb.
     *
     * Not scoped by learning direction, deliberately, because the server's list
     * is not: marking a word in 中文→日文 and finding the mark gone after a
     * switch would read as the app forgetting.
     */
    data object UsersFavorites : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/favorites",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /** 已收進 — 物見 items this account saved, shaped as catalogue words. */
    data class UsersSavedWords(val lang: String, val learning: LearningDirection) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/saved-words",
            query = listOf("lang" to lang, "learning" to learning.wire),
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /** 刪除帳號. Sent as a POST — that is the verb the server route has. */
    data object DeleteAccount : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/delete-account",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    // MARK: 物見（wire 上叫 public/community）

    /**
     * The 物見 feed.
     *
     * `PublicCached` on purpose: these routes are anonymous and share one CDN
     * cache. That is also why 封鎖 filters on the client — see `BlockList`.
     */
    data class AtlasFeed(val limit: Int) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public",
            query = listOf("limit" to limit.toString()),
            policy = EndpointPolicy.PublicCached,
        )
    }

    data class AtlasItem(val slug: String, val lang: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public/$slug",
            query = listOf("lang" to lang),
            policy = EndpointPolicy.PublicCached,
        )
    }

    data class AtlasAuthorPage(val handle: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public/authors/$handle",
            policy = EndpointPolicy.PublicCached,
        )
    }

    /**
     * Published collections.
     *
     * `lang` is required — the backend 400s without it — because the feed is
     * scoped to the direction the user is learning.
     */
    data class AtlasCollections(val lang: String, val limit: Int) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public/collections",
            query = listOf("lang" to lang, "limit" to limit.toString()),
            policy = EndpointPolicy.PublicCached,
        )
    }

    data class AtlasCollection(val slug: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public/collections/$slug",
            policy = EndpointPolicy.PublicCached,
        )
    }

    data class AtlasSave(val slug: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public/$slug/save",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    data class AtlasItemReport(val slug: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public/$slug/report",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    data class AtlasCollectionReport(val slug: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public/collections/$slug/report",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    data class AtlasAuthorReport(val handle: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/public/authors/$handle/report",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    // MARK: 自製圖鑑（拍照 → 辨識 → 確認 → 發布）

    /** Upload a photo. Recognition runs server-side in the same request. */
    data object AtlasImages : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/images",
            // Upload plus a vision pass in one round trip; the default timeout
            // is for reads and this is neither fast nor retriable.
            policy = EndpointPolicy.PrivateFreshSlow,
        )
    }

    /** A second, explicit recognition pass. Costs another AI call. */
    data class AtlasRecognize(val imageId: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/images/$imageId/recognize",
            policy = EndpointPolicy.PrivateFreshSlow,
        )
    }

    data class AtlasConfirm(val imageId: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/images/$imageId/confirm",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /** Make the study cards for a confirmed item. */
    data class AtlasCards(val itemId: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/items/$itemId/cards",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /**
     * Put a finished item on 物見.
     *
     * A separate, explicit step — confirming a capture makes a private card and
     * nothing more. Publishing someone's own photograph to a public feed is a
     * decision, not a side effect of naming it.
     */
    data class AtlasPublish(val itemId: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/items/$itemId/publish",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /** 取消公開. Reversible by design — the item and its SRS history stay. */
    data class AtlasWithdraw(val itemId: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/items/$itemId/withdraw",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /** Tier, limits and usage. The server re-checks on every write; this is a mirror. */
    data object Entitlement : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/atlas/entitlement",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /** Who the signed-in user is. */
    data object Me : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/me",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /** The account's 封鎖 list. Server-stored, client-applied. */
    data object Blocks : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/users/blocks",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    data class Categories(val lang: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/categories",
            query = listOf("lang" to lang),
            policy = EndpointPolicy.PublicCached,
        )
    }

    /**
     * A study session's cards.
     *
     * `lang` and `learning` are the **live** UI language and direction, not the
     * debounced server settings: a queue built seconds after a switch would
     * otherwise come from the language the user just left.
     */
    data class StudyQueue(
        val mode: StudyMode,
        val limit: Int,
        val new: Int,
        val categories: List<String>,
        val lang: String,
        val learning: LearningDirection,
    ) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/study/queue",
            query = listOf(
                "mode" to mode.wire,
                "limit" to limit.toString(),
                "new" to new.toString(),
                // Comma-separated category ids; empty = no filter (study all).
                // The backend strips empty / "all" sentinels for us.
                "category" to categories.joinToString(","),
                "lang" to lang,
                "learning" to learning.wire,
            ),
            policy = EndpointPolicy.PrivateServerCached,
        )
    }

    /**
     * One SRS answer. `PrivateFresh`: it is a write, and there is nothing to
     * cache about it.
     */
    data object StudyAnswer : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/study/answer",
            policy = EndpointPolicy.PrivateFresh,
        )
    }

    /**
     * Technically anonymous-friendly (returns null), but kept authed so a debug
     * button actually exercises the Bearer path. The backend tolerates either.
     */
    data object SmokeWhoami : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/test_smoke/whoami",
            policy = EndpointPolicy.PrivateServerCached,
        )
    }

    /** Kept because logging wants exactly this one fact. */
    val path: String get() = descriptor.path
}
