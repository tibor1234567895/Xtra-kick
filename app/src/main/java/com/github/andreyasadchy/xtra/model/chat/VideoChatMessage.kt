package com.github.andreyasadchy.xtra.model.chat

class VideoChatMessage(
    val id: String?,
    val offsetSeconds: Int?,
    val userId: String?,
    val userLogin: String?,
    val userName: String?,
    val message: String?,
    val color: String?,
    val emotes: List<KickEmote>?,
    val badges: List<KickBadge>?,
    val fullMsg: String?,
)