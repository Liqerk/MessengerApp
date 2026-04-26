package com.example.myapplication.transport

import com.example.myapplication.ChatItem
import com.example.myapplication.Message
import com.example.myapplication.User
import java.io.File

sealed class TransportResult<out T> {
    data class Success<T>(val data: T) : TransportResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : TransportResult<Nothing>()
}

interface MessageTransport {
    fun onMessageReceived(callback: (Message) -> Unit)
    fun removeMessageCallback(callback: (Message) -> Unit)

    suspend fun register(login: String, mail: String, password: String): TransportResult<String>
    suspend fun login(login: String, password: String): TransportResult<String>
    suspend fun sendMessage(sender: String, receiver: String, text: String, clientMessageId: String = ""): TransportResult<Int>
    suspend fun sendMediaMessage(sender: String, receiver: String, text: String, type: String, mediaUrl: String, duration: Int,clientMessageId: String = ""): TransportResult<Int>
    suspend fun getMessages(user1: String, user2: String): TransportResult<List<Message>>
    suspend fun markAsRead(currentUser: String, sender: String): TransportResult<Boolean>
    suspend fun getChatList(currentUser: String): TransportResult<List<ChatItem>>
    suspend fun searchUsers(query: String, currentUser: String): TransportResult<List<User>>
    suspend fun deleteMessage(messageId: Int, clientMessageId: String): TransportResult<Boolean>
    suspend fun deleteChat(user1: String, user2: String): TransportResult<Boolean>

    fun connect()
    fun disconnect()
    fun isConnected(): Boolean
    suspend fun uploadFile(file: File): TransportResult<String>
}