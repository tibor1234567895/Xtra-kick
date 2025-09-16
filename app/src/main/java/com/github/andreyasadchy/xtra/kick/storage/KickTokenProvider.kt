package com.github.andreyasadchy.xtra.kick.storage

/**
 * Minimal abstraction for providing Kick OAuth tokens to API and chat clients.
 */
interface KickTokenProvider {
    val accessToken: String?
    val refreshToken: String?
    val tokenType: String?
    val scopes: Set<String>
    val expiresAtMillis: Long?

    fun isAccessTokenExpired(graceMillis: Long = DEFAULT_GRACE_PERIOD_MILLIS): Boolean

    companion object {
        private const val DEFAULT_GRACE_PERIOD_MILLIS = 60_000L
    }
}
