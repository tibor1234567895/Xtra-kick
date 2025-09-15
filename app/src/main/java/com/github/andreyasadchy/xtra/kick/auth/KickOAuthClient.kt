package com.github.andreyasadchy.xtra.kick.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import com.github.andreyasadchy.xtra.kick.config.KickEnvironment
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.security.MessageDigest
import java.util.UUID
import okio.ByteString.Companion.toByteString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KickOAuthClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val environment: KickEnvironment
) {

    suspend fun buildAuthorizationUrl(state: String, codeChallenge: String, scopes: List<String> = environment.scopes): HttpUrl {
        val base = "${environment.normalizedOauthBaseUrl}/oauth/authorize".toHttpUrl()
        val builder = base.newBuilder()
            .addQueryParameter("response_type", "code")
            .addQueryParameter("client_id", environment.requireClientId())
            .addQueryParameter("redirect_uri", environment.requireRedirectUri())
            .addQueryParameter("scope", scopes.joinToString(" "))
            .addQueryParameter("code_challenge", codeChallenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("state", state)
        return builder.build()
    }

    suspend fun exchangeAuthorizationCode(code: String, codeVerifier: String): KickTokenResponse {
        return requestToken(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", environment.requireClientId())
                .add("client_secret", environment.clientSecret)
                .add("redirect_uri", environment.requireRedirectUri())
                .add("code", code)
                .add("code_verifier", codeVerifier)
                .build()
        )
    }

    suspend fun refreshToken(refreshToken: String): KickTokenResponse {
        return requestToken(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", environment.requireClientId())
                .add("client_secret", environment.clientSecret)
                .add("refresh_token", refreshToken)
                .build()
        )
    }

    suspend fun generateAppAccessToken(): KickTokenResponse {
        return requestToken(
            FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", environment.requireClientId())
                .add("client_secret", environment.clientSecret)
                .build()
        )
    }

    suspend fun revokeToken(token: String, tokenTypeHint: String? = null) {
        withContext(Dispatchers.IO) {
            val urlBuilder = "${environment.normalizedOauthBaseUrl}/oauth/revoke".toHttpUrl().newBuilder()
                .addQueryParameter("token", token)
            tokenTypeHint?.let { urlBuilder.addQueryParameter("token_type_hint", it) }
            val request = Request.Builder()
                .url(urlBuilder.build())
                .post(FormBody.Builder().build())
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OAuthException("Failed to revoke token", response)
                }
            }
        }
    }

    suspend fun introspect(accessToken: String): KickTokenIntrospection? {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${environment.normalizedOauthBaseUrl}/token/introspect")
                .post(FormBody.Builder().build())
                .header("Authorization", "Bearer $accessToken")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OAuthException("Unable to introspect token", response)
                }
                val body = response.body?.string() ?: throw IOException("Missing introspection body")
                json.decodeFromString<KickTokenIntrospectionEnvelope>(body).data
            }
        }
    }

    suspend fun requestAppAccessToken(): KickTokenResponse = generateAppAccessToken()

    private suspend fun requestToken(body: RequestBody): KickTokenResponse {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${environment.normalizedOauthBaseUrl}/oauth/token")
                .post(body)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw OAuthException("Token request failed", response)
                }
                val payload = response.body?.string() ?: throw IOException("Missing token body")
                json.decodeFromString(payload)
            }
        }
    }

    companion object {
        fun generateCodeVerifier(): String {
            val random = UUID.randomUUID().toString().replace("-", "")
            return random + random.take(32)
        }

        fun generateCodeChallenge(codeVerifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
            return digest.toByteString().base64Url().trimEnd('=')
        }
    }

    class OAuthException(message: String, response: Response) : IOException(
        "$message (HTTP ${response.code})"
    )
}
