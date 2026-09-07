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
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.takeFrom
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException

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

    /** Builds, authenticates, sends, retries once on 401, and checks the status. */
    @PublishedApi
    internal suspend fun send(endpoint: Endpoint, method: HttpMethod, body: Any?): HttpResponse {
        val descriptor = endpoint.descriptor
        val policy = descriptor.policy

        val template = HttpRequestBuilder().apply {
            this.method = method
            url(baseUrl.trimEnd('/') + descriptor.path)
            descriptor.query.forEach { (name, value) -> url.parameters.append(name, value) }
            accept(ContentType.Application.Json)
            timeout { requestTimeoutMillis = policy.timeoutMillis }
            // A shared on-disk cache is shared across accounts on the device, so
            // anything that can vary by caller must never be served from it.
            if (!policy.access.mayBeCachedAcrossCallers ||
                policy.cachePolicy == CachePolicy.ReloadIgnoringCache
            ) {
                header(HttpHeaders.CacheControl, "no-cache")
            }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

        when {
            policy.access.requiresToken ->
                template.header(HttpHeaders.Authorization, "Bearer ${tokens.validAccessToken()}")

            policy.access.attachesTokenWhenAvailable && tokens.isSignedIn ->
                runCatching { tokens.validAccessToken() }.getOrNull()
                    ?.let { template.header(HttpHeaders.Authorization, "Bearer $it") }
        }

        val first = execute(template)
        if (first.status.value == 401 && policy.access.mayRetryUnauthorized) {
            // Same request, fresh Authorization. Asking for a token refreshes
            // the session as a side effect, so re-reading it is enough.
            val fresh = tokens.validAccessToken()
            val retry = HttpRequestBuilder().takeFrom(template).apply {
                headers.remove(HttpHeaders.Authorization)
                header(HttpHeaders.Authorization, "Bearer $fresh")
            }
            return check(execute(retry))
        }
        return check(first)
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
