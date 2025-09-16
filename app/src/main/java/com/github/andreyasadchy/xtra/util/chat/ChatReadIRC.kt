package com.github.andreyasadchy.xtra.util.chat

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.Random
import javax.net.ssl.SSLSocketFactory

private const val TAG = "ChatReadIRC"

class ChatReadIRC(
    private val useSSL: Boolean,
    private val loggedIn: Boolean,
    channelName: String,
    private val onConnect: () -> Unit,
    private val onDisconnect: (String, String) -> Unit,
    private val onChatMessage: (String, Boolean) -> Unit,
    private val onClearMessage: (String) -> Unit,
    private val onClearChat: (String) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onRoomState: (String) -> Unit,
) : Thread() {
    private var socketIn: Socket? = null
    private lateinit var readerIn: BufferedReader
    private lateinit var writerIn: BufferedWriter
    private val hashChannelName: String = "#$channelName"
    var isActive = true

    override fun run() {

        fun handlePing(writer: BufferedWriter) {
            write("PONG :tmi.twitch.tv", writer)
            writer.flush()
        }

        do {
            try {
                connect()
                while (true) {
                    val messageIn = readerIn.readLine()!!
                    messageIn.run {
                        when {
                            contains("PRIVMSG") -> onChatMessage(this, false)
                            contains("USERNOTICE") -> onChatMessage(this, true)
                            contains("CLEARMSG") -> onClearMessage(this)
                            contains("CLEARCHAT") -> onClearChat(this)
                            contains("NOTICE") -> {
                                if (!loggedIn) {
                                    onNotice(this)
                                }
                            }
                            contains("ROOMSTATE") -> onRoomState(this)
                            startsWith("PING") -> handlePing(writerIn)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Disconnecting from $hashChannelName")
                if (e.message != "Socket closed" && e.message != "socket is closed" && e.message != "Connection reset" && e.message != "recvfrom failed: ECONNRESET (Connection reset by peer)") {
                    onDisconnect(e.toString(), e.stackTraceToString())
                }
                close()
                sleep(1000L)
            } catch (e: Exception) {
                close()
                sleep(1000L)
            }
        } while (isActive)
    }

    private fun connect() {
        Log.d(TAG, "Connecting to Kick IRC - SSL $useSSL")
        try {
            socketIn = if (useSSL) {
                SSLSocketFactory.getDefault().createSocket("irc.twitch.tv", 6697)
            } else {
                Socket("irc.twitch.tv", 6667)
            }.apply {
                readerIn = BufferedReader(InputStreamReader(getInputStream()))
                writerIn = BufferedWriter(OutputStreamWriter(getOutputStream()))
            }
            write("NICK justinfan${Random().nextInt(((9999 - 1000) + 1)) + 1000}", writerIn) //random number between 1000 and 9999
            write("CAP REQ :twitch.tv/tags twitch.tv/commands", writerIn)
            write("JOIN $hashChannelName", writerIn)
            writerIn.flush()
            Log.d(TAG, "Successfully connected to - $hashChannelName")
            onConnect()
        } catch (e: IOException) {
            Log.e(TAG, "Error connecting to Kick IRC", e)
            throw e
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        close()
        isActive = false
    }

    private fun close() {
        try {
            socketIn?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error while closing socketIn", e)
        }
    }

    @Throws(IOException::class)
    private fun write(message: String, vararg writers: BufferedWriter?) {
        writers.forEach { it?.write(message + System.lineSeparator()) }
    }
}
