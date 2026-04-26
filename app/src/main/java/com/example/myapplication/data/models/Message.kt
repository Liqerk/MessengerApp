package com.example.myapplication

data class Message(
    val id: Int = 0,
    val sender: String,
    val clientMessageId: String = "",
    val receiver: String,
    val text: String,
    val timestamp: String = "",
    val isRead: Boolean = false,
    val type: String = "text",
    val mediaUrl: String? = null,
    val duration: Int = 0,
    val replyToId: Int = 0,
    val replyToText: String? = null,
    val isFavorite: Boolean = false,
    val isSent: Boolean = true,
    val isSynced: Boolean = true
) {
    // ✅ Добавьте метод для сравнения сообщений
    fun isSameAs(other: Message): Boolean {
        return this.sender == other.sender &&
                this.receiver == other.receiver &&
                this.text == other.text &&
                this.timestamp == other.timestamp
    }
}