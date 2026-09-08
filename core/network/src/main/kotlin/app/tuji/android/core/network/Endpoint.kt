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
