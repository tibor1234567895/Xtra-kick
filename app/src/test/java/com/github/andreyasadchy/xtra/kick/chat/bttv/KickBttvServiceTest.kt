package com.github.andreyasadchy.xtra.kick.chat.bttv

import kotlinx.coroutines.runBlocking
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

class KickBttvServiceTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: KickBttvService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/3/")
        service = KickBttvService(
            okHttpClient = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true },
            baseUrl = baseUrl,
            userAgent = "UnitTest"
        )
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `global emotes map to overlay metadata`() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{\"id\":\"abc123\",\"code\":\"IceCold\",\"animated\":true}]""")
        )

        val emotes = service.getGlobalEmotes(preferWebp = false)

        assertEquals(1, emotes.size)
        val emote = emotes.first()
        assertEquals("IceCold", emote.name)
        assertTrue(emote.isOverlay)
        assertTrue(emote.isAnimated)
        assertEquals("https://cdn.betterttv.net/emote/abc123/1x", emote.images.url1x)
        assertEquals("/3/cached/emotes/global", mockWebServer.takeRequest().path)
    }

    @Test
    fun `channel emotes fall back to secondary identifiers`() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"channelEmotes":[{"id":"def456","code":"Hello","animated":false}]," +
                        """"sharedEmotes":[{"id":"ghi789","code":"World","animated":true}]}"""
                )
        )

        val identifiers = listOf(
            KickBttvIdentifier.KickChannelSlug("kick-slug"),
            KickBttvIdentifier.TwitchUserId("12345")
        )

        val emotes = service.getChannelEmotes(identifiers, preferWebp = true)

        assertEquals(2, emotes.size)
        val (hello, world) = emotes.sortedBy { it.name }
        assertEquals("Hello", hello.name)
        assertFalse(hello.isAnimated)
        assertEquals("https://cdn.betterttv.net/emote/def456/1x.webp", hello.images.url1x)
        assertEquals("World", world.name)
        assertTrue(world.isAnimated)
        assertEquals("https://cdn.betterttv.net/emote/ghi789/2x.webp", world.images.url2x)

        val firstRequest = mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()
        assertEquals("/3/cached/users/kick/kick-slug", firstRequest.path)
        assertEquals("/3/cached/users/twitch/12345", secondRequest.path)
    }

    @Test
    fun `missing identifiers return empty list`() = runBlocking {
        val emotes = service.getChannelEmotes(emptyList(), preferWebp = true)
        assertTrue(emotes.isEmpty())
    }
}
