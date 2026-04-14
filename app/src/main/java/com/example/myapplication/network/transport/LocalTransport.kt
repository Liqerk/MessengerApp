package com.example.myapplication.transport

import com.example.myapplication.ChatItem
import com.example.myapplication.Message
import com.example.myapplication.Repository
import com.example.myapplication.User
import java.io.File

class LocalTransport : MessageTransport {

    override suspend fun register(login: String, mail: String, password: String): TransportResult<String> {
        return try {
            if (Repository.register(login, mail, password)) {
                TransportResult.Success("ok")
            } else {
                TransportResult.Error("User already exists")
            }
        } catch (e: Exception) {
            TransportResult.Error("Registration failed", e)
        }
    }
    override suspend fun sendMediaMessage(
        sender: String,
        receiver: String,
        text: String,
        type: String,
        mediaUrl: String,
        duration: Int
    ): TransportResult<Int> {
        return try {
            Repository.ensureUserExists(receiver)

            val messageId = when (type) {
                "image" -> Repository.sendImageMessage(sender, receiver, mediaUrl, isSent = true)
                "audio" -> Repository.sendAudioMessage(sender, receiver, mediaUrl, duration, isSent = true)
                else -> Repository.sendMessage(sender, receiver, text, isSent = true)
            }

            if (messageId > 0) {
                TransportResult.Success(messageId)
            } else {
                TransportResult.Error("Failed to save media message")
            }
        } catch (e: Exception) {
            TransportResult.Error("Failed to send media message", e)
        }
    }
    override suspend fun login(login: String, password: String): TransportResult<String> {
        return try {
            if (Repository.login(login, password)) {
                TransportResult.Success("ok")
            } else {
                TransportResult.Error("Wrong credentials")
            }
        } catch (e: Exception) {
            TransportResult.Error("Login failed", e)
        }
    }

    override suspend fun sendMessage(sender: String, receiver: String, text: String): TransportResult<Int> {
        return TransportResult.Success(1)
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

    override suspend fun uploadFile(file: File): TransportResult<String> {
        return TransportResult.Success(file.absolutePath)
    }
    override fun onMessageReceived(callback: (Message) -> Unit) {}
    override fun connect() {}
    override fun disconnect() {}
    override fun isConnected() = true
}