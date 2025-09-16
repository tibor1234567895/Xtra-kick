package com.github.andreyasadchy.xtra.kick.api

import com.github.andreyasadchy.xtra.kick.config.KickEnvironment
import com.github.andreyasadchy.xtra.kick.storage.KickTokenProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KickApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: KickApiClient
    private val json = Json { ignoreUnknownKeys = true }

    private val tokenProvider = object : KickTokenProvider {
        override val accessToken: String? = "access-token"
        override val refreshToken: String? = "refresh-token"
        override val tokenType: String? = "Bearer"
        override val scopes: Set<String> = setOf("chat:read")
        override val expiresAtMillis: Long? = System.currentTimeMillis() + 60_000
        override fun isAccessTokenExpired(graceMillis: Long) = false
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val baseUrl = server.url("/").toString().trimEnd('/')
        val environment = KickEnvironment(
            apiBaseUrl = baseUrl,
            oauthBaseUrl = baseUrl,
            chatSocketUrl = "wss://ws.kick.com/v2",
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "app://redirect",
            scopes = listOf("chat:read")
        )
        client = KickApiClient(OkHttpClient(), json, environment, tokenProvider)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getLivestreams attaches query params`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))

        val response = client.getLivestreams(
            categoryId = 123,
            language = "en",
            limit = 25,
            page = 2,
            sort = "viewer_count"
        )

        assertEquals(0, response.data.size)
        val recorded = server.takeRequest()
        assertEquals("/livestreams", recorded.requestUrl?.encodedPath)
        assertEquals("123", recorded.requestUrl?.queryParameterValues("category_id")?.single())
        assertEquals("en", recorded.requestUrl?.queryParameterValues("language")?.single())
        assertEquals("25", recorded.requestUrl?.queryParameterValues("limit")?.single())
        assertEquals("2", recorded.requestUrl?.queryParameterValues("page")?.single())
        assertEquals("viewer_count", recorded.requestUrl?.queryParameterValues("sort")?.single())
        assertEquals("Bearer access-token", recorded.getHeader("Authorization"))
        assertEquals("client-id", recorded.getHeader("Client-Id"))
    }

    @Test
    fun `getChannelsBySlugs repeats slug parameter`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))

        client.getChannelsBySlugs(listOf("alpha", "beta"))

        val recorded = server.takeRequest()
        assertEquals("/channels", recorded.requestUrl?.encodedPath)
        val slugs = recorded.requestUrl?.queryParameterValues("slug") ?: emptyList()
        assertEquals(listOf("alpha", "beta"), slugs)
    }

    @Test
    fun `getUsers attaches multiple ids`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))

        client.getUsers(listOf(1, 2, 3))

        val recorded = server.takeRequest()
        assertEquals("/users", recorded.requestUrl?.encodedPath)
        val ids = recorded.requestUrl?.queryParameterValues("user_id") ?: emptyList()
        assertEquals(listOf("1", "2", "3"), ids)
    }

    @Test(expected = KickApiException::class)
    fun `execute throws on missing token`() = runTest {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val environment = KickEnvironment(
            apiBaseUrl = baseUrl,
            oauthBaseUrl = baseUrl,
            chatSocketUrl = "wss://ws.kick.com/v2",
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "app://redirect",
            scopes = listOf("chat:read")
        )
        val emptyTokenProvider = object : KickTokenProvider {
            override val accessToken: String? = null
            override val refreshToken: String? = null
            override val tokenType: String? = null
            override val scopes: Set<String> = emptySet()
            override val expiresAtMillis: Long? = null
            override fun isAccessTokenExpired(graceMillis: Long) = true
        }
        val failingClient = KickApiClient(OkHttpClient(), json, environment, emptyTokenProvider)
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"data":[]}"""))

        failingClient.getLivestreams()
    }
}
