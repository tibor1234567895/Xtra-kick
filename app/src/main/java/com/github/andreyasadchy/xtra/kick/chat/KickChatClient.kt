package com.github.andreyasadchy.xtra.kick.chat

import com.github.andreyasadchy.xtra.kick.config.KickEnvironment
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KickChatClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val environment: KickEnvironment
) {

    interface Listener {
        fun onConnected()
        fun onMessage(message: KickChatEnvelope)
        fun onClosed(code: Int, reason: String)
        fun onError(throwable: Throwable)
    }

    private val lock = Any()
    private var webSocket: WebSocket? = null
    private var listener: Listener? = null

    fun connect(channelId: Long, authToken: String?, listener: Listener) {
        synchronized(lock) {
            disconnect()
            this.listener = listener
            val requestBuilder = Request.Builder().url(environment.normalizedChatSocketUrl)
            val clientId = environment.clientId
            if (clientId.isNotBlank()) {
                requestBuilder.header("Client-Id", clientId)
            }
            webSocket = okHttpClient.newWebSocket(requestBuilder.build(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    this@KickChatClient.listener?.onConnected()
                    val joinEnvelope = KickChatEnvelope.joinChannel(
                        channelId = channelId,
                        authToken = authToken,
                        clientId = clientId.takeIf { it.isNotBlank() }
                    )
                    webSocket.send(json.encodeToString(joinEnvelope))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString<KickChatEnvelope>(text) }
                        .onSuccess { parsed -> this@KickChatClient.listener?.onMessage(parsed) }
                        .onFailure { error -> this@KickChatClient.listener?.onError(error) }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    this@KickChatClient.listener?.onClosed(code, reason)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    this@KickChatClient.listener?.onError(t)
                }
            })
        }
    }

    fun send(envelope: KickChatEnvelope): Boolean {
        return synchronized(lock) {
            val activeSocket = webSocket ?: return false
            activeSocket.send(json.encodeToString(envelope))
        }
    }

    fun disconnect(code: Int = NORMAL_CLOSURE_STATUS, reason: String? = null) {
        synchronized(lock) {
            webSocket?.close(code, reason)
            webSocket = null
            listener = null
        }
    }

    companion object {
        private const val NORMAL_CLOSURE_STATUS = 1000
    }
}
