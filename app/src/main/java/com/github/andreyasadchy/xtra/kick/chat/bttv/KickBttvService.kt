package com.github.andreyasadchy.xtra.kick.chat.bttv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KickBttvService @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
    private val userAgent: String = DEFAULT_USER_AGENT,
) {
    private val apiBaseUrl: HttpUrl = baseUrl

    suspend fun getGlobalEmotes(preferWebp: Boolean = true): List<KickBttvEmote> {
        val url = apiBaseUrl.newBuilder()
            .addPathSegment("cached")
            .addPathSegment("emotes")
            .addPathSegment("global")
            .build()
        val body = execute(url) ?: return emptyList()
        val payloads = json.decodeFromString<List<KickBttvEmotePayload>>(body)
        return payloads.mapNotNull { it.toEmote(preferWebp) }
    }

    suspend fun getChannelEmotes(
        identifiers: List<KickBttvIdentifier>,
        preferWebp: Boolean = true
    ): List<KickBttvEmote> {
        if (identifiers.isEmpty()) return emptyList()
        for (identifier in identifiers) {
            val urlBuilder = apiBaseUrl.newBuilder()
                .addPathSegment("cached")
                .addPathSegment("users")
            identifier.segments.forEach { segment -> urlBuilder.addPathSegment(segment) }
            val url = urlBuilder.build()
            val body = execute(url)
            if (body != null) {
                val response = json.decodeFromString<KickBttvUserResponse>(body)
                return (response.channelEmotes + response.sharedEmotes)
                    .mapNotNull { it.toEmote(preferWebp) }
                    .distinctBy { it.name }
            }
        }
        return emptyList()
    }

    private suspend fun execute(url: HttpUrl): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                response.code == 404 -> null
                else -> throw IOException("BetterTTV request failed with HTTP ${response.code}")
            }
        }
    }

    private fun KickBttvEmotePayload.toEmote(preferWebp: Boolean): KickBttvEmote? {
        val emoteId = id?.takeIf { it.isNotBlank() } ?: return null
        val name = code?.takeIf { it.isNotBlank() } ?: return null
        return KickBttvEmote(
            name = name,
            images = buildImageSet(emoteId, preferWebp),
            isAnimated = animated,
            isOverlay = name in OVERLAY_EMOTE_NAMES,
        )
    }

    private fun buildImageSet(emoteId: String, preferWebp: Boolean): KickBttvImageSet {
        val extension = if (preferWebp) "webp" else null
        val suffix1x = pickFirstAvailableSuffix(emoteId, scaleSuffixes("1x", extension))
        val suffix2x = pickFirstAvailableSuffix(emoteId, scaleSuffixes("2x", extension))
        val suffix3x = pickFirstAvailableSuffix(
            emoteId,
            (scaleSuffixes("3x", extension) + scaleSuffixes("2x", extension)).distinct()
        )
        return KickBttvImageSet(
            url1x = buildCdnUrl(emoteId, suffix1x),
            url2x = buildCdnUrl(emoteId, suffix2x),
            url3x = buildCdnUrl(emoteId, suffix3x),
            url4x = null,
        )
    }

    private fun scaleSuffixes(scale: String, extension: String?): List<String> =
        if (extension != null) listOf("$scale.$extension", scale) else listOf(scale)

    private fun buildCdnUrl(emoteId: String, suffix: String): String =
        "$CDN_BASE_URL/$emoteId/$suffix"

    private fun pickFirstAvailableSuffix(emoteId: String, candidates: List<String>): String {
        val availabilityOverride = assetAvailabilityOverride
        if (availabilityOverride != null) {
            for (candidate in candidates) {
                if (availabilityOverride(emoteId, candidate)) {
                    return candidate
                }
            }
            return candidates.last()
        }
        return candidates.first()
    }

    companion object {
        private const val DEFAULT_BASE_URL = "https://api.betterttv.net/3"
        private const val DEFAULT_USER_AGENT = "XtraKick/1.0"
        private const val CDN_BASE_URL = "https://cdn.betterttv.net/emote"
        internal var assetAvailabilityOverride: ((String, String) -> Boolean)? = null
        private val OVERLAY_EMOTE_NAMES = setOf(
            "IceCold",
            "SoSnowy",
            "SantaHat",
            "TopHat",
            "CandyCane",
            "ReinDeer",
            "cvHazmat",
            "cvMask"
        )
    }
}
