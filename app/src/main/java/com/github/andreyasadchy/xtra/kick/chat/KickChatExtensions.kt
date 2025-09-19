package com.github.andreyasadchy.xtra.kick.chat

import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.KickBadge
import com.github.andreyasadchy.xtra.model.chat.KickEmote
import com.github.andreyasadchy.xtra.model.chat.Reply
import com.github.andreyasadchy.xtra.util.KickApiHelper

fun KickChatMessage.toChatMessage(): ChatMessage {
    val isAction = type.equals("action", true)
    val isSystem = type.equals("system", true)
    val replyMessage = reply?.toReply()
    val badges = (metadata?.badges.orEmpty() + badges)
        .mapNotNull { it.toKickBadge() }
        .distinctBy { it.setId to it.version }
    val emotes = metadata?.emotes?.mapNotNull { it.toKickEmote() }
    return ChatMessage(
        id = id,
        userId = sender.id.toString(),
        userLogin = sender.username,
        userName = sender.username,
        message = if (isSystem) null else content,
        color = sender.identity?.color,
        emotes = emotes,
        badges = badges,
        isAction = isAction,
        isFirst = false,
        systemMsg = if (isSystem) content else null,
        reply = replyMessage,
        isReply = replyMessage != null,
        timestamp = KickApiHelper.parseIso8601DateUTC(createdAt),
        fullMsg = content,
    )
}

private fun KickChatBadge.toKickBadge(): KickBadge? {
    val setId = type.takeIf { it.isNotBlank() } ?: return null
    val version = when {
        !text.isNullOrBlank() -> text!!
        count != null -> count.toString()
        !source.isNullOrBlank() -> source!!
        else -> "1"
    }
    return KickBadge(
        setId = setId,
        version = version,
        source = source,
    )
}

private fun KickChatEmote.toKickEmote(): KickEmote? {
    val name = code ?: this.name ?: return null
    val urls = parseSrcSet(image?.srcSet ?: image?.srcset)
    val baseUrl = image?.src ?: url
    val url1x = urls["1x"] ?: baseUrl
    val url2x = urls["2x"] ?: url1x
    val url3x = urls["3x"] ?: url2x
    val url4x = urls["4x"] ?: url3x
    val format = type ?: if ((url1x ?: url4x)?.endsWith(".gif", true) == true) "gif" else null
    val animated = format?.contains("gif", true) == true
    return KickEmote(
        id = id ?: slug ?: name,
        name = name,
        url1x = url1x,
        url2x = url2x,
        url3x = url3x,
        url4x = url4x,
        format = format,
        isAnimated = animated,
    )
}

private fun KickChatReply.toReply(): Reply? {
    val threadParentId = id ?: return null
    return Reply(
        threadParentId = threadParentId,
        userLogin = username ?: displayName,
        userName = displayName ?: username,
        message = text,
    )
}

private fun parseSrcSet(srcSet: String?): Map<String, String> {
    if (srcSet.isNullOrBlank()) {
        return emptyMap()
    }
    return srcSet.split(',')
        .mapNotNull { entry ->
            val parts = entry.trim().split(" ")
            if (parts.size >= 2) {
                parts[1] to parts[0]
            } else {
                null
            }
        }
        .toMap()
}
