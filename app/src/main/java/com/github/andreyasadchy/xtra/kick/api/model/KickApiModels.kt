package com.github.andreyasadchy.xtra.kick.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KickCategory(
    val id: Long,
    val name: String,
    val thumbnail: String? = null
)

@Serializable
data class KickLivestream(
    @SerialName("broadcaster_user_id") val broadcasterUserId: Long,
    @SerialName("channel_id") val channelId: Long,
    @SerialName("has_mature_content") val hasMatureContent: Boolean? = null,
    val language: String? = null,
    val slug: String,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("stream_title") val streamTitle: String? = null,
    val thumbnail: String? = null,
    @SerialName("viewer_count") val viewerCount: Int? = null,
    val category: KickCategory? = null
)

@Serializable
data class KickStream(
    @SerialName("is_live") val isLive: Boolean,
    @SerialName("is_mature") val isMature: Boolean? = null,
    val key: String? = null,
    val language: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    @SerialName("viewer_count") val viewerCount: Int? = null
)

@Serializable
data class KickChannel(
    @SerialName("banner_picture") val bannerPicture: String? = null,
    @SerialName("broadcaster_user_id") val broadcasterUserId: Long? = null,
    val category: KickCategory? = null,
    @SerialName("channel_description") val channelDescription: String? = null,
    val slug: String,
    val stream: KickStream? = null,
    @SerialName("stream_title") val streamTitle: String? = null
)

@Serializable
data class KickUser(
    val email: String? = null,
    val name: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
    @SerialName("user_id") val userId: Long
)

@Serializable
data class KickLivestreamsResponse(
    val data: List<KickLivestream> = emptyList(),
    val message: String? = null
)

@Serializable
data class KickCategoriesResponse(
    val data: List<KickCategory> = emptyList(),
    val message: String? = null
)

@Serializable
data class KickCategoryResponse(
    val data: KickCategory? = null,
    val message: String? = null
)

@Serializable
data class KickChannelsResponse(
    val data: List<KickChannel> = emptyList(),
    val message: String? = null
)

@Serializable
data class KickUsersResponse(
    val data: List<KickUser> = emptyList(),
    val message: String? = null
)
