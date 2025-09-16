package com.github.andreyasadchy.xtra.kick.chat.bttv

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw emote payload returned by BetterTTV APIs.
 */
@Serializable
internal data class KickBttvEmotePayload(
    val id: String? = null,
    val code: String? = null,
    @SerialName("imageType") val imageType: String? = null,
    val animated: Boolean = false,
    @SerialName("modifier") val modifier: Boolean? = null
)

/**
 * Channel scoped response returned from `/cached/users/{platform}/{id}` endpoints.
 */
@Serializable
internal data class KickBttvUserResponse(
    val id: String? = null,
    val channelEmotes: List<KickBttvEmotePayload> = emptyList(),
    val sharedEmotes: List<KickBttvEmotePayload> = emptyList()
)

/**
 * Normalised emote metadata consumed by the Kick chat stack.
 */
data class KickBttvEmote(
    val name: String,
    val images: KickBttvImageSet,
    val isAnimated: Boolean,
    val isOverlay: Boolean
)

/**
 * Convenience wrapper that exposes the four standard BetterTTV image scales.
 */
data class KickBttvImageSet(
    val url1x: String,
    val url2x: String,
    val url3x: String,
    val url4x: String?
)

/**
 * Identifiers accepted by BetterTTV for channel level queries.
 */
sealed class KickBttvIdentifier internal constructor(
    internal val segments: List<String>
) {
    class KickChannelSlug(slug: String) : KickBttvIdentifier(listOf("kick", slug))
    class TwitchUserId(userId: String) : KickBttvIdentifier(listOf("twitch", userId))
    class Custom(platform: String, vararg extraSegments: String) :
        KickBttvIdentifier(listOf(platform) + extraSegments.filter { it.isNotBlank() })

    init {
        require(segments.isNotEmpty()) { "segments must not be empty" }
        require(segments.none { it.isBlank() }) { "segments must not contain blanks" }
    }

    override fun toString(): String = segments.joinToString(separator = ":")
}
