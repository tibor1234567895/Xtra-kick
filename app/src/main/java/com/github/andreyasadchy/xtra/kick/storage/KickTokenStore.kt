package com.github.andreyasadchy.xtra.kick.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.github.andreyasadchy.xtra.kick.auth.KickTokenResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KickTokenStore @Inject constructor(
    @ApplicationContext context: Context
) : KickTokenProvider {

    private val preferences: SharedPreferences

    init {
        val legacyPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        preferences = createEncryptedPreferences(context, legacyPreferences)
    }

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

    private fun createEncryptedPreferences(
        context: Context,
        legacyPreferences: SharedPreferences
    ): SharedPreferences {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return legacyPreferences
        }

        val encryptedPreferences = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME_ENCRYPTED,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (error: GeneralSecurityException) {
            legacyPreferences
        } catch (error: IOException) {
            legacyPreferences
        }

        if (encryptedPreferences !== legacyPreferences) {
            migrateLegacyTokensIfNeeded(legacyPreferences, encryptedPreferences)
        }

        return encryptedPreferences
    }

    private fun migrateLegacyTokensIfNeeded(
        legacyPreferences: SharedPreferences,
        encryptedPreferences: SharedPreferences
    ) {
        if (encryptedPreferences.getBoolean(KEY_MIGRATION_COMPLETE, false)) {
            return
        }

        val legacyAccessToken = legacyPreferences.getString(KEY_ACCESS_TOKEN, null)
        val legacyRefreshToken = legacyPreferences.getString(KEY_REFRESH_TOKEN, null)
        val legacyTokenType = legacyPreferences.getString(KEY_TOKEN_TYPE, null)
        val legacyScopes = legacyPreferences.getString(KEY_SCOPES, null)
        val legacyExpiresAt = if (legacyPreferences.contains(KEY_EXPIRES_AT)) {
            legacyPreferences.getLong(KEY_EXPIRES_AT, 0L)
        } else {
            null
        }

        encryptedPreferences.edit(commit = true) {
            putBoolean(KEY_MIGRATION_COMPLETE, true)
            if (legacyAccessToken != null) {
                putString(KEY_ACCESS_TOKEN, legacyAccessToken)
            } else {
                remove(KEY_ACCESS_TOKEN)
            }

            if (!legacyRefreshToken.isNullOrBlank()) {
                putString(KEY_REFRESH_TOKEN, legacyRefreshToken)
            } else {
                remove(KEY_REFRESH_TOKEN)
            }

            if (legacyTokenType != null) {
                putString(KEY_TOKEN_TYPE, legacyTokenType)
            } else {
                remove(KEY_TOKEN_TYPE)
            }

            putString(KEY_SCOPES, legacyScopes ?: "")

            if (legacyExpiresAt != null) {
                putLong(KEY_EXPIRES_AT, legacyExpiresAt)
            } else {
                remove(KEY_EXPIRES_AT)
            }
        }

        legacyPreferences.edit(commit = true) {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_TOKEN_TYPE)
            remove(KEY_SCOPES)
            remove(KEY_EXPIRES_AT)
        }
    }

    data class KickTokenSnapshot(
        val accessToken: String?,
        val refreshToken: String?,
        val tokenType: String?,
        val scopes: Set<String>,
        val expiresAtMillis: Long?
    )

    private companion object {
        const val PREFS_NAME = "kick_tokens"
        const val PREFS_NAME_ENCRYPTED = "kick_tokens_encrypted"
        const val KEY_MIGRATION_COMPLETE = "tokens_migrated"
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_TOKEN_TYPE = "token_type"
        const val KEY_SCOPES = "scopes"
        const val KEY_EXPIRES_AT = "expires_at"
    }
}
