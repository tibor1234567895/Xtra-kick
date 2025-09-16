package com.github.andreyasadchy.xtra.model.ui

import android.os.Parcelable
import com.github.andreyasadchy.xtra.util.KickApiHelper
import kotlinx.parcelize.Parcelize

@Parcelize
class Stream(
    var id: String? = null,
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    var gameId: String? = null,
    var gameSlug: String? = null,
    var gameName: String? = null,
    val type: String? = null,
    var title: String? = null,
    var viewerCount: Int? = null,
    var startedAt: String? = null,
    val thumbnailUrl: String? = null,

    var profileImageUrl: String? = null,
    val tags: List<String>? = null,
    val user: User? = null,
) : Parcelable {

    val thumbnail: String?
        get() = KickApiHelper.getTemplateUrl(thumbnailUrl, "video")
    val channelLogo: String?
        get() = KickApiHelper.getTemplateUrl(profileImageUrl, "profileimage")
}
