package com.github.andreyasadchy.xtra.kick.storage

import android.content.Context
import androidx.core.content.edit
import com.github.andreyasadchy.xtra.kick.auth.KickTokenResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KickTokenStore @Inject constructor(
    @ApplicationContext context: Context
) : KickTokenProvider {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val accessToken: String?
        get() = preferences.getString(KEY_ACCESS_TOKEN, null)

    override val refreshToken: String?
        get() = preferences.getString(KEY_REFRESH_TOKEN, null)

    override val tokenType: String?
        get() = preferences.getString(KEY_TOKEN_TYPE, null)

    override val scopes: Set<String>
        get() = preferences.getString(KEY_SCOPES, null)
            ?.split(' ')
            ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            ?.toSet()
            ?: emptySet()

    override val expiresAtMillis: Long?
        get() = if (preferences.contains(KEY_EXPIRES_AT)) {
            preferences.getLong(KEY_EXPIRES_AT, 0L)
        } else {
            null
        }

    override fun isAccessTokenExpired(graceMillis: Long): Boolean {
        val expiresAt = expiresAtMillis ?: return true
        return System.currentTimeMillis() >= expiresAt - graceMillis
    }

    fun update(response: KickTokenResponse) {
        val expiresAt = System.currentTimeMillis() + (response.expiresIn * 1000)
        preferences.edit {
            putString(KEY_ACCESS_TOKEN, response.accessToken)
            putString(KEY_TOKEN_TYPE, response.tokenType)
            putString(KEY_SCOPES, response.scope ?: "")
            putLong(KEY_EXPIRES_AT, expiresAt)
            if (response.refreshToken.isNullOrBlank()) {
                remove(KEY_REFRESH_TOKEN)
            } else {
                putString(KEY_REFRESH_TOKEN, response.refreshToken)
            }
        }
    }

    fun clear() {
        preferences.edit {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_TOKEN_TYPE)
            remove(KEY_SCOPES)
            remove(KEY_EXPIRES_AT)
        }
    }

    fun snapshot(): KickTokenSnapshot = KickTokenSnapshot(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        scopes = scopes,
        expiresAtMillis = expiresAtMillis
    )

    data class KickTokenSnapshot(
        val accessToken: String?,
        val refreshToken: String?,
        val tokenType: String?,
        val scopes: Set<String>,
        val expiresAtMillis: Long?
    )

    private companion object {
        const val PREFS_NAME = "kick_tokens"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_TOKEN_TYPE = "token_type"
        const val KEY_SCOPES = "scopes"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
