package com.github.andreyasadchy.xtra.kick.chat

import com.github.andreyasadchy.xtra.kick.config.KickEnvironment
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class KickChatClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: KickChatClient
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val httpUrl = server.url("/socket")
        val environment = KickEnvironment(
            apiBaseUrl = "https://api.kick.com/public/v1",
            oauthBaseUrl = "https://id.kick.com",
            chatSocketUrl = httpUrl.toString().replaceFirst("http", "ws"),
            clientId = "client-id",
            clientSecret = "client-secret",
            redirectUri = "app://redirect",
            scopes = listOf("chat:read")
        )
        client = KickChatClient(OkHttpClient(), json, environment)
    }

    @After
    fun tearDown() {
        client.disconnect()
        server.shutdown()
    }

    @Test
    fun `connect sends join envelope`() = runBlocking {
        val joinMessage = CompletableDeferred<KickChatEnvelope>()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    joinMessage.complete(json.decodeFromString(KickChatEnvelope.serializer(), text))
                }
            })
        )

        val events = mutableListOf<KickChatEvent>()
        val listener = object : KickChatClient.Listener {
            override fun onConnected() {}
            override fun onMessage(event: KickChatEvent) { events.add(event) }
            override fun onClosed(code: Int, reason: String) {}
            override fun onError(throwable: Throwable) {}
        }

        client.connect(channelId = 55, authToken = "token", listener = listener)

        val join = joinMessage.await()
        assertEquals("phx_join", join.event)
        assertEquals("chatrooms:55", join.topic)
        assertEquals("token", join.payload["token"]?.jsonPrimitive?.content)
    }
}
