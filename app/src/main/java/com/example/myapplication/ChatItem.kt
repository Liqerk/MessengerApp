package com.example.myapplication

data class ChatItem(
    val id: Int,
    val name: String,
    val image: String,
    val lastMessage: String,
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isPinned: Boolean = false
)