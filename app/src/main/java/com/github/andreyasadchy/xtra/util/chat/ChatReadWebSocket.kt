package com.github.andreyasadchy.xtra.util.chat

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Random
import java.util.Timer
import kotlin.concurrent.schedule

class ChatReadWebSocket(
    private val loggedIn: Boolean,
    channelName: String,
    private val client: OkHttpClient,
    private val onConnect: (() -> Unit)? = null,
    private val onDisconnect: ((String, String) -> Unit)? = null,
    private val onChatMessage: ((String, Boolean) -> Unit)? = null,
    private val onClearMessage: ((String) -> Unit)? = null,
    private val onClearChat: ((String) -> Unit)? = null,
    private val onNotice: ((String) -> Unit)? = null,
    private val onRoomState: ((String) -> Unit)? = null,
    private val webSocketListener: WebSocketListener? = null,
) {
    val hashChannelName: String = "#$channelName"
    private var socket: WebSocket? = null
    internal var pingTimer: Timer? = null
    internal var pongTimer: Timer? = null
    var isActive = true

    fun connect() {
        socket = client.newWebSocket(
            Request.Builder().url("wss://irc.chat.kick.com").build(),
            webSocketListener ?: ChatReadWebSocketListener()
        )
    }

    fun disconnect() {
        pingTimer?.cancel()
        pongTimer?.cancel()
        socket?.close(1000, null)
        isActive = false
    }

    fun reconnect() {
        socket?.close(1000, null)
        connect()
    }

    internal fun startPingTimer() {
        pingTimer = Timer().apply {
            schedule(270000) {
                write("PING")
                startPongTimer()
            }
        }
    }

    private fun startPongTimer() {
        pongTimer = Timer().apply {
            schedule(10000) {
                reconnect()
            }
        }
    }

    fun write(message: String) {
        socket?.send(message + System.lineSeparator())
    }

    private inner class ChatReadWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            write("CAP REQ :kick.com/tags kick.com/commands")
            write("NICK justinfan${Random().nextInt(((9999 - 1000) + 1)) + 1000}") //random number between 1000 and 9999
            write("JOIN $hashChannelName")
            onConnect?.invoke()
            pingTimer?.cancel()
            pongTimer?.cancel()
            startPingTimer()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            text.removeSuffix("\r\n").split("\r\n").forEach {
                it.run {
                    when {
                        contains("PRIVMSG") -> onChatMessage?.invoke(this, false)
                        contains("USERNOTICE") -> onChatMessage?.invoke(this, true)
                        contains("CLEARMSG") -> onClearMessage?.invoke(this)
                        contains("CLEARCHAT") -> onClearChat?.invoke(this)
                        contains("NOTICE") -> {
                            if (!loggedIn) {
                                onNotice?.invoke(this)
                            }
                        }
                        contains("ROOMSTATE") -> onRoomState?.invoke(this)
                        startsWith("PING") -> write("PONG")
                        startsWith("PONG") -> {
                            pingTimer?.cancel()
                            pongTimer?.cancel()
                            startPingTimer()
                        }
                        startsWith("RECONNECT") -> {
                            pingTimer?.cancel()
                            pongTimer?.cancel()
                            reconnect()
                        }
                    }
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onDisconnect?.invoke(t.toString(), t.stackTraceToString())
        }
    }
}
