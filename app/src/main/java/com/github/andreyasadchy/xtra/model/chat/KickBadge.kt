package com.github.andreyasadchy.xtra.model.chat

import com.github.andreyasadchy.xtra.kick.chat.KickChatBadgeImage

data class KickBadge(
    val id: String? = null,
    val setId: String? = null,
    val version: String? = null,
    val localData: Pair<Long, Int>? = null,
    val url1x: String? = null,
    val url2x: String? = null,
    val url3x: String? = null,
    val url4x: String? = null,
    val title: String? = null,
    val description: String? = null,
    val format: String? = null,
    val isAnimated: Boolean = false,
    val source: String? = null,
    val type: String? = null,
    val text: String? = null,
    val count: Int? = null,
    val image: KickChatBadgeImage? = null,
    val badgeImage: KickChatBadgeImage? = null,
)
