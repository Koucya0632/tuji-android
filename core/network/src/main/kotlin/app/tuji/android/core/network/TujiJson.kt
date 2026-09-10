package app.tuji.android.core.network

import kotlinx.serialization.json.Json

/**
 * Project-wide JSON config, so every call site agrees.
 *
 * ⚠️ The backend is **not** uniformly camelCase: `/api/words` is, and
 * `/api/study/queue` is not (see [app.tuji.android.core.model.StudyCard]).
 * An earlier version of this comment claimed otherwise, and the study flow
 * failed to decode in production because of it.
 *
 * [Json.ignoreUnknownKeys] is `true` to match Swift's `Codable`, which ignores
 * extra keys silently. Without it every field the backend adds — and it adds
 * them without telling the clients — becomes a hard decode failure on an app
 * already in users' hands.
 *
 * `explicitNulls = false` for the encode side: the server routes read camelCase
 * body keys directly and were written for the web client. iOS learned this the
 * expensive way — converting keys to snake_case on the way out silently dropped
 * every multi-word field, which showed up as a settings save returning 200 with
 * defaults and a hard 400 from study/answer.
 *
 * `coerceInputValues = true` for the decode side, because **this backend sends
 * explicit nulls for absent collections on some routes and omits the key on
 * others**. `/api/words/{id}` omits `examples`; `/api/atlas/items/{id}/detail`
 * sends `"examples": null` — and a default value covers a *missing* key, not a
 * present null. Without it, opening a card the user photographed failed the
 * whole decode on a field nobody was reading.
 *
 * It is set here rather than by making each field nullable on purpose: the fact
 * is about the transport, not about any one model, and fixing it one field at a
 * time leaves the next one to be found by a user.
 */
val TujiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    // Lets a model declare an alternative wire name with @JsonNames. Needed
    // because the payload is **mixed**: /api/study/queue hands back near-raw
    // rows (`image_url`, `card_type`) alongside camelCase keys
    // (`readingSegments`) in the same object. iOS's `.convertFromSnakeCase`
    // absorbs both silently; here each raw field says so in the model.
    useAlternativeNames = true
}
