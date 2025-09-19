package com.github.andreyasadchy.xtra.util.chat

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.Timer
import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate

class PubSubWebSocket(
    private val channelId: String,
    private val userId: String?,
    private val gqlToken: String?,
    private val collectPoints: Boolean,
    private val notifyPoints: Boolean,
    private val showRaids: Boolean,
    private val showPolls: Boolean,
    private val showPredictions: Boolean,
    private val client: OkHttpClient,
    private val onPlaybackMessage: (JSONObject) -> Unit,
    private val onStreamInfo: (JSONObject) -> Unit,
    private val onRewardMessage: (JSONObject) -> Unit,
    private val onPointsEarned: (JSONObject) -> Unit,
    private val onClaimAvailable: () -> Unit,
    private val onMinuteWatched: () -> Unit,
    private val onRaidUpdate: (JSONObject, Boolean) -> Unit,
    private val onPollUpdate: (JSONObject) -> Unit,
    private val onPredictionUpdate: (JSONObject) -> Unit,
) {
    private var socket: WebSocket? = null
    private var pingTimer: Timer? = null
    private var pongTimer: Timer? = null
    private var minuteWatchedTimer: Timer? = null

    fun connect() {
        socket = client.newWebSocket(
            Request.Builder().url("wss://pubsub.kick.com").build(),
            PubSubWebSocketListener()
        )
    }

    fun disconnect() {
        pingTimer?.cancel()
        pongTimer?.cancel()
        minuteWatchedTimer?.cancel()
        minuteWatchedTimer = null
        socket?.close(1000, null)
    }

    private fun reconnect() {
        socket?.close(1000, null)
        connect()
    }

    private fun listen() {
        val message = JSONObject().apply {
            put("type", "LISTEN")
            put("data", JSONObject().apply {
                put("topics", JSONArray().apply {
                    put("video-playback-by-id.$channelId")
                    put("broadcast-settings-update.$channelId")
                    put("community-points-channel-v1.$channelId")
                    if (showRaids) {
                        put("raid.$channelId")
                    }
                    if (showPolls) {
                        put("polls.$channelId")
                    }
                    if (showPredictions) {
                        put("predictions-channel-v1.$channelId")
                    }
                    if (!userId.isNullOrBlank() && !gqlToken.isNullOrBlank()) {
                        if (collectPoints) {
                            put("community-points-user-v1.$userId")
                        }
                    }
                })
                if (!userId.isNullOrBlank() && !gqlToken.isNullOrBlank() && collectPoints) {
                    put("auth_token", gqlToken)
                }
            })
        }.toString()
        socket?.send(message)
    }

    private fun startPingTimer() {
        pingTimer = Timer().apply {
            schedule(270000) {
                val ping = JSONObject().apply { put("type", "PING") }.toString()
                socket?.send(ping)
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

    private fun startMinuteWatchedTimer() {
        minuteWatchedTimer = Timer().apply {
            scheduleAtFixedRate(60000, 60000) {
                onMinuteWatched()
            }
        }
    }

    private inner class PubSubWebSocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            listen()
            pingTimer?.cancel()
            pongTimer?.cancel()
            startPingTimer()
            if (collectPoints && !userId.isNullOrBlank() && !gqlToken.isNullOrBlank() && minuteWatchedTimer == null) {
                startMinuteWatchedTimer()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val json = if (text.isNotBlank()) JSONObject(text) else null
                when (json?.optString("type")) {
                    "MESSAGE" -> {
                        val data = json.optJSONObject("data")
                        val topic = data?.optString("topic")
                        val message = data?.optString("message")?.let { if (it.isNotBlank()) JSONObject(it) else null }
                        val messageType = message?.optString("type")
                        if (topic != null && messageType != null) {
                            when {
                                topic.startsWith("video-playback-by-id") -> onPlaybackMessage(message)
                                topic.startsWith("broadcast-settings-update") && messageType.startsWith("broadcast_settings_update") -> onStreamInfo(message)
                                topic.startsWith("community-points-channel") && messageType.startsWith("reward-redeemed") -> onRewardMessage(message)
                                topic.startsWith("community-points-user") -> {
                                    when {
                                        messageType.startsWith("points-earned") && notifyPoints -> {
                                            val messageData = message.optJSONObject("data")
                                            val messageChannelId = messageData?.optString("channel_id")
                                            if (channelId == messageChannelId) {
                                                onPointsEarned(message)
                                            }
                                        }
                                        messageType.startsWith("claim-available") && collectPoints -> onClaimAvailable()
                                    }
                                }
                                topic.startsWith("raid") && showRaids -> {
                                    when {
                                        messageType.startsWith("raid_update") -> onRaidUpdate(message, false)
                                        messageType.startsWith("raid_go") -> onRaidUpdate(message, true)
                                    }
                                }
                                topic.startsWith("polls") && showPolls -> onPollUpdate(message)
                                topic.startsWith("predictions-channel") && showPredictions -> onPredictionUpdate(message)
                            }
                        }
                    }
                    "PONG" -> {
                        pingTimer?.cancel()
                        pongTimer?.cancel()
                        startPingTimer()
                    }
                    "RECONNECT" -> {
                        pingTimer?.cancel()
                        pongTimer?.cancel()
                        reconnect()
                    }
                }
            } catch (e: Exception) {

            }
        }
    }
}
