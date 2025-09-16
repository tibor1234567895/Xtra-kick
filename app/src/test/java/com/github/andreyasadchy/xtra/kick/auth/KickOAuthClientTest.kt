package com.github.andreyasadchy.xtra.kick.auth

import com.github.andreyasadchy.xtra.kick.config.KickEnvironment
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KickOAuthClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: KickOAuthClient
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val environment = KickEnvironment(
            apiBaseUrl = "https://api.kick.com/public/v1",
            oauthBaseUrl = server.url("/").toString().trimEnd('/'),
            chatSocketUrl = "wss://ws.kick.com/v2",
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "app://redirect",
            scopes = listOf("chat:read")
        )
        client = KickOAuthClient(OkHttpClient(), json, environment)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `exchangeAuthorizationCode posts expected form body`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"abc","token_type":"Bearer","expires_in":3600,"refresh_token":"ref","scope":"chat:read"}""")
        )

        val response = client.exchangeAuthorizationCode(code = "CODE", codeVerifier = "VERIFIER")

        val recorded = server.takeRequest()
        assertEquals("/oauth/token", recorded.path)
        assertEquals(
            "grant_type=authorization_code&client_id=client-id&client_secret=client-secret&redirect_uri=app%3A%2F%2Fredirect&code=CODE&code_verifier=VERIFIER",
            recorded.body.readUtf8()
        )
        assertEquals("application/x-www-form-urlencoded", recorded.getHeader("Content-Type"))
        assertEquals("abc", response.accessToken)
        assertEquals("ref", response.refreshToken)
    }

    @Test
    fun `refreshToken posts refresh grant`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"def","token_type":"Bearer","expires_in":3600,"refresh_token":"ref"}""")
        )

        val response = client.refreshToken(refreshToken = "OLD")

        val recorded = server.takeRequest()
        assertEquals("/oauth/token", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("grant_type=refresh_token"))
        assertEquals("def", response.accessToken)
    }

    @Test
    fun `introspect returns parsed payload`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"data":{"active":true,"client_id":"client-id","exp":123,"scope":"chat:read","token_type":"Bearer"}}""")
        )

        val result = client.introspect("token-value")

        val recorded = server.takeRequest()
        assertEquals("/token/introspect", recorded.path)
        assertEquals("Bearer token-value", recorded.getHeader("Authorization"))
        requireNotNull(result)
        assertTrue(result.active)
        assertEquals("client-id", result.clientId)
    }

    @Test
    fun `revokeToken hits revoke endpoint`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("\"ok\""))

        client.revokeToken(token = "abc", tokenTypeHint = "access_token")

        val recorded = server.takeRequest()
        assertEquals("/oauth/revoke", recorded.requestUrl?.encodedPath)
        assertEquals("token=abc&token_type_hint=access_token", recorded.requestUrl?.encodedQuery)
    }

    @Test
    fun `generateCodeChallenge is url safe`() {
        val verifier = KickOAuthClient.generateCodeVerifier()
        val challenge = KickOAuthClient.generateCodeChallenge(verifier)
        assertEquals(challenge, challenge.trim())
        assertFalse(challenge.contains('+'))
        assertFalse(challenge.contains('/'))
        assertFalse(challenge.contains('='))
    }
}
