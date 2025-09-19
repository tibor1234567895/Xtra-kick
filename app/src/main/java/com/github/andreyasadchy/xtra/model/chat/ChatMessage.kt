package com.github.andreyasadchy.xtra.model.chat

import com.github.andreyasadchy.xtra.kick.chat.KickChatBadge
import com.github.andreyasadchy.xtra.kick.chat.KickChatEmote

class ChatMessage(
    val id: String? = null,
    val userId: String? = null,
    val userLogin: String? = null,
    val userName: String? = null,
    val message: String? = null,
    val color: String? = null,
    val emotes: List<KickEmote>? = null,
    val badges: List<KickBadge>? = null,
    val kickBadges: List<KickChatBadge>? = null,
    val kickEmotesRaw: List<KickChatEmote>? = null,
    val isAction: Boolean = false,
    val isFirst: Boolean = false,
    val bits: Int? = null,
    val systemMsg: String? = null,
    val msgId: String? = null,
    val reward: ChannelPointReward? = null,
    val reply: Reply? = null,
    val isReply: Boolean = false,
    val replyParent: ChatMessage? = null,
    val timestamp: Long? = null,
    val fullMsg: String? = null,
    var translatedMessage: String? = null,
    var translationFailed: Boolean = false,
    var messageLanguage: String? = null,
)