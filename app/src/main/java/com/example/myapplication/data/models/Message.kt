package com.example.myapplication

data class Message(
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