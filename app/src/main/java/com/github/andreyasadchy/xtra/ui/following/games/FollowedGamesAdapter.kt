package com.github.andreyasadchy.xtra.ui.following.games

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.res.use
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentFollowedGamesListItemBinding
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.ui.game.GameMediaFragmentDirections
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesFragmentDirections
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.KickApiHelper
import com.github.andreyasadchy.xtra.util.convertDpToPixels
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.visible

class FollowedGamesAdapter(
    private val fragment: Fragment,
) : PagingDataAdapter<Game, FollowedGamesAdapter.PagingViewHolder>(
    object : DiffUtil.ItemCallback<Game>() {
        override fun areItemsTheSame(oldItem: Game, newItem: Game): Boolean =
            oldItem.gameId == newItem.gameId

        override fun areContentsTheSame(oldItem: Game, newItem: Game): Boolean =
            oldItem.viewersCount == newItem.viewersCount
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagingViewHolder {
        val binding = FragmentFollowedGamesListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PagingViewHolder(binding, fragment)
    }

    override fun onBindViewHolder(holder: PagingViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PagingViewHolder(
        private val binding: FragmentFollowedGamesListItemBinding,
        private val fragment: Fragment,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Game?) {
            with(binding) {
                if (item != null) {
                    val context = fragment.requireContext()
                    root.setOnClickListener {
                        fragment.findNavController().navigate(
                            if (context.prefs().getBoolean(C.UI_GAMEPAGER, true)) {
                                GamePagerFragmentDirections.actionGlobalGamePagerFragment(
                                    gameId = item.gameId,
                                    gameSlug = item.gameSlug,
                                    gameName = item.gameName,
                                    updateLocal = item.followLocal
                                )
                            } else {
                                GameMediaFragmentDirections.actionGlobalGameMediaFragment(
                                    gameId = item.gameId,
                                    gameSlug = item.gameSlug,
                                    gameName = item.gameName,
                                    updateLocal = item.followLocal
                                )
                            }
                        )
                    }
                    if (item.boxArt != null) {
                        gameImage.visible()
                        fragment.requireContext().imageLoader.enqueue(
                            ImageRequest.Builder(fragment.requireContext()).apply {
                                data(item.boxArt)
                                diskCachePolicy(CachePolicy.DISABLED)
                                crossfade(true)
                                target(gameImage)
                            }.build()
                        )
                    } else {
                        gameImage.gone()
                    }
                    if (item.gameName != null) {
                        gameName.visible()
                        gameName.text = item.gameName
                    } else {
                        gameName.gone()
                    }
                    if (item.viewersCount != null) {
                        viewers.visible()
                        viewers.text = KickApiHelper.formatViewersCount(context, item.viewersCount!!, context.prefs().getBoolean(C.UI_TRUNCATEVIEWCOUNT, true))
                    } else {
                        viewers.gone()
                    }
                    if (item.broadcastersCount != null && context.prefs().getBoolean(C.UI_BROADCASTERSCOUNT, true)) {
                        broadcastersCount.visible()
                        broadcastersCount.text = context.resources.getQuantityString(R.plurals.broadcasters, item.broadcastersCount!!, item.broadcastersCount)
                    } else {
                        broadcastersCount.gone()
                    }
                    if (!item.tags.isNullOrEmpty() && context.prefs().getBoolean(C.UI_TAGS, true)) {
                        tagsLayout.removeAllViews()
                        tagsLayout.visible()
                        val tagsFlowLayout = Flow(context).apply {
                            layoutParams = ConstraintLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topToTop = tagsLayout.id
                                bottomToBottom = tagsLayout.id
                                startToStart = tagsLayout.id
                                endToEnd = tagsLayout.id
                            }
                            setWrapMode(Flow.WRAP_CHAIN)
                        }
                        tagsLayout.addView(tagsFlowLayout)
                        val ids = mutableListOf<Int>()
                        for (tag in item.tags!!) {
                            val text = TextView(context)
                            val id = View.generateViewId()
                            text.id = id
                            ids.add(id)
                            text.text = tag.name
                            context.obtainStyledAttributes(intArrayOf(com.google.android.material.R.attr.textAppearanceBodyMedium)).use {
                                TextViewCompat.setTextAppearance(text, it.getResourceId(0, 0))
                            }
                            if (tag.id != null) {
                                text.setOnClickListener {
                                    fragment.findNavController().navigate(
                                        GamesFragmentDirections.actionGlobalGamesFragment(
                                            tags = arrayOf(tag.id)
                                        )
                                    )
                                }
                            }
                            val padding = context.convertDpToPixels(5f)
                            text.setPadding(padding, 0, padding, 0)
                            tagsLayout.addView(text)
                        }
                        tagsFlowLayout.referencedIds = ids.toIntArray()
                    } else {
                        tagsLayout.gone()
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