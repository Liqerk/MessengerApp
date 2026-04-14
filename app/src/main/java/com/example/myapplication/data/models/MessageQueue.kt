package com.example.myapplication

import android.util.Log
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.*

object MessageQueue {

    private const val TAG = "MessageQueue"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val pendingMessages = mutableListOf<PendingMessage>()

    data class PendingMessage(
        val messageId: Int,
        val sender: String,
        val receiver: String,
        val text: String,
        val type: String = "text",
        val mediaUrl: String? = null
    )

    var onMessageSent: ((messageId: Int) -> Unit)? = null
    var onMessageFailed: ((messageId: Int) -> Unit)? = null

    fun enqueue(messageId: Int, sender: String, receiver: String, text: String, type: String = "text", mediaUrl: String? = null) {
        synchronized(pendingMessages) {
            if (pendingMessages.none { it.messageId == messageId }) {
                pendingMessages.add(
                    PendingMessage(
                        messageId = messageId,
                        sender = sender,
                        receiver = receiver,
                        text = text,
                        type = type,
                        mediaUrl = mediaUrl
                    )
                )
            }
        }
        Log.d(TAG, "📤 Message $messageId queued")
    }

    fun processPending() {
        if (TransportManager.getMode() != TransportMode.SERVER) {
            Log.d(TAG, "⏸️ Not in SERVER mode")
            return
        }

        scope.launch {
            val toSend = synchronized(pendingMessages) { pendingMessages.toList() }

            if (toSend.isEmpty()) {
                Log.d(TAG, "✅ No pending messages")
                return@launch
            }

            Log.d(TAG, "📤 Processing ${toSend.size} pending messages")

            for (msg in toSend) {
                try {
                    val result = TransportManager.get().sendMessage(
                        sender = msg.sender,
                        receiver = msg.receiver,
                        text = msg.text
                    )

                    when (result) {
                        is TransportResult.Success -> {
                            Repository.markMessageAsSent(msg.messageId)
                            synchronized(pendingMessages) {
                                pendingMessages.removeAll { it.messageId == msg.messageId }
                            }
                            onMessageSent?.invoke(msg.messageId)
                            Log.d(TAG, "✅ Sent ${msg.messageId}")
                        }

                        is TransportResult.Error -> {
                            onMessageFailed?.invoke(msg.messageId)
                            Log.e(TAG, "❌ Failed ${msg.messageId}: ${result.message}")
                        }
                    }

                    delay(200)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error sending ${msg.messageId}", e)
                    onMessageFailed?.invoke(msg.messageId)
                }
            }
        }
    }

    fun loadPendingFromDb(currentUser: String) {
        scope.launch {
            val unsent = Repository.getUnsentMessages(currentUser)

            synchronized(pendingMessages) {
                pendingMessages.clear()
                unsent.forEach { msg ->
                    pendingMessages.add(
                        PendingMessage(
                            messageId = msg.id,
                            sender = msg.sender,
                            receiver = msg.receiver,
                            text = msg.text,
                            type = msg.type,
                            mediaUrl = msg.mediaUrl
                        )
                    )
                }
            }

            if (unsent.isNotEmpty()) {
                Log.d(TAG, "📦 Loaded ${unsent.size} unsent messages")
            }
        }
    }

    fun getPendingCount(): Int = synchronized(pendingMessages) { pendingMessages.size }
}