package com.github.andreyasadchy.xtra.kick.auth

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.github.andreyasadchy.xtra.kick.storage.KickTokenProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KickTokenRefreshScheduler @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val tokenProvider: KickTokenProvider,
) {

    fun schedule(delayOverrideMillis: Long? = null) {
        val refreshToken = tokenProvider.refreshToken
        if (refreshToken.isNullOrBlank()) {
            WorkManager.getInstance(applicationContext)
                .cancelUniqueWork(KickTokenRefreshWorker.UNIQUE_WORK_NAME)
            return
        }

        val delayMillis = computeDelayMillis(delayOverrideMillis)
        val request = OneTimeWorkRequestBuilder<KickTokenRefreshWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(KickTokenRefreshWorker.UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            KickTokenRefreshWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancel() {
        WorkManager.getInstance(applicationContext)
            .cancelUniqueWork(KickTokenRefreshWorker.UNIQUE_WORK_NAME)
    }

    private fun computeDelayMillis(delayOverrideMillis: Long?): Long {
        val baseDelay = delayOverrideMillis ?: tokenProvider.expiresAtMillis?.let { expiresAt ->
            expiresAt - System.currentTimeMillis() - GRACE_PERIOD_MILLIS
        } ?: DEFAULT_DELAY_MILLIS
        return baseDelay.coerceAtLeast(0L)
    }

    companion object {
        private const val GRACE_PERIOD_MILLIS = 60_000L
        private val DEFAULT_DELAY_MILLIS = TimeUnit.HOURS.toMillis(1)
    }
}
