package com.github.andreyasadchy.xtra.kick.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representation of the events emitted by Kick's chat websocket.
 */
sealed interface KickChatEvent {
    val envelope: KickChatEnvelope

    /**
     * A standard chat message.
     */
    data class ChatMessage(
        override val envelope: KickChatEnvelope,
        val message: KickChatMessage
    ) : KickChatEvent

    /**
     * Any event that is currently not mapped to a strongly typed model.
     */
    data class Unknown(override val envelope: KickChatEnvelope) : KickChatEvent
}

/**
 * Chat message as delivered by Kick.
 */
@Serializable
data class KickChatMessage(
    val id: String,
    @SerialName("chatroom_id") val chatroomId: Long,
    val content: String,
    val type: String,
    @SerialName("created_at") val createdAt: String,
    val sender: KickChatSender,
    val metadata: KickChatMetadata? = null
) {
    val badges: List<KickChatBadge>
        get() = sender.identity?.badges.orEmpty()

    val emotes: List<KickChatEmote>
        get() = metadata?.emotes.orEmpty()

    val reply: KickChatReply?
        get() = metadata?.replyOrNull
}

@Serializable
data class KickChatSender(
    val id: Long,
    val username: String,
    val slug: String? = null,
    val identity: KickChatIdentity? = null
)

@Serializable
data class KickChatIdentity(
    val color: String? = null,
    val badges: List<KickChatBadge> = emptyList()
)

@Serializable
data class KickChatBadge(
    val type: String,
    val text: String? = null,
    val count: Int? = null,
    val source: String? = null,
    val image: KickChatBadgeImage? = null,
    @SerialName("badge_image") val badgeImage: KickChatBadgeImage? = null
) {
    val artwork: KickChatBadgeImage?
        get() = image ?: badgeImage
}

@Serializable
data class KickChatBadgeImage(
    val src: String? = null,
    val srcset: String? = null,
    @SerialName("src_set") val srcSet: String? = null
)

@Serializable
data class KickChatMetadata(
    val badges: List<KickChatBadge>? = null,
    val emotes: List<KickChatEmote>? = null,
    val reply: KickChatReply? = null,
    @SerialName("original_message") val originalMessage: KickChatMetadataMessage? = null,
    @SerialName("original_sender") val originalSender: KickChatMetadataSender? = null
) {
    val replyOrNull: KickChatReply?
        get() = reply ?: originalMessage?.let { message ->
            KickChatReply(
                id = message.id,
                userId = originalSender?.id,
                username = originalSender?.username,
                displayName = originalSender?.displayName,
                message = message.content
            )
        }
}

@Serializable
data class KickChatMetadataMessage(
    val id: String? = null,
    val content: String? = null
)

@Serializable
data class KickChatMetadataSender(
    val id: Long? = null,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val slug: String? = null
)

@Serializable
data class KickChatEmote(
    val id: String? = null,
    val name: String? = null,
    val type: String? = null,
    val source: String? = null,
    val code: String? = null,
    val url: String? = null,
    val slug: String? = null,
    val image: KickChatEmoteImage? = null,
    val channel: KickChatEmoteChannel? = null
)

@Serializable
data class KickChatEmoteImage(
    val src: String? = null,
    val srcset: String? = null,
    @SerialName("src_set") val srcSet: String? = null
)

@Serializable
data class KickChatEmoteChannel(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null
)

@Serializable
data class KickChatReply(
    val id: String? = null,
    @SerialName("user_id") val userId: Long? = null,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("message") val message: String? = null,
    val content: String? = null
) {
    val text: String?
        get() = message ?: content
}
