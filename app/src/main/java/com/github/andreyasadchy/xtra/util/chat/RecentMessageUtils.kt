package com.github.andreyasadchy.xtra.util.chat

import android.content.Context
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.ui.chat.KickChatMessageMapper

object RecentMessageUtils {
    fun parseChatMessage(message: String, userNotice: Boolean): ChatMessage {
        return KickChatMessageMapper.fromBackfillMessage(message, userNotice)
    }

    fun parseClearMessage(message: String): Pair<ChatMessage, String?> {
        return KickChatMessageMapper.parseClearMessage(message, backfill = true)
    }

    fun parseClearChat(context: Context, message: String): ChatMessage {
        return KickChatMessageMapper.parseClearChat(context, message, backfill = true)
    }

    fun parseNotice(context: Context, message: String): ChatMessage {
        return KickChatMessageMapper.parseNotice(context, message, backfill = true).first
    }
}
