package com.github.andreyasadchy.xtra.model.chat

data class KickEmote(
    val id: String? = null,
    val name: String? = null,
    val localData: Pair<Long, Int>? = null,
    val url1x: String? = null,
    val url2x: String? = null,
    val url3x: String? = null,
    val url4x: String? = null,
    val format: String? = null,
    val isAnimated: Boolean = false,
    var begin: Int = 0,
    var end: Int = 0,
    val setId: String? = null,
    val ownerId: String? = null,
)
