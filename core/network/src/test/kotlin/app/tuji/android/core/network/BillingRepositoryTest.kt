package app.tuji.android.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Play verify call, through the real client and the real JSON — the body
 * has to actually serialize. A `@Serializable` type in a module without the
 * serialization plugin failed exactly here, in front of a paying user, while
 * every unit test above the network stayed green.
 */
class BillingRepositoryTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private object Tokens : AccessTokenProvider {
        override suspend fun validAccessToken() = "token"
        override suspend fun refreshedAccessToken(rejected: String?) = "token"
        override val isSignedIn = true
    }

    @Test fun `sends the product and token, and reads back the state`() = runTest {
        var body = ""
        var path = ""
        var method: HttpMethod? = null
        val engine = MockEngine { request ->
            path = request.url.encodedPath
            method = request.method
            body = request.body.toByteArray().decodeToString()
            respond("""{"lifetime":"active","state":"insert"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val billing = BillingRepository(TujiApiClient("https://example.test", Tokens, engine))

        assertEquals("insert", billing.verifyPlayPurchase("app.tuji.lifetime", "tok-123"))
        assertEquals("/api/billing/play/verify", path)
        assertEquals(HttpMethod.Post, method)
        val sent = Json.parseToJsonElement(body).jsonObject
        assertEquals("app.tuji.lifetime", sent["productId"]?.jsonPrimitive?.content)
        assertEquals("tok-123", sent["purchaseToken"]?.jsonPrimitive?.content)
    }

    @Test fun `a pending payment is a 202 whose state says so`() = runTest {
        val engine = MockEngine { respond("""{"state":"pending"}""", HttpStatusCode.Accepted, jsonHeaders) }
        val billing = BillingRepository(TujiApiClient("https://example.test", Tokens, engine))
        assertEquals("pending", billing.verifyPlayPurchase("app.tuji.credits.1000", "t"))
    }

    @Test fun `a points reply without a state decodes as null`() = runTest {
        val engine = MockEngine { respond("""{"deliveryAck":true,"status":"credited"}""", HttpStatusCode.OK, jsonHeaders) }
        val billing = BillingRepository(TujiApiClient("https://example.test", Tokens, engine))
        assertNull(billing.verifyPlayPurchase("app.tuji.credits.1000", "t"))
    }

    @Test fun `a refusal surfaces as an HTTP error with the server's body`() = runTest {
        val engine = MockEngine { respond("""{"error":"lifetime already owned"}""", HttpStatusCode.Conflict, jsonHeaders) }
        val billing = BillingRepository(TujiApiClient("https://example.test", Tokens, engine))
        val error = runCatching { billing.verifyPlayPurchase("app.tuji.lifetime", "t") }.exceptionOrNull()
        assertTrue(error is ApiError.Http && error.status == 409 && error.body!!.contains("already owned"))
    }
}
