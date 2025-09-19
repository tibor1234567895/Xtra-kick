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
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocketFactory

private const val TAG = "ChatWriteIRC"

class ChatWriteIRC(
    private val useSSL: Boolean,
    private val userLogin: String?,
    private val userToken: String?,
    channelName: String,
    private val onSendMessageError: (String, String) -> Unit,
    private val onNotice: (String) -> Unit,
    private val onUserState: (String) -> Unit,
) : Thread() {
    private var socketOut: Socket? = null
    private lateinit var readerOut: BufferedReader
    private lateinit var writerOut: BufferedWriter
    private val hashChannelName: String = "#$channelName"
    private val messageSenderExecutor: Executor = Executors.newSingleThreadExecutor()
    private var isActive = true

    override fun run() {

        fun handlePing(writer: BufferedWriter) {
            write("PONG :chat.kick.com", writer)
            writer.flush()
        }

        do {
            try {
                connect()
                while (true) {
                    val messageOut = readerOut.readLine()!!
                    messageOut.run {
                        when {
                            contains("PRIVMSG") -> {}
                            contains("USERNOTICE") -> {}
                            contains("CLEARMSG") -> {}
                            contains("CLEARCHAT") -> {}
                            contains("NOTICE") -> onNotice(this)
                            contains("ROOMSTATE") -> {}
                            contains("USERSTATE") -> onUserState(this)
                            startsWith("PING") -> handlePing(writerOut)
                        }
                    }
                }
            } catch (e: IOException) {
                Log.d(TAG, "Disconnecting from $hashChannelName")
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
            socketOut = if (useSSL) {
                SSLSocketFactory.getDefault().createSocket("irc.kick.com", 6697)
            } else {
                Socket("irc.kick.com", 6667)
            }.apply {
                readerOut = BufferedReader(InputStreamReader(getInputStream()))
                writerOut = BufferedWriter(OutputStreamWriter(getOutputStream()))
                write("PASS oauth:$userToken", writerOut)
                write("NICK $userLogin", writerOut)
            }
            write("CAP REQ :kick.com/tags kick.com/commands", writerOut)
            write("JOIN $hashChannelName", writerOut)
            writerOut.flush()
            Log.d(TAG, "Successfully connected to - $hashChannelName")
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
            socketOut?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error while closing socketOut", e)
        }
    }

    @Throws(IOException::class)
    private fun write(message: String, vararg writers: BufferedWriter?) {
        writers.forEach { it?.write(message + System.lineSeparator()) }
    }

    fun send(message: CharSequence, replyId: String?) {
        messageSenderExecutor.execute {
            try {
                val reply = replyId?.let { "@reply-parent-msg-id=${it} " } ?: ""
                write("${reply}PRIVMSG $hashChannelName :$message", writerOut)
                writerOut.flush()
                Log.d(TAG, "Sent message to $hashChannelName: $message")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message", e)
                onSendMessageError(e.toString(), e.stackTraceToString())
            }
        }
    }
}
