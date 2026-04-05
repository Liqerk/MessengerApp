package com.example.myapplication.transport

import com.example.myapplication.ChatItem
import com.example.myapplication.Message
import com.example.myapplication.User

sealed class TransportResult<out T> {
    data class Success<T>(val data: T) : TransportResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : TransportResult<Nothing>()
}

interface MessageTransport {
    // ========== АВТОРИЗАЦИЯ ==========
    suspend fun register(login: String, mail: String, password: String): TransportResult<String>
    suspend fun login(login: String, password: String): TransportResult<String>

    // ========== СООБЩЕНИЯ ==========
    suspend fun sendMessage(sender: String, receiver: String, text: String): TransportResult<Boolean>
    suspend fun getMessages(user1: String, user2: String): TransportResult<List<Message>>
    suspend fun markAsRead(currentUser: String, sender: String): TransportResult<Boolean>

    // ========== ЧАТЫ ==========
    suspend fun getChatList(currentUser: String): TransportResult<List<ChatItem>>

    // ========== ПОЛЬЗОВАТЕЛИ ==========
    suspend fun searchUsers(query: String, currentUser: String): TransportResult<List<User>>

    // ========== РЕАЛТАЙМ ==========
    fun onMessageReceived(callback: (Message) -> Unit)
    fun connect()
    fun disconnect()

    // ========== СОСТОЯНИЕ ==========
    fun isConnected(): Boolean
}