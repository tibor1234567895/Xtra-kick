package com.github.andreyasadchy.xtra.model.chat

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
)
