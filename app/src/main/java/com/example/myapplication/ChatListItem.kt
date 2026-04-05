package com.example.myapplication

sealed class ChatListItem {
    data class MessageItem(val message: Message) : ChatListItem()
    data class DateHeader(val date: String) : ChatListItem()
}