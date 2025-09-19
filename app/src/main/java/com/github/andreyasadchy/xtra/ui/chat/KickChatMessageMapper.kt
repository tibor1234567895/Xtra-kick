package com.github.andreyasadchy.xtra.ui.chat

import android.content.Context
import androidx.core.content.ContextCompat
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.kick.chat.KickChatBadge
import com.github.andreyasadchy.xtra.kick.chat.KickChatEmote
import com.github.andreyasadchy.xtra.kick.chat.KickChatMessage
import com.github.andreyasadchy.xtra.kick.chat.KickChatReply
import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.KickBadge
import com.github.andreyasadchy.xtra.model.chat.KickEmote
import com.github.andreyasadchy.xtra.model.chat.Reply
import com.github.andreyasadchy.xtra.model.chat.RoomState
import com.github.andreyasadchy.xtra.util.KickApiHelper
import java.util.LinkedHashMap

object KickChatMessageMapper {

    const val ACTION = "\u0001ACTION"

    fun fromKickMessage(message: KickChatMessage): ChatMessage {
        val isAction = message.type.equals("action", true)
        val isSystem = message.type.equals("system", true)
        val replyMessage = message.reply?.toReply()
        val badges = buildKickBadges(message)
        val emotes = buildKickEmotes(message.metadata?.emotes)
        return ChatMessage(
            id = message.id,
            userId = message.sender.id.toString(),
            userLogin = message.sender.username,
            userName = message.sender.username,
            message = if (isSystem) null else message.content,
            color = message.sender.identity?.color,
            emotes = emotes.takeUnless { it.isNullOrEmpty() },
            badges = badges.takeUnless { it.isNullOrEmpty() },
            kickBadges = (message.metadata?.badges.orEmpty() + message.sender.identity?.badges.orEmpty())
                .takeUnless { it.isEmpty() },
            kickEmotesRaw = message.metadata?.emotes?.takeUnless { it.isEmpty() },
            isAction = isAction,
            systemMsg = if (isSystem) message.content else null,
            reply = replyMessage,
            isReply = replyMessage != null,
            timestamp = KickApiHelper.parseIso8601DateUTC(message.createdAt),
            fullMsg = message.content,
        )
    }

    fun fromRawMessage(message: String, userNotice: Boolean): ChatMessage {
        return parseIrcMessage(message, userNotice, false)
    }

    fun fromBackfillMessage(message: String, userNotice: Boolean): ChatMessage {
        return parseIrcMessage(message, userNotice, true)
    }

    fun parseClearMessage(message: String, backfill: Boolean = false): Pair<ChatMessage, String?> {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        val messageInfo = parts[1]
        val msgIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
        val fallbackIndex = messageInfo.indexOf(" ", messageInfo.indexOf("#") + 1)
        val content = when {
            msgIndex != -1 -> messageInfo.substring(msgIndex + 1)
            backfill && fallbackIndex != -1 -> messageInfo.substring(fallbackIndex + 1)
            else -> null
        }
        val chatMessage = ChatMessage(
            userLogin = prefixes["login"],
            message = content,
            timestamp = prefixes["tmi-sent-ts"]?.toLong(),
            fullMsg = message,
        )
        return chatMessage to prefixes["target-msg-id"]
    }

    fun parseClearChat(context: Context, message: String, backfill: Boolean = false): ChatMessage {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        val messageInfo = parts[1]
        val firstColon = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
        val fallbackIndex = messageInfo.indexOf(" ", messageInfo.indexOf("#") + 1)
        val login = when {
            firstColon != -1 -> messageInfo.substring(firstColon + 1)
            backfill && fallbackIndex != -1 -> messageInfo.substring(fallbackIndex + 1)
            else -> null
        }
        val duration = prefixes["ban-duration"]
        val systemText = if (login != null) {
            if (duration != null) {
                ContextCompat.getString(context, R.string.chat_timeout).format(
                    login,
                    KickApiHelper.getDurationFromSeconds(context, duration)
                )
            } else {
                ContextCompat.getString(context, R.string.chat_ban).format(login)
            }
        } else {
            ContextCompat.getString(context, R.string.chat_clear)
        }
        return ChatMessage(
            userId = prefixes["target-user-id"],
            userLogin = login,
            systemMsg = systemText,
            timestamp = prefixes["tmi-sent-ts"]?.toLong(),
            fullMsg = message,
        )
    }

    fun parseNotice(context: Context, message: String, backfill: Boolean = false): Pair<ChatMessage, Boolean> {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        val messageInfo = parts[1]
        val msgId = prefixes["msg-id"]
        val msgIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
        val fallbackIndex = messageInfo.indexOf(" ", messageInfo.indexOf("#") + 1)
        val text = when {
            msgIndex != -1 -> messageInfo.substring(msgIndex + 1)
            backfill && fallbackIndex != -1 -> messageInfo.substring(fallbackIndex + 1)
            else -> messageInfo
        }
        val chatMessage = ChatMessage(
            systemMsg = KickApiHelper.getNoticeString(context, msgId, text),
            fullMsg = message,
        )
        return chatMessage to (msgId == "unraid_success")
    }

    fun parseRoomState(message: String): RoomState {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        return RoomState(
            emote = prefixes["emote-only"],
            followers = prefixes["followers-only"],
            unique = prefixes["r9k"],
            slow = prefixes["slow"],
            subs = prefixes["subs-only"],
        )
    }

    fun parseEmoteSets(message: String): List<String>? {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        return prefixes["emote-sets"]?.split(",")?.dropLastWhile { it.isEmpty() }
    }

    private fun parseIrcMessage(message: String, userNotice: Boolean, backfill: Boolean): ChatMessage {
        val parts = message.substring(1).split(" ".toRegex(), 2)
        val prefixes = splitAndMakeMap(parts[0], ";", "=")
        val messageInfo = parts[1]
        val userLogin = prefixes["login"] ?: run {
            try {
                messageInfo.substring(1, messageInfo.indexOf("!"))
            } catch (_: Exception) {
                null
            }
        }
        val systemMsg = prefixes["system-msg"]?.replace("\\s", " ")
        val segment = if (backfill) {
            extractBackfillSegment(messageInfo)
        } else {
            extractLiveSegment(messageInfo)
        }
        if (segment == null && userNotice) {
            return ChatMessage(
                userId = prefixes["user-id"],
                userLogin = userLogin,
                userName = prefixes["display-name"]?.replace("\\s", " "),
                systemMsg = systemMsg ?: messageInfo,
                timestamp = prefixes["tmi-sent-ts"]?.toLong(),
                fullMsg = message,
            )
        }
        val (userMessage, isAction) = parseActionSegment(segment ?: "")
        val emotes = prefixes["emotes"]?.let { raw ->
            val list = mutableListOf<KickEmote>()
            splitAndMakeMap(raw, "/", ":").forEach { (emoteId, ranges) ->
                ranges?.split(",")?.forEach { range ->
                    val index = range.split("-")
                    list.add(
                        KickEmote(
                            id = emoteId,
                            begin = index[0].toInt(),
                            end = index[1].toInt(),
                        )
                    )
                }
            }
            list
        }
        val badges = prefixes["badges"]?.let { raw ->
            val list = mutableListOf<KickBadge>()
            splitAndMakeMap(raw, ",", "/").forEach { (setId, version) ->
                version?.let {
                    list.add(
                        KickBadge(
                            setId = setId,
                            version = it,
                            type = setId,
                            text = it,
                        )
                    )
                }
            }
            list
        }
        val reply = prefixes["reply-thread-parent-msg-id"]?.let {
            Reply(
                threadParentId = it,
                userLogin = prefixes["reply-parent-user-login"],
                userName = prefixes["reply-parent-display-name"]?.replace("\\s", " "),
                message = prefixes["reply-parent-msg-body"]?.replace("\\s", " "),
            )
        }
        return ChatMessage(
            id = prefixes["id"],
            userId = prefixes["user-id"],
            userLogin = userLogin,
            userName = prefixes["display-name"]?.replace("\\s", " "),
            message = userMessage,
            color = prefixes["color"],
            emotes = emotes,
            badges = badges,
            isAction = isAction,
            isFirst = prefixes["first-msg"] == "1",
            bits = prefixes["bits"]?.toIntOrNull(),
            systemMsg = systemMsg,
            msgId = prefixes["msg-id"],
            reward = prefixes["custom-reward-id"]?.let { ChannelPointReward(id = it) },
            reply = reply,
            isReply = reply != null,
            timestamp = prefixes["tmi-sent-ts"]?.toLong(),
            fullMsg = message,
        )
    }

    private fun extractLiveSegment(messageInfo: String): String? {
        val msgIndex = messageInfo.indexOf(":", messageInfo.indexOf(":") + 1)
        return if (msgIndex != -1) messageInfo.substring(msgIndex + 1) else null
    }

    private fun extractBackfillSegment(messageInfo: String): String? {
        val channelIndex = messageInfo.indexOf("#", messageInfo.indexOf(":") + 1)
        if (channelIndex == -1) {
            return null
        }
        val msgIndex = messageInfo.indexOf(" ", channelIndex)
        if (msgIndex == -1 || msgIndex + 1 >= messageInfo.length) {
            return null
        }
        val segment = messageInfo.substring(msgIndex + 1)
        return if (segment.startsWith(":")) segment.substring(1) else segment
    }

    private fun parseActionSegment(segment: String): Pair<String, Boolean> {
        return if (!segment.startsWith(ACTION)) {
            segment to false
        } else {
            val endIndex = segment.lastIndex
            if (endIndex > 7) {
                segment.substring(8, endIndex) to true
            } else {
                "" to true
            }
        }
    }

    private fun buildKickBadges(message: KickChatMessage): List<KickBadge> {
        return (message.metadata?.badges.orEmpty() + message.sender.identity?.badges.orEmpty())
            .mapNotNull { mapBadge(it) }
            .distinctBy { Triple(it.setId, it.version, it.url1x) }
    }

    private fun mapBadge(badge: KickChatBadge?): KickBadge? {
        badge ?: return null
        val type = badge.type.takeIf { it.isNotBlank() } ?: return null
        val version = badge.text?.takeIf { it.isNotBlank() }
            ?: badge.count?.toString()
            ?: badge.source
            ?: "1"
        val artwork = badge.artwork
        val urls = parseSrcSet(artwork?.srcSet ?: artwork?.srcset)
        val baseUrl = artwork?.src
        val url1x = urls["1x"] ?: baseUrl
        val url2x = urls["2x"] ?: url1x
        val url3x = urls["3x"] ?: url2x
        val url4x = urls["4x"] ?: url3x
        val format = (url1x ?: url4x)?.let { if (it.endsWith(".gif", true)) "gif" else null }
        val isAnimated = format?.contains("gif", true) == true
        return KickBadge(
            id = badge.type,
            setId = type,
            version = version,
            url1x = url1x,
            url2x = url2x,
            url3x = url3x,
            url4x = url4x,
            format = format,
            isAnimated = isAnimated,
            source = badge.source,
            type = badge.type,
            text = badge.text,
            count = badge.count,
            image = badge.image,
            badgeImage = badge.badgeImage,
            title = badge.text,
        )
    }

    private fun buildKickEmotes(emotes: List<KickChatEmote>?): List<KickEmote>? {
        return emotes?.mapNotNull { mapEmote(it) }
    }

    private fun mapEmote(emote: KickChatEmote): KickEmote? {
        val name = emote.code ?: emote.name ?: return null
        val urls = parseSrcSet(emote.image?.srcSet ?: emote.image?.srcset)
        val baseUrl = emote.image?.src ?: emote.url
        val url1x = urls["1x"] ?: baseUrl
        val url2x = urls["2x"] ?: url1x
        val url3x = urls["3x"] ?: url2x
        val url4x = urls["4x"] ?: url3x
        val format = emote.type ?: (url1x ?: url4x)?.let { if (it.endsWith(".gif", true)) "gif" else null }
        val isAnimated = format?.contains("gif", true) == true
        return KickEmote(
            id = emote.id ?: emote.slug ?: name,
            name = name,
            url1x = url1x,
            url2x = url2x,
            url3x = url3x,
            url4x = url4x,
            format = format,
            isAnimated = isAnimated,
            source = emote.source,
            type = emote.type,
            code = emote.code,
            url = emote.url,
            slug = emote.slug,
            image = emote.image,
            channel = emote.channel,
            ownerId = emote.channel?.id?.toString(),
        )
    }

    private fun KickChatReply.toReply(): Reply {
        return Reply(
            threadParentId = id,
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

    private fun splitAndMakeMap(string: String, splitRegex: String, mapRegex: String): Map<String, String?> {
        val list = string.split(splitRegex.toRegex()).dropLastWhile { it.isEmpty() }
        val map = LinkedHashMap<String, String?>()
        for (pair in list) {
            val kv = pair.split(mapRegex.toRegex()).dropLastWhile { it.isEmpty() }
            map[kv[0]] = if (kv.size == 2) kv[1] else null
        }
        return map
    }
}
