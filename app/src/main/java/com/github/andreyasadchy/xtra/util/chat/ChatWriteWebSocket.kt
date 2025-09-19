package com.github.andreyasadchy.xtra.util.chat

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Timer
import kotlin.concurrent.schedule

class ChatWriteWebSocket(
    private val userLogin: String?,
    private val userToken: String?,
    channelName: String,
    private val client: OkHttpClient,
    private val onNotice: (String) -> Unit,
    private val onUserState: (String) -> Unit,
) {
    private val hashChannelName: String = "#$channelName"
    private var socket: WebSocket? = null
    private var pingTimer: Timer? = null
    private var pongTimer: Timer? = null

    fun connect() {
        socket = client.newWebSocket(
            Request.Builder().url("wss://irc.chat.kick.com").build(),
            ChatWriteWebSocketListener()
        )
    }

    fun disconnect() {
        pingTimer?.cancel()
        pongTimer?.cancel()
        socket?.close(1000, null)
    }

    private fun reconnect() {
        socket?.close(1000, null)
        connect()
    }

    private fun startPingTimer() {
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

    fun send(message: CharSequence, replyId: String?) {
        val reply = replyId?.let { "@reply-parent-msg-id=${it} " } ?: ""
        write("${reply}PRIVMSG $hashChannelName :$message")
    }

    private fun write(message: String) {
        socket?.send(message + System.lineSeparator())
    }

    private inner class ChatWriteWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            write("CAP REQ :kick.com/tags kick.com/commands")
            write("PASS oauth:$userToken")
            write("NICK $userLogin")
            write("JOIN $hashChannelName")
            pingTimer?.cancel()
            pongTimer?.cancel()
            startPingTimer()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            text.removeSuffix("\r\n").split("\r\n").forEach {
                it.run {
                    when {
                        contains("PRIVMSG") -> {}
                        contains("USERNOTICE") -> {}
                        contains("CLEARMSG") -> {}
                        contains("CLEARCHAT") -> {}
                        contains("NOTICE") -> onNotice(this)
                        contains("ROOMSTATE") -> {}
                        contains("USERSTATE") -> onUserState(this)
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
    }
}
