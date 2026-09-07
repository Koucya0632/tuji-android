package app.tuji.android.core.network

/**
 * Everything about a request that is not its path or its query — how it
 * authenticates, how it caches, how long it may take. Ported from
 * `Tuji/Core/Networking/EndpointPolicy.swift`.
 *
 * [cachePolicy] has **no default on purpose**. On iOS it used to be decided by
 * a `default:` arm and ten endpoints reached it by omission — nine of them
 * authenticated private reads. Whether a user's own data may sit in a shared
 * on-disk cache is not a question anyone should answer by forgetting to answer
 * it. The other two fields do default, because absent genuinely means
 * "standard" and getting them wrong fails loudly (a 401, not stale personal
 * data).
 */
data class EndpointPolicy(
    val cachePolicy: CachePolicy,
    val access: EndpointAccess = EndpointAccess.Authenticated,
    val timeoutMillis: Long = 15_000,
) {
    companion object {
        /** Authenticated, never served from cache — user data and every write. */
        val PrivateFresh = EndpointPolicy(CachePolicy.ReloadIgnoringCache)

        /** Authenticated, but honours the server's `Cache-Control`. */
        val PrivateServerCached = EndpointPolicy(CachePolicy.UseProtocolCache)

        /** Anonymous read behind the CDN; honours `Cache-Control`. */
        val PublicCached = EndpointPolicy(CachePolicy.UseProtocolCache, EndpointAccess.Anonymous)

        /** Anonymous write (analytics) — nothing to cache. */
        val PublicFresh = EndpointPolicy(CachePolicy.ReloadIgnoringCache, EndpointAccess.Anonymous)

        /** Anonymous read whose response depends on the caller when there is one. */
        val PublicFreshOptionalAuth =
            EndpointPolicy(CachePolicy.ReloadIgnoringCache, EndpointAccess.OptionalToken)

        /**
         * Authenticated AI call — image recognition and enrichment. These take
         * far longer than a normal call.
         */
        val PrivateFreshSlow = EndpointPolicy(CachePolicy.ReloadIgnoringCache, timeoutMillis = 60_000)
    }
}

enum class CachePolicy {
    UseProtocolCache,
    ReloadIgnoringCache,
}

/**
 * How a request authenticates. One value, three cases — on iOS it used to be
 * two independent booleans, which made two illegal states representable and one
 * word mean two things.
 *
 * `isPublic` answered *two* questions that part company on exactly one
 * endpoint: 「attach no bearer」 and 「never retry a 401」. The public collection
 * route is [OptionalToken], so a signed-in caller *does* send a token — and its
 * 401 was the one 401 never retried, leaving the user looking at the guest view
 * of a collection they had saved.
 */
enum class EndpointAccess {
    /** A bearer token is required; the request cannot be made without one. */
    Authenticated,

    /** No token, ever. The response is the same for everybody. */
    Anonymous,

    /**
     * Usable signed out, but a signed-in caller sends its token so the server
     * can reveal account-specific state (「已收藏」 and the like).
     */
    OptionalToken,
    ;

    /** The client must fetch a token before sending. */
    val requiresToken: Boolean get() = this == Authenticated

    /** The client should attach a token if one happens to be available. */
    val attachesTokenWhenAvailable: Boolean get() = this == OptionalToken

    /**
     * A 401 is worth one refresh-and-retry. True wherever a token may have been
     * attached — which includes [OptionalToken], the case the old `!isPublic`
     * guard excluded.
     */
    val mayRetryUnauthorized: Boolean get() = this != Anonymous

    /**
     * Whether a response for this access level may sit in a shared on-disk
     * cache. Anything that can vary by caller may not: the cache is shared
     * across accounts on the device.
     */
    val mayBeCachedAcrossCallers: Boolean get() = this == Anonymous
}

/** One endpoint's complete description. Built once per request by the client. */
data class EndpointDescriptor(
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val policy: EndpointPolicy,
)
