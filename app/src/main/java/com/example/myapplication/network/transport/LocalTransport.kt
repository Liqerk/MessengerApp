package com.example.myapplication.transport

import com.example.myapplication.ChatItem
import com.example.myapplication.Message
import com.example.myapplication.Repository
import com.example.myapplication.User

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

    override suspend fun sendMessage(sender: String, receiver: String, text: String): TransportResult<Boolean> {
        return try {
            val success = Repository.sendMessage(sender, receiver, text)
            TransportResult.Success(success)
        } catch (e: Exception) {
            TransportResult.Error("Failed to send message", e)
        }
    }

    override suspend fun getMessages(user1: String, user2: String): TransportResult<List<Message>> {
        return try {
            val messages = Repository.getMessages(user1, user2)
            TransportResult.Success(messages)
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
            val chats = Repository.getChatList(currentUser)
            TransportResult.Success(chats)
        } catch (e: Exception) {
            TransportResult.Error("Failed to load chats", e)
        }
    }

    override suspend fun searchUsers(query: String, currentUser: String): TransportResult<List<User>> {
        return try {
            val users = Repository.searchUsers(query, currentUser)
            TransportResult.Success(users)
        } catch (e: Exception) {
            TransportResult.Error("Search failed", e)
        }
    }

    override fun onMessageReceived(callback: (Message) -> Unit) {
        // Локально — нет реалтайма
    }

    override fun connect() {}
    override fun disconnect() {}
    override fun isConnected() = true // Локально всегда "подключен"
}