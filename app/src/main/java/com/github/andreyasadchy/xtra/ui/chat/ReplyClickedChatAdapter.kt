package com.github.andreyasadchy.xtra.ui.chat

import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.method.LinkMovementMethod
import android.text.style.ImageSpan
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.TooltipCompat
import androidx.core.text.getSpans
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.CheerEmote
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.NamePaint
import com.github.andreyasadchy.xtra.model.chat.StvBadge
import com.github.andreyasadchy.xtra.model.chat.KickBadge
import com.github.andreyasadchy.xtra.model.chat.KickEmote
import com.github.andreyasadchy.xtra.ui.view.NamePaintImageSpan
import com.github.andreyasadchy.xtra.util.chat.ChatAdapterUtils
import java.util.Random

class ReplyClickedChatAdapter(
    private val enableTimestamps: Boolean,
    private val timestampFormat: String?,
    private val firstMsgVisibility: Int,
    private val firstChatMsg: String,
    private val redeemedChatMsg: String,
    private val redeemedNoMsg: String,
    private val rewardChatMsg: String,
    private val replyMessage: String,
    private val imageClick: (String?, String?, String?, String?, Boolean?, Boolean?, String?) -> Unit,
    private val useRandomColors: Boolean,
    private val useReadableColors: Boolean,
    private val isLightTheme: Boolean,
    private val nameDisplay: String?,
    private val useBoldNames: Boolean,
    private val showNamePaints: Boolean,
    val namePaints: MutableList<NamePaint>?,
    val paintUsers: MutableMap<String, String>?,
    private val showStvBadges: Boolean,
    val stvBadges: MutableList<StvBadge>?,
    val stvBadgeUsers: MutableMap<String, String>?,
    private val showPersonalEmotes: Boolean,
    val personalEmoteSets: MutableMap<String, List<Emote>>?,
    val personalEmoteSetUsers: MutableMap<String, String>?,
    private val showSystemMessageEmotes: Boolean,
    private val chatUrl: String?,
    private val getEmoteBytes: ((String, Pair<Long, Int>) -> ByteArray?)?,
    private val fragment: Fragment,
    private val backgroundColor: Int,
    private val imageLibrary: String?,
    private val messageTextSize: Float,
    private val emoteSize: Int,
    private val badgeSize: Int,
    private val emoteQuality: String,
    private val animateGifs: Boolean,
    private val enableOverlayEmotes: Boolean,
    private val translateAllMessages: Boolean,
    private val translateMessage: (ChatMessage, String?) -> Unit,
    private val showLanguageDownloadDialog: (ChatMessage, String) -> Unit,
    messages: List<ChatMessage>?,
    private val userColors: HashMap<String, Int>,
    private val savedColors: HashMap<String, Int>,
    var loggedInUser: String?,
    var localKickEmotes: List<KickEmote>?,
    var globalStvEmotes: List<Emote>?,
    var channelStvEmotes: List<Emote>?,
    var globalBttvEmotes: List<Emote>?,
    var channelBttvEmotes: List<Emote>?,
    var globalFfzEmotes: List<Emote>?,
    var channelFfzEmotes: List<Emote>?,
    var globalBadges: List<KickBadge>?,
    var channelBadges: List<KickBadge>?,
    var cheerEmotes: List<CheerEmote>?,
    var selectedMessage: ChatMessage?,
) : RecyclerView.Adapter<ReplyClickedChatAdapter.ViewHolder>() {

    val threadParentId = selectedMessage?.reply?.threadParentId
    var messages: MutableList<ChatMessage>? =
        messages?.filter { (it.reply?.threadParentId == threadParentId || it.id == threadParentId) && !it.isReply }?.toMutableList()
            ?: selectedMessage?.let { mutableListOf(it) }
        set(value) {
            val oldSize = field?.size ?: 0
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize)
            }
            field = value
        }
    private val random = Random()
    private val savedLocalKickEmotes = mutableMapOf<String, ByteArray>()
    private val savedLocalBadges = mutableMapOf<String, ByteArray>()
    private val savedLocalCheerEmotes = mutableMapOf<String, ByteArray>()
    private val savedLocalEmotes = mutableMapOf<String, ByteArray>()

    var messageClickListener: ((ChatMessage, ChatMessage?) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val chatMessage = messages?.get(position) ?: return
        val result = ChatAdapterUtils.prepareChatMessage(
            chatMessage, holder.textView, enableTimestamps, timestampFormat, firstMsgVisibility, firstChatMsg, redeemedChatMsg, redeemedNoMsg,
            rewardChatMsg, replyMessage, { url, name, source, format, isAnimated, thirdParty, emoteId -> imageClick(url, name, source, format, isAnimated, thirdParty, emoteId) },
            useRandomColors, random, useReadableColors, isLightTheme, nameDisplay, useBoldNames, showNamePaints, namePaints, paintUsers,
            showStvBadges, stvBadges, stvBadgeUsers, showPersonalEmotes, personalEmoteSets, personalEmoteSetUsers, showSystemMessageEmotes,
            enableOverlayEmotes, loggedInUser, chatUrl, getEmoteBytes, userColors, savedColors, translateAllMessages, translateMessage,
            showLanguageDownloadDialog, false, localKickEmotes, globalStvEmotes, channelStvEmotes, globalBttvEmotes, channelBttvEmotes, globalFfzEmotes,
            channelFfzEmotes, globalBadges, channelBadges, cheerEmotes, savedLocalKickEmotes, savedLocalBadges, savedLocalCheerEmotes, savedLocalEmotes
        )
        if (chatMessage == selectedMessage) {
            holder.textView.setBackgroundResource(R.color.chatMessageSelected)
        }
        holder.bind(chatMessage, result.builder)
        ChatAdapterUtils.loadImages(
            fragment, holder.textView, { holder.bind(chatMessage, it) }, result.images, result.imagePaint, result.userName, result.userNameStartIndex,
            backgroundColor, imageLibrary, result.builder, result.translated, emoteSize, badgeSize, emoteQuality, animateGifs, enableOverlayEmotes,
            chatMessage, savedColors, useReadableColors, isLightTheme, showLanguageDownloadDialog, false
        )
    }

    fun updateBackground(chatMessage: ChatMessage, item: TextView) {
        if (chatMessage.message.isNullOrBlank()) {
            item.setBackgroundResource(0)
        } else {
            when {
                chatMessage.isFirst && firstMsgVisibility < 2 -> item.setBackgroundResource(R.color.chatMessageFirst)
                chatMessage.reward?.id != null && firstMsgVisibility < 2 -> item.setBackgroundResource(R.color.chatMessageReward)
                chatMessage.systemMsg != null || chatMessage.msgId != null -> item.setBackgroundResource(R.color.chatMessageNotice)
                loggedInUser?.let { user ->
                    if (chatMessage.userId != null && chatMessage.userLogin != user) {
                        item.text.split(" ").find {
                            !Patterns.WEB_URL.matcher(it).matches() && it.contains(user, true)
                        } != null
                    } else false
                } == true -> item.setBackgroundResource(R.color.chatMessageMention)
                else -> item.setBackgroundResource(0)
            }
        }
        (item.text as? Spannable)?.let { view ->
            view.getSpans<NamePaintImageSpan>().forEach {
                it.backgroundColor = (item.background as? ColorDrawable)?.color
                view.setSpan(it, view.getSpanStart(it), view.getSpanEnd(it), SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    fun updateTranslation(chatMessage: ChatMessage, item: TextView, previousTranslation: String?) {
        (item.text as? SpannableString)?.let { text ->
            val builder = SpannableStringBuilder()
            builder.append(
                if (previousTranslation != null) {
                    text.dropLast(previousTranslation.length + 1)
                } else {
                    text
                }
            )
            ChatAdapterUtils.addTranslation(chatMessage, builder, builder.length, savedColors, useReadableColors, isLightTheme, showLanguageDownloadDialog, false)
            item.text = builder
        }
    }

    override fun getItemCount(): Int = messages?.size ?: 0

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (animateGifs) {
            (holder.textView.text as? Spannable)?.let { view ->
                view.getSpans<ImageSpan>().forEach {
                    (it.drawable as? Animatable)?.start() ?:
                    (it.drawable as? LayerDrawable)?.let {
                        val lastIndex = it.numberOfLayers - 1
                        if (lastIndex > -1) {
                            for (i in 0..lastIndex) {
                                (it.getDrawable(i) as? Animatable)?.start()
                            }
                        }
                    }
                }
                view.getSpans<NamePaintImageSpan>().forEach {
                    (it.drawable as? Animatable)?.start()
                }
            }
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (animateGifs) {
            (holder.textView.text as? Spannable)?.let { view ->
                view.getSpans<ImageSpan>().forEach {
                    (it.drawable as? Animatable)?.stop() ?:
                    (it.drawable as? LayerDrawable)?.let {
                        val lastIndex = it.numberOfLayers - 1
                        if (lastIndex > -1) {
                            for (i in 0..lastIndex) {
                                (it.getDrawable(i) as? Animatable)?.stop()
                            }
                        }
                    }
                }
                view.getSpans<NamePaintImageSpan>().forEach {
                    (it.drawable as? Animatable)?.stop()
                }
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        val childCount = recyclerView.childCount
        if (animateGifs) {
            for (i in 0 until childCount) {
                ((recyclerView.getChildAt(i) as TextView).text as? Spannable)?.let { view ->
                    view.getSpans<ImageSpan>().forEach {
                        (it.drawable as? Animatable)?.stop() ?:
                        (it.drawable as? LayerDrawable)?.let {
                            val lastIndex = it.numberOfLayers - 1
                            if (lastIndex > -1) {
                                for (i in 0..lastIndex) {
                                    (it.getDrawable(i) as? Animatable)?.stop()
                                }
                            }
                        }
                    }
                    view.getSpans<NamePaintImageSpan>().forEach {
                        (it.drawable as? Animatable)?.stop()
                    }
                }
            }
        }
        super.onDetachedFromRecyclerView(recyclerView)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView = itemView as TextView

        fun bind(chatMessage: ChatMessage, formattedMessage: SpannableStringBuilder) {
            textView.apply {
                text = formattedMessage
                textSize = messageTextSize
                movementMethod = LinkMovementMethod.getInstance()
                TooltipCompat.setTooltipText(this, chatMessage.message ?: chatMessage.systemMsg)
                setOnClickListener {
                    if (selectionStart == -1 && selectionEnd == -1 && chatMessage != selectedMessage) {
                        messageClickListener?.invoke(chatMessage, selectedMessage)
                        selectedMessage = chatMessage
                        setBackgroundResource(R.color.chatMessageSelected)
                        (text as? Spannable)?.let { view ->
                            view.getSpans<NamePaintImageSpan>().forEach {
                                it.backgroundColor = (background as? ColorDrawable)?.color
                                view.setSpan(it, view.getSpanStart(it), view.getSpanEnd(it), SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                    }
                }
            }
        }
    }
}
