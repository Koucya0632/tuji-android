package app.tuji.android.core.network

import app.tuji.android.core.model.LearningDirection

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

    data class Categories(val lang: String) : Endpoint {
        override val descriptor get() = EndpointDescriptor(
            path = "/api/categories",
            query = listOf("lang" to lang),
            policy = EndpointPolicy.PublicCached,
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
