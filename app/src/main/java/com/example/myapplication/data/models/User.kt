package com.example.myapplication

data class User(
    val id: Int = 0,
    val login: String,
    val mail: String,
    val password: String,
    val isOnline: Boolean = false,
    val lastSeen: String = "",
    val avatarPath: String? = null
)