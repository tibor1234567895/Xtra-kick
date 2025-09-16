package com.github.andreyasadchy.xtra.ui.following.streams

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalFollowChannelRepository
import com.github.andreyasadchy.xtra.repository.datasource.FollowedStreamsDataSource
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.KickApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class FollowedStreamsViewModel @Inject constructor(
    @ApplicationContext applicationContext: Context,
    private val localFollowsChannel: LocalFollowChannelRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) : ViewModel() {

    val flow = Pager(
        if (applicationContext.prefs().getString(C.COMPACT_STREAMS, "disabled") != "disabled") {
            PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
        } else {
            PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)
        }
    ) {
        FollowedStreamsDataSource(
            userId = applicationContext.tokenPrefs().getString(C.USER_ID, null),
            localFollowsChannel = localFollowsChannel,
            gqlHeaders = KickApiHelper.getGQLHeaders(applicationContext, true),
            graphQLRepository = graphQLRepository,
            helixHeaders = KickApiHelper.getHelixHeaders(applicationContext),
            helixRepository = helixRepository,
            enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            apiPref = applicationContext.prefs().getString(C.API_PREFS_FOLLOWED_STREAMS, null)?.split(',') ?: KickApiHelper.followedStreamsApiDefaults,
            networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, "OkHttp"),
        )
    }.flow.cachedIn(viewModelScope)
}