package com.github.andreyasadchy.xtra.kick.chat.bttv

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        KickBttvService.assetAvailabilityOverride = null
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        KickBttvService.assetAvailabilityOverride = null
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
        assertEquals("https://cdn.betterttv.net/emote/abc123/2x", emote.images.url2x)
        assertEquals("https://cdn.betterttv.net/emote/abc123/3x", emote.images.url3x)
        assertNull(emote.images.url4x)
        assertEquals("/3/cached/emotes/global", mockWebServer.takeRequest().path)
    }

    @Test
    fun `3x asset falls back to 2x when unavailable`() = runBlocking {
        KickBttvService.assetAvailabilityOverride = { _, suffix ->
            suffix != "3x.webp" && suffix != "3x"
        }
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":"fallback","code":"Fallback","animated":false}]""")
        )

        val emotes = service.getGlobalEmotes(preferWebp = true)

        assertEquals(1, emotes.size)
        val emote = emotes.first()
        assertEquals("https://cdn.betterttv.net/emote/fallback/2x.webp", emote.images.url3x)
        assertEquals("https://cdn.betterttv.net/emote/fallback/2x.webp", emote.images.url2x)
        assertEquals("https://cdn.betterttv.net/emote/fallback/1x.webp", emote.images.url1x)
        assertNull(emote.images.url4x)
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
            KickBttvIdentifier.Custom("legacy", "12345")
        )

        val emotes = service.getChannelEmotes(identifiers, preferWebp = true)

        assertEquals(2, emotes.size)
        val (hello, world) = emotes.sortedBy { it.name }
        assertEquals("Hello", hello.name)
        assertFalse(hello.isAnimated)
        assertEquals("https://cdn.betterttv.net/emote/def456/1x.webp", hello.images.url1x)
        assertEquals("https://cdn.betterttv.net/emote/def456/2x.webp", hello.images.url2x)
        assertEquals("https://cdn.betterttv.net/emote/def456/3x.webp", hello.images.url3x)
        assertNull(hello.images.url4x)
        assertEquals("World", world.name)
        assertTrue(world.isAnimated)
        assertEquals("https://cdn.betterttv.net/emote/ghi789/2x.webp", world.images.url2x)
        assertEquals("https://cdn.betterttv.net/emote/ghi789/3x.webp", world.images.url3x)
        assertNull(world.images.url4x)

        val firstRequest = mockWebServer.takeRequest()
        val secondRequest = mockWebServer.takeRequest()
        assertEquals("/3/cached/users/kick/kick-slug", firstRequest.path)
        assertEquals("/3/cached/users/legacy/12345", secondRequest.path)
    }

    @Test
    fun `missing identifiers return empty list`() = runBlocking {
        val emotes = service.getChannelEmotes(emptyList(), preferWebp = true)
        assertTrue(emotes.isEmpty())
    }
}
