package app.tuji.android.core.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.takeFrom
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.readUTF8Line
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Typed HTTP client. Every protected request automatically picks up the current
 * access token via [AccessTokenProvider]; on 401 it retries once after the
 * session refreshes.
 *
 * Ported from `Tuji/Core/Networking/APIClient.swift`, including the mistake it
 * had to fix: **the retry re-sends the request, not the ingredients.** The iOS
 * version used to rebuild from the original body, which silently discarded
 * every per-call mutation made afterwards — a retried photo upload went out
 * with no body at all, because `upload` attaches its multipart payload after
 * building. Copying the request and re-stamping one header cannot diverge from
 * what was actually sent.
 *
 * [engine] is injectable for the same reason `urlSession` is on iOS: without it
 * the transport can only be exercised against the real backend, and the 401
 * retry — the part most worth testing — never runs in a test at all.
 */
class TujiApiClient(
    private val baseUrl: String,
    @PublishedApi internal val tokens: AccessTokenProvider = NoAccessToken,
    engine: HttpClientEngine? = null,
    /** Read per request, so a phone that changes zone mid-session follows it. */
    private val timeZone: () -> String = { java.time.ZoneId.systemDefault().id },
) {
    @PublishedApi
    internal val http: HttpClient = HttpClient(engine ?: OkHttp.create()) {
        install(ContentNegotiation) { json(TujiJson) }
        install(HttpTimeout)
        // Statuses are this client's business, not the plugin's: `check` turns
        // them into ApiError.Http with the body attached, and the 401 branch has
        // to see the response rather than an exception thrown past it.
        expectSuccess = false
    }

    suspend inline fun <reified T> get(endpoint: Endpoint): T =
        call(endpoint, HttpMethod.Get, body = null)

    suspend inline fun <reified T> post(endpoint: Endpoint, body: Any?): T =
        call(endpoint, HttpMethod.Post, body)

    suspend inline fun <reified T> postIdempotent(endpoint: Endpoint, body: Any?, key: String): T {
        val response = send(endpoint, HttpMethod.Post, body, mapOf("Idempotency-Key" to key))
        return try { response.body<T>() }
        catch (e: CancellationException) { throw e }
        catch (e: Throwable) { throw ApiError.Decoding(e) }
    }

    suspend inline fun <reified T> patch(endpoint: Endpoint, body: Any?): T =
        call(endpoint, HttpMethod.Patch, body)

    suspend inline fun <reified T> delete(endpoint: Endpoint): T =
        call(endpoint, HttpMethod.Delete, body = null)

    @PublishedApi
    internal suspend inline fun <reified T> call(
        endpoint: Endpoint,
        method: HttpMethod,
        body: Any?,
    ): T {
        val response = send(endpoint, method, body)
        return try {
            response.body<T>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            throw ApiError.Decoding(e)
        }
    }

    /**
     * POST whose answer is one JSON object per line, read as each line
     * arrives — iOS's `postLines`. 情境故事 streams its scene this way, so
     * reading starts seconds before the writing ends.
     *
     * The status is checked before the first line, the way [send] checks it:
     * a refusal throws [ApiError.Http] with its body, and a 401 is retried
     * once with a fresh token. A connection lost partway ends the flow with
     * [ApiError.Transport].
     */
    fun postLines(endpoint: Endpoint, body: Any?): Flow<String> = flow {
        val (template, sent) = authorized(endpoint, HttpMethod.Post, body, mapOf(HttpHeaders.Accept to "application/x-ndjson"))
        suspend fun open(builder: HttpRequestBuilder, retry: Boolean) {
            try {
                http.prepareRequest(HttpRequestBuilder().takeFrom(builder)).execute { response ->
                    if (response.status.value == 401 && retry && endpoint.descriptor.policy.access.mayRetryUnauthorized) {
                        val fresh = tokens.refreshedAccessToken(rejected = sent)
                        val again = HttpRequestBuilder().takeFrom(builder).apply {
                            headers.remove(HttpHeaders.Authorization)
                            header(HttpHeaders.Authorization, "Bearer $fresh")
                        }
                        open(again, retry = false)
                        return@execute
                    }
                    check(response)
                    val channel = response.bodyAsChannel()
                    while (true) {
                        val line = channel.readUTF8Line() ?: break
                        if (line.isNotBlank()) emit(line)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                throw e
            } catch (e: Throwable) {
                throw ApiError.Transport(e)
            }
        }
        open(template, retry = true)
    }

    /** Builds, authenticates, sends, retries once on 401, and checks the status. */
    @PublishedApi
    internal suspend fun send(endpoint: Endpoint, method: HttpMethod, body: Any?, extraHeaders: Map<String, String> = emptyMap()): HttpResponse {
        val (template, sent) = authorized(endpoint, method, body, extraHeaders)
        val policy = endpoint.descriptor.policy
        val first = execute(template)
        if (first.status.value == 401 && policy.access.mayRetryUnauthorized) {
            // Same request, fresh Authorization — fresh because the server said
            // so, not because the device's clock agrees. See refreshedAccessToken.
            val fresh = tokens.refreshedAccessToken(rejected = sent)
            val retry = HttpRequestBuilder().takeFrom(template).apply {
                headers.remove(HttpHeaders.Authorization)
                header(HttpHeaders.Authorization, "Bearer $fresh")
            }
            return check(execute(retry))
        }
        return check(first)
    }

    /** The request [send] and [postLines] make, with the token it carries. */
    private suspend fun authorized(
        endpoint: Endpoint,
        method: HttpMethod,
        body: Any?,
        extraHeaders: Map<String, String>,
    ): Pair<HttpRequestBuilder, String?> {
        val descriptor = endpoint.descriptor
        val policy = descriptor.policy

        val template = HttpRequestBuilder().apply {
            this.method = method
            url(baseUrl.trimEnd('/') + descriptor.path)
            descriptor.query.forEach { (name, value) -> url.parameters.append(name, value) }
            // A streamed answer asks for its own type instead.
            if (HttpHeaders.Accept !in extraHeaders) accept(ContentType.Application.Json)
            // The account's "today" — streak, heatmap, calendar, 今日目標 and
            // the check-in reward — is the phone's day, as on iOS. Without it
            // the server falls back to Asia/Taipei.
            header(TIMEZONE_HEADER, timeZone())
            extraHeaders.forEach { (name, value) -> header(name, value) }
            // **Both**, not just the request timeout. Ktor counts them
            // separately: `requestTimeoutMillis` bounds the whole call, while
            // `socketTimeoutMillis` bounds the gap *between packets* — and a
            // slow endpoint's whole point is that nothing arrives while the
            // server thinks. With only the first one set, the AI calls died on
            // the engine's default socket timeout long before their 60 seconds,
            // and said so in a Ktor sentence with the URL in it.
            timeout {
                requestTimeoutMillis = policy.timeoutMillis
                socketTimeoutMillis = policy.timeoutMillis
            }
            // A shared on-disk cache is shared across accounts on the device, so
            // anything that can vary by caller must never be served from it.
            if (!policy.access.mayBeCachedAcrossCallers ||
                policy.cachePolicy == CachePolicy.ReloadIgnoringCache
            ) {
                header(HttpHeaders.CacheControl, "no-cache")
            }
            if (body != null) {
                // A multipart body carries its own content type — with the
                // boundary in it — so stamping JSON over the top produces a
                // request the server cannot parse and an error that says
                // nothing about why.
                if (body !is io.ktor.http.content.OutgoingContent) {
                    contentType(ContentType.Application.Json)
                }
                setBody(body)
            }
        }

        val sent: String? = when {
            policy.access.requiresToken -> tokens.validAccessToken()
            policy.access.attachesTokenWhenAvailable && tokens.isSignedIn ->
                runCatching { tokens.validAccessToken() }.getOrNull()
            else -> null
        }
        sent?.let { template.header(HttpHeaders.Authorization, "Bearer $it") }
        return template to sent
    }

    private suspend fun execute(builder: HttpRequestBuilder): HttpResponse =
        try {
            // A fresh copy each send: an HttpRequestBuilder is consumed by the
            // pipeline, and the 401 retry needs the template intact.
            http.request(HttpRequestBuilder().takeFrom(builder))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            throw e
        } catch (e: Throwable) {
            throw ApiError.Transport(e)
        }

    private suspend fun check(response: HttpResponse): HttpResponse {
        if (response.status.isSuccess()) return response
        throw ApiError.Http(response.status.value, runCatching { response.bodyAsText() }.getOrNull())
    }
}

/** The phone's IANA zone, on every request; see [TujiApiClient.send]. */
const val TIMEZONE_HEADER = "X-Tuji-Timezone"
