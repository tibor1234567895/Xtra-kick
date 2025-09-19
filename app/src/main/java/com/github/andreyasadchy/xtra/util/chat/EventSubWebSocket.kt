package com.github.andreyasadchy.xtra.util.chat

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.Timer
import kotlin.concurrent.schedule

class EventSubWebSocket(
    private val client: OkHttpClient,
    private val onConnect: () -> Unit,
    private val onWelcomeMessage: (String) -> Unit,
    private val onChatMessage: (JSONObject, String?) -> Unit,
    private val onUserNotice: (JSONObject, String?) -> Unit,
    private val onClearChat: (JSONObject, String?) -> Unit,
    private val onRoomState: (JSONObject, String?) -> Unit,
) {
    private var socket: WebSocket? = null
    private var pongTimer: Timer? = null
    var isActive = true
    private var timeout = 10000L
    private var usingReconnectUrl = false
    private val handledMessageIds = mutableListOf<String>()

    fun connect(reconnectUrl: String? = null) {
        socket = client.newWebSocket(
            Request.Builder().url(reconnectUrl ?: "wss://eventsub.kick.com/ws").build(),
            EventSubWebSocketListener()
        )
    }

    fun disconnect() {
        pongTimer?.cancel()
        socket?.close(1000, null)
        isActive = false
    }

    private fun reconnect(reconnectUrl: String? = null) {
        socket?.close(1000, null)
        connect(reconnectUrl)
    }

    private fun startPongTimer() {
        pongTimer = Timer().apply {
            schedule(timeout) {
                reconnect()
            }
        }
    }

    private inner class EventSubWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            onConnect()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = if (text.isNotBlank()) JSONObject(text) else null
                json?.let {
                    val metadata = json.optJSONObject("metadata")
                    val messageId = if (metadata?.isNull("message_id") == false) metadata.optString("message_id").takeIf { it.isNotBlank() } else null
                    val timestamp = if (metadata?.isNull("message_timestamp") == false) metadata.optString("message_timestamp").takeIf { it.isNotBlank() } else null
                    if (!messageId.isNullOrBlank()) {
                        if (handledMessageIds.contains(messageId)) {
                            return
                        } else {
                            if (handledMessageIds.size > 200) {
                                handledMessageIds.removeAt(0)
                            }
                            handledMessageIds.add(messageId)
                        }
                    }
                    when (metadata?.optString("message_type")) {
                        "notification" -> {
                            pongTimer?.cancel()
                            startPongTimer()
                            val payload = json.optJSONObject("payload")
                            val event = payload?.optJSONObject("event")
                            if (event != null) {
                                when (metadata.optString("subscription_type")) {
                                    "channel.chat.message" -> onChatMessage(event, timestamp)
                                    "channel.chat.notification" -> onUserNotice(event, timestamp)
                                    "channel.chat.clear" -> onClearChat(event, timestamp)
                                    "channel.chat_settings.update" -> onRoomState(event, timestamp)
                                }
                            }
                        }
                        "session_keepalive" -> {
                            pongTimer?.cancel()
                            startPongTimer()
                        }
                        "session_reconnect" -> {
                            val payload = json.optJSONObject("payload")
                            val session = payload?.optJSONObject("session")
                            val reconnectUrl = if (session?.isNull("reconnect_url") == false) session.optString("reconnect_url").takeIf { it.isNotBlank() } else null
                            usingReconnectUrl = reconnectUrl != null
                            pongTimer?.cancel()
                            reconnect(reconnectUrl)
                        }
                        "session_welcome" -> {
                            val payload = json.optJSONObject("payload")
                            val session = payload?.optJSONObject("session")
                            if (session?.isNull("keepalive_timeout_seconds") == false) {
                                session.optInt("keepalive_timeout_seconds").takeIf { it > 0 }?.let { timeout = it * 1000L }
                            }
                            pongTimer?.cancel()
                            startPongTimer()
                            if (!usingReconnectUrl) {
                                val sessionId = session?.optString("id")
                                if (!sessionId.isNullOrBlank()) {
                                    onWelcomeMessage(sessionId)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}
