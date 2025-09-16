package com.github.andreyasadchy.xtra.kick.config

import com.github.andreyasadchy.xtra.BuildConfig

/**
 * Centralised configuration for accessing the Kick public API, OAuth server and chat websocket.
 */
data class KickEnvironment(
    val apiBaseUrl: String,
    val oauthBaseUrl: String,
    val chatSocketUrl: String,
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val scopes: List<String>
) {
    val normalizedApiBaseUrl: String = apiBaseUrl.trimEnd('/')
    val normalizedOauthBaseUrl: String = oauthBaseUrl.trimEnd('/')
    val normalizedChatSocketUrl: String = chatSocketUrl.trimEnd('/')

    fun requireClientId(): String = clientId.takeUnless { it.isBlank() }
        ?: error("KICK_CLIENT_ID environment variable is not configured")

    fun requireRedirectUri(): String = redirectUri.takeUnless { it.isBlank() }
        ?: error("KICK_REDIRECT_URI environment variable is not configured")

    fun scopeString(): String = scopes.joinToString(separator = " ")

    companion object {
        fun fromBuildConfig(): KickEnvironment {
            return KickEnvironment(
                apiBaseUrl = BuildConfig.KICK_API_BASE_URL.ifBlank { "https://api.kick.com/public/v1" },
                oauthBaseUrl = BuildConfig.KICK_OAUTH_BASE_URL.ifBlank { "https://id.kick.com" },
                chatSocketUrl = BuildConfig.KICK_CHAT_SOCKET_URL.ifBlank { "wss://ws.kick.com/v2" },
                clientId = BuildConfig.KICK_CLIENT_ID,
                clientSecret = BuildConfig.KICK_CLIENT_SECRET,
                redirectUri = BuildConfig.KICK_REDIRECT_URI,
                scopes = BuildConfig.KICK_SCOPES.split(' ')
                    .mapNotNull { it.trim().takeIf(String::isNotBlank) }
            )
        }
    }
}
