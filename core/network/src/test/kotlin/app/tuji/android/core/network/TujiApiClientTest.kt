package app.tuji.android.core.network

import app.tuji.android.core.model.LearningDirection
import app.tuji.android.core.model.WordsListResponse
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transport, exercised against a stub engine.
 *
 * This whole file exists because of the seam: without [AccessTokenProvider] the
 * only reachable endpoints would be the anonymous ones, and the 401 retry — the
 * part most worth pinning — could not be stood up at all.
 */
class TujiApiClientTest {

    private val wordsJson = """
        {"words":[{"id":"bath-ladle","word":"手おけ","reading":"ておけ",
                   "readingSegments":[{"ruby":"て","text":"手"},{"ruby":null,"text":"おけ"}],
                   "targetLanguage":"ja"}],
         "total":1}
    """.trimIndent()

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private class FakeTokens(
        private val tokens: MutableList<String>,
        override val isSignedIn: Boolean = true,
    ) : AccessTokenProvider {
        var asked = 0
            private set

        override suspend fun validAccessToken(): String {
            asked++
            return if (tokens.size > 1) tokens.removeAt(0) else tokens.first()
        }
    }

    @Test
    fun `a public read sends no bearer`() = runTest {
        var authorization: String? = "unset"
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond(wordsJson, HttpStatusCode.OK, jsonHeaders)
        }
        val api = TujiApiClient("https://example.test", FakeTokens(mutableListOf("t")), engine)

        val response: WordsListResponse = api.get(Endpoint.Words("zh-Hant", LearningDirection.ZH_JA))

        assertNull(authorization)
        assertEquals(1, response.words.size)
        assertEquals("手おけ", response.words.first().word)
    }

    @Test
    fun `the learning direction goes out as its wire value`() = runTest {
        var url = ""
        val engine = MockEngine { request ->
            url = request.url.toString()
            respond(wordsJson, HttpStatusCode.OK, jsonHeaders)
        }
        val api = TujiApiClient("https://example.test", engine = engine)

        api.get<WordsListResponse>(Endpoint.Words("zh-Hant", LearningDirection.ZH_JA))

        // `learning=ja` is a silent 200 that returns the English catalogue.
        assertTrue(url, url.contains("learning=zh-ja"))
        assertTrue(url, url.contains("lang=zh-Hant"))
    }

    @Test
    fun `a 401 on an authenticated read is retried once with a fresh token`() = runTest {
        val sent = mutableListOf<String?>()
        val engine = MockEngine { request ->
            sent += request.headers[HttpHeaders.Authorization]
            if (sent.size == 1) {
                respondError(HttpStatusCode.Unauthorized)
            } else {
                respond("""{"userId":"u1"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val tokens = FakeTokens(mutableListOf("stale", "fresh"))
        val api = TujiApiClient("https://example.test", tokens, engine)

        api.get<Map<String, String>>(Endpoint.SmokeWhoami)

        assertEquals(listOf("Bearer stale", "Bearer fresh"), sent)
        assertEquals(2, tokens.asked)
    }

    @Test
    fun `the retry sends the same request, not a rebuilt one`() = runTest {
        // The iOS bug this pins: rebuilding the retry from the original
        // ingredients dropped every per-call mutation, so a retried upload went
        // out with no body at all.
        val urls = mutableListOf<String>()
        val methods = mutableListOf<String>()
        val engine = MockEngine { request ->
            urls += request.url.toString()
            methods += request.method.value
            if (urls.size == 1) respondError(HttpStatusCode.Unauthorized)
            else respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = TujiApiClient("https://example.test", FakeTokens(mutableListOf("a", "b")), engine)

        api.get<Map<String, Boolean>>(Endpoint.SmokeWhoami)

        assertEquals(2, urls.size)
        assertEquals(urls[0], urls[1])
        assertEquals(methods[0], methods[1])
    }

    @Test
    fun `an anonymous 401 is not retried`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.Unauthorized)
        }
        val api = TujiApiClient("https://example.test", FakeTokens(mutableListOf("t")), engine)

        val error = runCatching {
            api.get<WordsListResponse>(Endpoint.Words("zh-Hant", LearningDirection.ZH_JA))
        }.exceptionOrNull()

        assertEquals(1, calls)
        assertTrue("$error", error is ApiError.Http)
        assertEquals(401, (error as ApiError.Http).status)
    }

    @Test
    fun `a second 401 is surfaced rather than retried forever`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.Unauthorized)
        }
        val api = TujiApiClient("https://example.test", FakeTokens(mutableListOf("a", "b")), engine)

        val error = runCatching { api.get<Map<String, String>>(Endpoint.SmokeWhoami) }.exceptionOrNull()

        assertEquals(2, calls)
        assertTrue("$error", error is ApiError.Http)
    }

    @Test
    fun `a malformed body is a decoding failure, not a transport one`() = runTest {
        // The distinction matters to the user: a transport failure is the
        // offline banner, a decoding failure is 資料解析失敗 and a bug.
        val engine = MockEngine { respond("{\"words\": \"not an array\"}", HttpStatusCode.OK, jsonHeaders) }
        val api = TujiApiClient("https://example.test", engine = engine)

        val error = runCatching {
            api.get<WordsListResponse>(Endpoint.Words("zh-Hant", LearningDirection.ZH_JA))
        }.exceptionOrNull()

        assertTrue("$error", error is ApiError.Decoding)
    }

    @Test
    fun `unknown wire fields do not sink the payload`() = runTest {
        // The backend adds fields without telling the clients. Strict decoding
        // would turn that into a hard failure on an app already shipped.
        val engine = MockEngine {
            respond(
                """{"words":[{"id":"x","word":"cat","somethingNew":42}],"total":1,"alsoNew":true}""",
                HttpStatusCode.OK,
                jsonHeaders,
            )
        }
        val api = TujiApiClient("https://example.test", engine = engine)

        val response: WordsListResponse =
            api.get(Endpoint.Words("zh-Hant", LearningDirection.ZH_EN))

        assertEquals("cat", response.words.single().word)
    }

    @Test
    fun `an optional-auth endpoint stays usable signed out`() = runTest {
        var authorization: String? = "unset"
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = TujiApiClient("https://example.test", NoAccessToken, engine)

        api.get<Map<String, Boolean>>(OptionalAuthProbe)

        assertNull(authorization)
    }

    @Test
    fun `an optional-auth endpoint attaches a token when signed in`() = runTest {
        var authorization: String? = null
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond("""{"ok":true}""", HttpStatusCode.OK, jsonHeaders)
        }
        val api = TujiApiClient("https://example.test", FakeTokens(mutableListOf("t")), engine)

        api.get<Map<String, Boolean>>(OptionalAuthProbe)

        assertEquals("Bearer t", authorization)
    }

    /**
     * A stand-in until a real optional-auth route lands with M3's 物見 work.
     * Standing this up is why [Endpoint] is a plain interface rather than a
     * sealed one.
     */
    private object OptionalAuthProbe : Endpoint {
        override val descriptor = EndpointDescriptor(
            path = "/api/probe",
            policy = EndpointPolicy.PublicFreshOptionalAuth,
        )
    }
}
