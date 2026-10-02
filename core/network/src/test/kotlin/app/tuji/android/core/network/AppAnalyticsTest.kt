package app.tuji.android.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAnalyticsTest {
    @Test fun `foreground launch sends an anonymous Android event once despite activity recreation`() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            calls++
            assertEquals("/api/events", request.url.encodedPath)
            assertEquals(HttpMethod.Post, request.method)
            assertNull(request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            assertEquals("app_open", body["type"]?.jsonPrimitive?.content)
            assertEquals("android", body["platform"]?.jsonPrimitive?.content)
            assertTrue(body["sessionId"]!!.jsonPrimitive.content.isNotBlank())
            assertFalse(body.containsKey("userId"))
            respond("""{"ok":true}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val analytics = AppAnalytics(TujiApiClient("https://example.test", engine = engine), this)
        // Constructing the object (background work) emits nothing.
        coroutineContext.job.children.toList().joinAll()
        assertEquals(0, calls)
        analytics.appOpened()
        analytics.appOpened()
        coroutineContext.job.children.toList().joinAll()
        assertEquals(1, calls)
    }

    @Test fun `analytics failure is isolated from the app and never loops on rejection`() = runTest {
        var calls = 0
        val engine = MockEngine {
            calls++
            respondError(HttpStatusCode.TooManyRequests)
        }
        val analytics = AppAnalytics(TujiApiClient("https://example.test", engine = engine), this)
        analytics.appOpened()
        coroutineContext.job.children.toList().joinAll()
        analytics.appOpened()
        coroutineContext.job.children.toList().joinAll()
        assertEquals(1, calls)
    }
}
