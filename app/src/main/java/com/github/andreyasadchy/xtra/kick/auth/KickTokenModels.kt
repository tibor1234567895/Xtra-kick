package com.github.andreyasadchy.xtra.kick.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KickTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null
)

@Serializable
data class KickTokenIntrospection(
    val active: Boolean,
    @SerialName("client_id") val clientId: String? = null,
    val exp: Long? = null,
    val scope: String? = null,
    @SerialName("token_type") val tokenType: String? = null
)

@Serializable
data class KickTokenIntrospectionEnvelope(
    val data: KickTokenIntrospection? = null,
    val message: String? = null
)
