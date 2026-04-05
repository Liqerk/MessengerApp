package com.example.myapplication.models

import com.example.myapplication.ChatItem
import com.example.myapplication.Message
import com.example.myapplication.User
import kotlinx.serialization.Serializable

// ========== ЗАПРОСЫ ==========

@Serializable
data class RegisterRequest(
    val login: String,
    val mail: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class SendMessageRequest(
    val receiver: String,
    val text: String,
    val type: String = "text",
    val mediaUrl: String? = null,
    val duration: Int = 0,
    val replyToId: Int = 0
)

// ========== ОТВЕТЫ ==========

@Serializable
data class AuthResponse(
    val token: String,
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: Int = 0,
    val login: String,
    val mail: String,
    val isOnline: Boolean = false,
    val avatarUrl: String? = null
)

@Serializable
data class MessageDto(
    val id: Int = 0,
    val sender: String,
    val receiver: String,
    val text: String,
    val timestamp: String = "",
    val isRead: Boolean = false,
    val type: String = "text",
    val mediaUrl: String? = null,
    val duration: Int = 0,
    val replyToId: Int = 0,
    val replyToText: String? = null,
    val isFavorite: Boolean = false
)

@Serializable
data class ChatItemDto(
    val id: Int = 0,
    val name: String,
    val image: String = "",
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isPinned: Boolean = false
)

// ========== МАППИНГ ==========

fun MessageDto.toDomain() = Message(
    id = id,
    sender = sender,
    receiver = receiver,
    text = text,
    timestamp = timestamp,
    isRead = isRead,
    type = type,
    mediaUrl = mediaUrl,
    duration = duration,
    replyToId = replyToId,
    replyToText = replyToText,
    isFavorite = isFavorite
)

fun Message.toDto() = MessageDto(
    id = id,
    sender = sender,
    receiver = receiver,
    text = text,
    timestamp = timestamp,
    isRead = isRead,
    type = type,
    mediaUrl = mediaUrl,
    duration = duration,
    replyToId = replyToId,
    replyToText = replyToText,
    isFavorite = isFavorite
)

fun ChatItemDto.toDomain() = ChatItem(
    id = id,
    name = name,
    image = image,
    lastMessage = lastMessage,
    lastMessageTime = lastMessageTime,
    unreadCount = unreadCount,
    isOnline = isOnline,
    isPinned = isPinned
)

fun UserDto.toDomain() = User(
    id = id,
    login = login,
    mail = mail,
    password = "",
    isOnline = isOnline,
    avatarPath = avatarUrl
)