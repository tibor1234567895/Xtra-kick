package com.github.andreyasadchy.xtra.kick.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

@Serializable
data class KickChatEnvelope(
    val event: String,
    val topic: String,
    val payload: JsonObject = buildJsonObject { },
    val ref: String? = null,
    @SerialName("join_ref") val joinRef: String? = null
) {
    companion object {
        fun joinChannel(channelId: Long, authToken: String? = null, clientId: String? = null): KickChatEnvelope {
            return KickChatEnvelope(
                event = "phx_join",
                topic = "chatrooms:$channelId",
                payload = buildJsonObject {
                    authToken?.let { put("token", it) }
                    clientId?.let { put("client_id", it) }
                },
                ref = UUID.randomUUID().toString()
            )
        }
    }
}
