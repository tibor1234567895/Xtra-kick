package com.github.andreyasadchy.xtra.kick.chat

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.github.andreyasadchy.xtra.model.chat.ChannelPointReward
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.KickBadge
import com.github.andreyasadchy.xtra.model.chat.KickEmote
import com.github.andreyasadchy.xtra.model.chat.Reply

fun JsonWriter.writeKickChatMessage(message: ChatMessage) {
    beginObject()
    message.id?.let { name("id").value(it) }
    message.userId?.let { name("userId").value(it) }
    message.userLogin?.let { name("userLogin").value(it) }
    message.userName?.let { name("userName").value(it) }
    message.message?.let { name("message").value(it) }
    message.systemMsg?.let { name("systemMsg").value(it) }
    message.color?.let { name("color").value(it) }
    if (message.isAction) {
        name("isAction").value(true)
    }
    if (message.isFirst) {
        name("isFirst").value(true)
    }
    message.bits?.let { name("bits").value(it.toLong()) }
    message.msgId?.let { name("msgId").value(it) }
    message.timestamp?.let { name("timestamp").value(it) }
    message.fullMsg?.let { name("fullMsg").value(it) }
    message.badges?.takeIf { it.isNotEmpty() }?.let { badges ->
        name("badges")
        beginArray()
        badges.forEach { badge ->
            beginObject()
            badge.id?.let { name("id").value(it) }
            badge.setId?.let { name("setId").value(it) }
            badge.version?.let { name("version").value(it) }
            badge.url1x?.let { name("url1x").value(it) }
            badge.url2x?.let { name("url2x").value(it) }
            badge.url3x?.let { name("url3x").value(it) }
            badge.url4x?.let { name("url4x").value(it) }
            badge.format?.let { name("format").value(it) }
            if (badge.isAnimated) {
                name("isAnimated").value(true)
            }
            badge.source?.let { name("source").value(it) }
            endObject()
        }
        endArray()
    }
    message.emotes?.takeIf { it.isNotEmpty() }?.let { emotes ->
        name("emotes")
        beginArray()
        emotes.forEach { emote ->
            beginObject()
            emote.id?.let { name("id").value(it) }
            emote.name?.let { name("name").value(it) }
            emote.url1x?.let { name("url1x").value(it) }
            emote.url2x?.let { name("url2x").value(it) }
            emote.url3x?.let { name("url3x").value(it) }
            emote.url4x?.let { name("url4x").value(it) }
            emote.format?.let { name("format").value(it) }
            if (emote.isAnimated) {
                name("isAnimated").value(true)
            }
            name("begin").value(emote.begin.toLong())
            name("end").value(emote.end.toLong())
            emote.setId?.let { name("setId").value(it) }
            emote.ownerId?.let { name("ownerId").value(it) }
            endObject()
        }
        endArray()
    }
    message.reward?.let { reward ->
        name("reward")
        beginObject()
        reward.id?.let { name("id").value(it) }
        reward.title?.let { name("title").value(it) }
        reward.cost?.let { name("cost").value(it.toLong()) }
        reward.url1x?.let { name("url1x").value(it) }
        reward.url2x?.let { name("url2x").value(it) }
        reward.url4x?.let { name("url4x").value(it) }
        endObject()
    }
    message.reply?.let { reply ->
        name("reply")
        beginObject()
        reply.threadParentId?.let { name("threadParentId").value(it) }
        reply.userLogin?.let { name("userLogin").value(it) }
        reply.userName?.let { name("userName").value(it) }
        reply.message?.let { name("message").value(it) }
        endObject()
    }
    if (message.isReply) {
        name("isReply").value(true)
    }
    endObject()
}

fun JsonReader.readKickChatMessage(): ChatMessage? {
    if (peek() != JsonToken.BEGIN_OBJECT) {
        skipValue()
        return null
    }
    beginObject()
    var id: String? = null
    var userId: String? = null
    var userLogin: String? = null
    var userName: String? = null
    var message: String? = null
    var systemMsg: String? = null
    var color: String? = null
    var isAction = false
    var isFirst = false
    var bits: Int? = null
    var msgId: String? = null
    var reward: ChannelPointReward? = null
    var reply: Reply? = null
    var isReply = false
    var timestamp: Long? = null
    var fullMsg: String? = null
    val badges = mutableListOf<KickBadge>()
    val emotes = mutableListOf<KickEmote>()
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextString()
            "userId" -> userId = nextString()
            "userLogin" -> userLogin = nextString()
            "userName" -> userName = nextString()
            "message" -> message = nextString()
            "systemMsg" -> systemMsg = nextString()
            "color" -> color = nextString()
            "isAction" -> isAction = nextBoolean()
            "isFirst" -> isFirst = nextBoolean()
            "bits" -> bits = when (peek()) {
                JsonToken.STRING -> nextString().toIntOrNull()
                else -> nextInt()
            }
            "msgId" -> msgId = nextString()
            "timestamp" -> timestamp = when (peek()) {
                JsonToken.STRING -> nextString().toLongOrNull()
                else -> nextLong()
            }
            "fullMsg" -> fullMsg = nextString()
            "badges" -> readBadges()?.let(badges::addAll)
            "emotes" -> readEmotes()?.let(emotes::addAll)
            "reward" -> reward = readReward()
            "reply" -> reply = readReply()
            "isReply" -> isReply = nextBoolean()
            else -> skipValue()
        }
    }
    endObject()
    return ChatMessage(
        id = id,
        userId = userId,
        userLogin = userLogin,
        userName = userName,
        message = message,
        color = color,
        emotes = emotes.takeIf { it.isNotEmpty() },
        badges = badges.takeIf { it.isNotEmpty() },
        isAction = isAction,
        isFirst = isFirst,
        bits = bits,
        systemMsg = systemMsg,
        msgId = msgId,
        reward = reward,
        reply = reply,
        isReply = isReply,
        timestamp = timestamp,
        fullMsg = fullMsg,
    )
}

private fun JsonReader.readBadges(): List<KickBadge>? {
    if (peek() != JsonToken.BEGIN_ARRAY) {
        skipValue()
        return null
    }
    val badges = mutableListOf<KickBadge>()
    beginArray()
    while (hasNext()) {
        readBadge()?.let(badges::add)
    }
    endArray()
    return badges.takeIf { it.isNotEmpty() }
}

private fun JsonReader.readBadge(): KickBadge? {
    if (peek() != JsonToken.BEGIN_OBJECT) {
        skipValue()
        return null
    }
    beginObject()
    var id: String? = null
    var setId: String? = null
    var version: String? = null
    var url1x: String? = null
    var url2x: String? = null
    var url3x: String? = null
    var url4x: String? = null
    var format: String? = null
    var isAnimated = false
    var source: String? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextString()
            "setId" -> setId = nextString()
            "version" -> version = nextString()
            "url1x" -> url1x = nextString()
            "url2x" -> url2x = nextString()
            "url3x" -> url3x = nextString()
            "url4x" -> url4x = nextString()
            "format" -> format = nextString()
            "isAnimated" -> isAnimated = nextBoolean()
            "source" -> source = nextString()
            else -> skipValue()
        }
    }
    endObject()
    return KickBadge(
        id = id,
        setId = setId,
        version = version,
        url1x = url1x,
        url2x = url2x,
        url3x = url3x,
        url4x = url4x,
        format = format,
        isAnimated = isAnimated,
        source = source,
    )
}

private fun JsonReader.readEmotes(): List<KickEmote>? {
    if (peek() != JsonToken.BEGIN_ARRAY) {
        skipValue()
        return null
    }
    val emotes = mutableListOf<KickEmote>()
    beginArray()
    while (hasNext()) {
        readEmote()?.let(emotes::add)
    }
    endArray()
    return emotes.takeIf { it.isNotEmpty() }
}

private fun JsonReader.readEmote(): KickEmote? {
    if (peek() != JsonToken.BEGIN_OBJECT) {
        skipValue()
        return null
    }
    beginObject()
    var id: String? = null
    var name: String? = null
    var url1x: String? = null
    var url2x: String? = null
    var url3x: String? = null
    var url4x: String? = null
    var format: String? = null
    var isAnimated = false
    var begin = 0
    var end = 0
    var setId: String? = null
    var ownerId: String? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextString()
            "name" -> name = nextString()
            "url1x" -> url1x = nextString()
            "url2x" -> url2x = nextString()
            "url3x" -> url3x = nextString()
            "url4x" -> url4x = nextString()
            "format" -> format = nextString()
            "isAnimated" -> isAnimated = nextBoolean()
            "begin" -> begin = when (peek()) {
                JsonToken.STRING -> nextString().toIntOrNull() ?: 0
                else -> nextInt()
            }
            "end" -> end = when (peek()) {
                JsonToken.STRING -> nextString().toIntOrNull() ?: 0
                else -> nextInt()
            }
            "setId" -> setId = nextString()
            "ownerId" -> ownerId = nextString()
            else -> skipValue()
        }
    }
    endObject()
    return KickEmote(
        id = id,
        name = name,
        url1x = url1x,
        url2x = url2x,
        url3x = url3x,
        url4x = url4x,
        format = format,
        isAnimated = isAnimated,
        begin = begin,
        end = end,
        setId = setId,
        ownerId = ownerId,
    )
}

private fun JsonReader.readReward(): ChannelPointReward? {
    if (peek() != JsonToken.BEGIN_OBJECT) {
        skipValue()
        return null
    }
    beginObject()
    var id: String? = null
    var title: String? = null
    var cost: Int? = null
    var url1x: String? = null
    var url2x: String? = null
    var url4x: String? = null
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextString()
            "title" -> title = nextString()
            "cost" -> cost = when (peek()) {
                JsonToken.STRING -> nextString().toIntOrNull()
                else -> nextInt()
            }
            "url1x" -> url1x = nextString()
            "url2x" -> url2x = nextString()
            "url4x" -> url4x = nextString()
            else -> skipValue()
        }
    }
    endObject()
    return ChannelPointReward(
        id = id,
        title = title,
        cost = cost,
        url1x = url1x,
        url2x = url2x,
        url4x = url4x,
    )
}

private fun JsonReader.readReply(): Reply? {
    if (peek() != JsonToken.BEGIN_OBJECT) {
        skipValue()
        return null
    }
    beginObject()
    var threadParentId: String? = null
    var userLogin: String? = null
    var userName: String? = null
    var message: String? = null
    while (hasNext()) {
        when (nextName()) {
            "threadParentId" -> threadParentId = nextString()
            "userLogin" -> userLogin = nextString()
            "userName" -> userName = nextString()
            "message" -> message = nextString()
            else -> skipValue()
        }
    }
    endObject()
    return Reply(
        threadParentId = threadParentId,
        userLogin = userLogin,
        userName = userName,
        message = message,
    )
}
