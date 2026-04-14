package com.example.myapplication.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String,
    val receiver: String,
    val text: String,
    val timestamp: String,
    val type: String, // "text", "image", "audio"
    val mediaUrl: String? = null,
    val isRead: Boolean = false
)