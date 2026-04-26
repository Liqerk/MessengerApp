package com.example.myapplication.transport

import com.example.myapplication.ChatItem
import com.example.myapplication.Message
import com.example.myapplication.Repository
import com.example.myapplication.User
import java.io.File

class LocalTransport : MessageTransport {

    private val messageCallbacks = mutableListOf<(Message) -> Unit>()
    private var connected = true

    override fun onMessageReceived(callback: (Message) -> Unit) {
        messageCallbacks.add(callback)
    }

    override fun removeMessageCallback(callback: (Message) -> Unit) {
        messageCallbacks.remove(callback)
    }

    override suspend fun register(
        login: String,
        mail: String,
        password: String
    ): TransportResult<String> {
        return if (Repository.register(login, mail, password)) {
            TransportResult.Success("local-token")
        } else {
            TransportResult.Error("Registration failed")
        }
    }

    override suspend fun login(login: String, password: String): TransportResult<String> {
        return if (Repository.login(login, password)) {
            TransportResult.Success("local-token")
        } else {
            TransportResult.Error("Invalid credentials")
        }
    }

    override suspend fun sendMessage(
        sender: String,
        receiver: String,
        text: String,
        clientMessageId: String
    ): TransportResult<Int> {
        return try {
            val localId = Repository.sendMessage(
                sender = sender,
                receiver = receiver,
                text = text,
                isSent = true,
                clientMessageId = clientMessageId
            )

            if (localId > 0) {
                val msg = Message(
                    id = localId,
                    clientMessageId = clientMessageId,
                    sender = sender,
                    receiver = receiver,
                    text = text,
                    isSent = true,
                    isSynced = true
                )

                messageCallbacks.forEach { it.invoke(msg) }
                TransportResult.Success(localId)
            } else {
                TransportResult.Error("Local send failed")
            }
        } catch (e: Exception) {
            TransportResult.Error("Local send failed: ${e.message}", e)
        }
    }
    override suspend fun deleteMessage(messageId: Int, clientMessageId: String): TransportResult<Boolean>{
        return try {
            val deleted = Repository.deleteMessage(messageId)
            TransportResult.Success(deleted)
        } catch (e: Exception) {
            TransportResult.Error("Delete failed", e)
        }
    }

    override suspend fun deleteChat(user1: String, user2: String): TransportResult<Boolean> {
        return try {
            Repository.deleteChat(user1, user2)
            TransportResult.Success(true)
        } catch (e: Exception) {
            TransportResult.Error("Delete chat failed", e)
        }
    }
    override suspend fun sendMediaMessage(
        sender: String,
        receiver: String,
        text: String,
        type: String,
        mediaUrl: String,
        duration: Int,
        clientMessageId: String
    ): TransportResult<Int> {return try {
        val localId = when (type) {
            "image" -> Repository.sendImageMessage(
                sender = sender,
                receiver = receiver,
                imagePath = mediaUrl,
                isSent = true,
                clientMessageId = clientMessageId // ✅ Передаем ID
            )
            "audio" -> Repository.sendAudioMessage(
                sender = sender,
                receiver = receiver,
                audioPath = mediaUrl,
                duration = duration,
                isSent = true,
                clientMessageId = clientMessageId // ✅ Передаем ID
            )
            else -> Repository.sendMessage(
                sender = sender,
                receiver = receiver,
                text = text,
                isSent = true,
                clientMessageId = clientMessageId // ✅ Передаем ID
            )
            }

            if (localId > 0) {
                val msg = Message(
                    id = localId,
                    sender = sender,
                    receiver = receiver,
                    text = text,
                    type = type,
                    mediaUrl = mediaUrl,
                    duration = duration,
                    isSent = true,
                    isSynced = true
                )

                messageCallbacks.forEach { it.invoke(msg) }
                TransportResult.Success(localId)
            } else {
                TransportResult.Error("Local media send failed")
            }
        } catch (e: Exception) {
            TransportResult.Error("Local media send failed: ${e.message}", e)
        }
    }

    override suspend fun getMessages(user1: String, user2: String): TransportResult<List<Message>> {
        return try {
            TransportResult.Success(Repository.getMessages(user1, user2))
        } catch (e: Exception) {
            TransportResult.Error("Failed to load messages", e)
        }
    }

    override suspend fun markAsRead(currentUser: String, sender: String): TransportResult<Boolean> {
        return try {
            Repository.markAsRead(currentUser, sender)
            TransportResult.Success(true)
        } catch (e: Exception) {
            TransportResult.Error("Failed to mark as read", e)
        }
    }

    override suspend fun getChatList(currentUser: String): TransportResult<List<ChatItem>> {
        return try {
            TransportResult.Success(Repository.getChatList(currentUser))
        } catch (e: Exception) {
            TransportResult.Error("Failed to load chats", e)
        }
    }

    override suspend fun searchUsers(query: String, currentUser: String): TransportResult<List<User>> {
        return try {
            TransportResult.Success(Repository.searchUsers(query, currentUser))
        } catch (e: Exception) {
            TransportResult.Error("Search failed", e)
        }
    }

    override fun connect() {
        connected = true
    }

    override fun disconnect() {
        connected = false
    }

    override fun isConnected(): Boolean = connected

    override suspend fun uploadFile(file: File): TransportResult<String> {
        return if (file.exists()) {
            TransportResult.Success(file.absolutePath)
        } else {
            TransportResult.Error("File not found")
        }
    }
}