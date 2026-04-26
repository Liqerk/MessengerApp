package com.example.myapplication

import android.util.Log
import com.example.myapplication.transport.TransportManager
import com.example.myapplication.transport.TransportMode
import com.example.myapplication.transport.TransportResult
import kotlinx.coroutines.*
import java.util.UUID

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
        val mediaUrl: String? = null,
        val duration: Int = 0,
        val clientMessageId: String // ✅ ДОБАВЛЕНО
    )

    var onMessageSent: ((messageId: Int) -> Unit)? = null
    var onMessageFailed: ((messageId: Int) -> Unit)? = null

    // ✅ enqueue теперь принимает clientMessageId
    fun enqueue(
        messageId: Int,
        sender: String,
        receiver: String,
        text: String,
        type: String = "text",
        mediaUrl: String? = null,
        duration: Int = 0,
        clientMessageId: String
    ) {
        synchronized(pendingMessages) {
            if (pendingMessages.none { it.messageId == messageId }) {
                pendingMessages.add(
                    PendingMessage(
                        messageId = messageId,
                        sender = sender,
                        receiver = receiver,
                        text = text,
                        type = type,
                        mediaUrl = mediaUrl,
                        duration = duration,
                        clientMessageId = clientMessageId
                    )
                )
                Log.d(TAG, "📤 Message $messageId queued with CID=$clientMessageId")
            }
        }
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
                    val result = if (msg.type == "text") {
                        // ✅ Передаём clientMessageId!
                        TransportManager.get().sendMessage(
                            sender = msg.sender,
                            receiver = msg.receiver,
                            text = msg.text,
                            clientMessageId = msg.clientMessageId
                        )
                    } else {
                        // ✅ Обрабатываем медиа с clientMessageId
                        TransportManager.get().sendMediaMessage(
                            sender = msg.sender,
                            receiver = msg.receiver,
                            text = msg.text,
                            type = msg.type,
                            mediaUrl = msg.mediaUrl ?: "",
                            duration = msg.duration,
                            clientMessageId = msg.clientMessageId
                        )
                    }

                    when (result) {
                        is TransportResult.Success -> {
                            Repository.markMessageAsSent(msg.messageId)
                            synchronized(pendingMessages) {
                                pendingMessages.removeAll { it.messageId == msg.messageId }
                            }
                            onMessageSent?.invoke(msg.messageId)
                            Log.d(TAG, "✅ Sent ${msg.messageId} (CID=${msg.clientMessageId})")
                        }

                        is TransportResult.Error -> {
                            onMessageFailed?.invoke(msg.messageId)
                            Log.e(TAG, "❌ Failed ${msg.messageId}: ${result.message}")
                        }
                    }

                    delay(100) // Небольшая задержка между сообщениями
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
                    // ✅ Берём clientMessageId из БД
                    val clientId = msg.clientMessageId.ifBlank {
                        // Fallback: если в старой БД нет ID, генерируем и обновляем
                        val newId = UUID.randomUUID().toString()
                        Log.w(TAG, "⚠️ Message ${msg.id} has no clientMessageId, generating: $newId")
                        // TODO: Добавить в Repository метод updateClientMessageId
                        newId
                    }

                    pendingMessages.add(
                        PendingMessage(
                            messageId = msg.id,
                            sender = msg.sender,
                            receiver = msg.receiver,
                            text = msg.text,
                            type = msg.type,
                            mediaUrl = msg.mediaUrl,
                            duration = msg.duration,
                            clientMessageId = clientId
                        )
                    )
                }
            }

            if (unsent.isNotEmpty()) {
                Log.d(TAG, "📦 Loaded ${unsent.size} unsent messages")
                // ✅ Сразу пробуем отправить
                processPending()
            }
        }
    }

    fun getPendingCount(): Int = synchronized(pendingMessages) { pendingMessages.size }

    // ✅ Вызывать при успешном подключении WS
    fun onConnected() {
        Log.d(TAG, "🔌 Connected, processing pending...")
        processPending()
    }
}