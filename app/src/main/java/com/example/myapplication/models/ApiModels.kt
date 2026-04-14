package com.example.myapplication.models

import com.example.myapplication.ChatItem
import com.example.myapplication.Message
import com.example.myapplication.User
import kotlinx.serialization.Serializable

// ========== ЗАПРОСЫ ==========

@Serializable
data class RegisterRequest(
    val login: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequest(
    val login: String,
    val password: String
)

@Serializable
data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null
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
    val accessToken: String,
    val refreshToken: String = "",
    val user: UserDto
)

@Serializable
data class UserDto(
    val id: Int = 0,
    val login: String,
    val email: String? = null,
    val mail: String? = null,
    val isOnline: Boolean = false,
    val avatarUrl: String? = null
) {
    fun getUserEmail(): String = email ?: mail ?: ""

}

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
    val replyToId: Int? = null,
    val replyToText: String? = null,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false
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
    timestamp = normalizeTimestamp(timestamp),
    isRead = isRead,
    type = type.lowercase(),
    mediaUrl = mediaUrl,
    duration = duration,
    replyToId = replyToId ?: 0,
    replyToText = replyToText,
    isFavorite = isFavorite,
    isSent = true,
    isSynced = true
)

private fun normalizeTimestamp(isoTimestamp: String): String {
    return try {
        isoTimestamp
            .replace("T", " ")
            .replace("Z", "")
            .substring(0, 19)
    } catch (e: Exception) {
        isoTimestamp
    }
}

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

// ✅ ИСПРАВЛЕНО: используем getEmail() вместо прямого email
fun UserDto.toDomain() = User(
    id = id,
    login = login,
    mail = getUserEmail(),  // ← Всегда String, не null
    password = "",
    isOnline = isOnline,
    avatarPath = avatarUrl
)

fun User.toDto() = UserDto(
    id = id,
    login = login,
    email = mail,
    mail = mail,  // ← Добавляем оба поля
    isOnline = isOnline,
    avatarUrl = avatarPath
)

// ========== ДЛЯ СТАРОГО API ==========

@Serializable
data class LegacyAuthResponse(
    val token: String,
    val user: LegacyUserDto
)

@Serializable
data class LegacyUserDto(
    val id: Int = 0,
    val login: String,
    val mail: String,
    val isOnline: Boolean = false,
    val avatarUrl: String? = null
)