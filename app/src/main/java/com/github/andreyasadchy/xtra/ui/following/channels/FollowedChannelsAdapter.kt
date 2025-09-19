package com.github.andreyasadchy.xtra.ui.following.channels

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import coil3.request.transformations
import coil3.transform.CircleCropTransformation
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentFollowedChannelsListItemBinding
import com.github.andreyasadchy.xtra.model.ui.User
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.KickApiHelper
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible

class FollowedChannelsAdapter(
    private val fragment: Fragment,
) : PagingDataAdapter<User, FollowedChannelsAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean =
            oldItem.channelId == newItem.channelId

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean = true
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagingViewHolder {
        val binding = FragmentFollowedChannelsListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PagingViewHolder(binding, fragment)
    }

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PagingViewHolder(
        private val binding: FragmentFollowedChannelsListItemBinding,
        private val fragment: Fragment,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: User?) {
            with(binding) {
                if (item != null) {
                    val context = fragment.requireContext()
                    root.setOnClickListener {
                        fragment.findNavController().navigate(
                            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                                channelId = item.channelId,
                                channelLogin = item.channelLogin,
                                channelName = item.channelName,
                                channelLogo = item.channelLogo,
                            )
                        )
                    }
                    if (item.channelLogo != null) {
                        userImage.visible()
                        fragment.requireContext().imageLoader.enqueue(
                            ImageRequest.Builder(fragment.requireContext()).apply {
                                data(item.channelLogo)
                                if (context.prefs().getBoolean(C.UI_ROUNDUSERIMAGE, true)) {
                                    transformations(CircleCropTransformation())
                                }
                                crossfade(true)
                                target(userImage)
                            }.build()
                        )
                    } else {
                        userImage.gone()
                    }
                    if (item.channelName != null) {
                        username.visible()
                        username.text = if (item.channelLogin != null && !item.channelLogin.equals(item.channelName, true)) {
                            when (context.prefs().getString(C.UI_NAME_DISPLAY, "0")) {
                                "0" -> "${item.channelName}(${item.channelLogin})"
                                "1" -> item.channelName
                                else -> item.channelLogin
                            }
                        } else {
                            item.channelName
                        }
                    } else {
                        username.gone()
                    }
                    if (item.lastBroadcast != null) {
                        val text = item.lastBroadcast?.let { KickApiHelper.formatTimeString(context, it) }
                        if (text != null) {
                            userStream.visible()
                            userStream.text = context.getString(R.string.last_broadcast_date, text)
                        } else {
                            userStream.gone()
                        }
                    } else {
                        userStream.gone()
                    }
                    if (item.followedAt != null) {
                        val text = KickApiHelper.formatTimeString(context, item.followedAt!!)
                        if (text != null) {
                            userFollowed.visible()
                            userFollowed.text = context.getString(R.string.followed_at, text)
                        } else {
                            userFollowed.gone()
                        }
                    } else {
                        userFollowed.gone()
                    }
                    if (item.followAccount) {
                        kickText.visible()
                    } else {
                        kickText.gone()
                    }
                    if (item.followLocal) {
                        localText.visible()
                    } else {
                        localText.gone()
                    }
                }
            }
        }
    }
}