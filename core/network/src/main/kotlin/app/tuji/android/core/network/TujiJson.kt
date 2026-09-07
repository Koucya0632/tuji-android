package app.tuji.android.core.network

import kotlinx.serialization.json.Json

/**
 * Project-wide JSON config, so every call site agrees.
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
 */
val TujiJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = false
}
