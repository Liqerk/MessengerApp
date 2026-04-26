// Создай новый файл: com/example/myapplication/models/WsEvents.kt

package com.example.myapplication.models

import kotlinx.serialization.Serializable

@Serializable
data class WsEnvelope(
    val type: String,
    val payload: String
)
@Serializable
data class WsMessagePayload(
    val receiver: String,
    val text: String,
    val replyToId: Int? = null,
    val clientMessageId: String = ""
)
@Serializable
data class WsTypingEvent(
    val sender: String,
    val receiver: String,
    val isTyping: Boolean
)

@Serializable
data class WsReadEvent(
    val sender: String,
    val reader: String
)
@Serializable
data class WsDeleteEvent(
    val messageId: Int,
    val clientMessageId: String = ""
)
@Serializable
data class WsDeleteChatEvent(
    val chatWith: String
)
@Serializable
data class WsOnlineEvent(
    val login: String,
    val isOnline: Boolean
)