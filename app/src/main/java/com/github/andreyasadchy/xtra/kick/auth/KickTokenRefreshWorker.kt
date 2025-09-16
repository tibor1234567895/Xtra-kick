package com.github.andreyasadchy.xtra.kick.auth

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.andreyasadchy.xtra.kick.storage.KickTokenProvider
import com.github.andreyasadchy.xtra.kick.storage.KickTokenStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class KickTokenRefreshWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val kickOAuthClient: KickOAuthClient,
    private val tokenStore: KickTokenStore,
    private val tokenProvider: KickTokenProvider,
    private val scheduler: KickTokenRefreshScheduler,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val refreshToken = tokenProvider.refreshToken
        if (refreshToken.isNullOrBlank()) {
            scheduler.cancel()
            return Result.success()
        }

        if (!tokenProvider.isAccessTokenExpired()) {
            scheduler.schedule()
            return Result.success()
        }

        return try {
            val response = kickOAuthClient.refreshToken(refreshToken)
            tokenStore.update(response)
            scheduler.schedule()
            Result.success()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to refresh Kick OAuth token", t)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "kick_token_refresh"
        private const val TAG = "KickRefreshWorker"
    }
}
