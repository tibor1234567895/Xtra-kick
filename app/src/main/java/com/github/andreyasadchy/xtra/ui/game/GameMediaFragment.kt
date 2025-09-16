package com.github.andreyasadchy.xtra.ui.game

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import androidx.recyclerview.widget.RecyclerView
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.databinding.FragmentMediaBinding
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.FragmentHost
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.common.Scrollable
import com.github.andreyasadchy.xtra.ui.common.Sortable
import com.github.andreyasadchy.xtra.ui.game.clips.GameClipsFragment
import com.github.andreyasadchy.xtra.ui.game.streams.GameStreamsFragment
import com.github.andreyasadchy.xtra.ui.game.videos.GameVideosFragment
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.KickApiHelper
import com.github.andreyasadchy.xtra.util.getAlertDialogBuilder
import com.github.andreyasadchy.xtra.util.gone
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.shortToast
import com.github.andreyasadchy.xtra.util.tokenPrefs
import com.github.andreyasadchy.xtra.util.visible
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GameMediaFragment : BaseNetworkFragment(), Scrollable, FragmentHost, IntegrityDialog.CallbackListener {

    private var _binding: FragmentMediaBinding? = null
    private val binding get() = _binding!!
    private val args: GamePagerFragmentArgs by navArgs()
    private val viewModel: GamePagerViewModel by viewModels()

    private var previousItem = -1

    override val currentFragment: Fragment?
        get() = childFragmentManager.findFragmentById(R.id.fragmentContainer)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previousItem = savedInstanceState?.getInt("previousItem", -1) ?: -1
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMediaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.integrity.collectLatest {
                    if (it != null &&
                        it != "done" &&
                        requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false) &&
                        requireContext().prefs().getBoolean(C.USE_WEBVIEW_INTEGRITY, true)
                    ) {
                        IntegrityDialog.show(childFragmentManager, it)
                        viewModel.integrity.value = "done"
                    }
                }
            }
        }
        with(binding) {
            val activity = requireActivity() as MainActivity
            val isLoggedIn = !KickApiHelper.getGQLHeaders(requireContext(), true)[C.HEADER_TOKEN].isNullOrBlank() ||
                    !KickApiHelper.getHelixHeaders(requireContext())[C.HEADER_TOKEN].isNullOrBlank()
            val setting = requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
            val navController = findNavController()
            val appBarConfiguration = AppBarConfiguration(setOf(R.id.rootGamesFragment, R.id.rootTopFragment, R.id.followPagerFragment, R.id.followMediaFragment, R.id.savedPagerFragment, R.id.savedMediaFragment))
            toolbar.setupWithNavController(navController, appBarConfiguration)
            toolbar.title = args.gameName
            toolbar.menu.findItem(R.id.login).title = if (isLoggedIn) getString(R.string.log_out) else getString(R.string.log_in)
            toolbar.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.followButton -> {
                        viewModel.isFollowing.value?.let {
                            if (it) {
                                requireContext().getAlertDialogBuilder()
                                    .setMessage(requireContext().getString(R.string.unfollow_channel, args.gameName))
                                    .setNegativeButton(getString(R.string.no), null)
                                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                                        viewModel.deleteFollowGame(
                                            args.gameId,
                                            setting,
                                            requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                            KickApiHelper.getGQLHeaders(requireContext(), true),
                                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                                        )
                                    }
                                    .show()
                            } else {
                                viewModel.saveFollowGame(
                                    args.gameId,
                                    args.gameSlug,
                                    args.gameName,
                                    setting,
                                    requireContext().filesDir.path,
                                    requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                    KickApiHelper.getGQLHeaders(requireContext(), true),
                                    KickApiHelper.getHelixHeaders(requireContext()),
                                    requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                                )
                            }
                        }
                        true
                    }
                    R.id.search -> {
                        findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment())
                        true
                    }
                    R.id.settings -> {
                        activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java))
                        true
                    }
                    R.id.login -> {
                        if (isLoggedIn) {
                            activity.getAlertDialogBuilder().apply {
                                setTitle(getString(R.string.logout_title))
                                requireContext().tokenPrefs().getString(C.USERNAME, null)?.let { setMessage(getString(R.string.logout_msg, it)) }
                                setNegativeButton(getString(R.string.no), null)
                                setPositiveButton(getString(R.string.yes)) { _, _ -> activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java)) }
                            }.show()
                        } else {
                            activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
                        }
                        true
                    }
                    else -> false
                }
            }
            if (setting < 2) {
                val followButton = toolbar.menu.findItem(R.id.followButton)
                followButton?.isVisible = true
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.isFollowing.collectLatest {
                            if (it != null) {
                                followButton?.apply {
                                    if (it) {
                                        icon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_favorite_black_24)
                                        title = requireContext().getString(R.string.unfollow)
                                    } else {
                                        icon = ContextCompat.getDrawable(requireContext(), R.drawable.baseline_favorite_border_black_24)
                                        title = requireContext().getString(R.string.follow)
                                    }
                                }
                            }
                        }
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        viewModel.follow.collectLatest { pair ->
                            if (pair != null) {
                                val following = pair.first
                                val errorMessage = pair.second
                                if (!errorMessage.isNullOrBlank()) {
                                    requireContext().shortToast(errorMessage)
                                } else {
                                    if (following) {
                                        requireContext().shortToast(requireContext().getString(R.string.now_following, args.gameName))
                                    } else {
                                        requireContext().shortToast(requireContext().getString(R.string.unfollowed, args.gameName))
                                    }
                                }
                                viewModel.follow.value = null
                            }
                        }
                    }
                }
            }
            if (!args.gameId.isNullOrBlank() || !args.gameName.isNullOrBlank()) {
                spinner.visible()
                (spinner.editText as? MaterialAutoCompleteTextView)?.apply {
                    setSimpleItems(resources.getStringArray(R.array.spinnerMedia))
                    setOnItemClickListener { _, _, position, _ ->
                        if (position != previousItem) {
                            childFragmentManager.beginTransaction().replace(R.id.fragmentContainer, onSpinnerItemSelected(position)).commit()
                            previousItem = position
                        }
                    }
                    if (previousItem == -1) {
                        childFragmentManager.beginTransaction().replace(R.id.fragmentContainer, onSpinnerItemSelected(0)).commit()
                        previousItem = 0
                    }
                    setText(adapter.getItem(previousItem).toString(), false)
                }
            } else {
                if (previousItem == -1) {
                    childFragmentManager.beginTransaction().replace(R.id.fragmentContainer, onSpinnerItemSelected(0)).commit()
                    previousItem = 0
                }
            }
            childFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                    if (requireContext().prefs().getBoolean(C.UI_THEME_APPBAR_LIFT, true)) {
                        f.view?.findViewById<RecyclerView>(R.id.recyclerView)?.let {
                            appBar.setLiftOnScrollTargetView(it)
                            it.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                                    super.onScrolled(recyclerView, dx, dy)
                                    appBar.isLifted = recyclerView.canScrollVertically(-1)
                                }
                            })
                            it.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                appBar.isLifted = it.canScrollVertically(-1)
                            }
                        }
                    } else {
                        appBar.setLiftable(false)
                        appBar.background = null
                    }
                    (f as? Sortable)?.setupSortBar(sortBar) ?: sortBar.root.gone()
                }
            }, false)
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                toolbar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    topMargin = insets.top
                }
                windowInsets
            }
        }
    }

    override fun initialize() {
        val setting = requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
        if (setting < 2) {
            viewModel.isFollowingGame(
                args.gameId,
                args.gameName,
                setting,
                requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                KickApiHelper.getGQLHeaders(requireContext(), true),
            )
        }
        if (args.updateLocal) {
            viewModel.updateLocalGame(
                requireContext().filesDir.path,
                args.gameId,
                args.gameName,
                requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                KickApiHelper.getGQLHeaders(requireContext()),
                KickApiHelper.getHelixHeaders(requireContext()),
            )
        }
    }

    private fun onSpinnerItemSelected(position: Int): Fragment {
        val fragment: Fragment = when (position) {
            0 -> GameStreamsFragment()
            1 -> GameVideosFragment()
            else -> GameClipsFragment()
        }
        return fragment.also { it.arguments = requireArguments() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("previousItem", previousItem)
        super.onSaveInstanceState(outState)
    }

    override fun scrollToTop() {
        binding.appBar.setExpanded(true, true)
        (currentFragment as? Scrollable)?.scrollToTop()
    }

    override fun onNetworkRestored() {
    }

    override fun onIntegrityDialogCallback(callback: String?) {
        if (callback != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    when (callback) {
                        "refresh" -> {
                            val setting = requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
                            if (setting < 2) {
                                viewModel.isFollowingGame(
                                    args.gameId,
                                    args.gameName,
                                    setting,
                                    requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                                    KickApiHelper.getGQLHeaders(requireContext(), true),
                                )
                            }
                        }
                        "follow" -> viewModel.saveFollowGame(
                            args.gameId,
                            args.gameSlug,
                            args.gameName,
                            requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                            requireContext().filesDir.path,
                            requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                            KickApiHelper.getGQLHeaders(requireContext(), true),
                            KickApiHelper.getHelixHeaders(requireContext()),
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                        )
                        "unfollow" -> viewModel.deleteFollowGame(
                            args.gameId,
                            requireContext().prefs().getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0,
                            requireContext().prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
                            KickApiHelper.getGQLHeaders(requireContext(), true),
                            requireContext().prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                        )
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}